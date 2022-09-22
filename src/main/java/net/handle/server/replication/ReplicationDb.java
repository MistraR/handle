/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import net.cnri.util.StreamTable;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.Util;
import net.handle.server.HandleServer;
import net.handle.server.ServerLog;
import net.handle.server.bdbje.JeUpgradeTool;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.PreloadConfig;

public class ReplicationDb {
    private static final String HANDLE_DB_NAME = "handlesDates";
    private static final String NA_DB_NAME = "nasDates";

    private Environment environment;
    private Database db;
    private Database naDB;

    private final HandleServer server;

    public ReplicationDb(File configDir, HandleServer server, StreamTable config) throws HandleException {
        this.server = server;
        File dbDir = new File(configDir, "replicationDb");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        envConfig.setLockTimeout(config.getInt("bdbje_timeout", 0), TimeUnit.MICROSECONDS);
        envConfig.setDurability(config.getBoolean("bdbje_no_sync_on_write", false) ? Durability.COMMIT_WRITE_NO_SYNC : Durability.COMMIT_SYNC);
        envConfig.setSharedCache(true);
        envConfig.setConfigParam(EnvironmentConfig.FREE_DISK, "0");
        try {
            environment = JeUpgradeTool.openEnvironment(dbDir, envConfig);
            db = openReplicationDb(HANDLE_DB_NAME);
            naDB = openReplicationDb(NA_DB_NAME);
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.CONFIGURATION_ERROR, "Unable to open replication database");
            he.initCause(e);
            throw he;
        }
    }

    public void deleteAll() throws Exception {
        if (db != null) {
            db = deleteReplicationDb(db, HANDLE_DB_NAME);
        }
        if (naDB != null) {
            naDB = deleteReplicationDb(naDB, NA_DB_NAME);
        }
    }

    private Database openReplicationDb(String dbName) throws DatabaseException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        dbConfig.setSortedDuplicates(false);
        com.sleepycat.je.Transaction openTxn = environment.beginTransaction(null, null);
        @SuppressWarnings("hiding")
        Database db = environment.openDatabase(openTxn, dbName, dbConfig);
        openTxn.commit();
        PreloadConfig preloadCfg = new PreloadConfig();
        preloadCfg.setMaxMillisecs(500);
        db.preload(preloadCfg);
        return db;
    }

    private Database deleteReplicationDb(@SuppressWarnings("hiding") Database db, String dbName) throws DatabaseException {
        Database tmpDB = db;
        com.sleepycat.je.Transaction killTxn = environment.beginTransaction(null, null);
        try {
            db = null;
            tmpDB.close();
            tmpDB = null;
            environment.truncateDatabase(killTxn, dbName, false);
            killTxn.commit(Durability.COMMIT_SYNC);
        } catch (DatabaseException t) {
            try {
                killTxn.abort();
            } catch (Throwable t2) {
            }
            throw t;
        } finally {
            db = openReplicationDb(dbName);
        }
        return db;
    }

    private class DBIterator implements Iterator<byte[]> {
        private Cursor cursor;
        private OperationStatus status;
        private final DatabaseEntry key = new DatabaseEntry();
        private final DatabaseEntry data = new DatabaseEntry();

        public DBIterator(Database db) throws HandleException {
            try {
                this.cursor = db.openCursor(null, null);
                status = cursor.getFirst(key, data, null);
            } catch (DatabaseException e) {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception ex) {
                    }
                }
                throw new HandleException(HandleException.SERVER_ERROR, "Error constructing storage iterator", e);
            }
        }

        public DBIterator(Database db, byte[] startingPoint, boolean inclusive) throws HandleException {
            try {
                this.cursor = db.openCursor(null, null);
                key.setData(startingPoint);
                status = cursor.getSearchKeyRange(key, data, null);
                if (!inclusive) {
                    if (Util.equals(key.getData(), startingPoint)) {// and we got an exact match move to the next element
                        if (hasNext()) {
                            next();
                        } else {
                            close();
                        }
                    }
                }
            } catch (Exception e) {
                if (cursor != null) {
                    try {
                        cursor.close();
                    } catch (Exception ex) {
                    }
                }
                throw new HandleException(HandleException.SERVER_ERROR, "Error constructing storage iterator", e);
            }
        }

        @Override
        public boolean hasNext() {
            if (status == null) {
                return false;
            }
            if (status == OperationStatus.SUCCESS) {
                return true;
            }
            close();
            status = null;
            return false;
        }

        @Override
        public byte[] next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            byte[] handle = key.getData();
            byte[] buf = new byte[16 + handle.length];
            Encoder.writeInt(buf, 0, handle.length);
            System.arraycopy(handle, 0, buf, 4, handle.length);
            System.arraycopy(data.getData(), 0, buf, 4 + handle.length, 12);
            try {
                status = cursor.getNext(key, data, null);
            } catch (DatabaseException e) {
                System.err.println("Error in ReplicationDBIterator: " + e);
                status = null;
                close();
            }
            return buf;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            try {
                if (cursor != null) {
                    cursor.close();
                }
            } catch (Throwable t) {
            }
        }
    }

    public Iterator<byte[]> iterator(boolean isNA) throws HandleException {
        if (!isNA && db != null) {
            return new DBIterator(db);
        } else if (isNA && naDB != null) {
            return new DBIterator(naDB);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    public Iterator<byte[]> iteratorFrom(boolean isNA, byte[] startingPoint, boolean inclusive) throws HandleException {
        if (!isNA && db != null) {
            return new DBIterator(db, startingPoint, inclusive);
        } else if (isNA && naDB != null) {
            return new DBIterator(naDB, startingPoint, inclusive);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    public boolean isMoreRecentThanLastDate(byte[] handle, long date, int priority, boolean isNA) throws HandleException {
        @SuppressWarnings({ "resource", "hiding" })
        Database db = isNA ? this.naDB : this.db;
        if (db != null) {
            try {
                DatabaseEntry dbVal = new DatabaseEntry();
                if (db.get(null, new DatabaseEntry(handle), dbVal, null) == OperationStatus.SUCCESS) {
                    byte[] data = dbVal.getData();
                    long lastDate = Encoder.readLong(data, 0);
                    int lastPriority = Encoder.readInt(data, 8);
                    return (date > lastDate) || (date == lastDate && priority >= lastPriority);
                }
            } catch (DatabaseException e) {
                server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Database error in replication date handling: " + e);
                HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Database error in replication date handling");
                he.initCause(e);
                throw he;
            }
        }
        return true;
    }

    // synchronization note: always in server.getWriteLock(handle)
    public long adjustAndSetLastDate(byte[] handle, long date, int priority, boolean isNA) throws HandleException {
        @SuppressWarnings({ "resource", "hiding" })
        Database db = isNA ? this.naDB : this.db;
        if (db != null) {
            try {
                DatabaseEntry dbVal = new DatabaseEntry();
                if (db.get(null, new DatabaseEntry(handle), dbVal, null) == OperationStatus.SUCCESS) {
                    byte[] data = dbVal.getData();
                    long lastDate = Encoder.readLong(data, 0);
                    Encoder.readInt(data, 8);
                    date = date <= lastDate ? lastDate + 1 : date;
                }
            } catch (DatabaseException e) {
                server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Database error in replication date handling: " + e);
                HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Database error in replication date handling");
                he.initCause(e);
                throw he;
            }
        }
        setLastDate(handle, date, priority, isNA);
        return date;
    }

    // synchronization note: always in server.getWriteLock(handle), or else during dump
    public void setLastDate(byte[] handle, long date, int priority, boolean isNA) throws HandleException {
        @SuppressWarnings({ "resource", "hiding" })
        Database db = isNA ? this.naDB : this.db;
        if (db != null) {
            byte[] bytes = new byte[12];
            Encoder.writeLong(bytes, 0, date);
            Encoder.writeInt(bytes, 8, priority);
            try {
                OperationStatus status = db.put(null, new DatabaseEntry(handle), new DatabaseEntry(bytes));
                if (status != OperationStatus.SUCCESS) {
                    server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Unknown status returned from db.put: " + status);
                    throw new HandleException(HandleException.INTERNAL_ERROR, "Database error in replication date handling");
                }
            } catch (DatabaseException e) {
                server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Database error in replication date handling: " + e);
                HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Database error in replication date handling");
                he.initCause(e);
                throw he;
            }
        }
    }

    public void shutdown() {
        try {
            if (db != null) {
                db.close();
            }
        } catch (Throwable e) {
        }
        try {
            if (naDB != null) {
                naDB.close();
            }
        } catch (Throwable e) {
        }
        try {
            if (environment != null) {
                environment.close();
            }
        } catch (Throwable e) {
        }
    }

}

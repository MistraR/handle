/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.bdbje;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import net.handle.server.*;
import net.handle.server.bdbje.BDBJEHandleStorage.DBWrapper.DBIterator;

import com.sleepycat.je.*;
import com.sleepycat.je.util.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/*************************************************************
 * Class that provides a storage mechanism for handle records
 * using the Berkeley DB JE database component.
 * The records are laid out like so: <pre>
 *
 * key = handle
 * value = numValues [ valueLength  valueReceivedTime valueClump ]*
 *
 * </pre>
 *
 * Every operation involving changing JDB content should check
 * Storage's status first.
 *************************************************************/
public final class BDBJEHandleStorage implements HandleStorage {
    private static final byte[] DB_STATUS_HANDLE = Util.encodeString("0.SITE/DB_STATUS");
    private static final byte[] BDBJE_STATUS_HDL_TYPE = Util.encodeString("BDBJE_STATUS");

    private boolean dumpOnCheckpoint = true;
    private boolean enableStatusHandle = true;
    private boolean logTxns = false;

    private File mainServerDir;
    private File serverDir;
    private Environment environment = null;
    private DBTransactionLog txnLog;

    private static final String HANDLE_DB_NAME = "handles";
    private static final String NA_DB_NAME = "nas";

    private DBWrapper db = null;
    private DBWrapper naDB = null;

    private final boolean readOnly = false;

    private final static byte BLANK_BYTES[] = {};

    /** Initialize the Berkeley DB handle database and homed prefix table.
     * The StreamTable passed to this class must contain a "serverDir" value
     * that references the server directory.
     */
    @Override
    public void init(net.cnri.util.StreamTable config) throws Exception {
        // Put the database in the directory specified by "db_directory" setting,
        // resorting to the server directory if the setting is not available
        mainServerDir = new File(config.getStr("serverDir", null));
        serverDir = new File(mainServerDir, config.getStr("db_directory", "bdbje"));

        if (!serverDir.exists()) serverDir.mkdirs();

        logTxns = config.getBoolean("enable_recovery_log", false);

        if (logTxns) txnLog = new DBTransactionLog(new File(serverDir, "dbtxns.log"));

        dumpOnCheckpoint = config.getBoolean("bdbje_dump_on_checkpoint", dumpOnCheckpoint);
        enableStatusHandle = config.getBoolean("bdbje_enable_status_handle", enableStatusHandle);

        // construct the environment
        System.err.println("Opening Berkeley database in " + serverDir.getAbsolutePath());

        // create the database environment
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setReadOnly(config.getBoolean(Common.READ_ONLY_DB_STORAGE_KEY, false));
        envConfig.setAllowCreate(true);
        envConfig.setLockTimeout(config.getInt("bdbje_timeout", 0), TimeUnit.MICROSECONDS);
        envConfig.setDurability(config.getBoolean("bdbje_no_sync_on_write", false) ? Durability.COMMIT_WRITE_NO_SYNC : Durability.COMMIT_SYNC);
        envConfig.setSharedCache(true);
        envConfig.setConfigParam(EnvironmentConfig.FREE_DISK, "0");
        environment = JeUpgradeTool.openEnvironment(serverDir, envConfig);

        db = new DBWrapper(environment, HANDLE_DB_NAME);
        naDB = new DBWrapper(environment, NA_DB_NAME);
    }

    public long count() throws HandleException {
        try {
            return db.count();
        } catch (Exception e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error counting database entries", e);
        }
    }

    /*********************************************************************
     * Returns true if this server is responsible for the given prefix.
     *********************************************************************/
    @Override
    public final boolean haveNA(byte authHandle[]) throws HandleException {
        try {
            return naDB.exists(Util.upperCase(authHandle));
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error retrieving NA data");
            he.initCause(e);
            throw he;
        }
    }

    /*********************************************************************
     * Sets a flag indicating whether or not this server is responsible
     * for the given prefix.
     *********************************************************************/
    @Override
    public final void setHaveNA(byte authHandle[], boolean flag) throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }

        try {
            byte[] handle = Util.upperCaseInPlace(authHandle);
            if (flag) {
                if (logTxns) txnLog.log(DBTransactionLog.SET_NA_VALUE, handle, BLANK_BYTES);
                naDB.put(handle, BLANK_BYTES);
            } else {
                if (logTxns) txnLog.log(DBTransactionLog.DELETE_NA_VALUE, handle, BLANK_BYTES);
                naDB.del(handle);
            }
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error recording NA data");
            he.initCause(e);
            throw he;
        }
    }

    /*********************************************************************
     * Creates the specified handle in the "database" with the specified
     * initial values
     *********************************************************************/
    @Override
    public final void createHandle(byte inHandle[], HandleValue values[]) throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }
        byte[] data = bytesOfHandleValues(values);

        // make sure that the handle doesn't already exist

        byte[] handle = inHandle;
        try {
            if (logTxns) txnLog.log(DBTransactionLog.SET_HDL_VALUE, handle, data);
            OperationStatus status = db.putNoOverwrite(handle, data);
            if (status == OperationStatus.KEYEXIST) {
                throw new HandleException(HandleException.HANDLE_ALREADY_EXISTS, "Handle already exists");
            } else if (status != OperationStatus.SUCCESS) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Unknown status returned from db.putNoOverwrite: " + status);
            }
        } catch (HandleException e1) {
            throw e1;
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error creating handle");
            he.initCause(e);
            throw he;
        }
    }

    private byte[] bytesOfHandleValues(HandleValue[] values) {
        // add the size of all of the clumps as well as a 'time-received' field
        // and 'value-length' field for each value
        int totalSize = Encoder.INT_SIZE;
        for (HandleValue value : values) {
            totalSize += (Encoder.calcStorageSize(value) + Encoder.INT_SIZE);
        }

        // write all of the clumps to a buffer
        byte data[] = new byte[totalSize];
        int offst = 0;
        offst += Encoder.writeInt(data, offst, values.length);
        for (HandleValue value : values) {
            int clumpLen = Encoder.encodeHandleValue(data, offst + Encoder.INT_SIZE, value);
            offst += Encoder.writeInt(data, offst, clumpLen);
            offst += clumpLen;
        }
        return data;
    }

    /*********************************************************************
     * Delete the specified handle in the database.
     *********************************************************************/
    @Override
    public final boolean deleteHandle(byte handle[]) throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }

        try {
            if (logTxns) txnLog.log(DBTransactionLog.DELETE_HDL_VALUE, handle, BLANK_BYTES);
            return db.del(handle);
        } catch (HandleException e1) {
            throw e1;
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error deleting handle");
            he.initCause(e);
            throw he;
        }
    }

    @Override
    public boolean exists(byte[] handle) throws HandleException {
        try {
            return db.exists(handle);
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error retrieving handle");
            he.initCause(e);
            throw he;
        }
    }

    /*********************************************************************
     * Return the pre-packaged values of the given handle that are either
     * in the indexList or the typeList.  This method should return any
     * values of type ALIAS or REDIRECT, even if they were not requested.
     *********************************************************************/
    @Override
    public final byte[][] getRawHandleValues(byte handle[], int indexList[], byte typeList[][]) throws HandleException {
        byte value[] = null;
        try {
            value = db.get(handle);
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error retrieving handle");
            he.initCause(e);
            throw he;
        }
        if (value == null) {
            if (enableStatusHandle && Util.equals(handle, DB_STATUS_HANDLE)) {
                // construct and return a set of handles values containing the status of
                // the database
                List<HandleValue> vals = new ArrayList<>();

                try {
                    StringBuffer status = new StringBuffer();
                    status.append(environment.getStats(null).toString());
                    vals.add(new HandleValue(1, BDBJE_STATUS_HDL_TYPE, Util.encodeString(status.toString())));
                } catch (Exception e) {
                    vals.add(new HandleValue(2, BDBJE_STATUS_HDL_TYPE, Util.encodeString("Error getting status: " + e)));
                }

                byte valBytes[][] = new byte[vals.size()][];
                for (int i = 0; i < valBytes.length; i++) {
                    HandleValue val = vals.get(i);
                    valBytes[i] = new byte[Encoder.calcStorageSize(val)];
                    Encoder.encodeHandleValue(valBytes[i], 0, val);
                }
                return valBytes;
            }
            return null;
        }

        int clumpLen;
        int bufPos = 0;
        boolean allValues = (indexList == null || indexList.length == 0) && (typeList == null || typeList.length == 0);
        int numValues = Encoder.readInt(value, bufPos);
        bufPos += Encoder.INT_SIZE;
        int origBufPos = bufPos;

        // figure out the number of records matching this request...
        int matches = 0;
        byte clumpType[];
        int clumpIndex;
        if (allValues) {
            matches = numValues;
        } else {
            for (int i = 0; i < numValues; i++) {
                clumpLen = Encoder.readInt(value, bufPos);
                bufPos += Encoder.INT_SIZE;

                clumpType = Encoder.getHandleValueType(value, bufPos);
                clumpIndex = Encoder.getHandleValueIndex(value, bufPos);

                if (Util.isParentTypeInArray(typeList, clumpType) || Util.isInArray(indexList, clumpIndex)) matches++;

                bufPos += clumpLen;
            }
        }

        // populate and return an array with the matched records...
        byte clumps[][] = new byte[matches][];
        int clumpNum = 0;
        bufPos = origBufPos;
        for (int i = 0; i < numValues; i++) {
            clumpLen = Encoder.readInt(value, bufPos);
            bufPos += Encoder.INT_SIZE;

            clumpType = Encoder.getHandleValueType(value, bufPos);
            clumpIndex = Encoder.getHandleValueIndex(value, bufPos);

            if (allValues || Util.isParentTypeInArray(typeList, clumpType) || Util.isInArray(indexList, clumpIndex)) {
                clumps[clumpNum] = new byte[clumpLen];
                System.arraycopy(value, bufPos, clumps[clumpNum], 0, clumpLen);
                clumpNum++;
            }

            bufPos += clumpLen;
        }
        return clumps;
    }

    /*********************************************************************
     * Replace the current values for the given handle with new values.
     *********************************************************************/
    @Override
    public final void updateValue(byte handle[], HandleValue values[]) throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }
        if (!exists(handle)) {
            throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST, "Cannot modify non-existent handle");
        }
        createOrUpdateRecord(handle, values);
    }

    /*********************************************************************
     * Replace the current values for the given handle with new values.
     *********************************************************************/
    @Override
    public void createOrUpdateRecord(byte[] handle, HandleValue[] values) throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }
        byte[] data = bytesOfHandleValues(values);
        try {
            if (logTxns) txnLog.log(DBTransactionLog.SET_HDL_VALUE, handle, data);

            db.put(handle, data);
        } catch (HandleException e1) {
            throw e1;
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error updating handle");
            he.initCause(e);
            throw he;
        }
    }

    /*********************************************************************
     * Scan the database, calling a method in the specified callback for
     * every handle in the database.
     *********************************************************************/
    @Override
    public final void scanHandles(ScanCallback callback) throws HandleException {
        DBWrapper.DBIterator e = db.getEnumerator();
        try {
            while (e.hasMoreElements()) {
                byte[][] record = e.nextElement();
                callback.scanHandle(record[0]);
            }
        } finally {
            e.close();
        }
    }

    @Override
    public boolean supportsDumpResumption() {
        return true;
    }

    @Override
    public final void scanHandlesFrom(byte[] startingPoint, boolean inclusive, ScanCallback callback) throws HandleException {
        DBWrapper.DBIterator e = db.getEnumeratorFrom(startingPoint, inclusive);
        try {
            while (e.hasMoreElements()) {
                byte[][] record = e.nextElement();
                callback.scanHandle(record[0]);
            }
        } finally {
            e.close();
        }
    }

    /*********************************************************************
     * Scan the NA database, calling a method in the specified callback for
     * every prefix handle in the database.
     *********************************************************************/
    @Override
    public void scanNAs(ScanCallback callback) throws HandleException {
        DBWrapper.DBIterator e = naDB.getEnumerator();

        try {
            while (e.hasMoreElements()) {
                byte[][] record = e.nextElement();
                callback.scanHandle(record[0]);
            }
        } finally {
            e.close();
        }
    }

    @Override
    public void scanNAsFrom(byte[] startingPoint, boolean inclusive, ScanCallback callback) throws HandleException {
        DBWrapper.DBIterator e = naDB.getEnumeratorFrom(startingPoint, inclusive);
        try {
            while (e.hasMoreElements()) {
                byte[][] record = e.nextElement();
                callback.scanHandle(record[0]);
            }
        } finally {
            e.close();
        }
    }

    /*********************************************************************
     * Scan the database for handles with the given prefix
     * and return an Enumeration of byte arrays with each byte array
     * being a handle.  <i>naHdl</i> is the prefix handle
     * for the prefix for which you want to list the handles.
     *********************************************************************/
    @Override
    public final Enumeration<byte[]> getHandlesForNA(byte naHdl[]) throws HandleException {
        //if(!haveNA(naHdl)) {
        //  throw new HandleException(HandleException.INVALID_VALUE,
        //                            "The requested prefix doesn't live here");
        //}

        boolean isZeroNA = Util.startsWithCI(naHdl, Common.NA_HANDLE_PREFIX);
        if (isZeroNA) naHdl = Util.getSuffixPart(naHdl);

        return new HdlsForNAEnum(db.getEnumerator(naHdl), naHdl);
    }

    /**********************************************************************
     * This is an class that scans all of the records in a database and
     * forwards only the handles that start with the given prefix.
     **********************************************************************/
    private final class HdlsForNAEnum implements Enumeration<byte[]>, Closeable {
        private final byte prefix[];
        private byte nextHdl[] = null;
        private final DBIterator dbEnum;
        private final boolean listingDerivedPrefixes;

        HdlsForNAEnum(DBIterator dbEnum, byte na[]) {
            this.listingDerivedPrefixes = Util.startsWithCI(na, Common.NA_HANDLE_PREFIX);
            this.prefix = Util.encodeString(Util.decodeString(na));
            this.dbEnum = dbEnum;
            seekNextValue();
        }

        @Override
        public void close() {
            dbEnum.close();
        }

        private final void seekNextValue() {
            nextHdl = null;
            byte tmpRecord[][];
            while (dbEnum.hasMoreElements()) {
                tmpRecord = dbEnum.nextElement();
                if (Util.startsWithCI(tmpRecord[0], prefix)) {
                    if (tmpRecord[0].length > prefix.length && (listingDerivedPrefixes ? tmpRecord[0][prefix.length] == (byte) '.' : tmpRecord[0][prefix.length] == (byte) '/')) {
                        nextHdl = tmpRecord[0];
                        return;
                    }
                }
            }
        }

        @Override
        public final boolean hasMoreElements() {
            return nextHdl != null;
        }

        @Override
        public final byte[] nextElement() {
            byte thisHdl[] = nextHdl;
            seekNextValue();
            return thisHdl;
        }
    }

    /*********************************************************************
     * Remove all of the records from the database.
     ********************************************************************/
    @Override
    public final void deleteAllRecords() throws HandleException {
        // check if write operations are allowed.
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY);
        }

        try {
            if (logTxns) txnLog.log(DBTransactionLog.DELETE_EVERYTHING, BLANK_BYTES, BLANK_BYTES);
            db.deleteAllRecords();
            naDB.deleteAllRecords();
        } catch (HandleException e1) {
            throw e1;
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error deleting all records");
            he.initCause(e);
            throw he;
        }
    }

    /*********************************************************************
     * Copy the current database to a backup file, and restart the transaction log.
     * Returns true if the checkpoint process was *started* (this doesn't mean
     * it was successful).
     *********************************************************************/
    @Override
    public final synchronized void checkpointDatabase() throws HandleException {
        try {
            environment.checkpoint(null);

            if (logTxns) txnLog.reset();
        } catch (Exception e) {
            HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error performing checkpoint");
            he.initCause(e);
            throw he;
        }
        if (dumpOnCheckpoint) {
            Calendar cal = Calendar.getInstance();
            int backupDate = cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
            int backupTime = cal.get(Calendar.HOUR_OF_DAY) * 100 + cal.get(Calendar.MINUTE);
            String backupSuffix = "-" + backupDate + ":" + backupTime + ".dump";

            try {
                FileOutputStream fout = new FileOutputStream(new File(mainServerDir, "handles" + backupSuffix));
                PrintStream out = new PrintStream(new BufferedOutputStream(fout, 1000000));
                new DbDump(environment, HANDLE_DB_NAME, out, false).dump();
                out.close();

                fout = new FileOutputStream(new File(mainServerDir, "nas" + backupSuffix));
                out = new PrintStream(new BufferedOutputStream(fout, 1000000));
                new DbDump(environment, NA_DB_NAME, out, false).dump();
                out.close();
            } catch (Throwable t) {
                System.err.println("Error dumping database: " + t);
                t.printStackTrace(System.err);
                HandleException he = new HandleException(HandleException.INTERNAL_ERROR, "Error performing backup");
                he.initCause(t);
                throw he;
            }
        }
    }

    /*********************************************************************
     * Close the database and clean up
     *********************************************************************/
    @Override
    public final void shutdown() {
        try {
            db.close();
        } catch (Throwable e) {
        }
        try {
            naDB.close();
        } catch (Throwable e) {
        }
        try {
            if (logTxns) txnLog.shutdown();
        } catch (Throwable e) {
        }
        try {
            environment.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Simple use of berkely db
     **/
    public static class DBWrapper {
        private Database db;
        private final String dbName;
        private final Environment environment;

        public DBWrapper(Environment environment, String dbName) throws Exception {
            this.environment = environment;
            this.dbName = dbName;

            openDB();
        }

        private void openDB() throws Exception {
            com.sleepycat.je.Transaction openTxn = environment.beginTransaction(null, null);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setTransactional(true);
            dbConfig.setAllowCreate(true);
            dbConfig.setSortedDuplicates(false);
            dbConfig.setReadOnly(environment.getConfig().getReadOnly());
            db = environment.openDatabase(openTxn, dbName, dbConfig);
            openTxn.commit();
            PreloadConfig preloadCfg = new PreloadConfig();
            preloadCfg.setMaxMillisecs(500);
            db.preload(preloadCfg);
        }

        public boolean exists(byte key[]) throws DatabaseException {
            DatabaseEntry dbVal = new DatabaseEntry();
            dbVal.setPartial(0, 0, true);
            if (db.get(null, new DatabaseEntry(key), dbVal, null) == OperationStatus.SUCCESS) {
                return true;
            }
            return false;
        }

        public byte[] get(byte key[]) throws DatabaseException {
            DatabaseEntry dbVal = new DatabaseEntry();
            if (db.get(null, new DatabaseEntry(key), dbVal, null) == OperationStatus.SUCCESS) {
                return dbVal.getData();
            }
            return null;
        }

        public void put(byte key[], byte data[]) throws DatabaseException, HandleException {
            OperationStatus status = db.put(null, new DatabaseEntry(key), new DatabaseEntry(data));
            if (status != OperationStatus.SUCCESS) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Unknown status returned from db.put: " + status);
            }
        }

        public OperationStatus putNoOverwrite(byte[] key, byte[] data) throws DatabaseException {
            return db.putNoOverwrite(null, new DatabaseEntry(key), new DatabaseEntry(data));
        }

        public void close() throws DatabaseException {
            db.close();
        }

        public long count() throws DatabaseException {
            return db.count();
        }

        public boolean del(byte key[]) throws DatabaseException {
            return db.delete(null, new DatabaseEntry(key)) == OperationStatus.SUCCESS;
        }

        public DBIterator getEnumerator() throws HandleException {
            return new DBIterator();
        }

        public DBIterator getEnumerator(byte filter[]) throws HandleException {
            return new DBIterator(filter);
        }

        public DBIterator getEnumeratorFrom(byte[] startingPoint, boolean inclusive) throws HandleException {
            return new DBIterator(startingPoint, inclusive);
        }

        public void deleteAllRecords() throws Exception {
            Database tmpDB = db;
            com.sleepycat.je.Transaction killTxn = environment.beginTransaction(null, null);
            try {
                db = null;
                tmpDB.close();
                tmpDB = null;
                environment.truncateDatabase(killTxn, dbName, false);
                killTxn.commit(Durability.COMMIT_SYNC);
            } catch (Exception t) {
                try { killTxn.abort(); } catch (Throwable t2) {}
                throw t;
            } finally {
                if (db == null) { // try to re-open the database, if necessary
                    openDB();
                }
            }
        }

        class DBIterator implements Enumeration<byte[][]>, Closeable {
            private Cursor cursor = null;
            private final DatabaseEntry keyEntry = new DatabaseEntry();
            private final DatabaseEntry valEntry = new DatabaseEntry();
            private OperationStatus lastStatus = null;
            byte currentValue[][] = null;
            byte prefix[] = null;

            public DBIterator() throws HandleException {
                try {
                    cursor = db.openCursor(null, null);
                    lastStatus = cursor.getFirst(keyEntry, valEntry, null);
                    //System.err.println("opening iterator: status="+lastStatus+"; key="+keyEntry);

                    if (lastStatus != null && lastStatus == OperationStatus.SUCCESS) {
                        loadValueFromEntries();
                    } else {
                        cursor.close();
                    }

                } catch (Exception e) {
                    if (cursor != null) try { cursor.close(); } catch (Exception ex) { }
                    throw new HandleException(HandleException.SERVER_ERROR, "Error constructing storage iterator", e);
                }
            }

            public DBIterator(byte[] startingPoint, boolean inclusive) throws HandleException {
                try {
                    cursor = db.openCursor(null, null);
                    keyEntry.setData(startingPoint);
                    lastStatus = cursor.getSearchKeyRange(keyEntry, valEntry, null);
                    if (lastStatus != null && lastStatus == OperationStatus.SUCCESS) {
                        loadValueFromEntries();
                    } else {
                        cursor.close();
                    }
                    if (!inclusive) { //and we got an exact match move to the next element
                        if (Util.equals(keyEntry.getData(), startingPoint)) {
                            if (hasMoreElements()) {
                                nextElement();
                            }
                        }
                    }
                } catch (Exception e) {
                    if (cursor != null) try { cursor.close(); } catch (Exception ex) { }
                    throw new HandleException(HandleException.SERVER_ERROR, "Error constructing storage iterator", e);
                }
            }

            public DBIterator(byte prefixFilter[]) throws HandleException {
                this.prefix = prefixFilter;
                try {
                    cursor = db.openCursor(null, null);
                    keyEntry.setData(this.prefix);
                    lastStatus = cursor.getSearchKeyRange(keyEntry, valEntry, null);
                    //System.err.println("opening iterator: filter="+
                    //                   Util.decodeString(prefixFilter)+
                    //                   " status="+lastStatus+"; key="+keyEntry);

                    if (lastStatus != null && lastStatus == OperationStatus.SUCCESS) {
                        // check to see if the next value starts with the prefix
                        if (Util.startsWithCI(keyEntry.getData(), this.prefix)) {
                            loadValueFromEntries();
                        } else {
                            cursor.close();
                            lastStatus = null;
                        }
                    } else {
                        cursor.close();
                    }
                } catch (Exception e) {
                    if (cursor != null) try { cursor.close(); } catch (Exception ex) { }
                    throw new HandleException(HandleException.SERVER_ERROR, "Error constructing storage iterator", e);
                }
            }

            @Override
            public synchronized boolean hasMoreElements() {
                return lastStatus == OperationStatus.SUCCESS;
            }

            private void loadValueFromEntries() throws Exception {
                byte b[][] = { new byte[keyEntry.getSize()], new byte[valEntry.getSize()] };
                System.arraycopy(keyEntry.getData(), 0, b[0], 0, b[0].length);
                System.arraycopy(valEntry.getData(), 0, b[1], 0, b[1].length);
                this.currentValue = b;
            }

            @Override
            public synchronized byte[][] nextElement() throws java.util.NoSuchElementException {
                if (cursor == null || lastStatus == null || lastStatus != OperationStatus.SUCCESS) throw new java.util.NoSuchElementException();

                byte returnVal[][] = currentValue;

                // fetch the next item....
                try {
                    lastStatus = cursor.getNext(keyEntry, valEntry, null);
                    if (lastStatus == null || lastStatus != OperationStatus.SUCCESS) {
                        lastStatus = null;
                        cursor.close();
                    } else {
                        loadValueFromEntries();

                        // check to see if the next value starts with the prefix
                        if (prefix != null && !Util.startsWithCI(this.currentValue[0], this.prefix)) {
                            cursor.close();
                            lastStatus = null;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error scanning handles: " + e);
                    e.printStackTrace(System.err);
                    try { cursor.close(); } catch (Throwable t) {}
                    cursor = null;
                    lastStatus = null;
                }
                return returnVal;
            }

            @Override
            public void close() {
                if (cursor != null) try { cursor.close(); } catch (Exception ex) { }
            }

        }

    }

    private final Random testR = new Random();
    private String testPrefix = "";
    private long testStopTime = 0;
    private Thread testReader = null;
    private Thread testWriter = null;
    private Thread testScanner = null;
    private Thread testDeleter = null;

    private final String getRandomTestHandle() {
        return testPrefix + (testR.nextInt() % 1000);
    }

    private final HandleValue[] testValues = { new HandleValue(1, Util.encodeString("URL"), Util.encodeString("http://handle.net")), new HandleValue(2, Util.encodeString("EMAIL"), Util.encodeString("hdladmin@cnri.reston.va.us")),
            new HandleValue(3, Util.encodeString("DESC"), Util.encodeString("Test handle")), };

    /** This spawns a bunch of threads that all read, write, and scan the
     * database at the same time in an effort to find concurrency issues. */
    void runTests(String prefix) {
        testPrefix = prefix.indexOf('/') < 0 ? prefix + "/test-" : prefix;
        testStopTime = System.currentTimeMillis() + (1000 * 120); // 2 minutes
        Runnable reader = () -> {
            long numReads = 0;
            while (System.currentTimeMillis() < testStopTime) {
                String hdl = getRandomTestHandle();
                try {
                    getRawHandleValues(Util.encodeString(hdl), null, null);
                    numReads++;
                } catch (Throwable t) {
                    System.err.println("Error reading <hdl:" + hdl + ">: " + t);
                    t.printStackTrace(System.err);
                }
            }
            System.out.println("Read " + numReads + " handles");
        };

        Runnable writer = () -> {
            long numWrites = 0;
            while (System.currentTimeMillis() < testStopTime) {
                String hdl = getRandomTestHandle();
                try {
                    byte hdlb[] = Util.encodeString(hdl);
                    if (getRawHandleValues(hdlb, null, null) == null) {
                        createHandle(hdlb, testValues);
                    } else {
                        updateValue(hdlb, testValues);
                    }
                    numWrites++;
                } catch (Throwable t) {
                    System.err.println("Error writing <hdl:" + hdl + ">: " + t);
                    t.printStackTrace(System.err);
                }
            }
            System.out.println("Wrote " + numWrites + " handles");
        };

        Runnable scanner = () -> {
            long numGets = 0;
            long numScans = 0;
            String prefixToScan = testPrefix;
            byte prefixb[] = Util.upperCaseInPlace(Util.encodeString(prefixToScan));

            while (System.currentTimeMillis() < testStopTime) {
                try {
                    Enumeration<byte[]> en = getHandlesForNA(prefixb);
                    while (en.hasMoreElements()) {
                        en.nextElement();
                        numGets++;
                    }
                    numScans++;
                } catch (Throwable t) {
                    System.err.println("Error scanning prefix " + prefixToScan + ": " + t);
                    t.printStackTrace(System.err);
                }
            }
            System.out.println("Iterated over " + numGets + " handles");
            System.out.println("Completed " + numScans + " scans on prefix " + prefixToScan);
        };

        Runnable deleter = () -> {
            long numDeletes = 0;
            while (System.currentTimeMillis() < testStopTime) {
                String hdl = getRandomTestHandle();
                try {
                    byte hdlb[] = Util.encodeString(hdl);
                    if (getRawHandleValues(hdlb, null, null) != null) {
                        deleteHandle(hdlb);
                    }
                    numDeletes++;
                } catch (Throwable t) {
                    System.err.println("Error deleting <hdl:" + hdl + ">: " + t);
                    t.printStackTrace(System.err);
                }
            }
            System.out.println("Deleted " + numDeletes + " handles");
        };

        testReader = new Thread(reader);
        testWriter = new Thread(writer);
        testScanner = new Thread(scanner);
        testDeleter = new Thread(deleter);

        testReader.start();
        testWriter.start();
        testScanner.start();
        testDeleter.start();

        try {
            testReader.join();
            testWriter.join();
            testScanner.join();
            testDeleter.join();
        } catch (Exception e) {
            System.err.println("Error re-joining threads: " + e);
            e.printStackTrace(System.err);
        }
    }

    public static final void main(String argv[]) throws Exception {
        String usage = "usage: java net.handle.server.bdbje.BDBJEHandleStorage " + "<dbdir> (list <prefix> | get <handle> | test <hdlprefix>)";
        if (argv.length < 2 || argv.length > 3) {
            System.err.println(usage);
            System.exit(1);
        }
        String dbdir = argv[0];
        String cmd = argv[1];
        String param = argv.length > 2 ? argv[2] : null;

        StreamTable config = new StreamTable();
        config.put("serverDir", dbdir);
        config.put("enable_recovery_log", false);
        config.put("bdbje_timeout", 0);
        config.put(Common.READ_ONLY_DB_STORAGE_KEY, true);

        BDBJEHandleStorage bdb = new BDBJEHandleStorage();
        bdb.init(config);
        if (cmd.equalsIgnoreCase("list")) {
            if (param == null) {
                System.err.println(usage);
                System.exit(1);
            }
            Enumeration<byte[]> en = bdb.getHandlesForNA(Util.upperCaseInPlace(Util.encodeString(param)));
            while (en.hasMoreElements()) {
                System.out.println(Util.decodeString(en.nextElement()));
            }
        } else if (cmd.equalsIgnoreCase("scan")) {
            bdb.scanHandles(handle -> System.out.println(Util.decodeString(handle)));
        } else if (cmd.equalsIgnoreCase("get")) {
            if (param == null) {
                System.err.println(usage);
                System.exit(1);
            }
            byte rawvals[][] = bdb.getRawHandleValues(Util.encodeString(param), null, null);
            System.out.println(param + ": (" + rawvals.length + ") values");
            for (byte[] rawval : rawvals) {
                HandleValue val = new HandleValue();
                Encoder.decodeHandleValue(rawval, 0, val);
                System.out.println(val.toString());
            }
        } else if (cmd.equalsIgnoreCase("test")) {
            if (param == null) {
                System.err.println(usage);
                System.exit(1);
            }
            bdb.runTests(param);
        } else {
            System.err.println("Unknown command: " + cmd);
            System.exit(1);
        }
        System.exit(0);
    }

}

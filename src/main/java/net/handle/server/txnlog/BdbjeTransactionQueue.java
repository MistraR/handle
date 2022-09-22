/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import com.sleepycat.je.*;

import net.cnri.util.StreamTable;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Transaction;
import net.handle.hdllib.TransactionScannerInterface;
import net.handle.server.HandleServer;
import net.handle.server.bdbje.JeUpgradeTool;

/**
 * Class responsible for keeping track of the transactions.
 * Transactions are stored in a BerkleyDB.
 * The berkelydb  database files will be stored in a subfolder of /txns
 */
public class BdbjeTransactionQueue extends AbstractTransactionQueue {
    private static final String DB_DIR_NAME = "db"; //The berkelydb will be stored in a subfolder of /txns
    private final Environment dbEnvironment;
    private final Database txnLogDatabase;
    private volatile long lastTxnId = 0;
    private volatile long firstDate = Long.MAX_VALUE;
    private boolean shutdown;
    private final boolean readonly;

    public BdbjeTransactionQueue(File queueDir, StreamTable config) throws Exception {
        File dbDir = new File(queueDir, DB_DIR_NAME);
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        this.readonly = config.getBoolean(HandleServer.READ_ONLY_TXN_QUEUE, false);
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setSharedCache(true);
        envConfig.setTransactional(true);
        envConfig.setLockTimeout(config.getInt("bdbje_timeout", 0), TimeUnit.MICROSECONDS);
        envConfig.setDurability(config.getBoolean("bdbje_no_sync_on_write", false) ? Durability.COMMIT_WRITE_NO_SYNC : Durability.COMMIT_SYNC);
        envConfig.setReadOnly(readonly);
        envConfig.setConfigParam(EnvironmentConfig.FREE_DISK, "0");
        dbEnvironment = JeUpgradeTool.openEnvironment(dbDir, envConfig);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(true);
        dbConfig.setReadOnly(readonly);
        txnLogDatabase = dbEnvironment.openDatabase(null, "txnLogDatabase", dbConfig);
        lastTxnId = calculateLastTxnId();
        firstDate = calculateFirstDate();
    }

    @Override
    public long getLastTxnId() {
        return lastTxnId;
    }

    @Override
    public long getFirstDate() {
        return firstDate;
    }

    private long calculateFirstDate() {
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            try (Cursor cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED)) {
                OperationStatus status = cursor.getNext(key, data, LockMode.READ_UNCOMMITTED); //gets the first entry in the db
                if (status == OperationStatus.NOTFOUND) {
                    return Long.MAX_VALUE;
                } else if (status == OperationStatus.SUCCESS) {
                    return Encoder.readLong(data.getData(), 9);
                } else {
                    throw new RuntimeException("Error getting first date; status " + status);
                }
            }
        } catch (DatabaseException e) {
            throw new RuntimeException("Error getting first date", e);
        }
    }

    private long calculateLastTxnId() {
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            try (Cursor cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED)) {
                OperationStatus status = cursor.getPrev(key, data, LockMode.READ_UNCOMMITTED); //gets the last entry in the db
                if (status == OperationStatus.NOTFOUND) {
                    return 0;
                } else if (status == OperationStatus.SUCCESS) {
                    return Encoder.readLong(key.getData(), 0);
                } else {
                    throw new RuntimeException("Error getting last transaction id; status " + status);
                }
            }
        } catch (DatabaseException e) {
            throw new RuntimeException("Error getting last transaction id", e);
        }
    }

    @Override
    public void addTransaction(long txnId, byte[] handle, HandleValue[] values, byte action, long date) throws Exception {
        if (readonly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Transaction queue is read-only");
        }
        if (txnId <= 0) {
            throw new HandleException(HandleException.INVALID_VALUE, "An attempt was made to store a transaction with zero or negative txnId.");
        }
        lastTxnId = txnId;
        if (firstDate == Long.MAX_VALUE) firstDate = date;
        Transaction txn = new Transaction(txnId, handle, values, action, date);
        BytesMap bytesMap = new BytesMap(txn);
        txnLogDatabase.put(null, bytesMap.getKey(), bytesMap.getData());
        notifyQueueListeners(txn);
    }

    @Override
    public synchronized void shutdown() {
        if (shutdown) return;
        shutdownQueueListeners();
        try {
            shutdown = true;
            if (txnLogDatabase != null) {
                try {
                    txnLogDatabase.close();
                } catch (IllegalStateException e) {
                    // wait for cursors to close
                    Thread.sleep(1000);
                    txnLogDatabase.close();
                }
            }
            if (dbEnvironment != null) {
                dbEnvironment.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing environment");
            e.printStackTrace();
        }
    }

    @Override
    public void deleteUntilDate(long date) {
        long lastId = getLastIdBeforeDate(date);
        deleteUpToAndIncludingId(lastId);
    }

    private long getLastIdBeforeDate(long date) {
        try (Cursor cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED)) {
            if (date < 0) date = 0;
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            long lastId = -1; // always either -1 or the id of a transaction which is before date and not the last txn
            long currId = -1; // always either -1 or the id of a transaction which is before date
            while (cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                if (shutdown) return -1;
                lastId = currId;
                Transaction txn = readTxn(data.getData());
                if (txn.date >= date) {
                    return lastId;
                }
                currId = txn.txnId;
            }
            // either no transactions, or the very last transaction is also old, in which case we keep just it
            return lastId;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteUpToAndIncludingId(long lastId) {
        boolean done = false;
        while (!done) {
            com.sleepycat.je.Transaction dbTxn = txnLogDatabase.getEnvironment().beginTransaction(null, null);
            try (Cursor cursor = txnLogDatabase.openCursor(dbTxn, CursorConfig.READ_UNCOMMITTED)) {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                int count = 0;
                done = true;
                while (count < 1000 && cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                    done = false;
                    if (shutdown) {
                        done = true;
                        break;
                    }
                    Transaction txn = readTxn(data.getData());
                    if (txn.txnId <= lastId) {
                        cursor.delete();
                        count++;
                    } else {
                        done = true;
                        break;
                    }
                }
                if (count == 0) done = true;
                cursor.close();
            } catch (Exception e) {
                dbTxn.abort();
                dbTxn = null;
                throw new RuntimeException(e);
            } finally {
                if (dbTxn != null) {
                    dbTxn.commit();
                }
            }
            firstDate = calculateFirstDate();
        }
    }

    @Override
    public TransactionScannerInterface getScanner(@SuppressWarnings("hiding") long lastTxnId) throws Exception {
        return new QueueScanner(lastTxnId);
    }

    public static byte[] toByteArray(long data) {
        return new byte[] { (byte) ((data >> 56) & 0xff), (byte) ((data >> 48) & 0xff), (byte) ((data >> 40) & 0xff), (byte) ((data >> 32) & 0xff), (byte) ((data >> 24) & 0xff), (byte) ((data >> 16) & 0xff), (byte) ((data >> 8) & 0xff),
                (byte) ((data >> 0) & 0xff), };
    }

    public static long fromByteArray(byte[] bytes) {
        long result = 0;
        for (byte b : bytes) {
            result = (result << 8) + (b & 0xff);
        }
        return result;
    }

    /**
     * Wraps a Transaction a key value pair of byte arrays for use with BerkelyDB
     */
    private static class BytesMap {

        private final byte[] key;
        private final byte[] data;

        public BytesMap(Transaction txn) throws IOException {
            key = toByteArray(txn.txnId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            encodeTransaction(txn, out);
            data = out.toByteArray();
        }

        public DatabaseEntry getKey() {
            return new DatabaseEntry(key);
        }

        public DatabaseEntry getData() {
            return new DatabaseEntry(data);
        }
    }

    private static void encodeTransaction(Transaction txn, OutputStream out) throws IOException {
        byte[] buf = new byte[8];
        Encoder.writeLong(buf, 0, txn.txnId);
        out.write(buf);
        out.write(txn.action);
        Encoder.writeLong(buf, 0, txn.date);
        out.write(buf);
        Encoder.writeInt(buf, 0, txn.hashOnAll);
        out.write(buf, 0, 4);
        Encoder.writeInt(buf, 0, txn.hashOnNA);
        out.write(buf, 0, 4);
        Encoder.writeInt(buf, 0, txn.hashOnId);
        out.write(buf, 0, 4);
        Encoder.writeInt(buf, 0, txn.handle.length);
        out.write(buf, 0, 4);
        out.write(txn.handle);
    }

    static Transaction readTxn(byte[] buf) {
        Transaction txn = new Transaction();
        txn.txnId = Encoder.readLong(buf, 0);
        txn.action = buf[8];
        txn.date = Encoder.readLong(buf, 9);
        txn.hashOnAll = Encoder.readInt(buf, 17);
        txn.hashOnNA = Encoder.readInt(buf, 21);
        txn.hashOnId = Encoder.readInt(buf, 25);
        try {
            txn.handle = Encoder.readByteArray(buf, 29);
        } catch (HandleException e) {
            throw new RuntimeException(e);
        }
        return txn;
    }

    //converts transactions to strings and writes them to standard out
    // args[0] is the dir of the txns db
    public static void main(String[] args) throws Exception {
        long lastTxnId = 0;
        if (args.length == 0) {
            System.out.println("Queue dir missing from args.");
            return;
        }
        String dirName = args[0];
        File dir = new File(dirName);
        if (!dir.exists()) {
            System.out.println(dirName + " directory is missing.");
        }
        if (args.length > 1) {
            lastTxnId = Long.parseLong(args[1]);
        }

        StreamTable config = new StreamTable();
        config.put(HandleServer.READ_ONLY_TXN_QUEUE, true);
        config.put("bdbje_no_sync_on_write", false);

        BdbjeTransactionQueue queue = new BdbjeTransactionQueue(dir, config);
        QueueScanner scanner = (QueueScanner) queue.getScanner(lastTxnId);

        Transaction txn = null;
        while ((txn = scanner.nextTransaction()) != null) {
            System.out.println(txn.toString());
        }
        scanner.close();
    }

    private class QueueScanner implements TransactionScannerInterface {
        private Cursor cursor;

        private Transaction next = null;

        QueueScanner(long afterTxnId) throws Exception {
            // For unclear reasons mirroring servers may request transactions after -1.
            // Our BDBJE key format doesn't have correct ordering for negative numbers and so things break.
            // We know that our first transaction will be 1 and so we simply start after 0 instead.
            if (afterTxnId < 0) afterTxnId = 0;
            DatabaseEntry key = new DatabaseEntry(toByteArray(afterTxnId));
            DatabaseEntry data = new DatabaseEntry();
            cursor = txnLogDatabase.openCursor(null, CursorConfig.READ_UNCOMMITTED);
            try {
                OperationStatus status = cursor.getSearchKeyRange(key, data, LockMode.READ_UNCOMMITTED); //moves cursor to the record at afterDate
                if (status != OperationStatus.SUCCESS) {
                    cursor.close();
                    cursor = null;
                } else {
                    long keyAsLong = fromByteArray(key.getData());
                    if (keyAsLong > afterTxnId) {
                        //there wasn't an exact match in the search so the current is already greater than afterdate
                        next = readTxn(data.getData());
                    }
                }
            } catch (Exception e) {
                close();
                throw e;
            }
        }

        @Override
        public Transaction nextTransaction() {
            if (next == null) {
                return getNextFromDB();
            } else {
                Transaction result = next;
                next = null;
                return result;
            }
        }

        private Transaction getNextFromDB() {
            if (cursor == null) return null;
            if (shutdown) {
                cursor.close();
                cursor = null;
                return null;
            }
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            try {
                boolean success = cursor.getNext(key, data, LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS;
                if (success) {
                    Transaction result = readTxn(data.getData());
                    return result;
                } else {
                    cursor.close();
                    cursor = null;
                    return null;
                }
            } catch (DatabaseException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            if (cursor == null) return;
            try {
                cursor.close();
                cursor = null;
            } catch (DatabaseException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.handle.hdllib.*;
import net.handle.jdb.*;
import java.io.*;
import java.util.Enumeration;

/*************************************************************
 * Class that provides a storage mechanism for handle records
 * using the JDB simple database object.
 * The records are laid out like so: <pre>
 *
 * key = handle-name
 * value = numValues [ valueLength  valueReceivedTime valueClump ]*
 *
 * </pre>
 *
 * Every operation involved change JDB content should check
 * Storage's status firstly, if it is read_only, throw
 * STORAGE_READONLY exception, otherwise, write DBTransaction
 * Log(write_head), execute, write Replication Transaction Log.
 *************************************************************/
public final class JDBHandleStorage implements HandleStorage

{
    private final File serverDir;
    private DBHash db;
    private DBHash naDB;
    private boolean logTxns = true;
    private DBTransactionLog txnLog;

    private final String WRITE_LOCK = "WRITE_LOCK";
    private final String READ_LOCK = "READ_LOCK";
    private boolean readOnly = false;

    private final static byte[] BLANK_BYTES = new byte[0];

    /*************************************************************
     * Constructor:
     * @param serverDir     Directory where the server/database files are located
     ************************************************************/
    public JDBHandleStorage(File serverDir, boolean logTxns) throws Exception {
        this.serverDir = serverDir;
        this.logTxns = logTxns;
    }

    /** Initialize the JDB handle database with the given configuration.
      This currently doesn't do anything for the JDB database.
     */
    @Override
    public void init(net.cnri.util.StreamTable config) throws Exception {
        readOnly = config.getBoolean(Common.READ_ONLY_DB_STORAGE_KEY, false);
        db = new DBHash(new File(serverDir, "handles.jdb"), 5000, 1000, readOnly);
        naDB = new DBHash(new File(serverDir, "nas.jdb"), 1000, 500, readOnly);
        if (logTxns) txnLog = new DBTransactionLog(new File(serverDir, "dbtxns.log"));
    }

    /*********************************************************************
     * Returns true if this server is responsible for the given prefix.
     *********************************************************************/
    @Override
    public final boolean haveNA(byte authHandle[]) throws HandleException {
        try {
            return naDB.getValue(Util.upperCase(authHandle)) != null;
        } catch (Exception e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error accessing NA data");
        }
    }

    /*********************************************************************
     * Sets a flag indicating whether or not this server is responsible
     * for the given prefix.
     *********************************************************************/
    @Override
    public final void setHaveNA(byte authHandle[], boolean flag) throws HandleException {
        synchronized (WRITE_LOCK) {
            // check if write operations are allowed.
            if (readOnly) {
                throw new HandleException(HandleException.STORAGE_RDONLY);
            }

            try {
                byte[] handle = Util.upperCase(authHandle);
                if (flag) {
                    if (logTxns) txnLog.log(DBTransactionLog.SET_NA_VALUE, handle, BLANK_BYTES);
                    naDB.setValue(handle, BLANK_BYTES);
                } else {
                    if (logTxns) txnLog.log(DBTransactionLog.DELETE_NA_VALUE, handle, BLANK_BYTES);
                    naDB.deleteValue(handle);
                }
            } catch (HandleException e1) {
                throw e1;
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Error accessing NA data");
            }
        } //end synchronized
    }

    /*********************************************************************
     * Creates the specified handle in the "database" with the specified
     * initial values
     *********************************************************************/
    @Override
    public final void createHandle(byte inHandle[], HandleValue values[]) throws HandleException {
        synchronized (WRITE_LOCK) {
            synchronized (READ_LOCK) {
                // check if write operations are allowed.
                if (readOnly) {
                    throw new HandleException(HandleException.STORAGE_RDONLY);
                }

                byte[] handle = inHandle;

                byte existingValue[] = null;
                try {
                    existingValue = db.getValue(handle);
                } catch (Exception e) {
                    throw new HandleException(HandleException.INTERNAL_ERROR, "Error checking for existing handle");
                }
                if (existingValue != null) throw new HandleException(HandleException.HANDLE_ALREADY_EXISTS, "Handle already exists");

                // add the size of all of the clumps as well as a 'time-received' field
                // and 'value-length' field for each value
                int totalSize = Encoder.INT_SIZE;
                for (int i = 0; i < values.length; i++) {
                    totalSize += (Encoder.calcStorageSize(values[i]) + Encoder.INT_SIZE);
                }

                // write all of the clumps to a buffer
                byte data[] = new byte[totalSize];
                int offst = 0;
                offst += Encoder.writeInt(data, offst, values.length);
                for (int i = 0; i < values.length; i++) {
                    int clumpLen = Encoder.encodeHandleValue(data, offst + Encoder.INT_SIZE, values[i]);
                    offst += Encoder.writeInt(data, offst, clumpLen);
                    offst += clumpLen;
                }

                try {
                    if (logTxns) txnLog.log(DBTransactionLog.SET_HDL_VALUE, handle, data);
                    db.setValue(handle, data);
                } catch (HandleException e1) {
                    throw e1;
                } catch (Exception e) {
                    throw new HandleException(HandleException.INTERNAL_ERROR, "Error creating handle");
                }
            }
        } //end synchronized
    }

    /*********************************************************************
     * Delete the specified handle in the database.
     *********************************************************************/
    @Override
    public final boolean deleteHandle(byte inHandle[]) throws HandleException {
        synchronized (WRITE_LOCK) {
            //check if write operations are allowed.
            if (readOnly) {
                throw new HandleException(HandleException.STORAGE_RDONLY);
            }

            byte[] handle = inHandle;

            boolean deleted;
            try {
                if (logTxns) txnLog.log(DBTransactionLog.DELETE_HDL_VALUE, handle, BLANK_BYTES);
                deleted = db.deleteValue(handle);
            } catch (HandleException e1) {
                throw e1;
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Error deleting handle");
            }
            return deleted;
        } //end synchronized
    }

    /*********************************************************************
     * Return the pre-packaged values of the given handle that are either
     * in the indexList or the typeList.  This method should return any
     * values of type ALIAS or REDIRECT, even if they were not requested.
     *********************************************************************/
    @Override
    public final byte[][] getRawHandleValues(byte inHandle[], int indexList[], byte typeList[][]) throws HandleException {

        byte[] handle = inHandle;

        byte value[];
        synchronized (READ_LOCK) {
            try {
                value = db.getValue(handle);
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Error retrieving handle");
            }
        }

        if (value == null) return null;

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
    public final void updateValue(byte inHandle[], HandleValue values[]) throws HandleException {
        synchronized (WRITE_LOCK) {
            // check if write operations are allowed.
            if (readOnly) {
                throw new HandleException(HandleException.STORAGE_RDONLY);
            }

            byte[] handle = inHandle;

            byte existingValue[] = null;
            try {
                existingValue = db.getValue(handle);
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Error checking for existing handle");
            }
            if (existingValue == null) throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST, "Cannot modify non-existent handle");

            // add the size of all of the clumps as well as a 'time-received' field
            // and 'value-length' field for each value
            int totalSize = Encoder.INT_SIZE;
            for (int i = 0; i < values.length; i++) {
                totalSize += (Encoder.calcStorageSize(values[i]) + Encoder.INT_SIZE);
            }

            // write all of the clumps to a buffer
            byte data[] = new byte[totalSize];
            int offst = 0;
            offst += Encoder.writeInt(data, offst, values.length);
            for (int i = 0; i < values.length; i++) {
                int clumpLen = Encoder.encodeHandleValue(data, offst + Encoder.INT_SIZE, values[i]);
                offst += Encoder.writeInt(data, offst, clumpLen);
                offst += clumpLen;
            }

            try {
                if (logTxns) txnLog.log(DBTransactionLog.SET_HDL_VALUE, handle, data);
                db.setValue(handle, data);
            } catch (HandleException e1) {
                throw e1;
            } catch (Exception e) {
                throw new HandleException(HandleException.INTERNAL_ERROR, "Error updating handle");
            }
        } //end synchronized
    }

    /*********************************************************************
     * Scan the database, calling a method in the specified callback for
     * every handle in the database.
     *********************************************************************/
    @Override
    public final void scanHandles(ScanCallback callback) throws HandleException {
        for (java.util.Enumeration<?> recs = db.getEnumerator(); recs.hasMoreElements();) {
            byte[][] record = (byte[][]) recs.nextElement();
            callback.scanHandle(record[0]);
        }
    }

    /*********************************************************************
     * Scan the NA database, calling a method in the specified callback for
     * every prefix handle in the database.
     *********************************************************************/
    @Override
    public void scanNAs(ScanCallback callback) throws HandleException {
        for (java.util.Enumeration<?> recs = naDB.getEnumerator(); recs.hasMoreElements();) {
            byte[][] record = (byte[][]) recs.nextElement();
            callback.scanHandle(record[0]);
        }
    }

    /*********************************************************************
     * Scan the database for handles with the given prefix
     * and return an Enumeration of byte arrays with each byte array
     * being a handle.  <i>naHdl</i> is the prefix handle
     * for the prefix that you want to list the handles for.
     *********************************************************************/
    @Override
    public final Enumeration<byte[]> getHandlesForNA(byte naHdl[]) throws HandleException {
        if (!haveNA(naHdl)) {
            throw new HandleException(HandleException.INVALID_VALUE, "The requested prefix doesn't live here");
        }

        boolean isZeroNA = Util.startsWithCI(naHdl, Common.NA_HANDLE_PREFIX);
        if (isZeroNA) naHdl = Util.getSuffixPart(naHdl);
        return new HdlsForNAEnum(db.getEnumerator(), naHdl);
    }

    /**********************************************************************
     * This is an class that scans all of the records in a database and
     * forwards only the handles that start with the given prefix.
     **********************************************************************/
    private final class HdlsForNAEnum implements Enumeration<byte[]> {
        private final byte prefix[];
        private byte nextHdl[] = null;
        private final Enumeration<byte[][]> dbEnum;
        private final boolean listingDerivedPrefixes;

        HdlsForNAEnum(Enumeration<byte[][]> dbEnum, byte na[]) {
            this.listingDerivedPrefixes = Util.startsWithCI(na, Common.NA_HANDLE_PREFIX);
            this.prefix = Util.encodeString(Util.decodeString(na));
            this.dbEnum = dbEnum;
            seekNextValue();
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
        synchronized (WRITE_LOCK) {
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
                throw new HandleException(HandleException.INTERNAL_ERROR, String.valueOf(e));
            }
        } //end synchronized
    }

    /*********************************************************************
     * Copy the current database to a backup file, and restart the transaction log.
     * Returns true if the checkpoint process was *started* (this doesn't mean
     * it was successful).
     *********************************************************************/
    @Override
    public final void checkpointDatabase() throws HandleException {
        if (!logTxns) {
            throw new HandleException(HandleException.INVALID_VALUE, "Transaction logging not enabled");
        }

        synchronized (WRITE_LOCK) {
            if (readOnly) {
                // already doing backup, we shouldn't start another concurrent backup.
                throw new HandleException(HandleException.STORAGE_RDONLY);
            }
            readOnly = true;
        }

        Thread th = null;
        try {
            th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ////// should probably create timestamped file name in order to have
                        ////// multiple backups
                        naDB.copyTo(new File(serverDir, "nas.bak"));
                        db.copyTo(new File(serverDir, "handles.bak"));
                        txnLog.reset();
                    } catch (Exception e) {
                        System.err.println("ERROR: Unable to backup JDB database: " + e);
                        e.printStackTrace(System.err);
                    } finally {
                        readOnly = false;
                    }
                }
            });
        } catch (Throwable t) {
            readOnly = false;
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error creating backup thread: " + t);
        }

        try {
            th.start();
        } catch (Throwable t) {
            readOnly = false;
            System.err.println("Unable to start checkpoint process: " + t);
            t.printStackTrace(System.err);
            throw new HandleException(HandleException.INTERNAL_ERROR, String.valueOf(t));
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
    }
}

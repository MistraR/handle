/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.jdb;

import java.io.*;
import java.util.*;
import java.security.*;

/**
 *
 * Object used to store key/value pairs in a fast, persistent index.
 *
 * Uses a hashing algorithm (with configurable hash size) to locate records in
 * the file quickly, and a memory cache (also with configurable size) to speed
 * up access to heavily used records.
 *
 * <pre>{@literal
 *
 *  BC_REC_START Block Format:
 *  -----------------------------------------------------
 *  | Block Code == BC_REC_START (1 byte)               |
 *  | Key Length (4 bytes)                              |
 *  | Data Length (4 bytes)                             |
 *  | Next Record Ptr (8 bytes)                         |
 *  | [ Cont. Block Ptr (8 bytes)                       |
 *  |     if ((KeyLen+DataLen) > (BlockSz-HeaderSz)) ]  |
 *  | Data (BlockSz-HeaderSz bytes)                     |
 *  | ...                                               |
 *  -----------------------------------------------------
 *
 *  BC_REC_CONT Block Format:
 *  -----------------------------------------------------
 *  | Block Code == BC_REC_CONT (1 byte)                |
 *  | Remainder Length (4 bytes)                        |
 *  | [ Cont. Block Ptr (8 bytes)                       |
 *  |     if this is not the last continuation block ]  |
 *  | Data                                              |
 *  | ...                                               |
 *  -----------------------------------------------------
 *
 *  BC_REC_UNUSED Block Format:
 *  -----------------------------------------------------
 *  | Block Code == BC_REC_UNUSED (1 byte)              |
 *  | Anything.  (BlockSz-1 bytes)                      |
 *  -----------------------------------------------------
 *
 * }</pre>
 *
 ******************************************************************************/

@SuppressWarnings({"rawtypes", "unchecked"})
public class DBHash {
    public static final String V1_FILE_ID = "JDBHash v0.1";
    private static final int BLOCK_SIZE = 1024;

    private static final byte BC_REC_UNUSED = 0;
    private static final byte BC_REC_START = 1;
    private static final byte BC_REC_CONT = 2;

    private static final int START_HEADER_SIZE = 1 + 4 + 4 + 8;
    private static final int START_WCONT_HEADER_SIZE = 1 + 4 + 4 + 8 + 8;
    private static final int CONT_HEADER_SIZE = 1 + 4;
    private static final int CONT_WCONT_HEADER_SIZE = 1 + 4 + 8;

    private RandomAccessFile raFile;
    private final File hashFile;
    private int hashLength;
    private long tableStartIndex;
    private final boolean readOnly;
    private Vector freeBlocks;
    private final MessageDigest md5;
    private final byte blankBytes[];
    private final byte buf[] = new byte[BLOCK_SIZE];

    public long stepCount = 0;
    public long numQueries = 0;

    // Contains pointers to locations in the data store.
    // Indexed by a N bit hash value.  May have to abstract this
    // out in the future so that it can be only partially loaded.

    private long hashIndex[];
    private long dataSegmentStart;
    private final BlockCache cache;
    public boolean DEBUG = false;

    /**
     * Create/open a hashed index file with a hashtable of <code>hashLength</code>
     * entries and a cache with a maximum of <code>cacheSize</code> records.
     */
    public DBHash(File hashFile, int hashLength, int cacheSize) throws Exception {
        this(hashFile, hashLength, cacheSize, false);
    }

    /**
     * Create/open a hashed index file with a hashtable of <code>hashLength</code>
     * entries and a cache with a maximum of <code>cacheSize</code> records.
     */
    public DBHash(File hashFile, int hashLength, int cacheSize, boolean readOnly) throws Exception {
        if (hashLength < 0) {
            throw new IllegalArgumentException("The hash length (" + hashLength + ") must be >= 0");
        }

        this.readOnly = readOnly;
        this.hashFile = hashFile;
        this.hashLength = hashLength;
        this.freeBlocks = new Vector();
        this.blankBytes = new byte[BLOCK_SIZE];
        this.cache = new BlockCache(cacheSize);

        // if the file doesn't exist, create a new empty file
        if (!hashFile.exists()) {
            if (readOnly) throw new FileNotFoundException(hashFile.getAbsolutePath());
            initNewFile();
        }

        // load the file (or at least the hash index part of it).
        raFile = new RandomAccessFile(hashFile, readOnly ? "r" : "rw");
        loadFromFile();

        md5 = MessageDigest.getInstance("MD5");
    }

    /**
     * Delete every record in the file.
     */
    public synchronized void deleteAllRecords() throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        System.err.println("Deleting records!!!");
        try {
            raFile.close();
        } catch (Exception e) {
            /* Ignore */ }

        cache.clear();
        freeBlocks = new Vector();
        initNewFile();
        raFile = new RandomAccessFile(hashFile, "rw");
        loadFromFile();
    }

    /**
     * Close the random-access file.
     */
    public void close() throws Exception {
        sync();
        raFile.close();
    }

    /**
     * Save all writes and make sure that the DB file is flushed to disk
     */
    private void sync() {
        try {
            raFile.getFD().sync();
        } catch (Exception e) {
            System.err.println("Exception syncing file: " + e);
        }
    }

    @Override
    @Deprecated
    public void finalize() {
        try {
            close();
        } catch (Exception e) {
            /* Ignore */ }
    }

    /**
     * Get the data value associated with the specified key.
     *
     * @return null if the specified key does not exist.
     */
    public final synchronized byte[] getValue(byte key[]) throws Exception { //// maybe we can synchronize only on md5 here??
        byte digest[] = md5.digest(key);
        int digestLen = digest.length;
        int hash = ((0x00ff & digest[digestLen - 1]) | (0x00ff & digest[digestLen - 2]) << 8 | (0x00ff & digest[digestLen - 3]) << 16 | ((0x000000ff & digest[digestLen - 4]) << 24));
        int hashKey = (hash & 0x7FFFFFFF) % hashLength;
        long recordLoc = hashIndex[hashKey];

        if (DEBUG) System.err.println(" getvalue(" + (new String(key)) + ") startLoc: " + Long.toHexString(recordLoc));

        if (recordLoc <= 0) {
            return null;
        }

        HashBlock block = getValueAtBlock(recordLoc, key);

        if (block != null) return block.data;

        return null;
    }

    /**
     * Read the block at the specified location.
     */
    private final synchronized HashBlock readBlock(long blockNum) throws Exception {
        HashBlock block = (HashBlock) cache.getBlock(blockNum);

        if (DEBUG) System.err.println("    reading block# " + Long.toHexString(blockNum));

        if (block != null) { // first check the cache...
            block.lastTouched = System.currentTimeMillis();

            if (DEBUG) System.err.println("    block was cached");

            return block;
        }

        block = new HashBlock();

        raFile.seek(blockNum); // read this block
        raFile.readFully(buf);

        int keyLen = (buf[1] & 0xff) << 24 | (buf[2] & 0xff) << 16 | (buf[3] & 0xff) << 8 | (buf[4] & 0xff);

        int dataLen = (buf[5] & 0xff) << 24 | (buf[6] & 0xff) << 16 | (buf[7] & 0xff) << 8 | (buf[8] & 0xff);

        block.thisRecord = blockNum;
        block.nextRecord = (buf[9] & 0xffL) << 56 | (buf[10] & 0xffL) << 48 | (buf[11] & 0xffL) << 40 | (buf[12] & 0xffL) << 32 | (buf[13] & 0xffL) << 24 | (buf[14] & 0xffL) << 16 | (buf[15] & 0xffL) << 8 | (buf[16] & 0xffL);
        int bloc = 17; // WHY?

        if (keyLen > 10000 || keyLen < 0 || dataLen > 1000000 || dataLen < 0) {
            System.err.println("invalid key/data length at block: " + Long.toHexString(blockNum) + '\n' + "  nextRecord=" + Long.toHexString(block.nextRecord) + '\n' + "  keyLen=" + keyLen + '\n' + "  dataLen=" + dataLen);

            throw new Exception("Data corruption exception.  " + "Invalid key/data length");
        }

        block.key = new byte[keyLen];
        block.data = new byte[dataLen];

        int len = keyLen + dataLen;

        boolean willContinue = (len + START_HEADER_SIZE > BLOCK_SIZE);
        int numBlocks = 1;
        int remainingLength;
        if (willContinue) remainingLength = len - (BLOCK_SIZE - START_WCONT_HEADER_SIZE);
        else remainingLength = len - (BLOCK_SIZE - START_HEADER_SIZE);

        // figure out how many blocks we need...
        if (remainingLength > 0) // count the last cont block
        {
            numBlocks++;
            remainingLength -= (BLOCK_SIZE - CONT_HEADER_SIZE);
        }

        while (remainingLength > 0) // count the rest of the cont blocks
        {
            numBlocks++;
            remainingLength -= (BLOCK_SIZE - CONT_WCONT_HEADER_SIZE);
        }

        long contBlock = 0;
        int numRead = 0;
        int thisHeaderSize;

        for (int i = 0; i < numBlocks; i++) // read each block...
        {
            int numReadThisBlock = 0;

            // if this isn't the last block, get the location of the next block

            if (i < numBlocks - 1) {
                contBlock = (buf[bloc] & 0xffL) << 56 | (buf[bloc + 1] & 0xffL) << 48 | (buf[bloc + 2] & 0xffL) << 40 | (buf[bloc + 3] & 0xffL) << 32 | (buf[bloc + 4] & 0xffL) << 24 | (buf[bloc + 5] & 0xffL) << 16
                    | (buf[bloc + 6] & 0xffL) << 8 | (buf[bloc + 7] & 0xffL);
                bloc += 8;
            }

            if (i == 0) thisHeaderSize = (numBlocks > 1) ? START_WCONT_HEADER_SIZE : START_HEADER_SIZE;
            else if (i < numBlocks - 1) thisHeaderSize = CONT_WCONT_HEADER_SIZE;
            else if (i == numBlocks - 1) thisHeaderSize = CONT_HEADER_SIZE;
            else {
                throw new Exception("File corrupted!! This shouldn't happen!!!!");
            }
            while (numReadThisBlock < BLOCK_SIZE - thisHeaderSize) {
                if (numRead + numReadThisBlock < keyLen) {
                    // read however much of the key is left to be read from this block
                    int r = Math.min(keyLen - numRead, BLOCK_SIZE - thisHeaderSize);
                    System.arraycopy(buf, bloc, block.key, numRead, r);
                    bloc += r;
                    numReadThisBlock += r;
                }

                if ((numRead + numReadThisBlock < len) && (numReadThisBlock < BLOCK_SIZE - thisHeaderSize)) { // read the part of the "value" data that is contained in this block
                    int r = Math.min(len - numRead - numReadThisBlock, BLOCK_SIZE - thisHeaderSize - numReadThisBlock);
                    System.arraycopy(buf, bloc, block.data, numRead + numReadThisBlock - keyLen, r);
                    bloc += r;
                    numReadThisBlock += r;
                }

                if (numRead + numReadThisBlock >= len) break;
            }
            numRead += numReadThisBlock;

            // if this wasn't the last block, read the next
            if (i < numBlocks - 1) {
                raFile.seek(contBlock);
                raFile.readFully(buf);
                bloc = 5;
            }
        }

        cache.putBlock(block); // Store the block in the cache
        return block;
    }

    /**
     * Retrieve the record associated with the given key, starting at the
     * specified block. SHOULD BE CALLED FROM WITHIN A SYNCHRONIZED BLOCK!!!!!
     */
    private final HashBlock getValueAtBlock(long blockNum, byte key[]) throws Exception {
        HashBlock block;
        do {
            block = readBlock(blockNum);
            if (keyMatches(block.key, key)) {
                if (DEBUG) System.err.println("   found block: " + (new String(block.key)));

                return block;
            } else {
                if (DEBUG) System.err.println("   skipping block: " + (new String(block.key)) + "; len=" + key.length);
            }
            blockNum = block.nextRecord;
        } while (block.nextRecord > 0);

        return null;
    }

    /**
     * Set the data value associated with the specified key. If a data value is
     * already associated with this key, it will be replaced.  If not, it will be
     * created.
     */
    public final synchronized void setValue(byte key[], byte data[]) throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        byte digest[] = md5.digest(key);

        int digestLen = digest.length;

        int hash = ((0x00ff & digest[digestLen - 1]) | (0x00ff & digest[digestLen - 2]) << 8 | (0x00ff & digest[digestLen - 3]) << 16 | ((0x000000ff & digest[digestLen - 4]) << 24));

        int hashKey = (hash & 0x7FFFFFFF) % hashLength;

        long recordLoc = hashIndex[hashKey];

        if (DEBUG) System.err.println(" setvalue(" + (new String(key)) + ") startLoc: " + Long.toHexString(recordLoc));

        if (recordLoc <= 0) { // There's no entry for this hash: create one
            long newRecordLoc = writeRecord(-1, key, data, 0);
            hashIndex[hashKey] = newRecordLoc;

            if (DEBUG) System.err.println("  NEW ENTRY: " + (new String(key)));

            writeHashIndex(hashKey, newRecordLoc);
        } else { // See if this key already exists...
            HashBlock dupBlock = getValueAtBlock(recordLoc, key);
            if (dupBlock != null) { // an item for this key already exists, so we will write the new
                                    // record at the same place, using the same nextRecord so the
                                    // linked list structure stays the same.

                if (DEBUG) System.err.println("  EXISTING ENTRY: " + (new String(key)));

                writeRecord(dupBlock.thisRecord, key, data, dupBlock.nextRecord);
            } else { // insert the new record at the head of the list...
                if (DEBUG) System.err.println("  NEW ENTRY (but existing hash): " + (new String(key)));

                long newRecordLoc = writeRecord(-1, key, data, recordLoc);
                hashIndex[hashKey] = newRecordLoc;
                writeHashIndex(hashKey, newRecordLoc);
            }
        }

        sync();
    }

    /**
     * Delete the specified key and it's associated value from the database.
     * Returns true if the key actually existed in the database, otherwise false.
     */
    public final synchronized boolean deleteValue(byte key[]) throws Exception {
        if (DEBUG) System.err.println("delete(" + (new String(key)) + ")");
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        byte digest[] = md5.digest(key);

        int digestLen = digest.length;

        int hash = ((0x00ff & digest[digestLen - 1]) | (0x00ff & digest[digestLen - 2]) << 8 | (0x00ff & digest[digestLen - 3]) << 16 | ((0x000000ff & digest[digestLen - 4]) << 24));

        int hashKey = (hash & 0x7FFFFFFF) % hashLength;

        long recordLoc = hashIndex[hashKey];

        if (recordLoc <= 0) return false;

        HashBlock block;

        long parentBlock = 0; // the address of the block that pointed to this block...

        while (recordLoc > 0) {
            block = readBlock(recordLoc);

            if (block == null) break;

            if (keyMatches(block.key, key)) { // Remove this record from the (possible) chain of records.
                // Also remove this record AND IT'S PARENT from the cache...
                if (parentBlock > 0) {
                    raFile.seek(parentBlock + 9);
                    raFile.writeLong(block.nextRecord);
                    cache.removeBlock(parentBlock);
                } else {
                    hashIndex[hashKey] = block.nextRecord;
                    writeHashIndex(hashKey, block.nextRecord);
                }

                cache.removeBlock(block.thisRecord);
                deleteStartRecord(block.thisRecord);

                sync();

                return true;
            }

            parentBlock = recordLoc;
            recordLoc = block.nextRecord;
        }

        return false;
    }

    /**
     *
     * See if the given keys (1) aren't null and (2) match.
     *
     * @return true if the keys are identical, false otherwise.
     *
     */

    private final boolean keyMatches(byte key1[], byte key2[]) {
        if (key1 == null || key2 == null || key1.length != key2.length) return false;

        for (int i = 0; i < key1.length; i++)
            if (key1[i] != key2[i]) return false;

        return true;
    }

    /**
     * Write the given hashKey->record-location mapping to disk.
     */
    private final void writeHashIndex(int hashKey, long newRecordLoc) throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");
        raFile.seek(tableStartIndex + (hashKey) * 8);
        raFile.writeLong(newRecordLoc);
        // extra sync, even though this *really* shouldn't be needed
        //----///////sync();
    }

    /**
     * Writes a record for the specified data at the given location. If startLoc
     * is  <= 0 then a new record is created.  Returns the beginning location of
     * the record.  If we are over-writing an existing record, we will use the
     * sames blocks as the old record, The calling code should 'sync' the file
     * after calling this method.
     */
    private final synchronized long writeRecord(long startLoc, byte key[], byte data[], long nextRecordLoc) throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        long contLoc = 0;

        if (startLoc > 0) { // The record exists, overwrite it
            cache.removeBlock(startLoc);
            raFile.seek(startLoc + 1);
            int oldKeyLen = raFile.readInt();
            int oldDataLen = raFile.readInt();
            if (oldDataLen + oldKeyLen > BLOCK_SIZE - START_HEADER_SIZE) { // the existing record is continued in another block
                raFile.writeLong(0); // Don't use skipBytes!
                contLoc = raFile.readLong();
            }
        }

        int keyLen = key.length;
        int dataLen = data.length;
        int len = keyLen + dataLen;
        boolean willContinue = (len + START_HEADER_SIZE > BLOCK_SIZE);

        buf[0] = BC_REC_START;
        int kl = key.length;
        int dl = data.length;
        buf[1] = (byte) ((kl & 0xff000000) >> 24); /////>>>
        buf[2] = (byte) ((kl & 0x00ff0000) >> 16);
        buf[3] = (byte) ((kl & 0x0000ff00) >> 8);
        buf[4] = (byte) ((kl & 0x000000ff));

        buf[5] = (byte) ((dl & 0xff000000) >> 24); /////>>>
        buf[6] = (byte) ((dl & 0x00ff0000) >> 16);
        buf[7] = (byte) ((dl & 0x0000ff00) >> 8);
        buf[8] = (byte) ((dl & 0x000000ff));

        buf[9] = (byte) ((nextRecordLoc & 0xff00000000000000L) >> 56); //////>>>
        buf[10] = (byte) ((nextRecordLoc & 0x00ff000000000000L) >> 48);
        buf[11] = (byte) ((nextRecordLoc & 0x0000ff0000000000L) >> 40);
        buf[12] = (byte) ((nextRecordLoc & 0x000000ff00000000L) >> 32);
        buf[13] = (byte) ((nextRecordLoc & 0x00000000ff000000L) >> 24);
        buf[14] = (byte) ((nextRecordLoc & 0x0000000000ff0000L) >> 16);
        buf[15] = (byte) ((nextRecordLoc & 0x000000000000ff00L) >> 8);
        buf[16] = (byte) ((nextRecordLoc & 0x00000000000000ffL));

        if (willContinue) { // the new record needs to be continued in another block
            long nextContLoc = writeContRecord(contLoc, key, data, BLOCK_SIZE - START_WCONT_HEADER_SIZE);
            buf[17] = (byte) ((nextContLoc & 0xff00000000000000L) >> 56);
            buf[18] = (byte) ((nextContLoc & 0x00ff000000000000L) >> 48);
            buf[19] = (byte) ((nextContLoc & 0x0000ff0000000000L) >> 40);
            buf[20] = (byte) ((nextContLoc & 0x000000ff00000000L) >> 32);
            buf[21] = (byte) ((nextContLoc & 0x00000000ff000000L) >> 24);
            buf[22] = (byte) ((nextContLoc & 0x0000000000ff0000L) >> 16);
            buf[23] = (byte) ((nextContLoc & 0x000000000000ff00L) >> 8);
            buf[24] = (byte) ((nextContLoc & 0x00000000000000ffL));

            int written = Math.min(keyLen, BLOCK_SIZE - START_WCONT_HEADER_SIZE);
            System.arraycopy(key, 0, buf, START_WCONT_HEADER_SIZE, written);
            if (written < BLOCK_SIZE - START_WCONT_HEADER_SIZE) {
                System.arraycopy(data, 0, buf, START_WCONT_HEADER_SIZE + written, BLOCK_SIZE - START_WCONT_HEADER_SIZE - written);
            }
        } else {
            System.arraycopy(key, 0, buf, START_HEADER_SIZE, key.length);
            System.arraycopy(data, 0, buf, START_HEADER_SIZE + key.length, data.length);
            if (contLoc > 0) {
                deleteContRecord(contLoc);
            }
        }

        if (startLoc <= 0) startLoc = getFreeBlock();

        raFile.seek(startLoc);

        if (DEBUG) System.err.println("  writing record at: " + Long.toHexString(startLoc));

        raFile.write(buf);
        //--/////////sync();
        HashBlock block = new HashBlock();
        block.key = key;
        block.data = data;
        block.thisRecord = startLoc;
        block.nextRecord = nextRecordLoc;
        cache.putBlock(block);

        return startLoc;
    }

    /**
     * deletes the continuation block and any chained blocks, starting at the
     * specified location.  MUST BE WITHIN A SYNCHRONIZED BLOCK!!!!!
     */
    private final synchronized void deleteContRecord(long startLoc) throws Exception {
        if (DEBUG) System.err.println("  deleting contblock: " + Long.toHexString(startLoc));
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        raFile.seek(startLoc);
        raFile.readByte();
        int remainderLen = raFile.readInt();

        if (remainderLen > BLOCK_SIZE - CONT_HEADER_SIZE) deleteContRecord(raFile.readLong());

        raFile.seek(startLoc);

        if (DEBUG) System.err.println("writing unused marker at: " + Long.toHexString(startLoc));

        raFile.writeByte(BC_REC_UNUSED);

        freeBlocks.addElement(Long.valueOf(startLoc));
    }

    /**
     * deletes the block and any chained blocks, starting at the specified
     * location.  This will return a pointer to the next record start block if
     * this is the beginning of a start block.
     */
    private final synchronized long deleteStartRecord(long startLoc) throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        raFile.seek(startLoc);

        raFile.readByte();
        int keyLen = raFile.readInt();
        int dataLen = raFile.readInt();
        long nextRecord = raFile.readLong();

        if (keyLen + dataLen > BLOCK_SIZE - START_HEADER_SIZE) {
            deleteContRecord(raFile.readLong());
        }

        raFile.seek(startLoc);

        if (DEBUG) System.err.println("writing unused marker at: " + Long.toHexString(startLoc));

        raFile.writeByte(BC_REC_UNUSED);
        freeBlocks.addElement(Long.valueOf(startLoc));

        return nextRecord;
    }

    /**
     * Writes the remainder of a record for the specified data, starting at the
     * given location.  If startLoc is  <= 0 then a new block is created.  Returns
     * the beginning location of the record.  currLoc specifies how much data has
     * been written already.
     */
    private final long writeContRecord(long startLoc, byte key[], byte data[], int currLoc) throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        long contLoc = 0;
        if (startLoc <= 0) {
            startLoc = getFreeBlock();
        } else {
            raFile.seek(startLoc + 1);
            int remainder = raFile.readInt();
            if (remainder > BLOCK_SIZE - CONT_HEADER_SIZE) {
                contLoc = raFile.readLong();
            }
        }

        int keyLen = key.length;
        int dataLen = data.length;
        int len = keyLen + dataLen;
        int remainderLen = len - currLoc;
        boolean willContinue = (remainderLen + CONT_HEADER_SIZE > BLOCK_SIZE);

        // write the start record header at startLoc
        if (DEBUG) System.err.println("  writing contblock at: " + Long.toHexString(startLoc));

        raFile.seek(startLoc);
        raFile.writeByte(BC_REC_CONT);
        raFile.writeInt(remainderLen);

        if (willContinue) {
            long contPlaceHolder = raFile.getFilePointer();

            raFile.writeLong(0); // Don't use skipBytes

            int written = 0;

            if (currLoc < keyLen) {
                written = Math.min(keyLen - currLoc, BLOCK_SIZE - CONT_WCONT_HEADER_SIZE);
                raFile.write(key, currLoc, written);
                currLoc += written;
            }

            if (written < BLOCK_SIZE - CONT_WCONT_HEADER_SIZE) {
                int bytesToWrite = BLOCK_SIZE - CONT_WCONT_HEADER_SIZE - written;
                raFile.write(data, currLoc - keyLen, bytesToWrite);
                currLoc += bytesToWrite;
            }
            ////sync();
            long nextContLoc = writeContRecord(contLoc, key, data, currLoc);
            raFile.seek(contPlaceHolder);
            raFile.writeLong(nextContLoc);
            raFile.seek(contPlaceHolder);
        } else {
            if (currLoc < keyLen) {
                raFile.write(key, currLoc, keyLen - currLoc);
                currLoc = keyLen;
            }

            raFile.write(data, currLoc - keyLen, dataLen - (currLoc - keyLen));
            currLoc += dataLen - (currLoc - keyLen);
            raFile.write(blankBytes, 0, BLOCK_SIZE - CONT_HEADER_SIZE - remainderLen);
            //--///////sync();
            if (contLoc > 0) {
                deleteContRecord(contLoc);
            }
        }
        //--/////////sync();
        return startLoc;
    }

    private final synchronized long getFreeBlock() throws Exception {
        int fbSize = freeBlocks.size();
        if (fbSize > 0) {
            long startLoc = ((Long) freeBlocks.elementAt(fbSize - 1)).longValue();
            freeBlocks.removeElementAt(fbSize - 1);
            return startLoc;
        } else {
            return raFile.length();
        }
    }

    /**
     * Load the data file, pulling the hash index into memory.
     */
    private final synchronized void loadFromFile() throws Exception {
        // Check to make sure the fileID is correct...
        String fileID = raFile.readUTF();
        if (fileID == null || !fileID.equals(V1_FILE_ID)) throw new Exception("Invalid file ID");

        hashLength = raFile.readInt();
        if (hashLength < 0) throw new IllegalArgumentException("The hash length (" + hashLength + ") must be >= 0");

        tableStartIndex = raFile.getFilePointer();

        // Read in the hash index table while setting
        // the hash mask based on the table size...

        hashIndex = new long[hashLength];
        for (int i = 0; i < hashLength; i++) {
            hashIndex[i] = raFile.readLong();
        }
        dataSegmentStart = raFile.getFilePointer();

        // Read the first byte of each block; if it
        // is zero, the block is unused, so put
        // a link to it in the unused block list.
        long recLoc = dataSegmentStart;
        long filelen = raFile.length();
        byte blockCode;

        while (recLoc < filelen) {
            raFile.seek(recLoc);
            blockCode = raFile.readByte();
            if (blockCode == BC_REC_UNUSED) freeBlocks.addElement(Long.valueOf(recLoc));
            recLoc += BLOCK_SIZE;
        }

        //System.err.println("opened DB with " + freeBlocks.size() + " free blocks");
    }

    /**
     * Initialize a new, empty, hash DB file.
     */
    private final void initNewFile() throws Exception {
        if (readOnly) throw new IOException("Attempted to modify read-only database");

        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(hashFile), 10000));

        // write the standard file header to identify the format
        out.writeUTF(V1_FILE_ID);
        // write a fresh index table
        out.writeInt(hashLength);
        for (int i = 0; i < hashLength; i++) {
            out.writeLong(0);
        }
        out.flush();
        out.close();
    }

    /**
     * Display a crude text-based graph of the hash distribution.
     */
    public synchronized void dumpDepthGraph() throws Exception {
        System.err.println("Dumping graph: ");
        for (int h = 0; h < hashIndex.length; h++) {
            long index = hashIndex[h];
            if (index <= 0) // 0L ???
                continue;

            HashBlock block;
            do {
                block = readBlock(index);
                index = block.nextRecord;
                System.err.print('X');
            } while (index > 0);

            System.err.println("");
        }
    }

    /**
     * Dump all of the records in the table in hexadecimal format with the keys
     * separated from the values by a colon.
     */
    public synchronized void dumpRecords(PrintStream out) {
        for (Enumeration recs = getEnumerator(); recs.hasMoreElements();) {
            byte keydata[][] = (byte[][]) recs.nextElement();
            out.print(new String(keydata[0]));

            //     for (int i = 0; i < keydata[0].length; i++)
            //         {
            //          out.print(Integer.toHexString(keydata[0][i]));
            //         }
            //
            //     out.print(':');
            //     for (int i = 0; i < keydata[1].length; i++)
            //         {
            //          out.print(Integer.toHexString(keydata[1][i]));
            //         }

            out.println("");
        }
    }

    /**
     * Return an enumeration used to iterate over all of the records in the table.
     */
    public Enumeration<byte[][]> getEnumerator() {
        return new TableIterator();
    }

    /**
     * Dump a human-readable display of all of the records in the table and cache.
     */
    public void dumpDataStructure(PrintStream out) {
        int hash = 0;

        out.println("Dumping data structure: ");

        HashBlock block = null;

        long blockNum = 0;

        while (hash < hashIndex.length) {
            while (hash < hashIndex.length && hashIndex[hash] == 0)
                hash++;

            if (hash >= hashIndex.length) break;

            blockNum = hashIndex[hash];
            out.println("Hash Index=" + hash + " location(h)=" + Long.toHexString(blockNum));
            do {
                try {
                    block = readBlock(blockNum);
                    blockNum = block.nextRecord;
                    out.println("   Read block# " + Long.toHexString(block.thisRecord) + '\n' + "         next# " + Long.toHexString(block.nextRecord) + '\n' + "       touched " + (new Date(block.lastTouched)) + '\n' + "           key "
                        + (new String(block.key)) + '\n');
                } catch (Exception e) {
                    out.println("   Got exception reading block hash# " + hash + '\n' + "       block#" + Long.toHexString(blockNum) + '\n' + "       e: " + e);
                    blockNum = 0;
                }
            } while (blockNum > 0);

            hash++;
        }
    }

    private synchronized int hashOfKey(byte[] key) {
        byte digest[] = md5.digest(key);
        int digestLen = digest.length;
        int hash = ((0x00ff & digest[digestLen - 1]) | (0x00ff & digest[digestLen - 2]) << 8 | (0x00ff & digest[digestLen - 3]) << 16 | ((0x000000ff & digest[digestLen - 4]) << 24));
        int hashKey = (hash & 0x7FFFFFFF) % hashLength;
        return hashKey;
    }

    /**
     * Iterator class used to enumerate all of the blocks in the table.
     */
    class TableIterator implements Enumeration<byte[][]> {
        private int currentHash = -1;
        private HashBlock currentBlock = null;

        @Override
        public final boolean hasMoreElements() {
            if (currentBlock != null && currentBlock.nextRecord > 0) return true;

            for (int h = currentHash + 1; h < hashIndex.length; h++) {
                if (hashIndex[h] > 0) return true;
            }

            return false;
        }

        @Override
        public final byte[][] nextElement() throws NoSuchElementException {
            try {
                while (currentBlock != null && currentBlock.nextRecord > 0) {
                    currentBlock = readBlock(currentBlock.nextRecord);
                    // Skip past any blocks with the wrong hash.
                    // This shouldn't happen, but did for a customer trying to migrate storage,
                    // so this is a workaround.
                    if (currentBlock != null && hashOfKey(currentBlock.key) == currentHash) {
                        return new byte[][] { currentBlock.key, currentBlock.data };
                    }
                }

                while (currentHash < hashIndex.length - 1) {
                    currentHash++;
                    long block = hashIndex[currentHash];
                    if (block > 0) {
                        currentBlock = readBlock(block);
                        return new byte[][] { currentBlock.key, currentBlock.data };
                    }
                }
            } catch (Exception e) {
                System.err.println("Exception enumerating blocks: " + e);
                System.err.println("currentBlock=\"" + ((currentBlock != null) ? (new String(currentBlock.key) + "\" at " + Long.toHexString(currentBlock.thisRecord)) : "null"));
            }

            throw new NoSuchElementException();
        }

    } // End of class TableIterator

    /**
     * Method to backup the DBHash file to given named file. Outside code MUST
     * make sure that no write operations are performed on the database while this
     * method is executing.
     *
     * @param newJDBFile File to copy this database to
     */
    public void copyTo(File newJDBFile) throws Exception {
        if (DEBUG) System.err.println("Backup JDB to:" + newJDBFile.getAbsolutePath());

        if (newJDBFile == null) throw new IOException("Invalid new JDB file, file can not be null");

        InputStream in = null;
        OutputStream out = null;

        try { //copy DBHash File
            if (DEBUG) System.err.println("Beginning copy of JDB file...");

            in = new FileInputStream(hashFile);
            out = new FileOutputStream(newJDBFile);
            @SuppressWarnings("hiding")
            byte[] buf = new byte[100000];

            long fileLength = hashFile.length();
            int n = 0, r = 0;

            while (n < fileLength && (r = in.read(buf)) > 0)
                out.write(buf, 0, r); // Check return ???

            if (DEBUG) System.err.println("End copy ... ");
        } finally // No "catch" ???
        {
            if (out != null) try {
                out.close();
            } catch (Exception e) {
                /* Ignore */ }

            if (in != null) try {
                in.close();
            } catch (Exception e) {
                /* Ignore */ }
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length != 1) {
            System.err.println("usage: java net.handle.jdb.DBHash <dbfile>");

            // Shouldn't we exit here ???
        }

        String dbfile = argv[0];
        System.err.println("loading " + dbfile);
        DBHash db = new DBHash(new File(dbfile), 5000, 1000);
        System.err.println("dumping database...");
        db.dumpRecords(System.err);
    }

}

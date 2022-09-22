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

/*******************************************************************************
 *
 * Read only version of DBHash.  Significantly faster startup since it doesn't
 * need to hunt down free blocks.
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

@SuppressWarnings("rawtypes")
public class RO_DBHash {
    public static final String V1_FILE_ID = "JDBHash v0.1";

    private static final int BLOCK_SIZE = 1024, START_HEADER_SIZE = 1 + 4 + 4 + 8, START_WCONT_HEADER_SIZE = 1 + 4 + 4 + 8 + 8, CONT_HEADER_SIZE = 1 + 4, CONT_WCONT_HEADER_SIZE = 1 + 4 + 8;

    private final RandomAccessFile raFile;

    private int hashLength;

    private final MessageDigest md5;

    private final byte buf[] = new byte[BLOCK_SIZE];

    // Contains pointers to locations in the data store.
    // Indexed by a N bit hash value.  May have to abstract this
    // out in the future so that it can be only partially loaded.

    private long hashIndex[];

    private final BlockCache cache;

    public boolean DEBUG = false;

    /******************************************************************************
     *
     * Create/open a hashed index file with a hashtable of <code>hashLength</code>
     * entries and a cache with a maximum of <code>cacheSize</code> records.
     *
     */

    public RO_DBHash(File hashFile, int hashLength, int cacheSize) throws Exception {
        if (hashLength < 0) throw new IllegalArgumentException("The hash length (" + hashLength + ") must be >= 0");

        this.hashLength = hashLength;
        this.cache = new BlockCache(cacheSize);

        // if the file doesn't exist, create a new empty file
        if (!hashFile.exists()) throw new Exception("Hash file " + hashFile.getPath() + "doesn't exist.");

        // load the file (or at least the hash index part of it).

        raFile = new RandomAccessFile(hashFile, "r");
        loadFromFile();

        md5 = MessageDigest.getInstance("MD5");
    }

    /******************************************************************************
     *
     * Close the random-access file.
     *
     */

    public void close() throws Exception {
        raFile.close();
    }

    /******************************************************************************
     *
     * Get the data value associated with the specified key.
     *
     * @return null if the specified key does not exist.
     *
     */

    public final synchronized byte[] getValue(byte key[]) throws Exception { //// Maybe we can synchronize only on md5 here ???
        byte digest[] = md5.digest(key);
        int digestLen = digest.length;

        int hash = ((0x00ff & digest[digestLen - 1]) | (0x00ff & digest[digestLen - 2]) << 8 | (0x00ff & digest[digestLen - 3]) << 16 | ((0x000000ff & digest[digestLen - 4]) << 24));

        int hashKey = (hash & 0x7FFFFFFF) % hashLength;

        long recordLoc = hashIndex[hashKey];

        if (DEBUG) System.err.println(" getvalue(" + (new String(key)) + ") startLoc: " + Long.toHexString(recordLoc));

        if (recordLoc <= 0) return null;

        HashBlock block = getValueAtBlock(recordLoc, key);
        if (block != null) return block.data;

        return null;
    }

    /******************************************************************************
     *
     * Read the block at the specified location.
     *
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
        int bloc = 17;

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

        // Figure out how many blocks we need...
        if (remainingLength > 0) // count the last cont block
        {
            numBlocks++;
            remainingLength -= (BLOCK_SIZE - CONT_HEADER_SIZE);
        }

        while (remainingLength > 0) // Count the rest of the cont blocks
        {
            numBlocks++;
            remainingLength -= (BLOCK_SIZE - CONT_WCONT_HEADER_SIZE);
        }

        long contBlock = 0;
        int numRead = 0;
        int thisHeaderSize;

        for (int i = 0; i < numBlocks; i++) // Read each block...
        {
            int numReadThisBlock = 0;

            // If this isn't the last block, get the location of the next block

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
                    // Read however much of the key is left to be read from this block
                    int r = Math.min(keyLen - numRead, BLOCK_SIZE - thisHeaderSize);
                    System.arraycopy(buf, bloc, block.key, numRead, r);
                    bloc += r;
                    numReadThisBlock += r;
                }

                if ((numRead + numReadThisBlock < len) && (numReadThisBlock < BLOCK_SIZE - thisHeaderSize)) { // Read the part of the "value" data that is contained in this block
                    int r = Math.min(len - numRead - numReadThisBlock, BLOCK_SIZE - thisHeaderSize - numReadThisBlock);
                    System.arraycopy(buf, bloc, block.data, numRead + numReadThisBlock - keyLen, r);
                    bloc += r;
                    numReadThisBlock += r;
                }

                if (numRead + numReadThisBlock >= len) break;
            }
            numRead += numReadThisBlock;

            // If this wasn't the last block, read the next
            if (i < numBlocks - 1) {
                raFile.seek(contBlock);
                raFile.readFully(buf);
                bloc = 5;
            }
        }

        cache.putBlock(block); // Store the block in the cache

        return block;
    }

    /******************************************************************************
     *
     * Retrieve the record associated with the given key, starting at the
     * specified block. SHOULD BE CALLED FROM WITHIN A SYNCHRONIZED BLOCK!!!!!
     *
     */

    private final HashBlock getValueAtBlock(long blockNum, byte key[]) throws Exception {
        HashBlock block;
        do {
            block = readBlock(blockNum);

            if (keyMatches(block.key, key)) {
                if (DEBUG) System.err.println("   found block: " + (new String(block.key)));
                return block;
            }

            if (DEBUG) System.err.println("   skipping block: " + (new String(block.key)) + "; len=" + key.length);

            blockNum = block.nextRecord;
        } while (block.nextRecord > 0);

        return null;
    }

    /******************************************************************************
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

    /******************************************************************************
     *
     * Load the data file, pulling the hash index into memory.
     *
     */

    private final synchronized void loadFromFile() throws Exception {
        // Check to make sure the fileID is correct...
        String fileID = raFile.readUTF();
        if (fileID == null || !fileID.equals(V1_FILE_ID)) throw new Exception("Invalid file ID");

        hashLength = raFile.readInt();
        if (hashLength < 0) throw new IllegalArgumentException("The hash length (" + hashLength + ") must be >= 0");

        // Read in the hash index table while setting
        // the hash mask based on the table size...
        hashIndex = new long[hashLength];
        for (int i = 0; i < hashLength; i++)
            hashIndex[i] = raFile.readLong();
    }

    /******************************************************************************
     *
     * Display a crude text-based graph of the hash distribution.
     *
     */

    public synchronized void dumpDepthGraph() throws Exception {
        System.err.println("Dumping graph: ");

        for (int h = 0; h < hashIndex.length; h++) {
            long index = hashIndex[h];
            if (index <= 0L) continue;

            HashBlock block;

            do {
                block = readBlock(index);
                index = block.nextRecord;
                System.err.print('X');
            } while (index > 0);

            System.err.println("");
        }
    }

    /******************************************************************************
     *
     * Dump all of the records in the table in hexadecimal format with the keys
     * separated from the values by a colon.
     *
     */

    public synchronized void dumpRecords(PrintStream out) {
        for (Enumeration recs = getEnumerator(); recs.hasMoreElements();) {
            byte keydata[][] = (byte[][]) recs.nextElement();
            out.print(new String(keydata[0]));
            out.println("");
        }
    }

    /******************************************************************************
     *
     * Return an enumeration used to iterate over all of the records in the table.
     *
     */

    public Enumeration getEnumerator() {
        return new TableIterator();
    }

    /******************************************************************************
     *
     * Dump a human-readable display of all of the records in the table and cache.
     *
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
                    e.printStackTrace(out);
                    blockNum = 0;
                }
            } while (blockNum > 0);

            hash++;
        }
    }

    /*************************************************************************
     *
     * Iterator class used to enumerate all of the blocks in the table.
     *
     ************************************************************************/

    class TableIterator implements Enumeration {
        private int currentHash = -1;
        private HashBlock currentBlock = null;

        /*** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         *
         * Return true if table has more yet-unused elements, false otherwise
         *
         */

        @Override
        public final boolean hasMoreElements() {
            if (currentBlock != null && currentBlock.nextRecord > 0) return true;

            for (int h = currentHash + 1; h < hashIndex.length; h++) {
                if (hashIndex[h] > 0) return true;
            }

            return false;
        }

        /*** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         *
         * Return the next yet-unused element; throw exception if none.
         *
         */

        @Override
        public final Object nextElement() throws NoSuchElementException {
            try {
                if (currentBlock != null && currentBlock.nextRecord > 0) {
                    currentBlock = readBlock(currentBlock.nextRecord);
                    if (currentBlock != null) return new byte[][] { currentBlock.key, currentBlock.data };
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
                System.err.println("currentBlock=\"" + ((currentBlock == null) ? "null" : (new String(currentBlock.key) + "\" at " + Long.toHexString(currentBlock.thisRecord))

                ));
                e.printStackTrace(System.err);
            }

            throw new NoSuchElementException();
        }

        /*** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * **/

    } // End of class TableIterator

    /******************************************************************************
     *
     * Main routine
     *
     */

    public static void main(String argv[]) throws Exception {
        if (argv.length == 1) {
            String dbfile = argv[0];

            System.err.println("loading " + dbfile);
            DBHash db = new DBHash(new File(dbfile), 5000, 1000);
            System.err.println("dumping database...");
            db.dumpRecords(System.err);
        } else System.err.println("usage: java net.handle.jdb.DBHash <dbfile>");
    }

    /*****************************************************************************/
}

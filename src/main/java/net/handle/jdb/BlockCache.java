/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.jdb;

/******************************************************************************
 *
 * Memory cache used to store blocks in the DBHash database class.
 *
 *  Potential Improvements:  Fix the cache purge algorithm
 *  (currently just removes all elements!).
 *
 */

class BlockCache {
    private final int maxSize;
    private boolean purging = false;
    private final LongTable blocks;

    /******************************************************************************
     *
     * Constructor
     *
     */

    BlockCache(int size) {
        blocks = new LongTable(size);
        maxSize = size;
    }

    /******************************************************************************
     *
     * Clear out the cache
     *
     */

    void clear() {
        blocks.clear();
    }

    /******************************************************************************
     *
     * Enter a block.  If the cache then has more contents than wanted, purge it.
     *
     */

    void putBlock(Block block) {
        long oldKey = block.thisRecord; // Use this block's offset as a key
        blocks.put(oldKey, block); // for putting it in the cache

        long time = System.currentTimeMillis(); // Stamp it with the current time
        block.lastTouched = time;

        if ((blocks.size() < maxSize) // Cache size still OK ...
            || (purging) // ... or Purge is already in progress
        ) return; // We're finished

        // else:  need to purge the cache
        synchronized (this) {
            if (purging)// How'd we get in this block, if purging is true???
                return;

            purging = true;
            try {
                blocks.clear();

                //                   System.err.println("purging...");
                //                   System.gc();   // System garbage collection call
                /*
                     for (LongTableEnumerator keys = blocks.keys(); keys.hasMoreElements(); )
                         {
                          long key = keys.nextKey();
                          Block b = blocks.get(key);
                          if ((b != null) && (b.lastTouched < time))
                             {
                              oldKey = key;
                              time = b.lastTouched;
                             }
                         }
                 */
            } catch (Exception e) {
                System.err.println("error purging cache: " + e);
                e.printStackTrace(System.err);
            } finally {
                purging = false;
            }

        }
    }

    /******************************************************************************
     *
     * Fetch a block
     *
     */

    Block getBlock(long blockNum) {
        Block b = blocks.get(blockNum);
        //if (b != null)
        //   b.lastTouched = System.currentTimeMillis();
        return b;
    }

    /******************************************************************************
     *
     * Delete a block
     *
     */

    void removeBlock(long blockNum) {
        blocks.remove(blockNum);
    }

    /*****************************************************************************/
}

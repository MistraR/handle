/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.jdb;

public class LongTable {
    private transient TableEntry table[];

    private transient int count; // Number of entries in table

    private int threshold; // table's capacity * loadFactor...the number of
    // table entries that triggers a doubling of the
    // table's size.  Store as int so it won't have
    // to be calculated with every entry.

    private final float loadFactor; // Percentage of table's capacity that will
    // trigger a doubling of it's size

    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final int DEFAULT_TABLE_CAPACITY = 101;

    /******************************************************************************
     *
     * Constructor with explicit initial capacity and load factor.
     *
     */

    public LongTable(int initialCapacity, float loadFactor) {
        if ((initialCapacity < 1) || (loadFactor <= 0.0)) throw new IllegalArgumentException();

        this.loadFactor = loadFactor;
        table = new TableEntry[initialCapacity];
        threshold = (int) (initialCapacity * loadFactor);
    }

    /******************************************************************************
     *
     * Constructor with explicit initial capacity, default load factor.
     *
     */

    public LongTable(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /******************************************************************************
     *
     * Constructor with default initial capacity and default load factor.
     *
     */

    public LongTable() {
        this(DEFAULT_TABLE_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /******************************************************************************
     *
     * Return number of elements in table.
     *
     */

    public int size() {
        return count;
    }

    /******************************************************************************
     *
     * Return true if table is empty, otherwise false.
     *
     */

    public boolean isEmpty() {
        return count == 0;
    }

    /******************************************************************************
     *
     * Return an enumerator object for the table.
     *
     */

    public synchronized LongTableEnumerator keys() {
        return new LongTableEnumerator(table);
    }

    /******************************************************************************
     *
     * Return true if table contains given block, otherwise false.
     *
     */

    public synchronized boolean contains(Block value) {
        if (value == null) throw new NullPointerException();

        TableEntry tab[] = table;

        for (int i = tab.length; i-- > 0;) {
            for (TableEntry e = tab[i]; e != null; e = e.next)
                if (e.value.equals(value)) return true;
        }

        return false;
    }

    /******************************************************************************
     *
     * Return true if given key is in table, otherwise false.
     *
     */

    public synchronized boolean containsKey(long key) {
        TableEntry tab[] = table;
        int hash = (int) ((key & 0xffffffff) | ((key & 0xffffffff00000000L) >> 32));
        int index = (hash & 0x7FFFFFFF) % tab.length;

        for (TableEntry e = tab[index]; e != null; e = e.next)
            if ((e.key == key) && (e.hash == hash)) return true;

        return false;
    }

    /******************************************************************************
     *
     * Fetch the block value for the given key.
     *
     */

    public synchronized Block get(long key) {
        TableEntry tab[] = table;

        int hash = (int) ((key & 0xffffffff) | ((key & 0xffffffff00000000L) >> 32)), index = (hash & 0x7FFFFFFF) % tab.length;

        for (TableEntry e = tab[index]; e != null; e = e.next)
            if ((e.key == key) && (e.hash == hash)) return e.value;

        return null;
    }

    /******************************************************************************
     *
     * Rehash the table.
     *
     */

    protected void rehash() {
        int oldCapacity = table.length;
        TableEntry oldTable[] = table;

        int newCapacity = (oldCapacity * 2) + 1;
        TableEntry newTable[] = new TableEntry[newCapacity];

        threshold = (int) (newCapacity * loadFactor);
        table = newTable;

        for (int i = oldCapacity; i-- > 0;) {
            for (TableEntry old = oldTable[i]; old != null;) {
                TableEntry e = old;
                old = old.next;

                int index = (e.hash & 0x7FFFFFFF) % newCapacity;
                e.next = newTable[index];
                newTable[index] = e;
            }
        }
    }

    /******************************************************************************
     *
     * Enter the given value into the table by the given key.  If threshold
     * would be exceeded, double the table's size and go recursive.
     *
     */

    public synchronized void put(long key, Block value) {
        if (value == null) // Make sure the value is not null
            throw new NullPointerException();

        // Makes sure the key is not already in the hashtable.
        TableEntry tab[] = table;

        int hash = (int) ((key & 0xffffffff) | ((key & 0xffffffff00000000L) >> 32)), index = (hash & 0x7FFFFFFF) % tab.length;

        for (TableEntry e = tab[index]; e != null; e = e.next) {
            if ((e.key == key) && (e.hash == hash)) {
                e.value = value;
                return;
            }
        }

        if (count >= threshold) {
            rehash(); // Rehash the table if the threshold is exceeded
            put(key, value);
            return;
        }

        TableEntry e = new TableEntry(); // Create the new entry.
        e.hash = hash;
        e.key = key;
        e.value = value;
        e.next = tab[index];
        tab[index] = e;
        count++;
    }

    /******************************************************************************
     *
     * Remove the block for the given key from the table.
     *
     */

    public synchronized void remove(long key) {
        TableEntry tab[] = table;

        int hash = (int) ((key & 0xffffffff) | ((key & 0xffffffff00000000L) >> 32)), index = (hash & 0x7FFFFFFF) % tab.length;

        for (TableEntry e = tab[index], prev = null; e != null; prev = e, e = e.next) {
            if ((e.key == key) && (e.hash == hash)) {
                if (prev == null) tab[index] = e.next;
                else prev.next = e.next;
                count--;
                return;
            }
        }
    }

    /******************************************************************************
     *
     * Clear the entire table.
     *
     */

    public synchronized void clear() {
        TableEntry tab[] = table;

        for (int index = tab.length; --index >= 0;)
            tab[index] = null;

        count = 0;
    }

    /*****************************************************************************/
}

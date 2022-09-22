/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.jdb;

import java.util.NoSuchElementException;

public class LongTableEnumerator {
    private int index;
    private final TableEntry table[];
    private TableEntry entry; // "Current" table entry, i.e., next to be used

    /******************************************************************************
     *
     * Constructor
     *
     */

    LongTableEnumerator(TableEntry table[]) {
        this.table = table;
        this.index = table.length;
    }

    /******************************************************************************
     *
     * Return true if the table contains more elements, otherwise false.
     *
     */

    public boolean hasMoreElements() {
        if (entry != null) return true;

        while (index-- > 0)
            if ((entry = table[index]) != null) return true;

        return false;
    }

    /******************************************************************************
     *
     * Return the table's next not-yet-used key.  Throw exception if none.
     *
     */

    public long nextKey() {
        if (entry == null) while ((index-- > 0) && ((entry = table[index]) == null)) {
        }

        if (entry != null) {
            TableEntry e = entry;
            entry = e.next;
            return e.key;
        }

        throw new NoSuchElementException("LongTableEnumerator");
    }

    /*****************************************************************************/
}

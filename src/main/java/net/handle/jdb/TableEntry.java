/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.jdb;

class TableEntry {
    int hash;
    long key;
    Block value;
    TableEntry next;

    /******************************************************************************
     *
     * Recursively create and return a copy of a linked list the TableEntry,
     * and the linked list of TableEntries starting with it, if any.
     *
     */

    @Override
    protected Object clone() {
        TableEntry entry = new TableEntry();
        entry.hash = hash;
        entry.key = key;
        entry.value = value;
        entry.next = (next == null) ? null : (TableEntry) next.clone();
        return entry;
    }

    /*****************************************************************************/
}

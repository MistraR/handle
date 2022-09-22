/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.hdllib;

import java.util.Iterator;

import net.cnri.util.StreamTable;

public interface ReplicationDaemonInterface {

    StreamTable replicationStatus() throws HandleException;

    void pauseReplication();

    void unpauseReplication();

    Iterator<byte[]> handleIterator() throws HandleException;

    Iterator<byte[]> naIterator() throws HandleException;

    Iterator<byte[]> handleIteratorFrom(byte[] startingPoint, boolean inclusive) throws HandleException;

    Iterator<byte[]> naIteratorFrom(byte[] startingPoint, boolean inclusive) throws HandleException;

    public void addQueueListener(TransactionQueueListener l);

    public void removeQueueListener(TransactionQueueListener l);

}

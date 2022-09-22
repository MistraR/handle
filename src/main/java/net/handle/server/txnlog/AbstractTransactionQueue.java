/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.handle.hdllib.Transaction;
import net.handle.hdllib.TransactionQueueInterface;
import net.handle.hdllib.TransactionQueueListener;

public abstract class AbstractTransactionQueue implements TransactionQueueInterface {
    @Override
    public void addTransaction(Transaction txn) throws Exception {
        addTransaction(txn.txnId, txn.handle, txn.values, txn.action, txn.date);
    }

    protected List<TransactionQueueListener> queueListeners = new CopyOnWriteArrayList<>();

    @Override
    public void addQueueListener(TransactionQueueListener l) {
        queueListeners.add(l);
    }

    @Override
    public void removeQueueListener(TransactionQueueListener l) {
        queueListeners.remove(l);
    }

    /******************************************************************
     * Notify any objects that are listening for new transactions
     * that the given transaction has been added to the queue.
     ******************************************************************/
    protected void notifyQueueListeners(Transaction txn) {
        for (TransactionQueueListener listener : queueListeners) {
            try {
                listener.transactionAdded(txn);
            } catch (Exception e) {
                System.err.println("error notifying queue listeners: " + e);
                e.printStackTrace(System.err);
            }
        }
    }

    protected void shutdownQueueListeners() {
        for (TransactionQueueListener listener : queueListeners) {
            try {
                listener.shutdown();
            } catch (Exception e) {
                System.err.println("error in queue listeners shutdown: " + e);
                e.printStackTrace(System.err);
            }
        }
    }
}

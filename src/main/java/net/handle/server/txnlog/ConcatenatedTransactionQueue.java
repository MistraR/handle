/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Transaction;
import net.handle.hdllib.TransactionQueueInterface;
import net.handle.hdllib.TransactionQueueListener;
import net.handle.hdllib.TransactionScannerInterface;

/**
 *
 * Given the old style file based transaction queue and the new style queue
 * this class provides a common interface concatenating the two queues allowing
 * the data to be iterated over as if in a single queue.
 *
 */
public class ConcatenatedTransactionQueue extends AbstractTransactionQueue {

    private volatile TransactionQueueInterface oldQueue;
    private final TransactionQueueInterface currentQueue;
    private final long lastTxnIdOfOldQueue;

    public ConcatenatedTransactionQueue(TransactionQueueInterface oldQueue, TransactionQueueInterface currentQueue) {
        this.oldQueue = oldQueue;
        this.currentQueue = currentQueue;

        TransactionQueueListener subListener = new TransactionQueueListener() {
            @Override
            public void transactionAdded(Transaction txn) {
                ConcatenatedTransactionQueue.this.notifyQueueListeners(txn);
            }

            @Override
            public void shutdown() {
                // no-op
            }
        };
        this.oldQueue.addQueueListener(subListener);
        this.currentQueue.addQueueListener(subListener);
        lastTxnIdOfOldQueue = oldQueue.getLastTxnId();
    }

    @Override
    public long getFirstDate() {
        if (oldQueue == null) return this.currentQueue.getFirstDate();
        return Math.min(this.oldQueue.getFirstDate(), this.currentQueue.getFirstDate());
    }

    @Override
    public long getLastTxnId() {
        long currentQueueLastTxnId = currentQueue.getLastTxnId();
        if (currentQueueLastTxnId < 1) return lastTxnIdOfOldQueue;
        else return currentQueueLastTxnId;
    }

    @Override
    public void addTransaction(long txnId, byte[] handle, HandleValue[] values, byte action, long date) throws Exception {
        currentQueue.addTransaction(txnId, handle, values, action, date);
        // currentQueue will run the transaction listener
        //        notifyQueueListeners(new Transaction(txnId, handle, values, action, date));
    }

    @Override
    public void shutdown() {
        shutdownQueueListeners();
        if (oldQueue != null) oldQueue.shutdown();
        currentQueue.shutdown();
    }

    @Override
    public void deleteUntilDate(long date) {
        long firstDateBefore = currentQueue.getFirstDate();
        currentQueue.deleteUntilDate(date);
        long firstDateAfter = currentQueue.getFirstDate();
        if (firstDateAfter > firstDateBefore) {
            TransactionQueueInterface originalQueue = this.oldQueue;
            this.oldQueue = null;
            originalQueue.shutdown();
            if (originalQueue instanceof FileBasedTransactionQueue) ((FileBasedTransactionQueue)originalQueue).deleteAllFiles();
        }
    }

    @Override
    public TransactionScannerInterface getScanner(long lastTimestamp) throws Exception {
        TransactionScannerInterface oldEnumeration = null;
        if (lastTimestamp < lastTxnIdOfOldQueue && oldQueue != null) {
            oldEnumeration = oldQueue.getScanner(lastTimestamp);
        }
        TransactionScannerInterface currentEnumeration = currentQueue.getScanner(lastTimestamp);
        return new QueueScanner(oldEnumeration, currentEnumeration);
    }

    public class QueueScanner implements TransactionScannerInterface {
        private final TransactionScannerInterface oldEnumeration;
        private final TransactionScannerInterface currentEnumeration;
        private boolean oldEnumerationDone = false;

        protected QueueScanner(TransactionScannerInterface oldEnumeration, TransactionScannerInterface currentEnumeration) throws Exception {
            this.oldEnumeration = oldEnumeration;
            this.currentEnumeration = currentEnumeration;
        }

        @Override
        public Transaction nextTransaction() throws Exception {
            Transaction txn;
            if (oldEnumeration != null && !oldEnumerationDone) {
                txn = oldEnumeration.nextTransaction();
                if (txn == null) oldEnumerationDone = true;
                else return txn;
            }
            return currentEnumeration.nextTransaction();
        }

        @Override
        public void close() {
            if (oldEnumeration != null) oldEnumeration.close();
            if (currentEnumeration != null) currentEnumeration.close();
        }
    }
}

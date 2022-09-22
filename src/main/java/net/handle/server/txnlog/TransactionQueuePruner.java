/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import net.handle.hdllib.TransactionQueueInterface;
import net.handle.hdllib.TransactionQueuesInterface;

import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TransactionQueuePruner {
    private final ScheduledExecutorService execServ;
    private final TransactionQueueInterface txnQueue;
    private final TransactionQueuesInterface otherTxnQueues;
    private final int daysToKeep;

    public TransactionQueuePruner(TransactionQueueInterface txnQueue, TransactionQueuesInterface otherTxnQueues, int daysToKeep) {
        this.daysToKeep = daysToKeep;
        this.txnQueue = txnQueue;
        this.otherTxnQueues = otherTxnQueues;
        execServ = Executors.newScheduledThreadPool(1);
        ((ScheduledThreadPoolExecutor) execServ).setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        ((ScheduledThreadPoolExecutor) execServ).setRemoveOnCancelPolicy(true);
    }

    public void start() throws Exception {
        if (daysToKeep <= 0) return;
        execServ.scheduleAtFixedRate(this::prune, 0, 1, TimeUnit.DAYS);
    }

    public void stop() {
        try {
            if (execServ != null) {
                execServ.shutdown();
                execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            System.err.println("Error stopping transaction log pruner.");
            e.printStackTrace();
        }
    }

    public void runOnceNow() {
        prune();
    }

    private void prune() {
        try {
            long deleteUntilSeconds = ZonedDateTime.now().minusDays(daysToKeep).toInstant().toEpochMilli();
            if (txnQueue != null) txnQueue.deleteUntilDate(deleteUntilSeconds);
            if (otherTxnQueues != null) {
                for (String name : otherTxnQueues.listQueueNames()) {
                    otherTxnQueues.getQueue(name).deleteUntilDate(deleteUntilSeconds);
                }
            }
        } catch (Throwable e) {
            System.err.println("Exception pruning transaction queue: " + e);
            e.printStackTrace();
        }
    }
}

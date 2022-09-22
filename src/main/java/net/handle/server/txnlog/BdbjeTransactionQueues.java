/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.cnri.util.StreamTable;
import net.cnri.util.StringUtils;
import net.handle.hdllib.TransactionQueueInterface;
import net.handle.hdllib.TransactionQueuesInterface;

public class BdbjeTransactionQueues implements TransactionQueuesInterface {

    private final Map<String, TransactionQueueInterface> queues;
    private final File queueDir;
    private File othersDir;
    private static final String OTHER_SERVERS_DIR = "others";

    private final TransactionQueueInterface thisServersTransactionQueue;

    private final StreamTable config;

    public BdbjeTransactionQueues(File queueDir, TransactionQueueInterface thisServersTransactionQueue, StreamTable config) {
        queues = new HashMap<>();
        this.queueDir = queueDir;
        this.thisServersTransactionQueue = thisServersTransactionQueue;
        this.config = config;
        loadQueuesFromOthersDir();
    }

    private void loadQueuesFromOthersDir() {
        othersDir = new File(queueDir, OTHER_SERVERS_DIR);
        if (othersDir.exists()) {
            File[] othersDirList = othersDir.listFiles();
            for (File file : othersDirList) {
                if (file.isDirectory()) {
                    try {
                        BdbjeTransactionQueue queue = new BdbjeTransactionQueue(file, config);
                        String name = StringUtils.decodeURL(file.getName());
                        queues.put(name, queue);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public synchronized TransactionQueueInterface createNewQueue(String name) throws Exception {
        if (queues.containsKey(name)) {
            throw new IllegalArgumentException("Cannot create a transaction queue for " + name + " as one already exists.");
        }
        String sourceDirName = StringUtils.encodeURLComponent(name);
        if (othersDir == null) {
            othersDir = new File(queueDir, OTHER_SERVERS_DIR);
            othersDir.mkdirs();
        }
        File sourceDir = new File(othersDir, sourceDirName);
        sourceDir.mkdirs();

        BdbjeTransactionQueue queue = new BdbjeTransactionQueue(sourceDir, config);
        queues.put(name, queue);
        return queue;
    }

    @Override
    public TransactionQueueInterface getThisServersTransactionQueue() {
        return thisServersTransactionQueue;
    }

    @Override
    public List<String> listQueueNames() {
        return new ArrayList<>(queues.keySet());
    }

    @Override
    public TransactionQueueInterface getQueue(String name) {
        return queues.get(name);
    }

    @Override
    public synchronized TransactionQueueInterface getOrCreateTransactionQueue(String name) throws Exception {
        TransactionQueueInterface result = getQueue(name);
        if (result == null) {
            result = createNewQueue(name);
        }
        return result;
    }
    
    @Override
    public void shutdown() {
        for (TransactionQueueInterface txnQueue : queues.values()) {
            txnQueue.shutdown();
        }
    }

}

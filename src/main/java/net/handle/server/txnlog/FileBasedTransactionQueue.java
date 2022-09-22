/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.txnlog;

import net.handle.hdllib.*;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;

/***********************************************************************
 * Class responsible for keeping track of the different transaction
 * queue files.  The transaction queue is separated into separate
 * files so that it will be easy to wipe out old transactions (just
 * delete the old files).  It also makes replication slightly faster
 * since the entire queue doesn't need to be scanned when starting
 * replication at a certain transaction ID.
 *
 * Individual records within each transaction log look something like this:
 <pre>

 &lt;CRLF&gt;&lt;txnID&gt;|&lt;action&gt;|&lt;date&gt;|&lt;hashOnAll&gt;|&lt;hashOnNA&gt;|&lt;hashOnId&gt;|&lt;handle-hex-encoded&gt;|
</pre>
 ***********************************************************************/
@SuppressWarnings({"rawtypes", "unchecked"})
public class FileBasedTransactionQueue extends AbstractTransactionQueue implements TransactionQueueInterface {

    private final File queueDir;
    private final boolean readonly;
    private final File queueIndexFile;
    private Vector queueFiles;
    private final Calendar calendar;
    private File lockFile;
    private boolean haveLock = false;
    private boolean initialized = false;

    private class QueueFileEntry {
        private final long startDate;
        private final long firstTxnId;
        private final int queueNumber;
        private Writer writer = null;
        private File queueFile = null;

        QueueFileEntry(long startDate, long firstTxnId, int queueNumber) {
            this.startDate = startDate;
            this.firstTxnId = firstTxnId;
            this.queueNumber = queueNumber;
        }

        long getQueueNumber() {
            return queueNumber;
        }

        synchronized File getQueueFile() {
            if (queueFile == null) {
                queueFile = new File(queueDir, String.valueOf(queueNumber) + ".q");
            }
            return queueFile;
        }

        synchronized void writeRecord(String record) throws IOException {
            if (writer == null) {
                writer = new OutputStreamWriter(new FileOutputStream(getQueueFile().getAbsolutePath(), true), "UTF-8");
            }
            writer.write(record);
            writer.flush();
        }

        synchronized void close() {
            Writer tmpWriter = writer;
            writer = null;
            if (tmpWriter != null) {
                try {
                    tmpWriter.close();
                } catch (Exception e) {
                    System.err.println("Error closing queue writer: " + e);
                    e.printStackTrace(System.err);
                }
            }
        }

        @Override
        public String toString() {
            return String.valueOf(queueNumber) + "; firsttxn=" + firstTxnId + "; startDate=" + startDate + "; file=" + queueFile;
        }

    }

    public FileBasedTransactionQueue(File queueDir, boolean readonly) throws Exception {
        this.queueDir = queueDir;
        this.readonly = readonly;
        this.lockFile = new File(queueDir, "lock");
        this.queueIndexFile = new File(queueDir, "index");
        this.queueListeners = new CopyOnWriteArrayList<>();

        calendar = Calendar.getInstance();

        if (!readonly) {
            getLock();
            // try release lock on shutdown.  only works on java 1.3 and greater,
            Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown()));
        }

        initQueueIndex();
        this.initialized = true;
    }

    @Override
    public synchronized long getFirstDate() {
        if (queueFiles.size() <= 0) return Long.MAX_VALUE;
        QueueFileEntry entry = (QueueFileEntry) queueFiles.elementAt(0);
        return entry.startDate;
    }

    @Override
    public synchronized long getLastTxnId() {
        if (queueFiles.size() <= 0) return 0;
        long txnId = 0;
        try {
            QueueScanner scanner = new QueueScanner((QueueFileEntry) queueFiles.elementAt(queueFiles.size() - 1));
            while (true) {
                Transaction txn = scanner.nextTransaction();
                if (txn != null) txnId = txn.txnId;
                if (txn == null) break;
            }
        } catch (Exception e) {
            System.err.println("Error getting transaction ID: " + e + "\n   using " + txnId);
        }
        return txnId;
    }

    private synchronized int getQueueFileName(Date dt) {
        calendar.setTime(dt);
        return calendar.get(Calendar.YEAR) * 10000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH);
    }

    private synchronized void initQueueIndex() throws Exception {
        queueFiles = new Vector();
        if (!queueIndexFile.exists() || queueIndexFile.length() <= 0) {
            // setup the queues for a NEW server
            if (!readonly) {
                Transaction dummyTxn = new Transaction();
                dummyTxn.txnId = -1;
                dummyTxn.handle = new byte[0];
                dummyTxn.action = Transaction.ACTION_PLACEHOLDER;
                dummyTxn.hashOnAll = 0;
                dummyTxn.hashOnNA = 0;
                dummyTxn.hashOnId = 0;
                dummyTxn.values = new HandleValue[0];
                addTransaction(dummyTxn);
            }
        } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(queueIndexFile), "UTF-8"));
            try {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) break;
                    line = line.trim();
                    if (line.length() <= 0) continue;
                    String[] parts = line.split("\t");
                    String startDateStr = parts.length >= 1 ? parts[0] : "";
                    String firstTxnIdStr = parts.length >= 2 ? parts[1] : "";
                    String queueNumberStr = parts.length >= 3 ? parts[2] : "";
                    long startDate = Long.parseLong(startDateStr);
                    long firstTxnId = Long.parseLong(firstTxnIdStr);
                    int queueNumber = Integer.parseInt(queueNumberStr);
                    queueFiles.addElement(new QueueFileEntry(startDate, firstTxnId, queueNumber));
                }
            } finally {
                reader.close();
            }
        }
    }

    // TODO: race condition here!  another lock file could be created in between
    //       the check to see if it exists and when it is created.  Not horrible,
    //       since transaction queues are not created very often, but less
    //       than desirable.
    private synchronized void getLock() throws Exception {
        if (lockFile.exists()) {
            System.err.println("Error: lock file (" + lockFile + ") exists.  If you are sure " + "that another server is not running, remove this file and restart " + "the server");
            throw new Exception("Queue files are locked");
        } else {
            try {
                try {
                    new File(lockFile.getParent()).mkdirs();
                } catch (Exception e) {
                }

                OutputStream out = new FileOutputStream(lockFile);
                out.write("lock".getBytes("UTF-8"));
                out.close();
                haveLock = true;
            } catch (Exception e) {
                throw new Exception("Cannot create lock file: " + e);
            }
        }
    }

    private synchronized void releaseLock() {
        if (!haveLock) return;

        try {
            // Thread.currentThread().dumpStack();
            lockFile.delete();
            haveLock = false;
        } catch (Throwable e) {
            System.err.println("Error removing transaction queue lock file: " + e);
        } finally {
            if (!haveLock) lockFile = null;
        }
    }

    private synchronized QueueFileEntry getCurrentQueue() {
        if (queueFiles.size() <= 0) return null;
        return (QueueFileEntry) queueFiles.elementAt(queueFiles.size() - 1);
    }

    /** Log the specified transaction */
    @Override
    public void addTransaction(long txnId, byte handle[], HandleValue[] values, byte action, long date) throws Exception {
        if (readonly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Transaction queue is read-only");
        }

        Transaction txn = new Transaction();
        txn.txnId = txnId;
        txn.handle = handle;
        txn.values = values;
        txn.action = action;
        txn.date = date;

        // generate each type of hash for the handle so that we don't have to
        // re-generate it for every retrieve-transaction request.
        txn.hashOnAll = SiteInfo.getHandleHash(handle, SiteInfo.HASH_TYPE_BY_ALL);
        txn.hashOnNA = SiteInfo.getHandleHash(handle, SiteInfo.HASH_TYPE_BY_PREFIX);
        txn.hashOnId = SiteInfo.getHandleHash(handle, SiteInfo.HASH_TYPE_BY_SUFFIX);

        addTransaction(txn);
    }

    /*******************************************************************************
     * Log the specified transaction to the current queue (creating a new queue, if
     * necessary
     *******************************************************************************/
    @Override
    public synchronized void addTransaction(Transaction txn) throws Exception {
        if (readonly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Transaction queue is read-only");
        }

        // check to see if we should start a new queue
        Date now = new Date();
        int qnum = getQueueFileName(now);

        QueueFileEntry currentQueue = getCurrentQueue();

        if (currentQueue == null || qnum > currentQueue.getQueueNumber()) {
            // if we are in a new time period, create a new queue
            currentQueue = createNewQueue(now.getTime(), txn.txnId, qnum);
        }

        currentQueue.writeRecord(encodeTransaction(txn));

        notifyQueueListeners(txn);
    }

    /*****************************************************************************
     * Create and initialize a new transaction queue for transaction on or
     * after the given starting date/time.  This will create the queue file
     * and add an entry for it into the queue index file.
     *****************************************************************************/
    private synchronized QueueFileEntry createNewQueue(long startDate, long firstTxnId, int queueNum) throws Exception {
        closeCurrentQueue();
        Writer writer = new OutputStreamWriter(new FileOutputStream(queueIndexFile.getAbsolutePath(), true), "UTF-8");
        QueueFileEntry newQueue = new QueueFileEntry(startDate, firstTxnId, queueNum);
        String record = String.valueOf(startDate) + '\t' + String.valueOf(firstTxnId) + '\t' + String.valueOf(queueNum) + "\t\n";
        writer.write(record);
        writer.close();
        queueFiles.addElement(newQueue);
        return newQueue;
    }

    private synchronized void closeCurrentQueue() throws Exception {
        QueueFileEntry qfe = getCurrentQueue();
        if (qfe != null) qfe.close();
    }

    private synchronized QueueFileEntry getNextQueue(QueueFileEntry presentQueue) {
        for (int i = queueFiles.size() - 2; i >= 0; i--) {
            QueueFileEntry qfe = (QueueFileEntry) queueFiles.elementAt(i);
            if (qfe == presentQueue) {
                return (QueueFileEntry) queueFiles.elementAt(i + 1);
            }
        }

        // the called must have the last queue already...
        return null;
    }

    private String encodeTransaction(Transaction txn) {
        StringBuffer sb = new StringBuffer();
        sb.append(txn.txnId);
        sb.append('|');
        sb.append(txn.action);
        sb.append('|');
        sb.append(txn.date);
        sb.append('|');
        sb.append(txn.hashOnAll);
        sb.append('|');
        sb.append(txn.hashOnNA);
        sb.append('|');
        sb.append(txn.hashOnId);
        sb.append('|');
        sb.append(Util.decodeHexString(txn.handle, false));
        sb.append('|');
        sb.append('\n');
        return sb.toString();
    }

    private int nextField(int currPos, String txnStr) throws Exception {
        if (currPos >= txnStr.length()) throw new Exception("No more fields in transaction");
        int i = currPos + 1;
        for (; i < txnStr.length(); i++) {
            if (txnStr.charAt(i) == '|') {
                break;
            }
        }
        return i;
    }

    private Transaction decodeTransaction(String txnStr) {
        try {
            int sepIdx = -1;
            Transaction txn = new Transaction();
            txn.txnId = Long.parseLong(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.action = (byte) Integer.parseInt(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.date = Long.parseLong(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.hashOnAll = Integer.parseInt(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.hashOnNA = Integer.parseInt(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.hashOnId = Integer.parseInt(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            txn.handle = Util.encodeHexString(txnStr.substring(sepIdx + 1, sepIdx = nextField(sepIdx, txnStr)));
            return txn;
        } catch (Exception e) {
            System.err.println("Exception decoding transaction: \n  " + txnStr + "\n  " + e);
            e.printStackTrace(System.err);
        }
        return null;

    }

    @Override
    public synchronized void shutdown() {
        if (!initialized || readonly) return;
        try {
            shutdownQueueListeners();
            closeCurrentQueue();
        } catch (Throwable e) {
            System.err.println("Error shutting down transaction queue: " + e);
        }
        releaseLock();
    }

    @Override
    public void deleteUntilDate(long date) {
        throw new UnsupportedOperationException();
    }

    public void deleteAllFiles() {
        for (File file : queueDir.listFiles()) {
            if (file.getName().endsWith(".q")) file.delete();
            else if (file.getName().equals("index")) file.delete();
            else if (file.getName().equals("lock")) file.delete();
        }
    }
    
    @Override
    public synchronized TransactionScannerInterface getScanner(long lastTxnID) throws Exception {
        if (queueFiles.size() <= 0) { // shouldn't happen!
            return null;
        }

        QueueFileEntry queueEntry = null;
        for (int i = 0; i < queueFiles.size(); i++) {
            QueueFileEntry nextEntry = (QueueFileEntry) queueFiles.elementAt(i);
            if (nextEntry.firstTxnId >= 0 && nextEntry.firstTxnId > lastTxnID) {
                if (queueEntry == null) {
                    // this is the first daily queue and it doesn't include the last
                    // received txn... bad news... the requestor will have to redump
                    return new QueueScanner(nextEntry);
                } else {
                    // this queue starts with later txn ID than the requestor is
                    // asking for, so the previous queue should have what he wants.
                    return new QueueScanner(queueEntry);
                }
            }
            queueEntry = nextEntry;
        }

        if (queueEntry == null) {
            // no queues found?  shouldn't get here.
            return new QueueScanner((QueueFileEntry) queueFiles.elementAt(0));
        }

        // return a scanner for the most recent queue
        return new QueueScanner(queueEntry);
    }

    public class QueueScanner implements TransactionScannerInterface {
        private BufferedReader reader;
        private QueueFileEntry queueFileEntry;

        protected QueueScanner(QueueFileEntry queueFileEntry) throws Exception {
            connectToQueue(queueFileEntry);
        }

        private void connectToQueue(QueueFileEntry nextQueue) throws Exception {
            queueFileEntry = nextQueue;
            if (reader != null) close();
            reader = null;
            try {
                File f = queueFileEntry.getQueueFile();
                if (f.exists() && f.canRead()) {
                    reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
                } else {
                    throw new Exception("Cannot access file: " + f);
                }
            } catch (Exception e) {
                throw new Exception("Unable to open transaction log: " + e);
            }
        }

        @Override
        public synchronized Transaction nextTransaction() throws Exception {
            String line = null;
            do {
                line = reader.readLine();
                if (line == null) {
                    // reached end of queue file.  if there are no more queues, we're done.
                    // otherwise, start scanning the next queue.
                    QueueFileEntry nextQueue = getNextQueue(queueFileEntry);
                    if (nextQueue == null) {
                        // end of the line... no more queues
                        return null;
                    } else {
                        connectToQueue(nextQueue);
                    }
                } else if (line.trim().length() > 0) {
                    Transaction txn = decodeTransaction(line);
                    // skip placeholder transactions..
                    if (txn.action != Transaction.ACTION_PLACEHOLDER) {
                        return txn;
                    }
                }
            } while (true);
        }

        @Override
        public void close() {
            try {
                reader.close();
            } catch (Exception e) {
            }
        }

    }

    class Shutdown implements Runnable {
        @Override
        public void run() {
            try {
                shutdown();
            } catch (Throwable t) {
                System.err.println("Error shutting down txn queue: " + t);
            }
        }
    }

}

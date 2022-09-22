/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.server.*;
import net.handle.hdllib.*;
import net.handle.jdb.*;
import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** This class defines an object that processes records in a database
    transaction log and applies each record to one of several JDB
    handle databases specified on the command-line.  Which destination
    database the transaction is applied against is determined by
    computing the hash of the handle in the transaction.

    This class is useful after the SplitServer program has been run
    on the checkpoint/backup copy of a primary database.  Running this
    class on the transaction log will then bring the destination servers
    into sync with the current state of the primary database.

    After the end of the transaction log is reached, the date of the
    last transaction is printed out.  This date can be used to quickly
    resume processing the log at a later time.

    This class performs the same hashing as SplitServer, so the order
    of destination servers on the command-line is important.
 */
public class SplitRecoveryLog {
    private final DateTimeFormatter dateFormat;

    private long startDate = -1;
    private final File origDir;
    private final DBHash destDBs[];
    private final File txnFile;
    private final int numDBs;

    public static void main(String argv[]) throws Exception {
        if (argv.length < 3) {
            System.err.println("usage: java net.handle.apps.tools.SplitRecoveryLog [-d <sincedate>] <sourcedir> <destdir1> <destdir2> ...");
            return;
        }

        SplitRecoveryLog splitter = new SplitRecoveryLog(argv);
        splitter.doit();
        splitter.cleanup();
    }

    SplitRecoveryLog(String argv[]) throws Exception {
        dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd:HH:mm::ss").withZone(ZoneId.systemDefault());
        int logIdx = 0;
        String sinceDateStr = null;
        for (int i = 0; i < argv.length; i++) {
            if (argv[i].equals("-d")) {
                sinceDateStr = argv[i + 1];
                logIdx = i + 2;
                i++;
            }
        }

        if (sinceDateStr != null) {
            Instant dt = dateFormat.parse(sinceDateStr, Instant::from);
            startDate = dt.toEpochMilli();
            System.err.println("using date: " + dt);
        }
        origDir = new File(argv[logIdx]);
        txnFile = new File(origDir, "dbtxns.log");
        int destIdx = logIdx + 1;

        if (!txnFile.exists() || !txnFile.canRead()) {
            throw new Exception("Unable to read transaction file in source dir: " + origDir);
        }

        numDBs = argv.length - destIdx;
        destDBs = new DBHash[numDBs];
        for (int i = 0; i < numDBs; i++) {
            System.err.println("loading dest db: " + argv[i + destIdx]);
            destDBs[i] = new DBHash(new File(argv[i + destIdx], "handles.jdb"), 5000, 1000);
        }
    }

    final void doit() throws Exception {
        System.err.println("Restoring from transactions...");
        System.err.flush();
        InputStream txnInput = new BufferedInputStream(new FileInputStream(txnFile));
        int count = 0;
        int skipped = 0;
        long lastDate = 0;
        DBTxn txn = null;
        try {
            System.err.println("start date: " + startDate);
            while ((txn = DBTxn.readTxn(txnInput)) != null) {
                lastDate = txn.getDate();
                if (startDate != -1 && startDate > lastDate) {
                    skipped++;
                    if (skipped % 400 == 0) {
                        System.err.print("\rskipped: " + count);
                    }
                    continue;
                }
                count++;
                if (count < 100) {
                    System.err.println("processing record " + count + ": " + txn + " date: " + lastDate);
                }
                switch (txn.getAction()) {
                case DBTransactionLog.SET_HDL_VALUE:
                    destDBs[SiteInfo.determineServerNum(txn.getKey(), SiteInfo.HASH_TYPE_BY_ALL, numDBs)].setValue(txn.getKey(), txn.getValue());
                    break;
                case DBTransactionLog.DELETE_HDL_VALUE:
                    destDBs[SiteInfo.determineServerNum(txn.getKey(), SiteInfo.HASH_TYPE_BY_ALL, numDBs)].deleteValue(txn.getKey());
                    break;
                case DBTransactionLog.SET_NA_VALUE:
                    //naDB.setValue(txn.getKey(), txn.getValue());
                    break;
                case DBTransactionLog.DELETE_NA_VALUE:
                    //naDB.deleteValue(txn.getKey());
                    break;
                case DBTransactionLog.DELETE_EVERYTHING:
                    System.err.println("\nDELETING EVERYTHING!!!!");
                    ////////////////////////////////////
                    // clear all of the destination records
                    for (int i = 0; i < destDBs.length; i++) {
                        destDBs[i].deleteAllRecords();
                    }
                    break;
                default:
                    throw new Exception("Error: Unknown action in transaction log: " + ((int) txn.getAction()));
                }
                if (count % 1000 == 0) {
                    System.err.print("\rprocessed: " + count);
                }
            }
        } finally {
            System.err.println("\rprocessed: " + count + " last date: " + dateFormat.format(Instant.ofEpochMilli(lastDate)));
        }
        txnInput.close();
    }

    void cleanup() throws Exception {
        for (int i = 0; i < destDBs.length; i++) {
            destDBs[i].close();
        }
    }

}

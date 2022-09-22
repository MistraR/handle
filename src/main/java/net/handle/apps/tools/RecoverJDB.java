/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.server.*;
import net.handle.jdb.*;
import java.io.*;

class RecoverJDB {
    private final File hdlDBFile;
    private final File naDBFile;
    private final File hdlBackupFile;
    private final File naBackupFile;
    private final File txnFile;

    RecoverJDB(File hdlBackupFile, File hdlDBFile, File naBackupFile, File naDBFile, File txnFile) {
        this.hdlBackupFile = hdlBackupFile;
        this.hdlDBFile = hdlDBFile;
        this.naBackupFile = naBackupFile;
        this.naDBFile = naDBFile;
        this.txnFile = txnFile;
    }

    void doRecovery() throws Exception {
        // copy .bak files over their corresponding .jdb files
        System.out.println("Restoring backup databases...");
        System.out.flush();
        if (hdlBackupFile.exists()) {
            copyFile(hdlBackupFile, hdlDBFile);
        } else {
            hdlDBFile.delete();
        }
        if (naBackupFile.exists()) {
            copyFile(naBackupFile, naDBFile);
        } else {
            naDBFile.delete();
        }

        System.out.println("Opening databases...");
        System.out.flush();
        DBHash hdlDB = new DBHash(hdlDBFile, 5000, 1000);
        DBHash naDB = new DBHash(naDBFile, 1000, 500);

        System.out.println("Restoring from transactions...");
        System.out.flush();
        try (InputStream txnInput = new BufferedInputStream(new FileInputStream(txnFile))) {
            DBTxn txn = null;
            while ((txn = DBTxn.readTxn(txnInput)) != null) {
                System.err.println("Restore txn: " + txn);
                switch (txn.getAction()) {
                case DBTransactionLog.SET_HDL_VALUE:
                    hdlDB.setValue(txn.getKey(), txn.getValue());
                    break;
                case DBTransactionLog.DELETE_HDL_VALUE:
                    hdlDB.deleteValue(txn.getKey());
                    break;
                case DBTransactionLog.SET_NA_VALUE:
                    naDB.setValue(txn.getKey(), txn.getValue());
                    break;
                case DBTransactionLog.DELETE_NA_VALUE:
                    naDB.deleteValue(txn.getKey());
                    break;
                case DBTransactionLog.DELETE_EVERYTHING:
                    hdlDB.deleteAllRecords();
                    naDB.deleteAllRecords();
                    break;
                default:
                    throw new Exception("Error: Unknown action in transaction log: " + ((int) txn.getAction()));
                }
            }
        }
        hdlDB.close();
        naDB.close();
    }

    private static final void copyFile(File source, File dest) throws IOException {
        byte buf[] = new byte[10000];
        int r;
        try (FileInputStream fin = new FileInputStream(source); FileOutputStream fout = new FileOutputStream(dest);) {
            while ((r = fin.read(buf)) >= 0)
                fout.write(buf, 0, r);
        }
    }

    private static final void printUsage() {
        System.err.println("usage: java net.handle.apps.tools.RecoverJDB <server_directory>");
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length < 1) {
            printUsage();
            System.exit(1);
            return;
        }

        // check the server directory...
        File hsDir = new File(argv[0]);
        System.err.println("directory: " + hsDir);
        if (!hsDir.exists() || !hsDir.isDirectory()) {
            System.err.println("Server directory doesn't exist!");
            System.exit(1);
            return;
        }

        // check for a backup file and txn log
        File hdlBackupFile = new File(hsDir, "handles.bak");
        System.err.println(" backup file: " + hdlBackupFile);
        File hdlDBFile = new File(hsDir, "handles.jdb");
        System.err.println(" DB file: " + hdlDBFile);
        boolean haveHDLBackup = hdlBackupFile.exists();
        if (!haveHDLBackup) {
            System.err.println("Warning: Handle backup file (handles.bak) doesn't exist; " + "assuming all changes are in the dbtxns.log file and " + "that we can safely delete the current database.");
        }

        File naBackupFile = new File(hsDir, "nas.bak");
        File naDBFile = new File(hsDir, "nas.jdb");
        boolean haveNABackup = naBackupFile.exists();
        if (!haveNABackup) {
            System.err.println("Warning: NA backup file (nas.bak) doesn't exist; " + "assuming all changes are in the dbtxns.log file and " + "that we can safely delete the current database.");
        }

        File txnFile = new File(hsDir, "dbtxns.log");
        if (!txnFile.exists()) {
            System.err.println("Error: Transaction log file (dbtxns.log) doesn't exist!");
            System.exit(1);
            return;
        }

        System.out.println("Welcome to the Handle Server database recovery tool.\n");
        System.out.println("This tool should only be used if the handle database");
        System.out.println("has somehow been corrupted.\n");
        System.out.println("WARNING:  This program will attempt to restore the handles.jdb");
        System.out.println("and nas.jdb files.  If you would like to keep the current");
        System.out.println("contents of these files then BACK THEM UP NOW!\n");
        System.out.println("Type the word \"continue\" and hit ENTER to continue\n");
        System.out.flush();
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line = in.readLine();
        if (line == null || !line.trim().toUpperCase().equals("CONTINUE")) {
            System.out.println("Exiting...");
            System.out.flush();
            return;
        }

        RecoverJDB recoverer = new RecoverJDB(hdlBackupFile, hdlDBFile, naBackupFile, naDBFile, txnFile);
        recoverer.doRecovery();
    }

}

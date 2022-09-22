/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.cnri.util.StreamTable;
import net.handle.server.*;
import net.handle.server.bdbje.BDBJEHandleStorage;
import net.handle.hdllib.HSG;

import java.io.*;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.DbLoad;

class RecoverBDBJE {
    private final File environmentFile;
    private final File hdlBackupFile;
    private final File naBackupFile;
    private final File txnFile;

    private static final String HANDLE_DB_NAME = "handles";
    private static final String NA_DB_NAME = "nas";

    RecoverBDBJE(File environmentFile, File hdlBackupFile, File naBackupFile, File txnFile) {
        this.environmentFile = environmentFile;
        this.hdlBackupFile = hdlBackupFile;
        this.naBackupFile = naBackupFile;
        this.txnFile = txnFile;
    }

    void doRecovery() throws Exception {
        System.out.println("Restoring backup databases...");
        System.out.flush();

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setSharedCache(true);
        Environment environment = new Environment(environmentFile, null);
        BufferedReader reader = null;
        try {
            DbLoad loader = new DbLoad();
            loader.setEnv(environment);
            loader.setDbName(HANDLE_DB_NAME);
            loader.setNoOverwrite(false);
            loader.setTextFileMode(false);
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(hdlBackupFile), "UTF-8"));
            loader.setInputReader(reader);
            loader.load();

            reader.close();

            loader = new DbLoad();
            loader.setEnv(environment);
            loader.setDbName(NA_DB_NAME);
            loader.setNoOverwrite(false);
            loader.setTextFileMode(false);
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(naBackupFile), "UTF-8"));
            loader.setInputReader(reader);
            loader.load();

            if (txnFile != null) {
                System.out.println("Opening databases...");
                System.out.flush();
                BDBJEHandleStorage.DBWrapper hdlDB = new BDBJEHandleStorage.DBWrapper(environment, HANDLE_DB_NAME);
                BDBJEHandleStorage.DBWrapper naDB = new BDBJEHandleStorage.DBWrapper(environment, NA_DB_NAME);

                System.out.println("Restoring from transactions...");
                System.out.flush();
                try (InputStream txnInput = new BufferedInputStream(new FileInputStream(txnFile))) {
                    DBTxn txn = null;
                    while ((txn = DBTxn.readTxn(txnInput)) != null) {
                        System.err.println("Restore txn: " + txn);
                        switch (txn.getAction()) {
                        case DBTransactionLog.SET_HDL_VALUE:
                            hdlDB.put(txn.getKey(), txn.getValue());
                            break;
                        case DBTransactionLog.DELETE_HDL_VALUE:
                            hdlDB.del(txn.getKey());
                            break;
                        case DBTransactionLog.SET_NA_VALUE:
                            naDB.put(txn.getKey(), txn.getValue());
                            break;
                        case DBTransactionLog.DELETE_NA_VALUE:
                            naDB.del(txn.getKey());
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
        } finally {
            if (reader!=null) try { reader.close(); } catch (Throwable e) { }
            try { environment.close(); } catch (Throwable e) { }
        }
    }

    private static final void printUsage() {
        System.err.println("usage: java net.handle.apps.tools.RecoverBDBJE <server_directory> <handles_backup_file> <nas_backup_file> [<txn-file>]");
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length < 3) {
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
        File hdlBackupFile = new File(argv[1]);
        System.err.println(" backup file: " + hdlBackupFile);
        boolean haveHDLBackup = hdlBackupFile.exists();
        if (!haveHDLBackup) {
            System.err.println("Handle backup file doesn't exist.");
            System.exit(1);
            return;
        }

        File naBackupFile = new File(argv[2]);
        boolean haveNABackup = naBackupFile.exists();
        if (!haveNABackup) {
            System.err.println("NA backup file doesn't exist.");
            System.exit(1);
            return;
        }

        File txnFile = null;
        if (argv.length >= 4) {
            txnFile = new File(argv[3]);
            boolean haveTxnFile = txnFile.exists();
            if (!haveTxnFile) {
                System.err.println("Transaction file doesn't exist.");
                System.exit(1);
                return;
            }
        }

        System.out.println("Welcome to the Handle Server database recovery tool.\n");
        System.out.println("This tool should only be used if the handle database");
        System.out.println("has somehow been corrupted.\n");
        System.out.println("WARNING:  This program will attempt to restore the BDBJE");
        System.out.println("handle database.  If you would like to keep the current");
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

        StreamTable configTable = new StreamTable();
        // Load configTable from the config file
        try {
            configTable.readFromFile(new File(hsDir, HSG.CONFIG_FILE_NAME));
        } catch (Exception e) {
            System.err.println("Error reading configuration: " + e);
            return;
        }
        StreamTable config = (StreamTable) configTable.get(AbstractServer.HDLSVR_CONFIG);
        if (config == null) throw new Exception("Configuration setting \"" + AbstractServer.HDLSVR_ID + "\" is required.");
        File environmentFile = new File(hsDir, config.getStr("db_directory", "bdbje"));

        RecoverBDBJE recoverer = new RecoverBDBJE(environmentFile, hdlBackupFile, naBackupFile, txnFile);
        recoverer.doRecovery();
    }

}

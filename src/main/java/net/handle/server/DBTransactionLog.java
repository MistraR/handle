/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.io.*;

/***********************************************************************
 * Class responsible for logging modifications to a database.
 *
 ***********************************************************************/
public class DBTransactionLog {

    public static final byte SET_HDL_VALUE = (byte) 0;
    public static final byte DELETE_HDL_VALUE = (byte) 1;
    public static final byte SET_NA_VALUE = (byte) 2;
    public static final byte DELETE_NA_VALUE = (byte) 3;
    public static final byte DELETE_EVERYTHING = (byte) 4;

    private final File logFile;
    private BufferedOutputStream log;

    public DBTransactionLog(File logFile) throws Exception {
        this.logFile = logFile;
        openWriter();
    }

    private final void openWriter() throws Exception {
        this.log = new BufferedOutputStream(new FileOutputStream(logFile.getAbsolutePath(), true));
    }

    public synchronized void reset() throws Exception {
        System.err.println("Resetting transaction log...");

        log.close();
        logFile.delete();
        openWriter();

        System.err.println("Done resetting transaction queue");
    }

    /** Log the specified transaction */
    public synchronized void log(byte action, byte key[], byte val[]) throws Exception {
        DBTxn txn = new DBTxn(action, key, val, System.currentTimeMillis());
        log.write(txn.getLogBytes());
        log.flush();
    }

    public synchronized void shutdown() {
        try {
            this.log.close();
        } catch (Throwable e) {
            System.err.println("Error shutting down transaction log: " + e);
            e.printStackTrace(System.err);
        }
    }

}

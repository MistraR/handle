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
import java.io.*;

@SuppressWarnings("incomplete-switch")
class ViewJDBLog {
    private final File txnFile;

    ViewJDBLog(File txnFile) {
        this.txnFile = txnFile;
    }

    void viewLog(String hdlsToView[]) throws Exception {
        byte encHdls[][] = new byte[hdlsToView.length][];
        for (int i = 0; i < encHdls.length; i++) {
            encHdls[i] = Util.encodeString(hdlsToView[i].toUpperCase());
        }
        boolean showAll = hdlsToView.length == 0;

        System.out.println("Restoring from transactions...");
        System.out.flush();
        try (InputStream txnInput = new BufferedInputStream(new FileInputStream(txnFile))) {
            DBTxn txn = null;
            while ((txn = DBTxn.readTxn(txnInput)) != null) {
                if (showAll) {
                    System.out.println(txnToString(txn));
                    continue;
                }
                byte hdl[] = txn.getKey();
                Util.upperCaseInPlace(hdl);
                for (int i = 0; i < encHdls.length; i++) {
                    if (Util.equals(hdl, encHdls[i])) {
                        System.out.println(txnToString(txn));
                        System.out.flush();
                        break;
                    }
                }
            }
        }
    }

    private static final String txnToString(DBTxn txn) throws Exception {
        StringBuffer sb = new StringBuffer();
        switch (txn.getAction()) {
        case DBTransactionLog.SET_HDL_VALUE:
            sb.append("SET  ");
            break;
        case DBTransactionLog.DELETE_HDL_VALUE:
            sb.append("DELETE  ");
            break;
        case DBTransactionLog.SET_NA_VALUE:
            sb.append("SET NA  ");
            break;
        case DBTransactionLog.DELETE_NA_VALUE:
            sb.append("DELETE NA  ");
            break;
        case DBTransactionLog.DELETE_EVERYTHING:
            sb.append("DELETE EVERYTHING!!!  ");
            break;
        }
        sb.append(Util.decodeString(txn.getKey()));
        sb.append("   ");
        sb.append(new java.util.Date(txn.getDate()));
        sb.append("\n");
        if (txn.getAction() != DBTransactionLog.SET_HDL_VALUE) return sb.toString();
        byte value[] = txn.getValue();
        int clumpLen;
        int bufPos = 0;
        int numValues = Encoder.readInt(value, bufPos);
        bufPos += Encoder.INT_SIZE;

        HandleValue values[] = new HandleValue[numValues];
        for (int i = 0; i < numValues; i++) {
            clumpLen = Encoder.readInt(value, bufPos);
            bufPos += Encoder.INT_SIZE;
            values[i] = new HandleValue();
            Encoder.decodeHandleValue(value, bufPos, values[i]);
            sb.append("  ");
            sb.append(values[i]);
            sb.append("\n");
            bufPos += clumpLen;
        }

        return sb.toString();
    }

    private static final void printUsage() {
        System.err.println("usage: java net.handle.apps.tools.ViewJDBLog <server_directory> <handle-to-show> [<handle-to-show>...]");
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

        // check for a txn log
        File txnFile = new File(hsDir, "dbtxns.log");
        if (!txnFile.exists()) {
            System.err.println("Error: Transaction log file (dbtxns.log) doesn't exist!");
            System.exit(1);
            return;
        }
        String hdls[] = new String[argv.length - 1];
        System.arraycopy(argv, 1, hdls, 0, hdls.length);

        ViewJDBLog recoverer = new ViewJDBLog(txnFile);
        recoverer.viewLog(hdls);
    }

}

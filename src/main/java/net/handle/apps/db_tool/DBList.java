/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import net.handle.server.*;

public class DBList {
    private final HandleStorage storage;
    private boolean verbose = false;

    public DBList(HandleStorage storage) {
        this.storage = storage;
    }

    public void listHandles() {
        try {
            storage.scanHandles(new ScanCallback() {
                @Override
                public void scanHandle(byte handle[]) {
                    System.out.println(Util.decodeString(handle));
                    if (verbose) {
                        try {
                            byte[][] chunks = storage.getRawHandleValues(handle, null, null);
                            HandleValue[] values = Encoder.decodeHandleValues(chunks);
                            GsonUtility.getPrettyGson().toJson(values, System.out);
                            System.out.println();
                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                        }
                        System.out.println();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void listPrefixes() {
        try {
            storage.scanNAs(new ScanCallback() {
                @Override
                public void scanHandle(byte handle[]) {
                    System.out.println(Util.decodeString(handle));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv == null || argv.length < 1) {
            System.err.println("usage: java net.handle.apps.db_tool.DBList <server-directory> [-p] [-v]");
            return;
        }

        java.io.File serverDir = new java.io.File(argv[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverInfo, true, true);
        DBList dbList = new DBList(storage);
        for (String arg : argv) {
            if (arg.equals("-p")) {
                dbList.listPrefixes();
                System.exit(0);
            }
            if (arg.equals("-v")) {
                dbList.verbose = true;
            }
            //      System.err.println("Ignoring unrecognized parameter: "+arg);
        }
        dbList.listHandles();
    }

}

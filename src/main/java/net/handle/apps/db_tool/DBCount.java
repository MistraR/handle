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
import net.handle.server.bdbje.BDBJEHandleStorage;

public class DBCount {
    public static void main(String argv[]) throws Exception {
        if (argv == null || argv.length < 1) {
            System.err.println("usage: java net.handle.apps.db_tool.DBCount <server-directory>");
            return;
        }

        java.io.File serverDir = new java.io.File(argv[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverInfo, true, true);
        if (storage instanceof BDBJEHandleStorage) {
            System.out.println(((BDBJEHandleStorage) storage).count());
        } else {
            System.err.println("count currently implemented only for BDBJEHandleStorage");
        }
    }

}

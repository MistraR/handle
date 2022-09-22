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

public class DBRemove {
    public DBRemove(HandleStorage storage, boolean caseSensitive, String[] handles) {
        for (int i = 1; i < handles.length; i++) {
            System.out.println("\nRemoving " + handles[i]);
            try {
                storage.deleteHandle(caseSensitive ? Util.encodeString(handles[i]) : Util.upperCase(Util.encodeString(handles[i])));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv == null || argv.length < 2) {
            System.err.println("usage: java net.handle.apps.db_tool.DBRemove <server-directory> <handle> [<handle>] ...");
            return;
        }

        java.io.File serverDir = new java.io.File(argv[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverInfo, true, false);
        @SuppressWarnings("unused")
        DBRemove dbremove = new DBRemove(storage, serverInfo.getBoolean("case_sensitive"), argv);

    }

}

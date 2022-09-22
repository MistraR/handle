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
import java.io.*;

public class DBResolve {
    private final HandleStorage storage;
    private boolean caseSensitive;

    public DBResolve(HandleStorage storage) {
        this.storage = storage;
    }

    public HandleValue[] resolve(String handle) throws HandleException {
        byte[][] rawValues = this.storage.getRawHandleValues(caseSensitive ? Util.encodeString(handle) : Util.upperCase(Util.encodeString(handle)), null, null);
        if (rawValues == null) return null;
        HandleValue values[] = new HandleValue[rawValues.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = new HandleValue();
            Encoder.decodeHandleValue(rawValues[i], 0, values[i]);
        }
        return values;
    }

    public static void main(String argv[]) throws Exception {
        if (argv == null || argv.length < 1) {
            System.err.println("usage: java net.handle.apps.db_tool.DBResolve <server-directory>");
            System.err.println("followed by handles to resolve one per line");
            return;
        }

        java.io.File serverDir = new java.io.File(argv[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverInfo, true, true);
        DBResolve dbResolve = new DBResolve(storage);
        dbResolve.caseSensitive = serverInfo.getBoolean("case_sensitive");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String handle;
        while ((handle = reader.readLine()) != null) {
            System.out.println(handle);
            try {
                HandleValue[] values = dbResolve.resolve(handle);
                if (values == null) {
                    System.out.println("null");
                    continue;
                }
                for (HandleValue value : values) {
                    System.out.println(value.toDetailedString());
                }
            } catch (HandleException e) {
                System.out.println(e);
            }
            System.out.println();
        }

    }

}

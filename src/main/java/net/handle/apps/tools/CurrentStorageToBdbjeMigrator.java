/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import java.io.File;

import net.cnri.util.StreamTable;
import net.handle.hdllib.HandleStorage;
import net.handle.server.HandleStorageFactory;
import net.handle.server.bdbje.BDBJEHandleStorage;

public class CurrentStorageToBdbjeMigrator {

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 1) {
            System.err.println("Error <server-directory> argument is missing.");
            return;
        }
        File serverDir = new java.io.File(args[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage currentStorage = HandleStorageFactory.getStorage(serverDir, serverInfo, true, true);
        StreamTable serverInfoBdbje = (StreamTable) serverInfo.deepClone();
        serverInfoBdbje.put(HandleStorageFactory.STORAGE_TYPE, "BDBJE");
        HandleStorage bdbjeStorage = HandleStorageFactory.getStorage(serverDir, serverInfoBdbje, true, false);
        if (bdbjeStorage instanceof BDBJEHandleStorage) {
            System.out.println("Created instance of BDBJEHandleStorage.");
        } else {
            System.out.println("Error creating bdbje storage.");
            return;
        }
        StorageMigrator storageMigrator = new StorageMigrator(currentStorage, bdbjeStorage);
        System.out.println("Starting migration.");
        storageMigrator.migrate();
        currentStorage.shutdown();
        bdbjeStorage.shutdown();
        System.out.println();
        System.out.println("All records migrated to new storage.");

        File handlesJdb = new File(serverDir, "handles.jdb");
        if (handlesJdb.exists()) {
            renameJdbStorageFiles(serverDir);
            System.out.println("Old storage files renamed.");
            System.out.println("Migration complete.");
        } else {
            System.out.println("Migration complete.");
            System.out.println("=================================================================");
            System.out.println("= You now need to edit you config.dct to use the bdbje storage. =");
            System.out.println("=================================================================");
        }
    }

    private static void renameJdbStorageFiles(File serverDir) {
        File handlesJdb = new File(serverDir, "handles.jdb");
        File handlesJdbBack = new File(serverDir, "handles.jdb.back");
        handlesJdb.renameTo(handlesJdbBack);
        File nasJdb = new File(serverDir, "nas.jdb");
        File nasJdbBack = new File(serverDir, "nas.jdb.back");
        nasJdb.renameTo(nasJdbBack);
    }

}

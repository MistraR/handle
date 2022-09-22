/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import java.io.File;
import java.lang.reflect.Method;

/**
 * Abstract class that uses a static method to construct a HandleStorage instance.
 */
public abstract class HandleStorageFactory {
    public static final String STORAGE_TYPE = "storage_type";
    public static final String STORAGE_CLASS = "storage_class";
    public static final String CUSTOM_STORAGE_CONFIG = "storage_config";
    public static final String SQL_CONFIG = "sql_settings";

    /**
     * Create a HandleStorage instance using the server configuration from
     * the given StreamTable configuration that is based in the given
     * directory.
     */
    public static HandleStorage getStorage(File serverDir, StreamTable config, boolean isPrimary) throws Exception {
        return getStorage(serverDir, config, isPrimary, false);
    }

    /**
     * Create a HandleStorage instance using the server configuration from
     * the given StreamTable configuration that is based in the given
     * directory.
     */
    public static HandleStorage getStorage(File serverDir, StreamTable config, boolean isPrimary, boolean isReadonly) throws Exception {
        HandleStorage storage = null;

        // open the handle database
        String storageType = config.getStr(STORAGE_TYPE, "").toUpperCase().trim();
        if (storageType.equals("")) {
            if (new File(serverDir, "handles.jdb").exists()) {
                storageType = "JDB";
            } else {
                storageType = "BDBJE";
            }
        }

        if (storageType.equals("SQL")) {
            StreamTable sqlConfig = (StreamTable) config.get(SQL_CONFIG);
            if (sqlConfig == null) {
                throw new HandleException(HandleException.INVALID_VALUE, "Missing " + SQL_CONFIG + " section in config file");
            }
            storage = new SQLHandleStorage();
            sqlConfig.put("sql_read_only", isReadonly);
            storage.init(sqlConfig);
            return storage;
        } else if (storageType.equals("CUSTOM")) {
            if (isReadonly) {
                throw new HandleException(HandleException.SERVER_ERROR, "Unable to create read-only custom storage");
            }
            // load a user-defined class to be used as storage
            String storageClassName = String.valueOf(config.get(STORAGE_CLASS)).trim();
            Class<?> storageClass = Class.forName(storageClassName);

            StreamTable cfgTable = (StreamTable) config.get(CUSTOM_STORAGE_CONFIG);
            if (cfgTable == null) cfgTable = new StreamTable();

            Object obj = storageClass.getConstructor().newInstance();
            if (obj instanceof HandleStorage) {
                storage = (HandleStorage) obj;
                cfgTable.put("serverDir", serverDir.getAbsolutePath());
                performCustomInit(storage, cfgTable);
                return storage;
            } else {
                throw new HandleException(HandleException.INVALID_VALUE, "Custom storage class " + storageClassName + " does not implement the HandleStorage interface");
            }
        } else if (storageType.equals("JDB")) {
            storage = new JDBHandleStorage(serverDir, isPrimary);
            StreamTable configCopy = (StreamTable) config.deepClone();
            configCopy.put("serverDir", serverDir.getAbsolutePath());
            configCopy.put(Common.READ_ONLY_DB_STORAGE_KEY, isReadonly);
            storage.init(configCopy);
            return storage;
        } else { // if(storageType.equalsIgnoreCase("BDBJE")) {
            storage = new net.handle.server.bdbje.BDBJEHandleStorage();
            StreamTable configCopy = (StreamTable) config.deepClone();
            configCopy.put("serverDir", serverDir.getAbsolutePath());
            configCopy.put(Common.READ_ONLY_DB_STORAGE_KEY, isReadonly);
            storage.init(configCopy);
            return storage;
        }
    }

    // sad hack to get around changed signature of HandleStorage.init
    @SuppressWarnings("deprecation")
    private static void performCustomInit(HandleStorage storage, StreamTable cfgTable) throws Exception {
        Class<?> storageClass = storage.getClass();
        try {
            storage.init(cfgTable);
            return;
        } catch (AbstractMethodError e) {
            Method method = storageClass.getMethod("init", net.handle.util.StreamTable.class);
            net.handle.util.StreamTable oldStreamTable = new net.handle.util.StreamTable();
            oldStreamTable.readFrom(cfgTable.writeToString());
            method.invoke(storage, oldStreamTable);
            return;
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import net.cnri.util.SimpleCommandLine;
import net.cnri.util.StreamTable;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HSG;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleRecord;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.Util;
import net.handle.server.AbstractServer;
import net.handle.server.HandleStorageFactory;

public class LoadHandlesIntoStorageFromJson {

    public static void printHelp() {
        System.out.println("-f\tThe path to the input json file containing handle records.");
        System.out.println();
        System.out.println("-h\tHandle server directory.");
        System.out.println();
    }

    public static Gson gson = GsonUtility.getPrettyGson();
    public static String inputFileName = null;
    public static String configDirStr = null;

    public static void main(String[] args) throws Exception {
        SimpleCommandLine cl = new SimpleCommandLine("f", "h");
        cl.parse(args);

        if (cl.hasOption("f")) {
            inputFileName = cl.getOptionArgument("f");
        } else {
            System.out.println("Missing f");
            printHelp();
            return;
        }

        if (cl.hasOption("h")) {
            configDirStr = cl.getOptionArgument("h");
        } else {
            System.out.println("Missing h");
            printHelp();
            return;
        }

        File serverDir = new File(configDirStr);
        if (!((serverDir.exists()) && (serverDir.isDirectory()))) {
            System.err.println("Invalid configuration directory: " + configDirStr + ".");
            return;
        }

        StreamTable configTable = new StreamTable();
        // Load configTable from the config file
        try {
            configTable.readFromFile(new File(serverDir, HSG.CONFIG_FILE_NAME));
        } catch (Exception e) {
            System.err.println("Error reading configuration: " + e);
            return;
        }

        StreamTable serverConfig = (StreamTable) configTable.get(AbstractServer.HDLSVR_CONFIG);
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverConfig, true, false);
        File inputFile = new File(inputFileName);
        @SuppressWarnings("resource")
        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"));
        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("homedPrefixes".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String prefix = reader.nextString();
                        try {
                            homePrefix(prefix, storage);
                        } catch (HandleException e) {
                            e.printStackTrace();
                        }
                    }
                    reader.endArray();
                    continue;
                }
                if ("handleRecords".equals(name)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        @SuppressWarnings("unused")
                        String handle = reader.nextName();
                        HandleRecord handleRecord = readNextHandleRecord(reader);
                        try {
                            createHandleRecordInStorage(handleRecord, storage);
                        } catch (HandleException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } finally {
            reader.close();
            storage.shutdown();
        }
    }

    public static void homePrefix(String prefix, HandleStorage storage) throws HandleException {
        storage.setHaveNA(Util.upperCaseInPlace(Util.encodeString(prefix)), true);
        System.out.println("Homed PREFIX: " + prefix);
    }

    public static void createHandleRecordInStorage(HandleRecord handleRecord, HandleStorage storage) throws HandleException {
        String handle = handleRecord.getHandle();
        byte[] handleBytes = Util.encodeString(handle);
        byte[][] chunks = storage.getRawHandleValues(handleBytes, null, null);
        if (chunks == null) storage.createHandle(handleBytes, handleRecord.getValuesAsArray());
        else storage.updateValue(handleBytes, handleRecord.getValuesAsArray());
        System.out.println("Created: " + handle);
    }

    public static HandleRecord readNextHandleRecord(JsonReader reader) {
        HandleRecord handleRecord = (HandleRecord) gson.fromJson(reader, HandleRecord.class);
        return handleRecord;
    }

}

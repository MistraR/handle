/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;

import net.cnri.util.SimpleCommandLine;
import net.cnri.util.StreamTable;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HSG;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleRecord;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ScanCallback;
import net.handle.hdllib.Util;
import net.handle.server.AbstractServer;
import net.handle.server.HandleStorageFactory;

public class StorageToJson {

    private static void printHelp() {
        System.out.println("First argument is directory of handle server.");
        System.out.println("Second argument is name of the output file.");
    }

    public static void main(String[] argv) throws Exception {
        SimpleCommandLine simpleCommandLine = new SimpleCommandLine();
        simpleCommandLine.parse(argv);
        List<String> operands = simpleCommandLine.getOperands();
        if (operands.size() != 2) {
            printHelp();
            return;
        }
        String configDirStr = operands.get(0);
        String outFilename = operands.get(1);

        StreamTable configTable = new StreamTable();
        File serverDir = new File(configDirStr);

        if (!((serverDir.exists()) && (serverDir.isDirectory()))) {
            System.err.println("Invalid configuration directory: " + configDirStr + ".");
            return;
        }

        // Load configTable from the config file
        try {
            configTable.readFromFile(new File(serverDir, HSG.CONFIG_FILE_NAME));
        } catch (Exception e) {
            System.err.println("Error reading configuration: " + e);
            return;
        }

        try {
            StreamTable serverConfig = (StreamTable) configTable.get(AbstractServer.HDLSVR_CONFIG);
            HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverConfig, true, true);
            toJson(storage, outFilename);
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
    }

    private static void toJson(HandleStorage storage, String fileName) throws IOException, HandleException {
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(fileName), "UTF-8"))) {
            writer.setIndent("  ");

            JsonPrefixScanner prefixScanner = new JsonPrefixScanner(writer);

            writer.beginObject();
            writer.name("homedPrefixes").beginArray();
            storage.scanNAs(prefixScanner);

            writer.endArray();

            JsonHandleScanner handleScanner = new JsonHandleScanner(writer, storage);

            writer.name("handleRecords").beginObject();
            storage.scanHandles(handleScanner);
            writer.endObject();

            writer.endObject();
        }
    }

    public static class JsonPrefixScanner implements ScanCallback {
        private final JsonWriter writer;

        public JsonPrefixScanner(JsonWriter writer) {
            this.writer = writer;
        }

        @Override
        public void scanHandle(byte[] handleBytes) throws HandleException {
            String handle = Util.decodeString(handleBytes);
            try {
                writer.value(handle);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public static class JsonHandleScanner implements ScanCallback {

        private final JsonWriter writer;
        private final HandleStorage storage;

        public JsonHandleScanner(JsonWriter writer, HandleStorage storage) {
            this.writer = writer;
            this.storage = storage;
        }

        @Override
        public void scanHandle(byte[] handleBytes) throws HandleException {
            String handle = Util.decodeString(handleBytes);
            System.out.println("Storing " + handle);
            byte[][] handleRecordBytes = storage.getRawHandleValues(handleBytes, null, null);
            Gson gson = GsonUtility.getPrettyGson();
            HandleValue[] values = Encoder.decodeHandleValues(handleRecordBytes);
            HandleRecord handleRecord = new HandleRecord();
            handleRecord.setHandle(handle);
            handleRecord.setValues(values);
            try {
                writer.name(handle);
                gson.toJson(handleRecord, HandleRecord.class, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

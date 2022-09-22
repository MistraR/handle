/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.io.File;
import java.util.List;

import net.cnri.util.SimpleCommandLine;
import net.cnri.util.StreamTable;
import net.handle.hdllib.HSG;
import net.handle.hdllib.HandleRecord;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;
import net.handle.hdllib.trust.ChainBuilder;
import net.handle.hdllib.trust.ChainVerifier;
import net.handle.hdllib.trust.HandleRecordTrustVerifier;
import net.handle.hdllib.trust.TrustException;
import net.handle.server.Main;
import net.handle.server.ServerLog;
import net.handle.server.Version;

public class DumpHandles {

    private static void printUsage() {
        System.err.println("Usage: hdl-dumpfromprimary [-no-delete] [-service-handle <handle>] <server-directory>");
    }

    public static void main(String[] argv) throws Exception {
        Main main = null;
        SimpleCommandLine simpleCommandLine = new SimpleCommandLine("service-handle");
        simpleCommandLine.parse(argv);
        boolean deleteAll = !simpleCommandLine.hasOption("no-delete");
        String serviceHandle = simpleCommandLine.getOptionArgument("service-handle");
        List<String> operands = simpleCommandLine.getOperands();
        if (operands.size() != 1) {
            printUsage();
            return;
        }
        String configDirStr = operands.get(0);

        StreamTable configTable = new StreamTable();
        // Get, check serverDir
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

        // Create the Main server object and start it
        try {
            main = new Main(serverDir, configTable);
            main.logError(ServerLog.ERRLOG_LEVEL_INFO, "Handle.net Server Software version " + Version.version);
            // main.initialize();
            SiteInfo[] sites = null;
            if (serviceHandle != null) sites = getServiceHandleSites(serviceHandle);
            main.dumpFromPrimary(deleteAll, sites);
            main.shutdown();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            System.out.println("Error: " + e.getMessage());
            System.out.println("       (see the error log for details.)\n");
            System.out.println("Shutting down...");
            System.err.println("Shutting down...");
            if (main != null) try {
                main.shutdown();
            } catch (Exception e2) { /* Ignore */
            }
            System.exit(0);
        }
    }

    private static SiteInfo[] getServiceHandleSites(String serviceHandle) throws Exception {
        HandleResolver resolver = new HandleResolver();
        ChainBuilder builder = new ChainBuilder(resolver);
        ChainVerifier verifier = new ChainVerifier(resolver.getConfiguration().getRootKeys());
        HandleRecordTrustVerifier handleRecordTrustVerifier = new HandleRecordTrustVerifier(builder, verifier);
        HandleValue[] values = resolver.resolveHandle(serviceHandle);
        HandleRecord handleRecord = new HandleRecord(serviceHandle, values);
        if (!handleRecordTrustVerifier.validateHandleRecord(handleRecord)) {
            throw new TrustException("Unable to obtain and validate service handle record from resolver!");
        }
        System.out.println("Dumping from sites at " + serviceHandle);
        return Util.getSitesFromValues(values);
    }
}

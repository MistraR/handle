/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.handle.util.FileSystemReadOnlyChecker;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MonitorDaemon extends Thread {

    private volatile String monitorDataString;
    private final int sleepSeconds;
    private final long startTime;
    private final Gson gson = new Gson();
    private SystemInfoWrapper systemInfoWrapper;

    private final AtomicLong numRequests;
    private final AtomicLong numResolutionRequests;
    private final AtomicLong numAdminRequests;
    private final AtomicLong numTxnRequests;

    private final File baseDir;

    private AtomicInteger requestsPastMinute;
    private AtomicInteger peakRequestsPerMinute;

    private volatile boolean keepRunning = true;

    public MonitorDaemon(int sleepSeconds, long startTime, AtomicLong numRequests, AtomicLong numResolutionRequests, AtomicLong numAdminRequests, AtomicLong numTxnRequests, File baseDir) {
        super("MonitorDaemon");
        setDaemon(true);
        this.sleepSeconds = sleepSeconds;
        this.startTime = startTime;

        this.numRequests = numRequests;
        this.numResolutionRequests = numResolutionRequests;
        this.numAdminRequests = numAdminRequests;
        this.numTxnRequests = numTxnRequests;

        this.baseDir = baseDir;

        initializeSystemInfoWrapper();
    }

    public void setRequestCounters(AtomicInteger requestsPastMinute, AtomicInteger peakRequestsPerMinute) {
        this.requestsPastMinute = requestsPastMinute;
        this.peakRequestsPerMinute = peakRequestsPerMinute;
    }

    private void initializeSystemInfoWrapper() {
        try {
            systemInfoWrapper = new SystemInfoWrapper();
        } catch (Throwable e) {
            System.err.println("Error initializing SystemInfoWrapper in MonitorDaemon: " + e);
        }
    }

    @Override
    public void run() {
        while (keepRunning) {
            try {
                JsonObject monitorData = getServerInfo();
                monitorDataString = gson.toJson(monitorData);
                for (int i = 0; i < sleepSeconds && keepRunning; i++) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    public void shutdown() {
        keepRunning = false;
    }

    public String getStatusString() {
        return monitorDataString;
    }

    private JsonObject getServerInfo() {
        JsonObject systemInfo = new JsonObject();

        systemInfo.addProperty("version", Version.version);
        if (systemInfoWrapper != null) {
            try {
                systemInfo.add("loadAvg", getLoadAverageInfo());
                systemInfo.addProperty("domainName", getFQDN());
                systemInfo.add("mem", getMemInfo());
                systemInfo.add("diskInfo", getDiskInfo());
            } catch (Exception e) {
                systemInfo.addProperty("systemInfoError", e.toString());
            }
        }
        if (baseDir != null) systemInfo.addProperty("isReadOnly", testIfFileSystemIsReadOnly());
        systemInfo.addProperty("startTime", startTime);
        if (numRequests != null) systemInfo.add("requests", getReqInfo());
        if (requestsPastMinute != null) systemInfo.add("requestsPerMinute", getProxyReqInfo());
        systemInfo.addProperty("lastUpdate", System.currentTimeMillis());

        return systemInfo;
    }

    private JsonObject getReqInfo() {
        JsonObject reqTable = new JsonObject();
        reqTable.addProperty("resolution", numResolutionRequests);
        reqTable.addProperty("admin", numAdminRequests);
        reqTable.addProperty("txn", numTxnRequests);
        reqTable.addProperty("total", numRequests);
        return reqTable;
    }

    private JsonObject getProxyReqInfo() {
        JsonObject reqTable = new JsonObject();
        reqTable.addProperty("recent", requestsPastMinute.get());
        reqTable.addProperty("peak", peakRequestsPerMinute.get());
        return reqTable;
    }

    private boolean testIfFileSystemIsReadOnly() {
        return FileSystemReadOnlyChecker.isReadOnly(baseDir);
    }

    private String getFQDN() {
        return systemInfoWrapper.getFQDN();
    }

    private JsonArray getLoadAverageInfo() {
        return systemInfoWrapper.getLoadAverageInfo();
    }

    private JsonArray getDiskInfo() {
        return systemInfoWrapper.getDiskInfo();
    }

    private JsonObject getMemInfo() {
        return systemInfoWrapper.getMemInfo();
    }

}

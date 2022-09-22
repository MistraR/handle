/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.NetworkParams;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;

public class SystemInfoWrapper {
    private final HardwareAbstractionLayer hal;
    private final OperatingSystem os;

    public SystemInfoWrapper() {
        SystemInfo systemInfo = new SystemInfo();
        hal = systemInfo.getHardware();
        os = systemInfo.getOperatingSystem();
    }

    public String getFQDN() {
        NetworkParams net = os.getNetworkParams();
        String hostname = net.getHostName();
        String domain = net.getDomainName();
        return hostname + "." + domain;
    }

    public JsonArray getDiskInfo() {
        JsonArray diskInfos = new JsonArray();

        OSFileStore[] fsystems = os.getFileSystem().getFileStores();
        for (OSFileStore system : fsystems) {
            JsonObject diskInfo = new JsonObject();
            String devName = system.getName();
            String dirName = system.getMount();
            String name = devName + ":" + dirName;
            long freeBytes = system.getUsableSpace();
            long totalBytes = system.getTotalSpace();
            long usedBytes = totalBytes - freeBytes;
            diskInfo.addProperty("name", name);
            diskInfo.addProperty("free", freeBytes);
            diskInfo.addProperty("used", usedBytes);
            diskInfos.add(diskInfo);
        }

        return diskInfos;
    }

    public JsonObject getMemInfo() {
        JsonObject memInfo = new JsonObject();
        GlobalMemory mem = hal.getMemory();
        long available = mem.getAvailable();
        long total = mem.getTotal();
        memInfo.addProperty("used", total - available);
        memInfo.addProperty("free", available);
        return memInfo;
    }

    public JsonArray getLoadAverageInfo() {
        JsonArray loadAverageInfo = new JsonArray();

        CentralProcessor processor = hal.getProcessor();
        double[] laArray = processor.getSystemLoadAverage(3);
        for (double la : laArray) {
            loadAverageInfo.add(fixDoubleForJson(la));
        }
        return loadAverageInfo;
    }

    private double fixDoubleForJson(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return -1;
        return d;
    }

}

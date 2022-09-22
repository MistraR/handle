/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;

public abstract class AbstractServer {
    public static final String SERVER_TYPE = "server_type";
    public static final String HDLSVR_ID = "server";
    public static final String HDLSVR_CONFIG = "server_config";
    public static final String CACHESVR_ID = "cache";
    public static final String CACHESVR_CONFIG = "cache_config";
    public static final String PROXYSVR_ID = "proxy";
    public static final String PROXYSVR_CONFIG = "proxy_config";
    public static final String TRACE_MESSAGES = "trace_outgoing_messages";
    public static final String AUTO_UPDATE_ROOT_INFO = "auto_update_root_info";

    protected volatile boolean keepRunning = true;

    protected HandleResolver resolver;
    protected Main main;
    protected StreamTable config;

    protected AbstractServer() {
        // for testing
    }

    protected AbstractServer(Main main, StreamTable config, HandleResolver resolver) {
        this.config = config;
        this.main = main;
        this.resolver = resolver;

        if (config != null) {
            if (config.getBoolean(TRACE_MESSAGES, false) || config.getBoolean("trace_resolution", false)) {
                resolver.traceMessages = true;
            }
            if (!config.getBoolean(AUTO_UPDATE_ROOT_INFO, true)) {
                resolver.getConfiguration().setAutoUpdateRootInfo(false);
            }
        }

        resolver.getConfiguration().startAutoUpdate(resolver);
    }

    public void start() {
    }

    public StreamTable getConfig() {
        return main.getConfig();
    }

    public java.io.File getConfigDir() {
        return main.getConfigDir();
    }

    public HandleResolver getResolver() {
        return resolver;
    }

    public void logError(int level, String message) {
        main.logError(level, message);
    }

    public boolean logHttpAccesses() {
        return main.logHttpAccesses();
    }

    public void logAccess(String accesssType, InetAddress addr, int opCode, int rsCode, String message, long time) {
        main.logAccess(accesssType, addr, opCode, rsCode, message, time);
    }

    /**********************************************************************
     * Given a request object, should handle the request and return a response
     **********************************************************************/
    public abstract void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException;

    /**********************************************************************
     * Tell the server to shutdown (save open files and clean up resources)
     **********************************************************************/
    public void shutdown() {
        keepRunning = false;
    }

    public abstract X509Certificate getCertificate();

    public abstract X509Certificate[] getCertificateChain();

    public abstract PrivateKey getCertificatePrivateKey();

    public abstract PublicKey getPublicKey();

    public abstract PrivateKey getPrivateKey();

    /**
     * Tell the server to re-dump all handles from a primary handle server
     */
    public abstract void dumpHandles() throws HandleException, java.io.IOException;

    /**********************************************************************
     * Create a server instance based on the configuration.
     **********************************************************************/
    public static AbstractServer getInstance(Main main, StreamTable configTable, HandleResolver resolver) throws Exception {
        String serverType = String.valueOf(configTable.get(SERVER_TYPE, HDLSVR_ID));
        if (serverType.equals(HDLSVR_ID)) {
            StreamTable config = (StreamTable) configTable.get(HDLSVR_CONFIG);
            if (config == null) throw new Exception("Configuration setting \"" + HDLSVR_CONFIG + "\" is required.");
            return new HandleServer(main, config, resolver);

        } else if (serverType.equals(CACHESVR_ID)) {
            StreamTable config = (StreamTable) configTable.get(CACHESVR_CONFIG);
            if (config == null) throw new Exception("Configuration setting \"" + CACHESVR_CONFIG + "\" is required.");
            return new CacheServer(main, config, resolver);

        } else {
            throw new Exception("Configuration setting \"" + SERVER_TYPE + "\" must be " + "\"" + HDLSVR_ID + "\", or \"" + CACHESVR_ID + "\"");
        }

    }
}

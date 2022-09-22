/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import net.handle.server.dns.DnsTcpInterface;
import net.handle.server.dns.DnsUdpInterface;

/***********************************************************************
 * base class describing an object that listens to a network
 * interface/port and handles incoming requests.  Most subclasses
 * will actually dispatch incoming requests to RequestHandlers in
 * separate threads.
 ***********************************************************************/

public abstract class NetworkInterface implements Runnable {
    private volatile boolean keepRunning = true;
    protected AbstractServer server;
    protected boolean needsGC = false;
    boolean processAdminRequests = true;
    boolean processQueries = true;
    protected Main main;
    protected ExecutorService handlerPool = null;

    public static final String INTFC_HDLTCP = "hdl_tcp";
    public static final String INTFC_HDLUDP = "hdl_udp";
    public static final String INTFC_HDLHTTP = "hdl_http";
    public static final String INTFC_DNSUDP = "dns_udp";
    public static final String INTFC_DNSTCP = "dns_tcp";

    public NetworkInterface(Main main) {
        this.main = main;
        this.server = main.getServer();
    }

    public static NetworkInterface getInstance(Main main, String frontEndLabel, StreamTable configTable) throws Exception {
        if (frontEndLabel.startsWith(INTFC_HDLUDP)) {
            return new HdlUdpInterface(main, (StreamTable) configTable.get(frontEndLabel + "_config"));
        } else if (frontEndLabel.startsWith(INTFC_HDLTCP)) {
            return new HdlTcpInterface(main, (StreamTable) configTable.get(frontEndLabel + "_config"));
        } else if (frontEndLabel.startsWith(INTFC_HDLHTTP)) {
            // dealt with elsewhere
            return null;
        } else if (frontEndLabel.startsWith(INTFC_DNSUDP)) {
            return new DnsUdpInterface(main, (StreamTable) configTable.get(frontEndLabel + "_config"), main.getDNSConfig());
        } else if (frontEndLabel.startsWith(INTFC_DNSTCP)) {
            return new DnsTcpInterface(main, (StreamTable) configTable.get(frontEndLabel + "_config"), main.getDNSConfig());
        } else {
            throw new Exception("Invalid interface type: \"" + frontEndLabel + "\"");
        }
    }

    /** Should be called after the initialization of the subclass is complete in
     * order to set the interface settings such as processAdminRequests and
     * processQueries. */
    protected final void initialize() {
        if (server instanceof HandleServer) {
            ServerInfo svrInfo = ((HandleServer) server).getServerInfo();
            Interface ifcs[] = svrInfo.interfaces;
            int bindPort = getPort();
            byte protocol = getProtocol();
            for (int i = 0; ifcs != null && i < ifcs.length; i++) {
                if (ifcs[i] != null && ifcs[i].port == bindPort && ifcs[i].protocol == protocol) {
                    // the port and protocol match, use those to determine the capabilities
                    // advertised for this interface
                    processAdminRequests = ifcs[i].type == Interface.ST_ADMIN || ifcs[i].type == Interface.ST_ADMIN_AND_QUERY;
                    processQueries = ifcs[i].type == Interface.ST_ADMIN_AND_QUERY || ifcs[i].type == Interface.ST_QUERY;
                    break;
                }
            }
        }
    }

    /** Should be overridden to return the protocol as listed in the
     * net.handle.hdllib.Interface.SP_* constants. */
    abstract public byte getProtocol();

    /** Should be overridden to return the protocol as listed in the
     * net.handle.hdllib.Interface.SP_* constants. */
    abstract public int getPort();

    /** Returns true if this interface has served enough requests to warrant
      invoking the garbage collector. */
    final boolean needsGC() {
        return needsGC;
    }

    /** Tells the interface that the garbage collector has been run.  This
      resets the needsGC flag. */
    final void resetGC() {
        needsGC = false;
    }

    /** Determine whether or not this interface can process the given message.
     * If this returns null then the message can be processed.  If it returns
     * a non-null String then that String should be returned as an error message
     * in a response with protocol error response code. */
    public String canProcessMsg(AbstractRequest req) {
        return Interface.canProcessMsg(req, processQueries, processAdminRequests);
    }

    @Override
    public final void run() {
        while (keepRunning) {
            try {
                serveRequests();
            } catch (Throwable t) {
                if (!keepRunning) return;
                main.logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error establishing interface: " + t);
                t.printStackTrace();
            }
            ExecutorService pool = handlerPool;
            if (pool != null) pool.shutdown();
            stopService();
            try {
                // sleep for about 5 minutes...
                // the interface is probably just gummed up so we can't bind
                if (keepRunning) {
                    Thread.sleep(300000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ThreadDeath e) {
                // ignore
            } catch (Throwable t) {
                main.logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error sleeping!: " + t);
            }
        }
    }

    /****************************************************************
     * Tells this interface to stop listening for new connections
     * and to wrap up any current connections.
     ****************************************************************/
    public final void stopRunning() {
        keepRunning = false;
        stopService();
        ExecutorService pool = handlerPool;
        if (pool != null) {
            pool.shutdown();
            boolean terminated = false;
            try {
                terminated = pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!terminated) {
                pool.shutdownNow();
                try {
                    terminated = pool.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /****************************************************************
     * Tells the interface to finish up the current operation and
     * stop listening for new connections.
     ***************************************************************/
    protected abstract void stopService();

    /****************************************************************
     * Tells the interface to listen for incoming requests until
     * stopService() is called.
     ***************************************************************/
    public abstract void serveRequests();
}

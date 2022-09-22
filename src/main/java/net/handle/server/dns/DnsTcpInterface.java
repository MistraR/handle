/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.dns;

import java.net.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import net.cnri.util.GrowBeforeTransferQueueThreadPoolExecutor;
import net.cnri.util.StreamTable;
import net.handle.hdllib.Util;
import net.handle.server.Main;
import net.handle.server.NetworkInterface;
import net.handle.server.ServerLog;

/**
 *    Description:  Object that listens for UDP-BIND requests and passes them off
 *                  to a DnsUdpRequestHandler.java object to be handled.
 *
 *    @see DnsUdpRequestHandler
 */
public class DnsTcpInterface extends NetworkInterface {
    private InetAddress bindAddress;
    private int threadLife = 500;
    private int bindPort = 53;
    private final int backlog;
    private int numThreads = 10;

    private int maxHandlers = 200;
    private boolean logAccesses = false;
    private ServerSocket socket = null;
    private boolean keepServing = true;

    public DnsConfiguration dnsConfig;

    public DnsTcpInterface(Main main, StreamTable config, DnsConfiguration dnsConfig) throws UnknownHostException {
        super(main);
        this.dnsConfig = dnsConfig;
        // get the specific IP address to bind to, if any.
        Object bindAddressStr = config.get("bind_address");
        if (bindAddressStr == null) {
            bindAddress = null;
        } else {
            bindAddress = InetAddress.getByName(String.valueOf(bindAddressStr));
        }
        bindPort = Integer.parseInt((String) config.get("bind_port"));// get the port to listen on...
        backlog = Integer.parseInt((String) config.get("backlog", "-1"));// get the max backlog size...

        try { // get the number of thread (default is 10);
            numThreads = Integer.parseInt((String) config.get("num_threads"));
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified thread count, using default: " + numThreads);
        }

        try { // get the max backlog size...
            if (config.containsKey("max_handlers")) {
                maxHandlers = Integer.parseInt((String) config.get("max_handlers"));
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified max_handlers count, using default: " + maxHandlers);
        }

        try { // get the maximum thread life (default is 500)
            if (config.containsKey("thread_life")) {
                threadLife = Integer.parseInt((String) config.get("thread_life"));
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Invalid thread life, using default: " + threadLife);
        }
        // check if we should log accesses or not...
        logAccesses = config.getBoolean("log_accesses");
    }

    /** Since this is not a generic Handle interface, we don't advertise this protocol for general Handle resolution. */
    @Override
    public byte getProtocol() {
        return (byte) -1;
    }

    @Override
    public int getPort() {
        return bindPort;
    }

    /**
     * Tells the interface to finish up the current operation and
     * stop listening for new connections.
     */
    @Override
    protected void stopService() {
        keepServing = false;
        try {
            socket.close();
        } catch (Exception e) {
        }
    }

    @Override
    public void serveRequests() {
        try {
            if (bindAddress == null) {
                socket = new ServerSocket(bindPort, backlog);
            } else {
                socket = new ServerSocket(bindPort, backlog, bindAddress);
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_FATAL, String.valueOf(this.getClass()) + ": Error setting up server socket: " + e);
            return;
        }

        System.out.println("DNS Request Listener:");
        System.out.println("   address: " + (bindAddress == null ? "ANY" : "" + Util.rfcIpRepr(bindAddress)));
        System.out.println("      port: " + bindPort);

        handlerPool = new GrowBeforeTransferQueueThreadPoolExecutor(numThreads, maxHandlers, 1, TimeUnit.MINUTES, new LinkedTransferQueue<>());
        System.out.println("Starting DNS-TCP request handlers...");
        try {
            System.out.flush();
        } catch (Exception e) {
        }

        long recvTime = 0;
        while (keepServing) {
            try {
                @SuppressWarnings("resource")
                Socket newsock = socket.accept();
                newsock.setSoTimeout(2 * 60 * 1000);
                recvTime = System.currentTimeMillis();
                handlerPool.execute(new DnsTcpRequestHandler(main, dnsConfig, logAccesses, newsock, recvTime));
            } catch (Exception e) {
                if (keepServing) {
                    main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "" + this.getClass() + ": Error handling request: " + e);
                    e.printStackTrace(System.err);
                }
            }
        }
        try {
            socket.close();
        } catch (Exception e) {
        }
    }
}

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
public class DnsUdpInterface extends NetworkInterface {
    private static final int MAX_UDP_PACKET_SIZE = 512; // Could set to 4096, but requests should always fit in 512

    private InetAddress bindAddress;
    private int threadLife = 500;
    private int bindPort = 53;
    private int numThreads = 10;

    private int maxHandlers = 200;
    private boolean logAccesses = false;
    private DatagramSocket dsocket = null;
    private boolean keepServing = true;

    public DnsConfiguration dnsConfig;

    public DnsUdpInterface(Main main, StreamTable config, DnsConfiguration dnsConfig) throws UnknownHostException {
        super(main);

        this.dnsConfig = dnsConfig;

        // get the specific IP address to bind to, if any.
        Object bindAddressStr = config.get("bind_address");
        if (bindAddressStr == null) bindAddress = null;
        else bindAddress = InetAddress.getByName(String.valueOf(bindAddressStr));

        // get the port to listen on...
        bindPort = Integer.parseInt((String) config.get("bind_port"));

        // get the number of thread (default is 10);
        try {
            numThreads = Integer.parseInt((String) config.get("num_threads"));
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified thread count, using default: " + numThreads);
        }

        // get the max backlog size...
        try {
            if (config.containsKey("max_handlers")) {
                maxHandlers = Integer.parseInt((String) config.get("max_handlers"));
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified max_handlers count, using default: " + maxHandlers);
        }

        // get the maximum thread life (default is 500)
        try {
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
            dsocket.close();
        } catch (Exception e) {
        }
    }

    @Override
    public void serveRequests() {
        try {
            if (bindAddress == null) {
                dsocket = new DatagramSocket(bindPort);
            } else {
                dsocket = new DatagramSocket(bindPort, bindAddress);
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_FATAL, String.valueOf(this.getClass()) + ": Error setting up server socket: " + e);
            return;
        }

        System.out.println("DNS Request Listener:");
        System.out.println("   address: " + (bindAddress == null ? "ANY" : "" + Util.rfcIpRepr(bindAddress)));
        System.out.println("      port: " + bindPort);

        handlerPool = new GrowBeforeTransferQueueThreadPoolExecutor(numThreads, maxHandlers, 1, TimeUnit.MINUTES, new LinkedTransferQueue<>());
        System.out.println("Starting DNS-UDP request handlers...");
        try {
            System.out.flush();
        } catch (Exception e) {
        }

        long recvTime = 0;
        while (keepServing) {
            try {
                DatagramPacket dPacket = new DatagramPacket(new byte[MAX_UDP_PACKET_SIZE], MAX_UDP_PACKET_SIZE);
                dsocket.receive(dPacket);

                recvTime = System.currentTimeMillis();

                handlerPool.execute(new DnsUdpRequestHandler(main, dsocket, dnsConfig, logAccesses, dPacket, recvTime));

            } catch (Exception e) {
                if (keepServing) {
                    main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "" + this.getClass() + ": Error handling request: " + e);
                    e.printStackTrace(System.err);
                }
            }
        }

        try {
            dsocket.close();
        } catch (Exception e) {
        }
    }
}

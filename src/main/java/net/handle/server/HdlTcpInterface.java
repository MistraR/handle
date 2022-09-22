/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.GrowBeforeTransferQueueThreadPoolExecutor;
import net.cnri.util.StreamTable;
import net.handle.hdllib.*;

import java.net.*;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/***********************************************************************
 * base class describing an object that listens to a network
 * interface/port and handles incoming requests.  Most subclasses
 * will actually dispatch incoming requests to RequestHandlers in
 * separate threads.
 ***********************************************************************/

public class HdlTcpInterface extends NetworkInterface {

    private InetAddress bindAddress;
    private int threadLife = 200;
    private int maxHandlers = 200;
    private int bindPort = 2641;
    private int backlog;
    private int numThreads = 10;
    private int maxIdleTime = 5 * 60 * 1000;
    private boolean logAccesses = false;
    private ServerSocket socket = null;
    private boolean keepServing = true;

    public HdlTcpInterface(Main main, StreamTable config) throws Exception {
        super(main);
        init(config);
    }

    @Override
    public byte getProtocol() {
        return Interface.SP_HDL_TCP;
    }

    @Override
    public int getPort() {
        return bindPort;
    }

    private void init(StreamTable config) throws Exception {
        Object bindAddressStr = config.get("bind_address"); // get the specific IP address to bind to, if any.
        if (bindAddressStr == null) {
            bindAddress = null;
        } else {
            bindAddress = InetAddress.getByName(String.valueOf(bindAddressStr));
        }
        bindPort = Integer.parseInt((String) config.get("bind_port")); // get the port to listen on...
        backlog = Integer.parseInt((String) config.get("backlog", "-1")); // get the max backlog size...

        try { // get the max backlog size...
            if (config.containsKey("max_handlers")) {
                maxHandlers = Integer.parseInt((String) config.get("max_handlers"));
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified max_handlers count, using default: " + maxHandlers);
        }

        try { // get the number of thread (default is 10);
            numThreads = Integer.parseInt((String) config.get("num_threads"));
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "unspecified thread count, using default: " + numThreads);
        }

        try { // get the maximum thread life (default is 200)
            if (config.containsKey("thread_life")) {
                threadLife = Integer.parseInt((String) config.get("thread_life"));
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Invalid thread life, using default: " + threadLife);
        }

        try {
            if (config.containsKey("max_idle_time")) {
                maxIdleTime = Integer.parseInt((String) config.get("max_idle_time"));
                if (maxIdleTime < 0) throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Invalid max_idle_time, using default: " + maxIdleTime);
        }

        // check if we should log accesses or not...
        logAccesses = config.getBoolean("log_accesses");
        super.initialize();
    }

    /****************************************************************
     * Tells the interface to finish up the current operation and
     * stop listening for new connections.
     ***************************************************************/
    @Override
    protected void stopService() {
        keepServing = false;
        try {
            socket.close();
        } catch (Exception e) {
        }
    }

    /****************************************************************
     * Tells the interface to listen for incoming requests until
     * stopService() is called.
     ***************************************************************/
    @Override
    public void serveRequests() {
        keepServing = true;
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
        System.out.println("TCP handle Request Listener:");
        System.out.println("   address: " + (bindAddress == null ? "ANY" : "" + Util.rfcIpRepr(bindAddress)));
        System.out.println("      port: " + bindPort);
        handlerPool = new GrowBeforeTransferQueueThreadPoolExecutor(numThreads, maxHandlers, 1, TimeUnit.MINUTES, new LinkedTransferQueue<>());
        System.out.println("Starting TCP request handlers...");
        try {
            System.out.flush();
        } catch (Exception e) {
        }
        long recvTime = 0;
        while (keepServing) {
            try {
                @SuppressWarnings("resource")
                Socket newsock = socket.accept();
                newsock.setSoTimeout(maxIdleTime);
                recvTime = System.currentTimeMillis();
                handlerPool.execute(new HdlTcpRequestHandler(main, this, logAccesses, newsock, recvTime));
                //        if(++reqCount > 1000) {
                //          needsGC = true;
                //          reqCount = 0;
                //        }
            } catch (Exception e) {
                if (keepServing) {
                    main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Error handling request: " + e);
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

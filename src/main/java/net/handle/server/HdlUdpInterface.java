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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/***********************************************************************
 * base class describing an object that listens to a network
 * interface/port and handles incoming requests.  Most subclasses
 * will actually dispatch incoming requests to RequestHandlers in
 * separate threads.
 ***********************************************************************/

public class HdlUdpInterface extends NetworkInterface {
    private InetAddress bindAddress;
    private int threadLife = 500;
    private int bindPort = 2641;
    private int numThreads = 10;
    private int maxHandlers = 200;
    private boolean logAccesses = false;
    private DatagramSocket dsocket = null;
    private boolean keepServing = true;

    private final ConcurrentMap<String, HdlUdpPendingRequest> pendingRequests;

    public HdlUdpInterface(Main main, StreamTable config) throws Exception {
        super(main);
        pendingRequests = new ConcurrentHashMap<>();
        init(config);
    }

    @Override
    public byte getProtocol() {
        return Interface.SP_HDL_UDP;
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
            dsocket.close();
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
                dsocket = new DatagramSocket(bindPort);
            } else {
                dsocket = new DatagramSocket(bindPort, bindAddress);
            }
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_FATAL, String.valueOf(this.getClass()) + ": Error setting up server socket: " + e);
            return;
        }

        System.out.println("UDP handle Request Listener:");
        System.out.println("   address: " + (bindAddress == null ? "ANY" : "" + Util.rfcIpRepr(bindAddress)));
        System.out.println("      port: " + bindPort);

        handlerPool = new GrowBeforeTransferQueueThreadPoolExecutor(numThreads, maxHandlers, 1, TimeUnit.MINUTES, new LinkedTransferQueue<>());
        //    handlerPool.setHandlerLife(threadLife);
        System.out.println("Starting UDP request handlers...");
        try {
            System.out.flush();
        } catch (Exception e) {
        }
        long recvTime = 0;
        while (keepServing) {
            try {
                DatagramPacket dPacket = new DatagramPacket(new byte[Common.MAX_UDP_PACKET_SIZE], Common.MAX_UDP_PACKET_SIZE);
                dsocket.receive(dPacket);
                recvTime = System.currentTimeMillis();
                handlerPool.execute(new HdlUdpRequestHandler(main, dsocket, this, logAccesses, dPacket, recvTime));
                //((HdlUdpRequestHandler)handlerPool.getHandler()).serviceRequest(dPacket, recvTime);
                //        if(++reqCount > 1000) {
                //          needsGC = true;
                //          reqCount = 0;
                //        }
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

    /************************************************************************************
     * This should be called by request handlers when packets are received that are
     * part of multi-packet messages (ie when the messageLength value in the envelope
     * is longer than Common.MAX_UDP_PACKET_SIZE).  If this is the first packet received
     * for any given message, then this will block and wait until either 1) all of the
     * packets have been received, or 2) we time-out waiting for all of the packets.
     * If 1) then we return an HdlUdpPendingRequest object which the caller can then
     * call getMessage() on to get the entire message.  If 2) then null will be returned
     * and the caller just ignores the packet.
     ************************************************************************************/
    HdlUdpPendingRequest addMultiPacketListener(MessageEnvelope env, DatagramPacket pkt, InetAddress addr) {
        String id = HdlUdpPendingRequest.getRequestId(addr, env.requestId);
        HdlUdpPendingRequest req = null;

        // check the list of pending requests to see if someone else is
        // already listening for this request
        HdlUdpPendingRequest existingReq = pendingRequests.get(id);
        if (existingReq == null) {
            req = new HdlUdpPendingRequest(id, env, pkt);
            existingReq = pendingRequests.putIfAbsent(id, req);
        }
        if (existingReq != null) { // the request is already pending... and already has a handler.
            // so we will add this packet to the request and go away
            existingReq.addPacket(env, pkt);
            if (existingReq.isComplete()) {
                // notify the handler that the request is complete
                synchronized (existingReq) {
                    existingReq.notifyAll(); // could just be a notify() call.. shouldn't matter
                }
            }
            return null;
        }
        if (req == null) throw new AssertionError();
        // this is the first packet received for a new request
        // go to sleep until the rest of the request comes in...
        // at which time, someone will wake us up.  Or just
        // timeout after a certain period.
        synchronized (req) {
            // wait for a maximum of 5 seconds
            try {
                req.wait(5000);
            } catch (Exception e) {
            }
            // remove the request since we are handling (or ignoring) it
            pendingRequests.remove(req.idString);
        }
        // if the request is complete, return it.  Otherwise, throw it out.
        if (!req.isComplete()) return null;
        return req;
    }
}

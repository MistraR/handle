/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.dns;

import net.handle.dnslib.*;
import net.handle.server.Main;
import net.handle.server.ServerLog;

import java.io.IOException;

import java.net.*;

/**
 *    Description:  A DnsUdpRequestHandler object will handle requests submitted using
 *                  the BIND protocol.  The requests for DNS names will be translated
 *                  into handle names and resolved using the specified handle source
 *                  object.  After the resolution is made, a response is returned
 *                  using the DNS BIND protocol.
 */

public class DnsUdpRequestHandler implements Runnable {
    public static final String ACCESS_TYPE = "UDP:DNS";

    private final Main main;
    public final DnsConfiguration dnsConfig;
    private final NameServer nameServer;
    private final boolean logAccesses;
    private final DatagramSocket dsocket;

    private final DatagramPacket packet;
    private final long recvTime;

    public DnsUdpRequestHandler(Main main, DatagramSocket dsock, DnsConfiguration dnsConfig, boolean logAccesses, DatagramPacket packet, long recvTime) {
        this.main = main;
        this.dsocket = dsock;
        this.logAccesses = logAccesses;
        this.dnsConfig = dnsConfig;
        this.nameServer = dnsConfig.getNameServer();
        this.packet = packet;
        this.recvTime = recvTime;
    }

    @Override
    public void run() {
        try {
            handleRequest();
        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception processing request: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Description: Sends a response
     * @param dnsMessage
     */

    private void sendResponse(Message dnsMessage, int udpPayloadSize) {
        InetAddress addr = packet.getAddress();
        int port = packet.getPort();

        try {
            byte buf[] = dnsMessage.getDatagram(udpPayloadSize);
            dsocket.send(new DatagramPacket(buf, buf.length, addr, port));

            if (logAccesses) {
                long time = System.currentTimeMillis() - recvTime;
                String logName = dnsMessage.getQuestionNameAsString();
                main.logAccess(ACCESS_TYPE, packet.getAddress(), dnsMessage.getOpcode(), dnsMessage.getExtendedResponseCode(), logName, time);
            }

        } catch (IOException e) {
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": unable to send response packet to " + addr + ":" + port);
        }
    }

    /**
     * Parse the DNS request from the UDP and convert it to a handle request
     * that is sent to the back-end for processing.
     * @throws IOException
     */
    private void handleRequest() throws IOException {
        // packet and recvTime will have been set
        boolean allowed = dnsConfig.getAllowQuery(packet.getAddress());
        boolean recursionAvailable = dnsConfig.getRecursive(packet.getAddress());

        Message response;
        byte[] queryBytes = packet.getData();
        int[] udpPayloadArr = new int[] { 512 };
        if (!allowed) response = nameServer.refusalResponse(queryBytes);
        else {
            response = nameServer.respondToBytes(queryBytes, recursionAvailable, udpPayloadArr);
        }

        if (response == null) return;

        sendResponse(response, udpPayloadArr[0]);
    }
}

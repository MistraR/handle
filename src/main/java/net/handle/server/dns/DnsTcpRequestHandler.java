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
import java.io.InputStream;
import java.io.OutputStream;

import java.net.*;

/**
 *    Description:  A DnsUdpRequestHandler object will handle requests submitted using
 *                  the BIND protocol.  The requests for DNS names will be translated
 *                  into handle names and resolved using the specified handle source
 *                  object.  After the resolution is made, a response is returned
 *                  using the DNS BIND protocol.
 */

public class DnsTcpRequestHandler implements Runnable {
    public static final String ACCESS_TYPE = "TCP:DNS";

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private final long recvTime;

    private final Main main;
    public final DnsConfiguration dnsConfig;
    private final boolean logAccesses;
    private final NameServer nameServer;

    public DnsTcpRequestHandler(Main main, DnsConfiguration dnsConfig, boolean logAccesses, Socket socket, long recvTime) {
        this.main = main;
        this.logAccesses = logAccesses;
        this.dnsConfig = dnsConfig;
        this.nameServer = this.dnsConfig.getNameServer();
        this.socket = socket;
        this.recvTime = recvTime;
    }

    @Override
    public void run() {
        try {
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
            handleRequest();

        } catch (Exception e) {
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception processing request: " + e);
            e.printStackTrace();
        } finally {
            close();
        }
    }

    /**
     * Description: Sends a response
     * @param dnsMessage
     */

    private void sendResponse(Message dnsMessage) {
        try {
            byte buf[] = dnsMessage.getDatagram(65535);
            out.write((buf.length >> 8) & 0xFF);
            out.write(buf.length & 0xFF);
            out.write(buf);
            out.flush();
            if (logAccesses) {
                long time = System.currentTimeMillis() - recvTime;
                String logName = dnsMessage.getQuestionNameAsString();
                main.logAccess(ACCESS_TYPE, socket.getInetAddress(), dnsMessage.getOpcode(), dnsMessage.getExtendedResponseCode(), logName, time);
            }

        } catch (IOException e) {
            InetAddress addr = socket.getInetAddress();
            int port = socket.getPort();
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": unable to send response packet to " + addr + ":" + port);
            e.printStackTrace();
        }
    }

    private void close() {
        if (in != null) {
            try {
                in.close();
            } catch (Exception e) {
            }
            in = null;
        }
        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
            }
            out = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
            }
            socket = null;
        }
    }

    /**
     * Parse the DNS request from the socket and convert it to a handle request
     * that is sent to the back-end for processing.
     * @throws IOException
     */
    private void handleRequest() throws IOException {
        // socket and recvTime will have been set

        boolean allowed = dnsConfig.getAllowQuery(socket.getInetAddress());
        boolean recursionAvailable = dnsConfig.getRecursive(socket.getInetAddress());

        Message response;
        try {
            while (true) {
                int firstByte = in.read();
                int secondByte = in.read();
                if (firstByte < 0 || secondByte < 0) return;
                int len = ((firstByte & 0xFF) << 8) | (secondByte & 0xFF);

                byte[] queryBytes = new byte[len];
                int n = 0;
                int r = 0;
                while (n < len) {
                    r = in.read(queryBytes, n, len - n);
                    if (r <= 0) break;
                    n += r;
                }
                if (n < len) return;
                if (!allowed) response = nameServer.refusalResponse(queryBytes);
                else {
                    response = nameServer.respondToBytes(queryBytes, recursionAvailable, null);
                }

                if (response == null) return;

                sendResponse(response);
            }
        } catch (Throwable e) {
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Error handling request: " + e);
            e.printStackTrace(System.err);
        }

    }
}

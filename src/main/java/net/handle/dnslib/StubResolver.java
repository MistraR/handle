/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/* TODO: change behavior when one of the forward-to servers goes down */
public class StubResolver implements NameResolver {
    private final List<InetAddress> recursiveResolvers;
    private final boolean useLargeEDNS;
    private Cache cache;

    public StubResolver(List<InetAddress> recursiveResolvers, boolean useLargeEDNS) {
        this.recursiveResolvers = recursiveResolvers;
        this.useLargeEDNS = useLargeEDNS;
    }

    @Override
    public void setCache(int prefetcherThreads, int size) {
        if (size <= 0) return;
        this.cache = new Cache(query -> realRespondToQuery(query), prefetcherThreads, size);
    }

    private static final Random RANDOM = new Random();

    @Override
    public Message respondToQuery(Message query) {
        if (cache != null) {
            Message response = cache.get(query);
            if (response != null) return response;
        }

        return realRespondToQuery(query);
    }

    Message realRespondToQuery(Message incomingQuery) {
        Message initialResponse = Message.initialResponse(incomingQuery, false, false);
        if (initialResponse == null || initialResponse.getExtendedResponseCode() != Message.RC_OK) return initialResponse;

        List<InetAddress> resolversCopy = new ArrayList<>(recursiveResolvers.size());
        resolversCopy.addAll(recursiveResolvers);
        Collections.shuffle(resolversCopy);
        for (InetAddress serverAddress : resolversCopy) {
            Message response = udpResponse(incomingQuery.getQuestion(), serverAddress);
            if (response == null) continue;
            if (response.truncated) response = tcpResponse(incomingQuery.getQuestion(), serverAddress);
            if (response == null) continue;

            response.id = incomingQuery.id;
            if (incomingQuery.ednsOptRecord != null) response.ednsOptRecord = useLargeEDNS ? Message.LARGE_EDNS : Message.SMALL_EDNS;
            if (cache != null) cache.put(incomingQuery, response);
            return response;
        }

        Message response = Message.initialResponse(incomingQuery, true, useLargeEDNS);
        response.setResponseCode(Message.RC_SERVER_ERROR);
        return response;
    }

    private Message udpResponse(Question question, InetAddress serverAddress) {
        // pick a randomized source port
        DatagramSocket sock = null;
        try {
            while (sock == null) {
                int port = RANDOM.nextInt(63336 - 1025) + 1025;
                int count = 0;
                try {
                    sock = new DatagramSocket(port);
                } catch (BindException e) {
                    count++;
                    if (count > 20) {
                        System.err.println("Unable to find random source port.");
                        sock = new DatagramSocket();
                    }
                }
            }

            sock.setSoTimeout(10000);
            Message query = new Message(question, true, false);
            byte[] queryBytes = query.getDatagram(512);
            DatagramPacket packet = new DatagramPacket(queryBytes, queryBytes.length, serverAddress, 53);
            sock.send(packet);

            packet = new DatagramPacket(new byte[512], 512);
            try {
                sock.receive(packet);
            } catch (Exception e) {
                return null;
            }
            try { sock.close(); } catch (Exception e) {}

            // query must be from the right server
            if (!serverAddress.equals(packet.getAddress())) return null;

            Message response = new Message();
            try {
                response.parseWire(packet.getData());
            } catch (ParseException e) {
                return null;
            }

            // query must match id and question
            if (response.id != query.id) return null;
            if (!query.getQuestion().equals(response.getQuestion())) return null;

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (sock!=null) try { sock.close(); } catch (Exception e) {}
        }
    }

    private Message tcpResponse(Question question, InetAddress serverAddress) {
        Socket tcpSock = new Socket();
        try {
            while (true) {
                int port = RANDOM.nextInt(63336 - 1025) + 1025;
                int count = 0;
                try {
                    tcpSock.bind(new InetSocketAddress(port));
                    break;
                } catch (BindException e) {
                    count++;
                    if (count > 20) {
                        System.err.println("Unable to find random source port.");
                        tcpSock.bind(null);
                        break;
                    }
                }
            }

            tcpSock.setSoTimeout(20000);
            tcpSock.setSoLinger(false, 0);
            tcpSock.connect(new InetSocketAddress(serverAddress, 53), 20000);

            Message query = new Message(question, true, false);
            byte[] responseBytes;
            try (
                InputStream in = tcpSock.getInputStream();
                OutputStream out = tcpSock.getOutputStream();
            ) {
                byte buf[] = query.getDatagram(65535);
                out.write((buf.length >> 8) & 0xFF);
                out.write(buf.length & 0xFF);
                out.write(buf);

                int firstByte = in.read();
                int secondByte = in.read();
                if (firstByte < 0 || secondByte < 0) return null;
                int len = ((firstByte & 0xFF) << 8) | (secondByte & 0xFF);

                responseBytes = new byte[len];
                int n = 0;
                int r = 0;
                while (n < len) {
                    r = in.read(responseBytes, n, len - n);
                    if (r <= 0) break;
                    n += r;
                }
                if (n < len) return null;
                try { tcpSock.close(); } catch (Exception e) {}
            }

            Message response = new Message();
            try {
                response.parseWire(responseBytes);
            } catch (ParseException e) {
                return null;
            }

            // query must match id and question
            if (response.id != query.id) return null;
            if (!query.getQuestion().equals(response.getQuestion())) return null;

            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try { tcpSock.close(); } catch (Exception e) {}
        }
    }
}

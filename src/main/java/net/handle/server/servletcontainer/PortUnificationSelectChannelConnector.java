/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.eclipse.jetty.http.HttpSchemes;
import org.eclipse.jetty.io.AsyncEndPoint;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.nio.AsyncConnection;
import org.eclipse.jetty.io.nio.ChannelEndPoint;
import org.eclipse.jetty.io.nio.SelectChannelEndPoint;
import org.eclipse.jetty.io.nio.SslConnection;
import org.eclipse.jetty.io.nio.SelectorManager.SelectSet;
import org.eclipse.jetty.io.nio.SslConnection.SslEndPoint;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.ssl.SslCertificates;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class PortUnificationSelectChannelConnector extends SslSelectChannelConnector {
    public PortUnificationSelectChannelConnector() {
        super();
    }

    public PortUnificationSelectChannelConnector(SslContextFactory sslContextFactory) {
        super(sslContextFactory);
    }

    @Override
    @SuppressWarnings("resource") // we don't need to close the channel here
    protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey key) throws IOException {
        return super.newEndPoint(new ReadAheadSocketChannelWrapper(channel, 1), selectSet, key);
    }

    @Override
    protected AsyncConnection newConnection(SocketChannel channel, AsyncEndPoint endPoint) {
        return new LazyConnection((ReadAheadSocketChannelWrapper) channel, endPoint);
    }

    @Override
    public void customize(EndPoint endpoint, Request request) throws IOException {
        String scheme = request.getScheme();
        try {
            super.customize(endpoint, request);
            if (endpoint instanceof SslConnection.SslEndPoint) {
                SslConnection.SslEndPoint sslEndPoint = (SslConnection.SslEndPoint) endpoint;
                request.setAttribute(TlsRenegotiationRequestor.class.getName(), new TlsRenegotiationRequestor(sslEndPoint, request));
            }
        } catch (ClassCastException e) {
            request.setScheme(scheme);
        }
    }

    @Override
    public boolean isConfidential(Request request) {
        if (request.getAttribute("javax.servlet.request.cipher_suite") != null) return true;
        else return isForwarded() && request.getScheme().equalsIgnoreCase(HttpSchemes.HTTPS);
    }

    @Override
    public boolean isIntegral(Request request) {
        return isConfidential(request);
    }

    class LazyConnection implements AsyncConnection {
        private final ReadAheadSocketChannelWrapper channel;
        private final AsyncEndPoint endPoint;
        private final long timestamp;
        private AsyncConnection connection;
        private SslEndPoint sslEndPoint;
        private SSLEngine sslEngine;

        public LazyConnection(ReadAheadSocketChannelWrapper channel, AsyncEndPoint endPoint) {
            this.channel = channel;
            this.endPoint = endPoint;
            this.timestamp = System.currentTimeMillis();
            determineNewConnection(channel, endPoint, false);
        }

        @Override
        public Connection handle() throws IOException {
            if (connection == null) {
                determineNewConnection(channel, endPoint, false);
                channel.throwPendingException();
            }
            if (connection != null) {
                boolean handshaking = sslEndPoint != null && sslEndPoint.isOpen() && sslEngine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING;
                connection.handle();
                if (handshaking && sslEndPoint.isOpen() && sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                    Request request = getRequestStoredInEndpointChannel();
                    if (request != null) {
                        request.removeAttribute("javax.servlet.request.X509Certificate");
                        SslCertificates.customize(sslEngine.getSession(), sslEndPoint, request);
                        channel.request = null;
                        request.getAsyncContext().dispatch();
                    }
                }
            }
            return this;
        }

        @SuppressWarnings("resource") // we don't need to close the channel here
        private Request getRequestStoredInEndpointChannel() {
            if (!(endPoint instanceof ChannelEndPoint)) return null;
            @SuppressWarnings("hiding")
            ByteChannel channel = ((ChannelEndPoint) endPoint).getChannel();
            if (!(channel instanceof ReadAheadSocketChannelWrapper)) return null;
            WeakReference<Request> requestRef = ((ReadAheadSocketChannelWrapper) channel).request;
            if (requestRef == null) return null;
            return requestRef.get();
        }

        @Override
        public long getTimeStamp() {
            return timestamp;
        }

        @Override
        public void onInputShutdown() throws IOException {
            determineNewConnection(channel, endPoint, true);
            connection.onInputShutdown();
        }

        @Override
        public boolean isIdle() {
            determineNewConnection(channel, endPoint, false);
            if (connection != null) return connection.isIdle();
            else return false;
        }

        @Override
        public boolean isSuspended() {
            determineNewConnection(channel, endPoint, false);
            if (connection != null) return connection.isSuspended();
            else return false;
        }

        @Override
        public void onClose() {
            determineNewConnection(channel, endPoint, true);
            connection.onClose();
        }

        @Override
        public void onIdleExpired(long l) {
            determineNewConnection(channel, endPoint, true);
            connection.onIdleExpired(l);
        }

        void determineNewConnection(@SuppressWarnings("hiding") ReadAheadSocketChannelWrapper channel, @SuppressWarnings("hiding") AsyncEndPoint endPoint, boolean force) {
            if (connection != null) return;
            byte[] bytes = channel.getBytes();
            if ((bytes == null || bytes.length == 0) && !force) return;
            if (looksLikeSsl(bytes)) {
                connection = PortUnificationSelectChannelConnector.super.newConnection(channel, endPoint);
                sslEndPoint = (SslEndPoint) ((SslConnection) connection).getSslEndPoint();
                sslEngine = sslEndPoint.getSslEngine();
            } else {
                connection = PortUnificationSelectChannelConnector.super.newPlainConnection(channel, endPoint);
            }
        }

        // TLS first byte is 0x16
        // SSLv2 first byte is >= 0x80
        // HTTP is guaranteed many bytes of ASCII
        private boolean looksLikeSsl(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return false; // force HTTP
            byte b = bytes[0];
            return b >= 0x7F || (b < 0x20 && b != '\n' && b != '\r' && b != '\t');
        }
    }

    static class ReadAheadSocketChannelWrapper extends SocketChannel {
        private final SocketChannel channel;
        private final ByteBuffer start;
        private byte[] bytes;
        private IOException pendingException;
        private int leftToRead;

        // unrelated hack to monkey-patch Jetty information flow for SSL renegotiation
        WeakReference<Request> request;

        public ReadAheadSocketChannelWrapper(SocketChannel channel, int readAheadLength) throws IOException {
            super(channel.provider());
            this.channel = channel;
            start = ByteBuffer.allocate(readAheadLength);
            leftToRead = readAheadLength;
            readAhead();
        }

        public synchronized void readAhead() throws IOException {
            if (leftToRead > 0) {
                int n = channel.read(start);
                if (n == -1) {
                    leftToRead = -1;
                } else {
                    leftToRead -= n;
                }
                if (leftToRead <= 0) {
                    start.flip();
                    bytes = new byte[start.remaining()];
                    start.get(bytes);
                    start.rewind();
                }
            }
        }

        public byte[] getBytes() {
            if (pendingException == null) {
                try {
                    readAhead();
                } catch (IOException e) {
                    pendingException = e;
                }
            }
            return bytes;
        }

        public void throwPendingException() throws IOException {
            if (pendingException != null) {
                IOException e = pendingException;
                pendingException = null;
                throw e;
            }
        }

        private int readFromStart(ByteBuffer dst) {
            int sr = start.remaining();
            int dr = dst.remaining();
            if (dr == 0) return 0;
            int n = Math.min(dr, sr);
            dst.put(bytes, start.position(), n);
            start.position(start.position() + n);
            return n;
        }

        @Override
        public synchronized int read(ByteBuffer dst) throws IOException {
            throwPendingException();
            readAhead();
            if (leftToRead > 0) return 0;
            int sr = start.remaining();
            if (sr > 0) {
                int n = readFromStart(dst);
                if (n < sr) return n;
            }
            return sr + channel.read(dst);
        }

        @Override
        public synchronized long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            throwPendingException();
            if (offset + length > dsts.length || length < 0 || offset < 0) {
                throw new IndexOutOfBoundsException();
            }
            readAhead();
            if (leftToRead > 0) return 0;
            int sr = start.remaining();
            int newOffset = offset;
            if (sr > 0) {
                int accum = 0;
                for (; newOffset < offset + length; newOffset++) {
                    accum += readFromStart(dsts[newOffset]);
                    if (accum == sr) break;
                }
                if (accum < sr) return accum;
            }
            return sr + channel.read(dsts, newOffset, length - newOffset + offset);
        }

        @Override
        public int hashCode() {
            return channel.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return channel.equals(obj);
        }

        @Override
        public String toString() {
            return channel.toString();
        }

        @Override
        public Socket socket() {
            return channel.socket();
        }

        @Override
        public boolean isConnected() {
            return channel.isConnected();
        }

        @Override
        public boolean isConnectionPending() {
            return channel.isConnectionPending();
        }

        @Override
        public boolean connect(SocketAddress remote) throws IOException {
            return channel.connect(remote);
        }

        @Override
        public boolean finishConnect() throws IOException {
            return channel.finishConnect();
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return channel.write(src);
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return channel.write(srcs, offset, length);
        }

        @Override
        protected void implCloseSelectableChannel() throws IOException {
            channel.close();
        }

        @Override
        protected void implConfigureBlocking(boolean block) throws IOException {
            channel.configureBlocking(block);
        }

        @Override
        public SocketAddress getLocalAddress() throws IOException {
            return channel.getLocalAddress();
        }

        @Override
        public <T> T getOption(java.net.SocketOption<T> name) throws IOException {
            return channel.getOption(name);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return channel.supportedOptions();
        }

        @Override
        public SocketChannel bind(SocketAddress local) throws IOException {
            return channel.bind(local);
        }

        @Override
        public SocketAddress getRemoteAddress() throws IOException {
            return channel.getRemoteAddress();
        }

        @Override
        public <T> SocketChannel setOption(java.net.SocketOption<T> name, T value) throws IOException {
            return channel.setOption(name, value);
        }

        @Override
        public SocketChannel shutdownInput() throws IOException {
            return channel.shutdownInput();
        }

        @Override
        public SocketChannel shutdownOutput() throws IOException {
            return channel.shutdownOutput();
        }
    }
}

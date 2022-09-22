/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.lang.ref.WeakReference;
import java.nio.channels.ByteChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.servlet.http.HttpServletRequest;

import net.handle.server.servletcontainer.PortUnificationSelectChannelConnector.ReadAheadSocketChannelWrapper;
import net.handle.server.servletcontainer.auth.StandardHandleAuthenticator;

import org.eclipse.jetty.io.nio.ChannelEndPoint;
import org.eclipse.jetty.io.nio.SslConnection;
import org.eclipse.jetty.io.nio.SslConnection.SslEndPoint;
import org.eclipse.jetty.server.Request;

public class TlsRenegotiationRequestor {
    private static final String PROCESSED_ATTRIBUTE_NAME = TlsRenegotiationRequestor.class.getName() + ".processed";

    private final SslConnection.SslEndPoint sslEndPoint;
    private final Request request;

    TlsRenegotiationRequestor(SslEndPoint sslEndPoint, Request request) {
        this.sslEndPoint = sslEndPoint;
        this.request = request;
    }

    public boolean isWantingTlsRenegotiation(Boolean wantClientAuth, boolean force) {
        if (!force && wantClientAuth == null) return false;
        if (request.getAttribute(PROCESSED_ATTRIBUTE_NAME) != null) return false;
        SSLEngine sslEngine = sslEndPoint.getSslEngine();
        if (!force) {
            if (wantClientAuth.booleanValue()) {
                if (sslEngine.getNeedClientAuth()) return false;
                if (sslEngine.getWantClientAuth()) {
                    if (StandardHandleAuthenticator.extractCertificate(request) != null) return false;
                }
            } else {
                if (!sslEngine.getNeedClientAuth() && !sslEngine.getWantClientAuth()) return false;
                if (sslEngine.getWantClientAuth()) {
                    if (StandardHandleAuthenticator.extractCertificate(request) == null) return false;
                }
            }
        }
        return true;
    }

    public boolean isNeedClientAuth() {
        SSLEngine sslEngine = sslEndPoint.getSslEngine();
        return sslEngine.getNeedClientAuth();
    }

    @SuppressWarnings("resource") // We don't need to close ChannelEndPoint.getChannel
    public void requestTlsRenegotiation(HttpServletRequest req, Boolean wantClientAuth) throws SSLException {
        request.setAttribute(PROCESSED_ATTRIBUTE_NAME, Boolean.TRUE);
        if (!(sslEndPoint.getEndpoint() instanceof ChannelEndPoint)) throw new AssertionError("unexpected object structure in requestTlsRenegotiation");
        ByteChannel channel = ((ChannelEndPoint) sslEndPoint.getEndpoint()).getChannel();
        if (!(channel instanceof ReadAheadSocketChannelWrapper)) throw new AssertionError("unexpected object structure in requestTlsRenegotiation");
        ((ReadAheadSocketChannelWrapper) channel).request = new WeakReference<>(request);
        SSLEngine sslEngine = sslEndPoint.getSslEngine();
        // We need to invalidate the old session in order to prevent session resumption
        sslEngine.getSession().invalidate();
        // don't downgrade from need-client-auth, which only occurs if explicitly configured that way
        if (wantClientAuth != null && !sslEngine.getNeedClientAuth()) {
            sslEngine.setWantClientAuth(wantClientAuth.booleanValue());
        }
        sslEngine.beginHandshake();
        //      sslEndPoint.flush();
        if (req == null) {
            request.startAsync();
        } else {
            request.startAsync(req, request.getServletResponse());
        }
    }
}

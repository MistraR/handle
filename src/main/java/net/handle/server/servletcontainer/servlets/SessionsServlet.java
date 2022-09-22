/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.jetty.server.SessionManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.cnri.util.StringUtils;
import net.handle.hdllib.GsonUtility;
import net.handle.server.servletcontainer.TlsRenegotiationRequestor;
import net.handle.server.servletcontainer.auth.AuthenticationInfoWithId;
import net.handle.server.servletcontainer.auth.AuthenticationResponse;
import net.handle.server.servletcontainer.auth.HandleAuthenticationStatus;
import net.handle.server.servletcontainer.auth.HandleAuthorizationHeader;
import net.handle.server.servletcontainer.auth.StandardHandleAuthenticator;

public class SessionsServlet extends BaseHandleRequestProcessingServlet {
    {
        allowString = "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS";
    }

    private SessionManager sessionManager;

    @Override
    public void init() throws ServletException {
        super.init();
        sessionManager = (SessionManager) getServletContext().getAttribute(SessionManager.class.getName());
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = req.getMethod();
        if ("TRACE".equals(method)) {
            super.service(req, resp);
            return;
        }
        if (!req.isSecure() && !"OPTIONS".equals(method)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String path = getPath(req);
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (path.isEmpty()) {
            if (!"POST".equals(method)) {
                if (!"OPTIONS".equals(method)) resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                resp.setHeader("Allow", "POST, TRACE, OPTIONS");
                return;
            }
        } else {
            if ("OPTIONS".equals(method)) {
                resp.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS");
                return;
            }
            if (!"this".equals(path)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        HttpServletRequest wrappedReq = (HttpServletRequest) req.getAttribute(SessionsServlet.class.getName() + ".wrappedReq");
        if (wrappedReq == null) {
            wrappedReq = req;
            if (BaseHandleRequestProcessingServlet.hasJsonEntity(req)) {
                try {
                    wrappedReq = new JsonParameterServletReq(req);
                } catch (JsonParseException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    return;
                } catch (IOException e) {
                    resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
            }
        }
        HandleAuthorizationHeader handleAuthHeader = HandleAuthorizationHeader.fromHeaderAndParameters(req.getHeader("Authorization"), wrappedReq);
        if (isAsyncTlsRenegotiate(handleAuthHeader, req, wrappedReq, resp)) return;
        req.setAttribute(HandleAuthorizationHeader.class.getName(), handleAuthHeader);
        if (!path.isEmpty() && "POST".equals(method)) {
            doPut(req, resp);
            return;
        }
        super.service(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HandleAuthorizationHeader handleAuthHeader = (HandleAuthorizationHeader) req.getAttribute(HandleAuthorizationHeader.class.getName());
        HttpSession session = req.getSession(false);
        if (handleAuthHeader != null && handleAuthHeader.getSessionId() != null && session == null && sessionManager != null) {
            session = sessionManager.getHttpSession(handleAuthHeader.getSessionId());
        }
        if (session == null && handleAuthHeader != null && handleAuthHeader.requiresSession()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        AuthenticationResponse authResp = getAuthenticationResponse(handleAuthHeader, req, session);
        if (authResp.isAuthenticating() && !authResp.isAuthenticated()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        GsonUtility.getGson().toJson(getSessionRepresentation(authResp), resp.getWriter());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HandleAuthorizationHeader handleAuthHeader = (HandleAuthorizationHeader) req.getAttribute(HandleAuthorizationHeader.class.getName());
        if (handleAuthHeader != null && handleAuthHeader.getSessionId() != null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        HttpSession session = req.getSession();
        AuthenticationResponse authResp = getAuthenticationResponse(handleAuthHeader, req, session);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        String newPath = req.getContextPath() + req.getServletPath();
        if (!newPath.endsWith("/")) newPath += "/";
        newPath += "this";
        resp.setHeader("Location", StringUtils.encodeURLPath(newPath));
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        GsonUtility.getGson().toJson(getSessionRepresentation(authResp), resp.getWriter());
    }

    private boolean isAsyncTlsRenegotiate(HandleAuthorizationHeader handleAuthHeader, HttpServletRequest req, HttpServletRequest wrappedReq, HttpServletResponse resp) throws IOException {
        if (handleAuthHeader == null) return false;
        if (!req.isSecure()) return false;
        Boolean wantClientAuth = handleAuthHeader.getClientCertAsBooleanObject();
        if (wantClientAuth == null && !handleAuthHeader.isRequestingForceRenegotiate()) return false;
        final TlsRenegotiationRequestor tlsRenegotiationRequestor = (TlsRenegotiationRequestor) req.getAttribute(TlsRenegotiationRequestor.class.getName());
        boolean isWanting = tlsRenegotiationRequestor.isWantingTlsRenegotiation(wantClientAuth, handleAuthHeader.isRequestingForceRenegotiate());
        if (!isWanting) return false;
        if (wantClientAuth != null && !wantClientAuth.booleanValue() && tlsRenegotiationRequestor.isNeedClientAuth()) {
            // can't downgrade to no client auth
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return true;
        } else {
            req.setAttribute(SessionsServlet.class.getName() + ".wrappedReq", wrappedReq);
            tlsRenegotiationRequestor.requestTlsRenegotiation(null, wantClientAuth);
            return true;
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HandleAuthorizationHeader handleAuthHeader = (HandleAuthorizationHeader) req.getAttribute(HandleAuthorizationHeader.class.getName());
        HttpSession session = req.getSession(false);
        if (handleAuthHeader != null && handleAuthHeader.getSessionId() != null && session == null && sessionManager != null) {
            session = sessionManager.getHttpSession(handleAuthHeader.getSessionId());
        }
        if (session == null && handleAuthHeader != null && handleAuthHeader.requiresSession()) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        AuthenticationResponse authResp = getAuthenticationResponse(handleAuthHeader, req, session);
        if (authResp.isAuthenticating() && !authResp.isAuthenticated()) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            resp.setStatus(HttpServletResponse.SC_OK);
        }
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        GsonUtility.getGson().toJson(getSessionRepresentation(authResp), resp.getWriter());
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HandleAuthorizationHeader handleAuthHeader = (HandleAuthorizationHeader) req.getAttribute(HandleAuthorizationHeader.class.getName());
        HttpSession session = req.getSession(false);
        if (handleAuthHeader != null && handleAuthHeader.getSessionId() != null && session == null && sessionManager != null) {
            session = sessionManager.getHttpSession(handleAuthHeader.getSessionId());
        }
        if (session == null) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        session.invalidate();
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private AuthenticationResponse getAuthenticationResponse(HandleAuthorizationHeader handleAuthHeader, HttpServletRequest req, HttpSession session) {
        AuthenticationResponse authResp = new AuthenticationResponse();
        req.setAttribute(AuthenticationResponse.class.getName(), authResp);
        HandleAuthenticationStatus status = null;
        if (session != null) {
            status = HandleAuthenticationStatus.fromSession(session, true);
            authResp.setSessionId(status.getSessionId());
            authResp.setNonce(status.getNonce());
            AuthenticationInfoWithId authInfo = status.getAuthInfoWithId();
            if (authInfo != null) {
                authResp.setId(authInfo.getId());
                authResp.setAuthenticated(true);
            }
            status = HandleAuthenticationStatus.processServerSignature(status, handleServer, session, handleAuthHeader, authResp);
        }
        new StandardHandleAuthenticator(req, session, status, authResp).authenticate();
        return authResp;
    }

    private JsonObject getSessionRepresentation(AuthenticationResponse authResp) {
        JsonObject res = new JsonObject();
        String sessionId = authResp.getSessionId();
        if (sessionId != null) {
            res.addProperty("sessionId", sessionId);
        }
        byte[] nonce = authResp.getNonce();
        if (nonce != null) {
            res.addProperty("nonce", Base64.encodeBase64String(nonce));
        }
        byte[] serverSignature = authResp.getServerSignature();
        if (serverSignature != null) {
            res.addProperty("serverAlg", authResp.getServerAlg());
            res.addProperty("serverSignature", Base64.encodeBase64String(serverSignature));
        }
        if (authResp.isAuthenticated()) {
            res.addProperty("authenticated", Boolean.TRUE);
            res.addProperty("id", authResp.getId());
        }
        if (!authResp.getErrors().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String error : authResp.getErrors()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(error);
            }
            res.addProperty("error", sb.toString());
        }
        if (res.entrySet().isEmpty()) {
            res.addProperty("authenticated", Boolean.FALSE);
        }
        return res;
    }
}

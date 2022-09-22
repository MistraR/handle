/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.cnri.util.ServletUtil;
import net.cnri.util.StringUtils;
import net.handle.apps.servlet_proxy.HDLProxy;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.ErrorResponse;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.RequestProcessor;
import net.handle.hdllib.Util;
import net.handle.server.servletcontainer.support.PreAuthenticatedRequestProcessor;

public class BaseHandleRequestProcessingServlet extends HttpServlet {
    net.handle.server.servletcontainer.HandleServerInterface handleServer;
    RequestProcessor requestHandler;
    boolean caseSensitive;
    protected String allowString = "TRACE, OPTIONS";

    @Override
    public void init() throws ServletException {
        handleServer = (net.handle.server.servletcontainer.HandleServerInterface) getServletContext().getAttribute("net.handle.server.HandleServer");
        if (handleServer == null) {
            requestHandler = (HandleResolver) getServletContext().getAttribute(HandleResolver.class.getName());
            if (requestHandler == null) requestHandler = new HandleResolver();
            caseSensitive = true;
        } else {
            requestHandler = new PreAuthenticatedRequestProcessor(handleServer, null);
            caseSensitive = handleServer.isCaseSensitive();
        }
    }

    protected static String getPath(HttpServletRequest servletReq) {
        String pathInfo = ServletUtil.pathExcluding(servletReq.getRequestURI(), servletReq.getContextPath() + servletReq.getServletPath());
        pathInfo = StringUtils.decodeURLIgnorePlus(pathInfo);
        if (pathInfo.startsWith("/")) pathInfo = pathInfo.substring(1);
        return pathInfo;
    }

    protected static int[] getIndexes(HttpServletRequest servletReq) throws NumberFormatException {
        String[] indexStrings = servletReq.getParameterValues("index");
        int[] indexes = null;
        if (indexStrings != null && indexStrings.length > 0) {
            indexes = new int[indexStrings.length];
            for (int i = 0; i < indexStrings.length; i++) {
                try {
                    indexes[i] = Integer.parseInt(indexStrings[i]);
                } catch (NumberFormatException e) {
                    throw e;
                }
            }
        }
        return indexes;
    }

    protected static AuthenticationInfo getAuthenticationInfo(HttpServletRequest servletReq) {
        return (AuthenticationInfo) servletReq.getAttribute(AuthenticationInfo.class.getName());
    }

    protected static InetAddress getRemoteInetAddress(HttpServletRequest servletReq) {
        try {
            HDLProxy hdlProxy = (HDLProxy) servletReq.getServletContext().getAttribute(HDLProxy.class.getName());
            if (hdlProxy == null) return InetAddress.getByName(servletReq.getRemoteAddr());
            return hdlProxy.getRemoteInetAddress(servletReq);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static Gson getGsonForRequest(HttpServletRequest servletReq) {
        Gson gson;
        if (getBooleanParameter(servletReq, "pretty")) {
            gson = GsonUtility.getNewGsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        } else {
            gson = GsonUtility.getGson();
        }
        return gson;
    }

    protected static boolean getBooleanParameter(HttpServletRequest servletReq, String param) {
        String value = servletReq.getParameter(param);
        if (value == null) return false;
        if (value.isEmpty()) return true;
        if ("yes".equalsIgnoreCase(value)) return true;
        if ("true".equalsIgnoreCase(value)) return true;
        return false;
    }

    protected AbstractResponse processRequest(HttpServletRequest servletReq, AbstractRequest handleReq) {
        handleReq.certify = getBooleanParameter(servletReq, "cert");
        handleReq.doNotRefer = getBooleanParameter(servletReq, "doNotRefer");
        try {
            return requestHandler.processRequest(handleReq, getRemoteInetAddress(servletReq));
        } catch (HandleException e) {
            return HandleException.toErrorResponse(handleReq, e);
        }
    }

    protected static AbstractResponse errorResponseFromException(Exception e) {
        AbstractResponse resp;
        //StringWriter writer = new StringWriter();
        //e.printStackTrace(new PrintWriter(writer));
        //String message = writer.toString();
        String message = e.toString();
        resp = new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(message));
        return resp;
    }

    protected void processResponse(HttpServletRequest servletReq, HttpServletResponse servletResp, AbstractRequest handleReq, AbstractResponse handleResp) throws IOException {
        processResponse(servletReq, servletResp, statusCodeFromResponse(handleResp), GsonUtility.serializeResponseToRequest(handleReq, handleResp));
        logAccess(servletReq, handleReq, handleResp);
    }

    private void logAccess(HttpServletRequest servletReq, AbstractRequest currentHdlRequest, AbstractResponse response) {
        if (handleServer != null) {
            if (!handleServer.logHttpAccesses()) return;
            long recvTime = ((Long) servletReq.getAttribute("recvTime")).longValue();
            long respTime = System.currentTimeMillis() - recvTime;
            handleServer.logAccess("HTTP:HDLApi", getRemoteInetAddress(servletReq), response.opCode, response.responseCode, Util.getAccessLogString(currentHdlRequest, response), respTime);
        } else {
            HDLProxy hdlProxy = (HDLProxy) getServletContext().getAttribute(HDLProxy.class.getName());
            if (hdlProxy == null) return;
            long responseTime = 0;
            try {
                long recvTime = ((Long) servletReq.getAttribute("recvTime")).longValue();
                responseTime = System.currentTimeMillis() - recvTime;
            } catch (Exception e) {
            }
            String referer = servletReq.getHeader("Referer");
            if (referer == null) referer = "";
            String userAgent = servletReq.getHeader("user-agent");
            byte[] handle = currentHdlRequest == null ? Common.BLANK_HANDLE : currentHdlRequest.handle;
            hdlProxy.logAccess("HTTP:HDLApi", response.opCode, response.responseCode, Util.decodeString(handle), hdlProxy.getRemoteAddr(servletReq), referer, userAgent, responseTime, null, null);
        }
    }

    static String scrubCallbackParameter(String callback) {
        StringBuilder sb = null;
        for (int i = 0; i < callback.length(); i++) {
            char ch = callback.charAt(i);
            if (ch == '_' || ch == '$' || (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (i > 0 && ch >= '0' && ch <= '9') || ch >= 0xA0) continue;
            if (sb == null) sb = new StringBuilder(callback);
            sb.setCharAt(i, '_');
        }
        if (sb == null) return callback;
        else return sb.toString();
    }

    public static void processResponse(HttpServletRequest servletReq, HttpServletResponse servletResp, int statusCode, JsonElement obj) throws IOException {
        String callback = servletReq.getParameter("callback");
        if (callback != null) callback = scrubCallbackParameter(callback);
        servletResp.setStatus(statusCode);
        if (callback == null) servletResp.setContentType("application/json");
        else servletResp.setContentType("application/javascript");
        servletResp.setCharacterEncoding("UTF-8");
        if (callback != null) servletResp.getWriter().append(callback).append("(");
        Gson gson = getGsonForRequest(servletReq);
        gson.toJson(obj, servletResp.getWriter());
        if (callback != null) servletResp.getWriter().append(");");
    }

    public static int statusCodeFromResponse(AbstractResponse resp) {
        switch (resp.responseCode) {
        case AbstractMessage.RC_SUCCESS:
            if (!resp.overwriteWhenExists && (resp.opCode == AbstractMessage.OC_CREATE_HANDLE || resp.opCode == AbstractMessage.OC_ADD_VALUE)) return HttpServletResponse.SC_CREATED;
            return HttpServletResponse.SC_OK;
        case AbstractMessage.RC_VALUES_NOT_FOUND:
            if (resp.opCode == AbstractMessage.OC_RESOLUTION) return HttpServletResponse.SC_OK;
            else return HttpServletResponse.SC_BAD_REQUEST;
        case AbstractMessage.RC_PREFIX_REFERRAL:
        case AbstractMessage.RC_SERVICE_REFERRAL:
            return HttpServletResponse.SC_MULTIPLE_CHOICES;
        case AbstractMessage.RC_HANDLE_NOT_FOUND:
            return HttpServletResponse.SC_NOT_FOUND;
        case AbstractMessage.RC_HANDLE_ALREADY_EXISTS:
        case AbstractMessage.RC_VALUE_ALREADY_EXISTS:
            return HttpServletResponse.SC_CONFLICT;
        case AbstractMessage.RC_PROTOCOL_ERROR:
        case AbstractMessage.RC_INVALID_HANDLE:
        case AbstractMessage.RC_INVALID_VALUE:
            return HttpServletResponse.SC_BAD_REQUEST;
        case AbstractMessage.RC_SERVER_NOT_RESP:
            return HttpServletResponse.SC_BAD_REQUEST; // TODO: consider other status codes for server not responsible
        case AbstractMessage.RC_OPERATION_NOT_SUPPORTED:
            return HttpServletResponse.SC_NOT_IMPLEMENTED;
        case AbstractMessage.RC_SERVER_TOO_BUSY:
        case AbstractMessage.RC_SERVER_BACKUP:
            return HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        case AbstractMessage.RC_AUTHENTICATION_NEEDED:
            return HttpServletResponse.SC_UNAUTHORIZED;
        case AbstractMessage.RC_INVALID_ADMIN:
        case AbstractMessage.RC_INSUFFICIENT_PERMISSIONS:
        case AbstractMessage.RC_AUTHENTICATION_FAILED:
        case AbstractMessage.RC_INVALID_CREDENTIAL:
            return HttpServletResponse.SC_FORBIDDEN;
        default:
            return HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("recvTime", Long.valueOf(System.currentTimeMillis()));
        String method = req.getMethod();
        if ("GET".equals(method)) doGet(req, resp);
        else if ("HEAD".equals(method)) doHead(req, resp);
        else if ("POST".equals(method)) doPost(req, resp);
        else if ("PUT".equals(method)) doPut(req, resp);
        else if ("DELETE".equals(method)) doDelete(req, resp);
        else if ("OPTIONS".equals(method)) doOptions(req, resp);
        else if ("TRACE".equals(method)) doTrace(req, resp);
        else {
            JsonObject json = new JsonObject();
            json.addProperty("responseCode", Integer.valueOf(AbstractMessage.RC_PROTOCOL_ERROR));
            json.addProperty("message", "Unknown method " + method);
            BaseHandleRequestProcessingServlet.processResponse(req, resp, HttpServletResponse.SC_NOT_IMPLEMENTED, json);
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Allow", allowString);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        methodNotAllowed(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        methodNotAllowed(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        methodNotAllowed(req, resp);
    }

    private void methodNotAllowed(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("responseCode", Integer.valueOf(AbstractMessage.RC_PROTOCOL_ERROR));
        json.addProperty("message", "Unknown HTTP API endpoint " + req.getMethod() + " " + req.getRequestURI());
        BaseHandleRequestProcessingServlet.processResponse(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED, json);
    }

    // POST defaults to GET-with-parameters-in-body
    @Override
    protected void doPost(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        if (hasJsonEntity(servletReq)) {
            try {
                doGet(new JsonParameterServletReq(servletReq), servletResp);
            } catch (JsonParseException e) {
                processResponse(servletReq, servletResp, null, errorResponseFromException(e));
            }
        } else {
            doGet(servletReq, servletResp);
        }
    }

    protected static boolean hasJsonEntity(HttpServletRequest servletReq) {
        String contentType = servletReq.getContentType();
        if (contentType == null) return false;
        contentType = contentType.trim().toLowerCase();
        if (contentType.equals("application/json")) return true;
        if (contentType.matches("^application/json\\s*;.*")) return true;
        return false;
    }

    static int getIntegerParameter(HttpServletRequest servletReq, String paramName, int defaultValue) throws NumberFormatException {
        String paramValue = servletReq.getParameter(paramName);
        if (paramValue == null) return defaultValue;
        return Integer.parseInt(paramValue);
    }
}

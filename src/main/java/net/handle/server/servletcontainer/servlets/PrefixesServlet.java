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

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.Common;
import net.handle.hdllib.GenericRequest;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.ListNAsRequest;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.hdllib.Util;
import net.handle.server.servletcontainer.support.LoggingResponseMessageCallbackWrapper;

import com.google.gson.JsonParseException;

public class PrefixesServlet extends BaseHandleRequestProcessingServlet {
    {
        allowString = "GET, HEAD, POST, DELETE, TRACE, OPTIONS";
    }

    @Override
    protected void doGet(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        String prefix = servletReq.getParameter("prefix");
        if (prefix == null) {
            prefix = getPath(servletReq);
        }
        if (prefix.isEmpty()) {
            listPrefixes(servletReq, servletResp);
        } else {
            servletResp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            servletResp.setHeader("Allow", "POST, DELETE, TRACE, OPTIONS");
            return;
        }
    }

    @Override
    protected void doOptions(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        String prefix = servletReq.getParameter("prefix");
        if (prefix == null) {
            prefix = getPath(servletReq);
        }
        if (prefix.isEmpty()) {
            servletResp.setHeader("Allow", "GET, HEAD, POST, DELETE, TRACE, OPTIONS");
        } else {
            servletResp.setHeader("Allow", "POST, DELETE, TRACE, OPTIONS");
        }
    }

    @Override
    protected void doPost(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        if (hasJsonEntity(servletReq)) {
            try {
                servletReq = new JsonParameterServletReq(servletReq);
            } catch (JsonParseException e) {
                processResponse(servletReq, servletResp, null, errorResponseFromException(e));
                return;
            }
        }
        String prefix = servletReq.getParameter("prefix");
        if (prefix == null) {
            prefix = getPath(servletReq);
        }
        AbstractRequest req;
        AbstractResponse resp;
        if (prefix.isEmpty()) {
            req = null;
            resp = errorResponseFromException(new Exception("Prefix not specified in homing request"));
        } else {
            req = new GenericRequest(Util.encodeString(prefix), AbstractMessage.OC_HOME_NA, getAuthenticationInfo(servletReq));
            resp = processRequest(servletReq, req);
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        if (hasJsonEntity(servletReq)) {
            try {
                servletReq = new JsonParameterServletReq(servletReq);
            } catch (JsonParseException e) {
                processResponse(servletReq, servletResp, null, errorResponseFromException(e));
                return;
            }
        }
        String prefix = servletReq.getParameter("prefix");
        if (prefix == null) {
            prefix = getPath(servletReq);
        }
        AbstractRequest req;
        AbstractResponse resp;
        if (prefix.isEmpty()) {
            req = null;
            resp = errorResponseFromException(new Exception("Prefix not specified in unhoming request"));
        } else {
            req = new GenericRequest(Util.encodeString(prefix), AbstractMessage.OC_UNHOME_NA, getAuthenticationInfo(servletReq));
            resp = processRequest(servletReq, req);
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    private void listPrefixes(HttpServletRequest servletReq, HttpServletResponse servletResp) throws IOException {
        try {
            AbstractRequest listReq = new ListNAsRequest(Common.BLANK_HANDLE, getAuthenticationInfo(servletReq));
            ListCallback listCallback = new ListCallback();
            listCallback.page = BaseHandleRequestProcessingServlet.getIntegerParameter(servletReq, "page", -1);
            listCallback.pageSize = BaseHandleRequestProcessingServlet.getIntegerParameter(servletReq, "pageSize", -1);
            listReq.certify = getBooleanParameter(servletReq, "cert");
            ResponseMessageCallback callbackWrapper = listCallback;
            if (handleServer != null) callbackWrapper = new LoggingResponseMessageCallbackWrapper(listCallback, handleServer, handleServer.logHttpAccesses(), listReq, getRemoteInetAddress(servletReq), "HDLApi");
            try {
                requestHandler.processRequest(listReq, getRemoteInetAddress(servletReq), callbackWrapper);
            } catch (HandleException e) {
                listCallback.unexpectedResponse = HandleException.toErrorResponse(listReq, e);
            }
            if (listCallback.unexpectedResponse != null) {
                processResponse(servletReq, servletResp, listReq, listCallback.unexpectedResponse);
            } else {
                listCallback.processListHandlesResponse(servletReq, servletResp, listReq, listCallback.handles, listCallback.totalCount);
            }
        } catch (IllegalArgumentException e) {
            processResponse(servletReq, servletResp, null, errorResponseFromException(e));
        }
    }
}

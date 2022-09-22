/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.util.StringUtils;
import net.handle.hdllib.AbstractMessage;
import net.handle.server.servletcontainer.servlets.BaseHandleRequestProcessingServlet;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.ErrorHandler;

import com.google.gson.JsonObject;

public class HandleApiErrorHandler extends ErrorHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestUri = StringUtils.decodeURLIgnorePlus(request.getRequestURI());
        if (requestUri.equals("/api") || requestUri.startsWith("/api/")) {
            int httpStatus;
            try {
                httpStatus = ((Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).intValue();
            } catch (Exception e) {
                httpStatus = 500;
            }
            String message = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
            if (message == null) {
                message = "Unexpected server error";
            }
            int handleStatus = handleStatusOfHttpStatus(httpStatus);
            JsonObject json = new JsonObject();
            json.addProperty("responseCode", handleStatus);
            json.addProperty("message", message);
            BaseHandleRequestProcessingServlet.processResponse(request, response, httpStatus, json);
        } else {
            super.handle(target, baseRequest, request, response);
        }
    }

    private int handleStatusOfHttpStatus(int httpStatus) {
        switch (httpStatus) {
        case HttpServletResponse.SC_BAD_REQUEST:
            return AbstractMessage.RC_PROTOCOL_ERROR;
        case HttpServletResponse.SC_UNAUTHORIZED:
            return AbstractMessage.RC_AUTHENTICATION_NEEDED;
        case HttpServletResponse.SC_FORBIDDEN:
            return AbstractMessage.RC_AUTHENTICATION_FAILED;
        case HttpServletResponse.SC_NOT_FOUND:
            return AbstractMessage.RC_PROTOCOL_ERROR;
        case HttpServletResponse.SC_CONFLICT:
            return AbstractMessage.RC_PROTOCOL_ERROR;
        case HttpServletResponse.SC_GONE:
            return AbstractMessage.RC_PROTOCOL_ERROR;
        case HttpServletResponse.SC_INTERNAL_SERVER_ERROR:
            return AbstractMessage.RC_ERROR;
        case HttpServletResponse.SC_SERVICE_UNAVAILABLE:
            return AbstractMessage.RC_SERVER_TOO_BUSY;
        case HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED:
            return AbstractMessage.RC_PROTOCOL_ERROR;
        default:
            if (httpStatus >= 500) {
                return AbstractMessage.RC_ERROR;
            } else {
                return AbstractMessage.RC_PROTOCOL_ERROR;
            }
        }
    }
}

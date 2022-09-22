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

import com.google.gson.JsonObject;

public class UnknownApiServlet extends BaseHandleRequestProcessingServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        noSuchEndpoint(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        noSuchEndpoint(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        noSuchEndpoint(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        noSuchEndpoint(req, resp);
    }

    private void noSuchEndpoint(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("responseCode", Integer.valueOf(AbstractMessage.RC_PROTOCOL_ERROR));
        json.addProperty("message", "Unknown HTTP API endpoint " + req.getMethod() + " " + req.getRequestURI());
        BaseHandleRequestProcessingServlet.processResponse(req, resp, HttpServletResponse.SC_NOT_FOUND, json);
    }

}

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

public class SiteServlet extends BaseHandleRequestProcessingServlet {
    {
        allowString = "GET, HEAD, TRACE, OPTIONS";
    }

    @Override
    protected void doGet(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        AbstractRequest req;
        AbstractResponse resp;
        try {
            req = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, getAuthenticationInfo(servletReq));
            resp = processRequest(servletReq, req);
        } catch (Exception e) {
            req = null;
            resp = errorResponseFromException(e);
        }
        processResponse(servletReq, servletResp, req, resp);
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import javax.servlet.http.HttpServletRequest;

import net.handle.server.servletcontainer.auth.HandleAuthorizationHeader;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.session.SessionHandler;

public class HandleAuthorizationEnabledSessionHandler extends SessionHandler {
    @Override
    protected void checkRequestedSessionId(Request baseRequest, HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null) {
            HandleAuthorizationHeader parsedHeader = HandleAuthorizationHeader.fromHeader(header);
            if (parsedHeader != null && (parsedHeader.getVersion() == null || "0".equals(parsedHeader.getVersion())) && parsedHeader.getSessionId() != null) {
                baseRequest.setRequestedSessionId(parsedHeader.getSessionId());
                baseRequest.setRequestedSessionIdFromCookie(true);
            }
        }
        super.checkRequestedSessionId(baseRequest, request);
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;

import net.cnri.util.FastDateFormat;
import net.handle.hdllib.AbstractMessage;
import net.handle.server.servletcontainer.servlets.BaseHandleRequestProcessingServlet;

public class UncaughtExceptionsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doHttpFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void doHttpFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException {
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            String requestUri = request.getRequestURI();
            String queryString = request.getQueryString();
            if (queryString != null) requestUri += "?" + queryString;
            System.err.println(FastDateFormat.getLocalFormat().formatNow() + " Unexpected error processing API request " + request.getMethod() + " " + requestUri);
            e.printStackTrace();
            JsonObject json = new JsonObject();
            json.addProperty("responseCode", Integer.valueOf(AbstractMessage.RC_ERROR));
            json.addProperty("message", "Unexpected server error");
            BaseHandleRequestProcessingServlet.processResponse(request, response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, json);
        }
    }

    @Override
    public void destroy() {
    }

}

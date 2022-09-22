/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

public class DefaultServlet extends HttpServlet {
    private final HttpServlet delegate;

    public DefaultServlet() {
        try {
            Class<?> delegateClass;
            try {
                delegateClass = Class.forName("org.eclipse.jetty.servlet.DefaultServlet");
            } catch (ClassNotFoundException e) {
                delegateClass = Class.forName("org.apache.catalina.servlets.DefaultServlet");
            }
            delegate = (HttpServlet) delegateClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public String getInitParameter(String name) {
        return delegate.getInitParameter(name);
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
        return delegate.getInitParameterNames();
    }

    @Override
    public ServletConfig getServletConfig() {
        return delegate.getServletConfig();
    }

    @Override
    public ServletContext getServletContext() {
        return delegate.getServletContext();
    }

    @Override
    public String getServletInfo() {
        return delegate.getServletInfo();
    }

    @Override
    public String getServletName() {
        return delegate.getServletName();
    }

    @Override
    public void init() throws ServletException {
        delegate.init();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        delegate.init(config);
    }

    @Override
    public void log(String message, Throwable t) {
        delegate.log(message, t);
    }

    @Override
    public void log(String msg) {
        delegate.log(msg);
    }

    @Override
    public void service(ServletRequest req, ServletResponse resp) throws ServletException, IOException {
        if (req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            String pathInfo = ((HttpServletRequest) req).getPathInfo();
            if (pathInfo == null || pathInfo.startsWith("/WEB-INF/") || pathInfo.startsWith("/META-INF/")) {
                ((HttpServletResponse) resp).setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            req = new HttpServletRequestWrapper((HttpServletRequest) req) {
                @Override
                public String getServletPath() {
                    return "/";
                }
            };
        }
        delegate.service(req, resp);
    }
}

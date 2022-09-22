/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class CorsAccessControlExposeHeadersFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op
    }

    @Override
    public void destroy() {
        // no-op
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if(request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            doHttpFilter((HttpServletRequest)request, (HttpServletResponse)response, chain);
        } else {
            chain.doFilter(request, response);
        }
    }

    private void doHttpFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request.getHeader("Origin") == null) {
            chain.doFilter(request, response);
        } else {
            HeaderFixingResponseWrapper wrappedResponse = new HeaderFixingResponseWrapper(response);
            chain.doFilter(request, wrappedResponse);
        }
    }

    private static class HeaderFixingResponseWrapper extends HttpServletResponseWrapper {
        private static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

        private final Set<String> exposedHeaders;

        public HeaderFixingResponseWrapper(HttpServletResponse response) {
            super(response);
            this.exposedHeaders = new HashSet<>();
            this.exposedHeaders.add("Content-Length");
            fixResponseExposeHeaders();
        }

        private void fixResponseExposeHeaders() {
            Collection<String> headers = getHeaders(ACCESS_CONTROL_EXPOSE_HEADERS);
            if (headers != null) {
                for (String header : headers) {
                    exposeAllHeaders(header);
                }
            }
            setExposeHeadersHeader();
        }

        private void setExposeHeadersHeader() {
            if (!exposedHeaders.isEmpty()) super.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS, commify(exposedHeaders));
        }

        private void exposeAllHeaders(String header) {
            if (header != null && !header.isEmpty()) {
                for (String field : header.split(",")) {
                    exposedHeaders.add(field);
                }
            }
        }

        @Override
        public void setDateHeader(String name, long date) {
            exposeHeader(name);
            super.setDateHeader(name, date);
        }

        @Override
        public void addDateHeader(String name, long date) {
            exposeHeader(name);
            super.addDateHeader(name, date);
        }

        @Override
        public void setHeader(String name, String value) {
            if (name.equalsIgnoreCase("access-control-expose-headers")) {
                exposeAllHeaders(value);
                setExposeHeadersHeader();
            } else {
                exposeHeader(name);
                super.setHeader(name, value);
            }
        }

        @Override
        public void addHeader(String name, String value) {
            if (name.equalsIgnoreCase("access-control-expose-headers")) {
                exposeAllHeaders(value);
                setExposeHeadersHeader();
            } else {
                exposeHeader(name);
                super.addHeader(name, value);
            }
        }

        @Override
        public void setIntHeader(String name, int value) {
            exposeHeader(name);
            super.setIntHeader(name, value);
        }

        @Override
        public void addIntHeader(String name, int value) {
            exposeHeader(name);
            super.addIntHeader(name, value);
        }

        private boolean isSimpleHeader(String name) {
            return name.equalsIgnoreCase("Cache-Control")
                || name.equalsIgnoreCase("Content-Language")
                || name.equalsIgnoreCase("Content-Type")
                || name.equalsIgnoreCase("Expires")
                || name.equalsIgnoreCase("Last-Modified")
                || name.equalsIgnoreCase("Pragma");
        }

        private void exposeHeader(String name) {
            if (exposedHeaders != null && !name.toLowerCase().startsWith("access-control-") && !isSimpleHeader(name)) {
                exposedHeaders.add(name);
                super.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS, commify(exposedHeaders));
            }
        }

        private String commify(Collection<String> ss) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for(String s : ss) {
                if(!first) sb.append(",");
                first = false;
                sb.append(s);
            }
            return sb.toString();
        }
    }
}

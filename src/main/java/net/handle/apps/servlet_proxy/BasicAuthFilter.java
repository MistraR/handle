/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.handle.hdllib.Util;

import org.apache.commons.codec.binary.Base64;

public class BasicAuthFilter implements Filter {

    private static String configUsername = null;
    private static String configPassword = null;

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        if (!request.isSecure()) {
            request.getServletContext().log("Request sent to HTTP, must be HTTPS");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (isAuthorized(request)) {
            chain.doFilter(request, response);
        } else {
            request.getServletContext().log("Unauthorized request");
            response.setHeader("WWW-Authenticate", "Basic realm=\"admin\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        configUsername = config.getInitParameter("username");
        configPassword = config.getInitParameter("password");
    }

    private boolean isAuthorized(ServletRequest request) {
        String authHeader = ((HttpServletRequest) request).getHeader("Authorization");
        if (authHeader == null) {
            return false;
        } else {
            Credentials c = new Credentials(authHeader);
            return checkPassword(c.getUsername(), c.getPassword());
        }
    }

    public static boolean checkPassword(String userID, String password) {
        return configPassword.equals(password) && configUsername.equals(userID);
    }

    protected class Credentials {
        private final String username;
        private final String password;

        public Credentials(String authHeader) {
            String encodedUsernameAndPassWord = getEncodedUserNameAndPassword(authHeader);
            String decodedAuthHeader = Util.decodeString(Base64.decodeBase64(encodedUsernameAndPassWord));
            username = decodedAuthHeader.substring(0, decodedAuthHeader.indexOf(":"));
            password = decodedAuthHeader.substring(decodedAuthHeader.indexOf(":") + 1);
        }

        private String getEncodedUserNameAndPassword(String authHeader) {
            return authHeader.substring(authHeader.indexOf(" ") + 1);
        }

        public String getPassword() {
            return password;
        }

        public String getUsername() {
            return username;
        }
    }
}

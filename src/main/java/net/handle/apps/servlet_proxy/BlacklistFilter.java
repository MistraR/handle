/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class BlacklistFilter implements Filter {

    String email;
    ScheduledExecutorService execServ;
    File blacklistFile;
    long lastLastModified;
    volatile Set<String> blacklist;

    @Override
    public void init(FilterConfig config) throws ServletException {
        ServletContext context = config.getServletContext();
        String blacklistPathString = context.getRealPath("/WEB-INF/blacklist.txt");
        if (blacklistPathString == null) {
            System.err.println("Unable to initialize blacklist refresh thread.");
            return;
        }
        blacklistFile = new File(blacklistPathString);
        email = config.getInitParameter("email");
        refreshBlacklist();
        execServ = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BlacklistFilter");
            t.setDaemon(true);
            return t;
        });
        execServ.scheduleAtFixedRate(() -> refreshBlacklist(), 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void destroy() {
        if (execServ != null) execServ.shutdown();
    }

    private void refreshBlacklist() {
        long lastModified = blacklistFile.lastModified();
        if (lastModified == 0 && lastLastModified > 0) {
            blacklist = null;
            lastLastModified = 0;
            return;
        }
        if (lastModified > lastLastModified) {
            Set<String> newBlacklist = new HashSet<>();
            FileInputStream fis = null;
            InputStreamReader isr = null;
            BufferedReader br = null;
            try {
                fis = new FileInputStream(blacklistFile);
                isr = new InputStreamReader(fis, "UTF-8");
                br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) newBlacklist.add(line);
                }
            } catch (IOException e) {
                System.err.println("Error refreshing blacklist");
                e.printStackTrace();
            } finally {
                if (br != null) try { br.close(); } catch (Exception e) { }
                if (isr != null) try { isr.close(); } catch (Exception e) { }
                if (fis != null) try { fis.close(); } catch (Exception e) { }
            }
            blacklist = newBlacklist;
            lastLastModified = lastModified;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        if (isBlacklisted(request)) {
            response.setStatus(429);//, "Too Many Requests");
            response.setContentType("text/plain");
            response.setCharacterEncoding("UTF-8");
            @SuppressWarnings("resource")
            PrintWriter writer = response.getWriter();
            writer.println("Your request has been declined due to a large number of requests.");
            writer.print("Please contact ");
            writer.print(email);
            writer.println(" for assistance.");
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isBlacklisted(ServletRequest request) {
        if (blacklist == null) return false;
        String ip = request.getRemoteAddr();
        if (ip == null || ip.isEmpty()) return false;
        return blacklist.contains(ip);
    }

}

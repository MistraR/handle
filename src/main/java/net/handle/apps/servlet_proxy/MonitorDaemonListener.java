/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import net.handle.server.MonitorDaemon;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import java.io.File;
import java.util.Properties;

public class MonitorDaemonListener implements ServletContextListener {
    private MonitorDaemon monitorDaemon;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Properties config;
        try {
            config = HDLProxy.loadHdlProxyProperties(sce.getServletContext(), null, false);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        String path = config.getProperty("access_log");
        File dir;
        if (path == null) {
            path = context.getRealPath("");
            dir = new File(path).getParentFile().getParentFile();
        } else {
            dir = new File(path).getParentFile();
        }
        monitorDaemon = new MonitorDaemon(60, System.currentTimeMillis(), null, null, null, null, dir);
        monitorDaemon.start();
        context.setAttribute(MonitorDaemon.class.getName(), monitorDaemon);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        monitorDaemon.shutdown();
    }
}

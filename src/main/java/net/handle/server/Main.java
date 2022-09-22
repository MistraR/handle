/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
 All rights reserved.

 The HANDLE.NET software is made available subject to the
 Handle.Net Public License Agreement, which may be obtained at
 http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
 \**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamTable;
import net.cnri.util.StreamVector;
import net.handle.apps.servlet_proxy.DefaultServlet;
import net.handle.apps.servlet_proxy.HDLProxy;
import net.handle.apps.servlet_proxy.ResponseHeaderFilter;
import net.handle.hdllib.*;
import net.handle.server.dns.DnsConfiguration;
import net.handle.server.replication.ReplicationDaemon;
import net.handle.server.servletcontainer.EmbeddedJetty;
import net.handle.server.servletcontainer.EmbeddedJettyConfig;
import net.handle.server.servletcontainer.HandleApiErrorHandler;
import net.handle.server.servletcontainer.HandleAuthorizationEnabledSessionHandler;
import net.handle.server.servletcontainer.EmbeddedJettyConfig.ConnectorConfig;
import net.handle.server.servletcontainer.auth.StandardHandleAuthenticationFilter;
import net.handle.server.servletcontainer.servlets.DisallowHttpZeroDotNineFilter;
import net.handle.server.servletcontainer.servlets.HandleJsonRestApiServlet;
import net.handle.server.servletcontainer.servlets.NativeServlet;
import net.handle.server.servletcontainer.servlets.PrefixesServlet;
import net.handle.server.servletcontainer.servlets.SessionsServlet;
import net.handle.server.servletcontainer.servlets.SiteServlet;
import net.handle.server.servletcontainer.servlets.UncaughtExceptionsFilter;
import net.handle.server.servletcontainer.servlets.UnknownApiServlet;
import net.handle.server.servletcontainer.servlets.VerificationsServlet;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.io.*;

import javax.servlet.DispatcherType;
import javax.servlet.SessionTrackingMode;

import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;

/*******************************************************************************
 *
 * Main class: instantiates a server and listeners, and starts them all.  This
 * class is invoked on the command-line and reads the configuration file.
 *
 ******************************************************************************/

public class Main {
    public static final String SERVLET_CONTAINER_CONFIG = "servlet_container_config";
    public static final String CONNECTORS_CONFIG = "connectors";
    public static final String WEBSVR_HTTP_CONFIG = "http_config";
    public static final String WEBSVR_HTTPS_CONFIG = "https_config";
    protected AbstractServer server;
    protected Vector<NetworkInterface> interfaces;
    protected DnsConfiguration dnsConfig;
    protected StreamTable configTable;
    protected File serverDir;
    protected ServerLog logger;
    protected ThreadGroup interfaceThreadGroup = null;
    protected HandleResolver resolver = null;
    protected EmbeddedJetty embeddedJetty;
    private boolean logHttpAccesses;

    private static String[] args;
    private volatile boolean restart = false;
    private String restartScript = null;

    private static void printUsage() {
        System.err.println("Usage: hdl-server <config-directory>");
    }

    public static void main(String argv[]) {
        System.out.println("Handle.Net Server Software version " + Version.version + ",startup parameters " + argv);
        args = argv;

        String configDirStr = null;

        if ((argv == null) || (argv.length != 1)) {
            printUsage();
            return;
        }

        configDirStr = argv[0]; // The only argument is the config-dir name

        Main main = null;

        StreamTable configTable = new StreamTable();
        // Get, check serverDir
        File serverDir = new File(configDirStr);

        if (!((serverDir.exists()) && (serverDir.isDirectory()))) {
            System.err.println("Invalid configuration directory: " + configDirStr + ".");
            return;
        }

        // Load configTable from the config file
        try {
            configTable.readFromFile(new File(serverDir, HSG.CONFIG_FILE_NAME));
        } catch (Exception e) {
            System.err.println("Error reading configuration: " + e);
            return;
        }

        // Create the Main server object and start it
        try {
            main = new Main(serverDir, configTable);
            main.logError(ServerLog.ERRLOG_LEVEL_INFO, "Handle.net Server Software version " + Version.version);
            main.initialize();
            main.start();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.out.println("Error: " + e.getMessage());
            System.out.println("       (see the error log for details.)\n");
            System.err.println("Shutting down...");
            if (main != null) try {
                main.shutdown();
            } catch (Exception e2) {
                /* Ignore */
            }
            System.exit(0);
        }
    }

    /**
     * Constructor for the Main object
     */
    public Main(File serverDir, StreamTable configTable) throws Exception {
        this.serverDir = serverDir;
        this.configTable = configTable;
        logger = new ServerLog(serverDir, configTable);
    }

    public StreamTable getConfig() {
        return configTable;
    }

    public boolean logHttpAccesses() {
        return logHttpAccesses;
    }

    public void setResolver(HandleResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Create a server object, and all of the interface objects based on
     * the configuration.
     */
    public File getConfigDir() {
        return serverDir;
    }

    /**
     * Returns configuration of DNS subsystem.
     */
    public DnsConfiguration getDNSConfig() {
        return dnsConfig;
    }

    public void dumpFromPrimary(boolean deleteAll) throws Exception {
        dumpFromPrimary(deleteAll, null);
    }

    /**
     * Dumps all handles
     */
    public void dumpFromPrimary(boolean deleteAll, SiteInfo[] sites) throws Exception {
        if (server != null) {
            throw new HandleException(HandleException.SERVER_ERROR, "Server has already been initialized");
        }

        // method deprecated, and who has a finalizer anyway?
        // System.runFinalizersOnExit(true);

        // Configure the resolver
        this.resolver = new HandleResolver();

        if (configTable.containsKey("tcp_timeout")) {
            int timeout = Integer.parseInt((String) configTable.get("tcp_timeout"));
            this.resolver.setTcpTimeout(timeout);
        }

        this.resolver.setCheckSignatures(true);
        this.resolver.traceMessages = configTable.getBoolean("trace_resolution") || configTable.getBoolean("trace_outgoing_messages");

        if (configTable.getBoolean(HSG.NO_UDP, false)) {
            resolver.setPreferredProtocols(new int[]{Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP});
        }

        // tell the server not to start the replication daemon
        StreamTable config = (StreamTable) configTable.get(AbstractServer.HDLSVR_CONFIG);
        config.put(HandleServer.DO_REPLICATION, false);
        config.put(HandleServer.ENABLE_HOMED_PREFIX_LOCAL_SITES, false);

        // Create the server object
        server = AbstractServer.getInstance(this, configTable, resolver);

        if (!(server instanceof HandleServer))
            throw new HandleException(HandleException.SERVER_ERROR, "server is of a type that cannot be replicated");

        ReplicationDaemon replicator = new ReplicationDaemon((HandleServer) server, config, serverDir);
        replicator.dumpHandles(deleteAll, sites);
    }

    /**
     * Create a server object, and all of the interface objects based on
     * the configuration.
     */
    public void initialize() throws Exception {
        if (server != null) {
            throw new HandleException(HandleException.SERVER_ERROR, "Server has already been initialized");
        }

        // method deprecated, and who has a finalizer anyway?
        // System.runFinalizersOnExit(true);

        // Configure the resolver
        this.resolver = new HandleResolver();
        this.resolver.setCheckSignatures(true);

        FilesystemConfiguration.configureResolverUsingKeys(resolver, configTable);
        //    try {
        //      File cacheFile = new File(getConfigDir(), CACHE_STORAGE_FILE);
        //      if (cacheFile.exists())
        //        cacheFile.delete();
        //
        //      Cache cache = new JDBCache(cacheFile);
        //      this.resolver.setCache(cache);
        //    } catch (Exception e) {
        //      System.err.println("Warning: Cannot create handle cache (" + e + ").");
        //      e.printStackTrace(System.err);
        //    }
        this.resolver.setCache(new MemCache(1024 * 16, 60 * 60));

        // Create the server object
        server = AbstractServer.getInstance(this, configTable, resolver);

        interfaces = new Vector<>();
        // Get the list of listeners
        Object obj = configTable.get(HSG.INTERFACES);
        if ((obj == null) || (!(obj instanceof Vector)) || (((Vector<?>) obj).size() < 1)) {
            throw new Exception("No \"" + HSG.INTERFACES + "\" specified!");
        }

        dnsConfig = new DnsConfiguration(server, (StreamTable) configTable.get(HSG.DNS_CONFIG));

        Vector<?> frontEndLabels = (Vector<?>) obj;
        for (int i = 0; i < frontEndLabels.size(); i++) {
            String frontEndLabel = String.valueOf(frontEndLabels.elementAt(i));
            NetworkInterface ifc = NetworkInterface.getInstance(this, frontEndLabel, configTable);
            if (ifc != null) interfaces.addElement(ifc);
        }

        initEmbeddedJetty();
        if (embeddedJetty != null) embeddedJetty.startPriorityDeploymentManager();
    }

    /**
     * Get the server object that handles requests sent to this server
     */
    public AbstractServer getServer() {
        return server;
    }

    /**
     * Start all of the listener threads and begin taking requests
     */
    public void start() throws Exception {
        server.start();

        interfaceThreadGroup = new ThreadGroup("Network Interfaces");

        for (int i = 0; i < interfaces.size(); i++) {
            NetworkInterface interfc = interfaces.elementAt(i);
            Thread t = new Thread(interfaceThreadGroup, interfc);
            t.start();
        }

        if (embeddedJetty != null) {
            System.out.println("Starting HTTP server...");
            embeddedJetty.startHttpServer();
        }

        // add a hook to safely shut down the interfaces, loggers, and databases
        // when exiting.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                cleanUp();
            }
        });

        new ServerMonitor().start();
    }

    private void initEmbeddedJetty() throws Exception {
        StreamTable servletContainerConfig = null;
        servletContainerConfig = (StreamTable) configTable.get(SERVLET_CONTAINER_CONFIG);
        if (servletContainerConfig != null) {
            if (servletContainerConfig.getBoolean("log_accesses", false)) logHttpAccesses = true;
        }
        StreamTable serverHttpConfig = null;
        {
            StreamTable serverConfig = (StreamTable) configTable.get("server_config");
            if (serverConfig != null) {
                serverHttpConfig = (StreamTable) serverConfig.get("http_config");
            }
        }
        StreamVector connectors = getConnectorConfigTables(servletContainerConfig);
        if (connectors.isEmpty()) {
            return;
        }
        EmbeddedJettyConfig jettyConfig = new EmbeddedJettyConfig();
        jettyConfig.addContextAttribute("net.handle.server.Main", this);
        if (server instanceof HandleServer) {
            jettyConfig.addContextAttribute("net.handle.server.HandleServer", server);
        }
        jettyConfig.addSystemClass("net.handle.hdllib.");
        jettyConfig.addSystemClass("net.handle.server.");
        jettyConfig.addSystemClass("com.google.gson.");
        jettyConfig.setBaseDir(serverDir);
        jettyConfig.setResolver(resolver);
        for (Object connectorConfigTable : connectors) {
            configureEmbeddedJettyConnector(jettyConfig, (StreamTable) connectorConfigTable);
        }
        if (servletContainerConfig == null || servletContainerConfig.getBoolean("enable_root_web_app", true)) {
            if (serverHttpConfig == null || serverHttpConfig.getBoolean("enable_root_web_app", true)) {
                boolean enableTrace = getEnableTrace(servletContainerConfig, serverHttpConfig, connectors);
                configureDefaultRootWebApp(jettyConfig, serverHttpConfig, enableTrace);
            }
        }
        embeddedJetty = new EmbeddedJetty(jettyConfig);
        embeddedJetty.setUpHttpServer();
    }

    private boolean getEnableTrace(StreamTable servletContainerConfig, StreamTable serverHttpConfig, StreamVector connectors) {
        // turn off HTTP TRACE if servlet_container_config, or server_config/http_config, or any connector does
        if (servletContainerConfig != null && !servletContainerConfig.getBoolean("enable_trace", true)) return false;
        if (serverHttpConfig != null && !serverHttpConfig.getBoolean("enable_trace", true)) return false;
        for (Object obj : connectors) {
            if (obj instanceof StreamTable) {
                boolean enableTrace = ((StreamTable) obj).getBoolean("enable_trace", true);
                if (!enableTrace) return false;
            }
        }
        return true;
    }

    private void configureDefaultRootWebApp(EmbeddedJettyConfig jettyConfig, StreamTable serverHttpConfig, boolean enableTrace) throws Exception {
        boolean enableProxy = true;
        if (serverHttpConfig != null) {
            enableProxy = serverHttpConfig.getBoolean("enable_proxy", true);
        }
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS | ServletContextHandler.SECURITY);
        HandleAuthorizationEnabledSessionHandler sessionHandler = new HandleAuthorizationEnabledSessionHandler();
        context.setSessionHandler(sessionHandler);
        org.eclipse.jetty.server.SessionManager sessionManager = sessionHandler.getSessionManager();
        sessionManager.setSessionTrackingModes(Collections.<SessionTrackingMode>emptySet());
        if (serverHttpConfig != null && serverHttpConfig.containsKey("session_timeout_seconds")) {
            int sessionTimeoutSeconds = serverHttpConfig.getInt("session_timeout_seconds", 1800); // default 30 minutes
            sessionManager.setMaxInactiveInterval(sessionTimeoutSeconds);
        }

        context.setAttribute("net.handle.server.Main", this);
        context.setAttribute(org.eclipse.jetty.server.SessionManager.class.getName(), sessionManager);
        if (server instanceof HandleServer) {
            context.setAttribute("net.handle.server.HandleServer", server);
        }
        context.setContextPath("/");

        context.setBaseResource(Resource.newResource(HDLProxy.class.getResource("resources/")));
        ServletHolder hdlProxy = new ServletHolder(HDLProxy.class.getName(), HDLProxy.class);
        hdlProxy.setInitOrder(1);
        context.getServletHandler().addServlet(hdlProxy);
        context.getServletHandler().addServlet(new ServletHolder(DefaultServlet.class.getName(), DefaultServlet.class));
        context.getServletHandler().addServlet(new ServletHolder(NativeServlet.class.getName(), NativeServlet.class));
        if (enableProxy) {
            ServletMapping mapping = new ServletMapping();
            mapping.setServletName(HDLProxy.class.getName());
            mapping.setPathSpec("/*");
            context.getServletHandler().addServletMapping(mapping);
            mapping = new ServletMapping();
            mapping.setServletName(DefaultServlet.class.getName());
            mapping.setPathSpec("/static/*");
            context.getServletHandler().addServletMapping(mapping);
        } else {
            ServletMapping mapping = new ServletMapping();
            mapping.setServletName(NativeServlet.class.getName());
            mapping.setPathSpec("/*");
            context.getServletHandler().addServletMapping(mapping);
        }

        ServletHolder handlesServletHolder = new ServletHolder(HandleJsonRestApiServlet.class.getName(), HandleJsonRestApiServlet.class);
        context.addServlet(handlesServletHolder, "/api/handles/*");
        //      ServletMapping mapping = new ServletMapping();
        //      mapping.setServletName(HandleJsonRestApiServlet.class.getName());
        //      mapping.setPathSpec("/api/*");
        //      context.getServletHandler().addServletMapping(mapping);

        ServletHolder unknownApiServletHolder = new ServletHolder(UnknownApiServlet.class.getName(), UnknownApiServlet.class);
        context.addServlet(unknownApiServletHolder, "/api/*");

        ServletHolder siteServletHolder = new ServletHolder(SiteServlet.class.getName(), SiteServlet.class);
        context.addServlet(siteServletHolder, "/api/site");
        ServletMapping mapping = new ServletMapping();
        mapping.setServletName(SiteServlet.class.getName());
        mapping.setPathSpec("/api/site/");
        context.getServletHandler().addServletMapping(mapping);

        context.addServlet(new ServletHolder(VerificationsServlet.class.getName(), VerificationsServlet.class), "/api/verifications/*");
        context.addServlet(new ServletHolder(PrefixesServlet.class.getName(), PrefixesServlet.class), "/api/prefixes/*");
        context.addServlet(new ServletHolder(SessionsServlet.class.getName(), SessionsServlet.class), "/api/sessions/*");

        if (serverHttpConfig != null && serverHttpConfig.get("headers") != null) {
            StreamTable headersMap = (StreamTable) serverHttpConfig.get("headers");
            FilterHolder filterHolder = new FilterHolder(ResponseHeaderFilter.class);
            for (String header : headersMap.keySet()) {
                filterHolder.setInitParameter(header, headersMap.getStr(header));
            }
            // do not re-run this one on TlsRenegotiationRequestor async
            context.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
        }

        FilterHolder filterHolder = new FilterHolder(org.eclipse.jetty.servlets.CrossOriginFilter.class);
        filterHolder.setInitParameter("allowedMethods", "GET,POST,HEAD,PUT,DELETE");
        filterHolder.setInitParameter("allowedHeaders", "X-Requested-With,Authorization,Origin,Accept,Content-Type");
        filterHolder.setInitParameter("preflightMaxAge", "86400");
        filterHolder.setInitParameter("allowCredentials", "false");
        filterHolder.setInitParameter("chainPreflight", "false");
        //filterHolder.setInitParameter("exposeHeaders", "Allow,Authentication-Info,Location,WWW-Authenticate");
        context.addFilter(filterHolder, "/*", null);

        filterHolder = new FilterHolder(org.eclipse.jetty.servlets.GzipFilter.class);
        filterHolder.setInitParameter("methods", "GET,POST,PUT,DELETE");
        filterHolder.setInitParameter("vary", "Accept-Encoding");
        context.addFilter(filterHolder, "/*", null);

        context.addFilter(UncaughtExceptionsFilter.class, "/api/*", null);
        context.addFilter(DisallowHttpZeroDotNineFilter.class, "/api/*", null);
        context.addFilter(StandardHandleAuthenticationFilter.class, "/api/*", null);

        context.setErrorHandler(new HandleApiErrorHandler());

        if (!enableTrace) {
            Constraint constraint = new Constraint();
            constraint.setName("Disable TRACE");
            constraint.setAuthenticate(true);
            ConstraintMapping constraintMapping = new ConstraintMapping();
            constraintMapping.setConstraint(constraint);
            constraintMapping.setMethod("TRACE");
            constraintMapping.setPathSpec("/");
            ConstraintSecurityHandler securityHandler = (ConstraintSecurityHandler) context.getSecurityHandler();
            securityHandler.addConstraintMapping(constraintMapping);
        }

        jettyConfig.addDefaultHandler(context);
    }

    private StreamVector getConnectorConfigTables(StreamTable servletContainerConfig) {
        StreamVector connectors;
        if (servletContainerConfig != null) {
            connectors = (StreamVector) servletContainerConfig.get(CONNECTORS_CONFIG);
            if (connectors == null) connectors = new StreamVector();
            if (servletContainerConfig.containsKey(WEBSVR_HTTP_CONFIG)) {
                connectors.add(servletContainerConfig.get(WEBSVR_HTTP_CONFIG));
            }
            if (servletContainerConfig.containsKey(WEBSVR_HTTPS_CONFIG)) {
                StreamTable table = (StreamTable) servletContainerConfig.get(WEBSVR_HTTPS_CONFIG);
                table.put("https", "yes");
                connectors.add(table);
            }
        } else {
            connectors = new StreamVector();
        }
        @SuppressWarnings("unchecked")
        Vector<String> labels = (Vector<String>) configTable.get(HSG.INTERFACES);
        for (String label : labels) {
            if (label.startsWith(NetworkInterface.INTFC_HDLHTTP)) {
                StreamTable intfConfig = (StreamTable) configTable.get(label + "_config");
                if (label.contains("https")) intfConfig.put("https", "yes");
                connectors.add(intfConfig);
            }
        }
        return connectors;
    }

    private void configureEmbeddedJettyConnector(EmbeddedJettyConfig jettyConfig, StreamTable connectorConfigTable) throws UnknownHostException {
        ConnectorConfig connectorConfig = new ConnectorConfig();
        connectorConfig.setHttps(connectorConfigTable.getBoolean("https", false));
        connectorConfig.setHttpOnly(connectorConfigTable.getBoolean("http_only", false));
        String webserverHttpAddressString = connectorConfigTable.getStr("bind_address");
        InetAddress httpListenAddress = null;
        if (webserverHttpAddressString != null)
            httpListenAddress = InetAddress.getByName(String.valueOf(webserverHttpAddressString));
        connectorConfig.setPort(connectorConfigTable.getInt("bind_port", 0));
        connectorConfig.setRedirectPort(connectorConfigTable.getInt("redirect_port", 0));
        connectorConfig.setListenAddress(httpListenAddress);
        boolean useSelfSignedCert = connectorConfigTable.getBoolean("https_default_self_signed_cert", true);
        if (useSelfSignedCert) {
            connectorConfig.setHttpsUseSelfSignedCert(true);
            try {
                X509Certificate[] certChain = server.getCertificateChain();
                if (certChain != null && certChain.length > 0) {
                    connectorConfig.setHttpsCertificateChain(certChain);
                    connectorConfig.setHttpsPubKey(certChain[0].getPublicKey());
                    connectorConfig.setHttpsPrivKey(server.getCertificatePrivateKey());
                } else {
                    PublicKey pubKey = server.getPublicKey();
                    PrivateKey privKey = server.getPrivateKey();
                    connectorConfig.setHttpsPubKey(pubKey);
                    connectorConfig.setHttpsPrivKey(privKey);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            connectorConfig.setHttpsUseSelfSignedCert(false);
            connectorConfig.setHttpsKeyStorePassword(connectorConfigTable.getStr("https_keystore_password", null));
            connectorConfig.setHttpsKeyPassword(connectorConfigTable.getStr("https_key_password", null));
            connectorConfig.setHttpsKeyStoreFile(connectorConfigTable.getStr("https_keystore_file", null));
            connectorConfig.setHttpsAlias(connectorConfigTable.getStr("https_alias", null));
        }
        connectorConfig.setHttpsClientAuth(connectorConfigTable.getStr("https_client_auth", "false"));
        jettyConfig.addConnector(connectorConfig);

        System.out.println((connectorConfig.isHttps() ? "HTTPS" : "HTTP") + " handle Request Listener:");
        System.out.println("   address: " + (httpListenAddress == null ? "ANY" : "" + Util.rfcIpRepr(httpListenAddress)));
        System.out.println("      port: " + connectorConfig.getPort());

        // ack, not distinguishing by connector
        if (connectorConfigTable.getBoolean("log_accesses", false)) logHttpAccesses = true;
    }

    /**
     * Bring down all interfaces, save any files, shutdown the server, and exit.
     * (I.e., does not return.)
     */
    public void shutdown() {
        new Thread() {
            @Override
            public void run() {
                System.exit(0);
            }
        }.start();
    }

    @SuppressWarnings("deprecation")
    private void stopAllThreads() {
        try {
            interfaceThreadGroup.stop();
        } catch (Throwable t) {
            /* Ignore */
        }
    }

    protected void cleanUp() {
        logError(ServerLog.ERRLOG_LEVEL_INFO, "Shutting down server at " + (new java.util.Date()));

        // Shut down all listeners
        while (interfaces.size() > 0) {
            NetworkInterface interfc = interfaces.elementAt(0);
            interfaces.removeElementAt(0);
            try {
                interfc.stopRunning();
            } catch (Throwable e) {
                logError(ServerLog.ERRLOG_LEVEL_REALBAD, "unable to shut down interface " + interfc + "; reason: " + e);
            }
        }

        // Try to stop all threads (in case the above didn't work)
        stopAllThreads();

        try { // Shut down the server
            server.shutdown();
        } catch (Exception e) {
            String msg = "Exception shutting down handle server :" + e;
            logError(ServerLog.ERRLOG_LEVEL_REALBAD, msg);
            System.err.println(msg);
            e.printStackTrace(System.err);
        }

        if (embeddedJetty != null) embeddedJetty.stopHttpServer();

        if (logger != null) {
            try {
                logger.shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down logger: " + e);
            }
        }
        if (restart) {
            try {
                restart();
            } catch (IOException e) {
                System.err.println("Error restarting server: " + e);
            }
        }
    }

    public boolean isRestarting() {
        return restart;
    }

    private void restart() throws IOException {
        String startServerCommand = getStartServerCommand();
        if (restartScript == null) {
            System.out.println("running: " + startServerCommand);
            Runtime.getRuntime().exec(startServerCommand);
        } else {

            String absoluteRestartScript = new File(serverDir, restartScript).getAbsolutePath();
            String[] commandWithArgs = {absoluteRestartScript, serverDir.getAbsolutePath(), startServerCommand};
            System.out.println("Running restart script: " + Arrays.toString(commandWithArgs));
            //          ProcessBuilder processBuilder = new ProcessBuilder(commandWithArgs);
            //          processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            //          processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            //          processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            //          Process process = processBuilder.start();
            Runtime.getRuntime().exec(commandWithArgs);
        }
    }

    private String getStartServerCommand() {
        StringBuilder cmd = new StringBuilder();
        cmd.append(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java ");
        for (String jvmArg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            cmd.append(jvmArg + " ");
        }
        cmd.append("-cp ").append(ManagementFactory.getRuntimeMXBean().getClassPath()).append(" ");
        cmd.append(Main.class.getName()).append(" ");
        for (String arg : args) {
            cmd.append(arg).append(" ");
        }
        String command = cmd.toString();
        return command;
    }

    public synchronized void shutdownAndRestart() {
        if (restart) {
            return;
        } else {
            restart = true;
            shutdown();
        }
    }

    public synchronized void shutdownAndRunScript(@SuppressWarnings("hiding") String restartScript) {
        if (restart) {
            return;
        } else {
            restart = true;
            this.restartScript = restartScript;
            shutdown();
        }
    }

    /**
     * Write the specified access record to the log
     */
    public void logAccess(String accesssType, InetAddress addr, int opCode, int rsCode, String message, long time) {
        if (logger == null) {
            System.out.println("Access: type=" + accesssType + "; addr=" + Util.rfcIpRepr(addr) + "; opCode: " + opCode + "; respCode: " + rsCode + "; message: " + message + "; " + time + "ms");
        } else {
            logger.logAccess(accesssType, addr, opCode, rsCode, message, time);
        }
    }

    /**
     * Write the specified error record to the log
     */
    public void logError(int level, String message) {
        if (logger == null) System.err.println("Error: level=" + level + "; message: " + message);
        else logger.logError(level, message);
    }

    private class ServerMonitor extends Thread {
        File keepRunningFile;

        ServerMonitor() throws IOException {
            setDaemon(true);
            setName("Server Monitor");

            keepRunningFile = new File(serverDir, "delete_this_to_stop_server");
            keepRunningFile.createNewFile();
            keepRunningFile.deleteOnExit();
        }

        @Override
        public void run() {
            while (true) {
                try {
                    if (!keepRunningFile.exists()) {
                        shutdown();
                        System.exit(0);
                    }

                    Thread.sleep(1000);
                } catch (Throwable t) {
                }
            }
        }
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import com.google.gson.Gson;
import net.cnri.simplexml.XTag;
import net.cnri.util.*;
import net.cnri.util.FastDateFormat.FormatSpec;
import net.handle.apps.servlet_proxy.HDLServletRequest.ResponseType;
import net.handle.apps.servlet_proxy.handlers.CIDRUtils;
import net.handle.hdllib.*;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;
import net.handle.hdllib.trust.JwtClaimsSet;
import net.handle.hdllib.trust.TrustException;
import net.handle.server.MonitorDaemon;
import net.handle.server.servletcontainer.HandleServerInterface;
import net.handle.server.servletcontainer.servlets.BaseHandleRequestProcessingServlet;
import net.handle.server.servletcontainer.servlets.HandleJsonRestApiServlet;
import net.handle.server.servletcontainer.servlets.NativeServlet;
import net.handle.server.servletcontainer.servlets.UnknownApiServlet;
import net.handle.server.servletcontainer.support.PreAuthenticatedRequestProcessor;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

public class HDLProxy extends HttpServlet {

    // constants
    public static final String ACTION_PARAM = "action";
    public static final String ACTION_REDIRECT = "redirect";
    public static final String ACTION_SHOWLOCS = "showurls";
    public static final String ACTION_SHOWVALUES = "showvalues";
    public static final String ACTION_TOOLBAR = "toolbar";
    public static final String ACTION_METADATA = "metadata";
    public static final String ACTION_REST = "api";

    public static final int MAX_ALIASES = 20;

    private final String DEFAULT_CONTACT_EMAIL = "hdladmin@cnri.reston.va.us";

    public static final byte MSG_INVALID_MSG_SIZE[] = Util.encodeString("Invalid message length");
    public static final byte MSG_INVALID_REQUEST[] = Util.encodeString("Invalid request");

    Map<String, String> handleLinkPrefixMap = new HashMap<>();

    protected String HTDOCS = "";
    public final static Object resolverInitLock = new Object();
    public static RequestProcessor resolver = null;
    private static MemCache memCache;
    private static MemCache memCacheCertified;
    protected static final NamespaceInfo DEFAULT_NAMESPACE_INFO = new NamespaceInfo();

    private HandleServerInterface handleServer;

    private final FastDateFormat dateFormat = new FastDateFormat(new FormatSpec("-", " ", ":", "", ".", true, true), TimeZone.getDefault());

    protected Properties config;
    private RotatingAccessLog logger;
    private boolean logReferrer = false;
    private boolean logHSAdmin = false;
    private boolean logUserAgent = false;
    private String favicon = null;
    private String robotsTxt = null;
    private final String defaultAction = ACTION_REDIRECT; // ACTION_TOOLBAR;

    class TypeHandlerEntry {
        TypeHandler handler;
        int position;
    }

    protected TypeHandler valueHandlers[] = {};

    // virtual host -> page mappings
    Hashtable<String, HTMLFile> queryPages = new Hashtable<>();
    Hashtable<String, HTMLFile> helpPages = new Hashtable<>();
    protected Hashtable<String, HTMLFile> errorPages = new Hashtable<>();
    Hashtable<String, HTMLFile> responsePages = new Hashtable<>();
    Hashtable<String, HTMLFile> valuesNotFoundPages = new Hashtable<>();

    Map<String, String> helpRedirect = new HashMap<>(); // if non-null help.html redirects there

    Map<String, Boolean> retryAuthOnNotFound = new HashMap<>(); // if true, will re-try resolutions auth when originally not found

    Map<String, List<String>> locattShortcutParameters = new HashMap<>(); // query parameters automatically changed to locatt parameters

    String remoteAddressHeader;
    List<CIDRUtils> remoteAddressInternalProxies;

    @Override
    public synchronized void destroy() {
        loadedSettings = false;
        if (logger != null) logger.shutdown();
        if (memCache != null) memCache.close();
        if (memCacheCertified != null) memCacheCertified.close();
        logger = null;
    }

    protected volatile boolean loadedSettings = false;

    protected void loadSettings() throws ServletException {
        if (loadedSettings) return;
        synchronized (resolverInitLock) {
            if (loadedSettings) return;
            if (handleServer == null) {
                System.err.println("initializing Handle proxy servlet");
                ServletConfig slConfig = getServletConfig();
                System.err.println("  servlet config: ");
                for (Enumeration<String> en = slConfig.getInitParameterNames(); en.hasMoreElements();) {
                    String name = en.nextElement();
                    System.err.println("   " + name + ": " + slConfig.getInitParameter(String.valueOf(name)));
                }

                ServletContext slContext = slConfig.getServletContext();
                System.err.println("  context config: ");
                for (Enumeration<String> en = slContext.getInitParameterNames(); en.hasMoreElements();) {
                    String name = en.nextElement();
                    System.err.println("   " + name + ": " + slContext.getInitParameter(String.valueOf(name)));
                }
            }

            // load configuration properties
            config = loadHdlProxyProperties(getServletContext(), getServletConfig(), handleServer == null);

            // htdocs dir
            HTDOCS = config.getProperty("htdocs");

            favicon = config.getProperty("favicon", null);
            if (favicon == null) {
                String faviconURL = config.getProperty("favicon_url", null);
                if (faviconURL != null) favicon = "redirect:" + faviconURL.trim();
            } else favicon = favicon.trim();

            robotsTxt = config.getProperty("robots_txt", null);
            if (robotsTxt == null) {
                robotsTxt = "res:resources/robots.txt";
            } else robotsTxt = robotsTxt.trim();

            // prefix string (e.g "hdl:") used before handle links

            handleLinkPrefixMap.put("default", config.getProperty("handle_link_prefix"));
            // also allow per-host setting

            // get the singleton logger so that we don't clobber the logs from other servlets
            if (handleServer == null) {
                try {
                    String logFileName = config.getProperty("access_log");
                    if (logFileName != null) {
                        File logDir = new File(logFileName).getParentFile();
                        System.err.println("loading logger for handle proxy");
                        System.err.println(" log folder: " + logDir.getAbsolutePath());
                        String rotationRate = config.getProperty("log_rotation_rate", "monthly");
                        RotatingAccessLog.RotationRate rate;
                        if ("daily".equalsIgnoreCase(rotationRate)) {
                            rate = RotatingAccessLog.RotationRate.ROTATE_DAILY;
                        } else if ("hourly".equalsIgnoreCase(rotationRate)) {
                            rate = RotatingAccessLog.RotationRate.ROTATE_HOURLY;
                        } else if ("never".equalsIgnoreCase(rotationRate)) {
                            rate = RotatingAccessLog.RotationRate.ROTATE_NEVER;
                        } else {
                            rate = RotatingAccessLog.RotationRate.ROTATE_MONTHLY;
                        }
                        logger = RotatingAccessLog.getLogger(logDir, rate);
                        MonitorDaemon monitorDaemon = (MonitorDaemon) getServletContext().getAttribute(MonitorDaemon.class.getName());
                        if (monitorDaemon != null) {
                            monitorDaemon.setRequestCounters(logger.getRequestsPastMinute(), logger.getPeakRequestsPerMinute());
                        }
                    }
                } catch (Exception e) {
                    throw new ServletException("Error loading logger", e);
                }
            }

            remoteAddressHeader = config.getProperty("remote_address_header");
            String remoteAddressInternalProxiesString = config.getProperty("remote_address_internal_proxies");
            if (remoteAddressInternalProxiesString != null) {
                String[] proxyStrings = remoteAddressInternalProxiesString.split("\\s*,\\s*");
                remoteAddressInternalProxies = new ArrayList<>();
                for (String proxyString : proxyStrings) {
                    try {
                        remoteAddressInternalProxies.add(new CIDRUtils(proxyString));
                    } catch (UnknownHostException e) {
                        throw new ServletException("Error parsing remote_address_internal_proxies", e);
                    }
                }
            }

            logReferrer = Boolean.valueOf(config.getProperty("log_referrer", "true")).booleanValue();
            logHSAdmin = Boolean.valueOf(config.getProperty("log_hs_admin", "false")).booleanValue();
            logUserAgent = Boolean.valueOf(config.getProperty("log_user_agent", "true")).booleanValue();
            // create a sorted list of handlers
            TreeSet<HDLProxy.TypeHandlerEntry> handlers = new TreeSet<>((o1, o2) -> o1.position - o2.position);

            retryAuthOnNotFound.put("default", Boolean.valueOf(config.getProperty("retry_auth_on_not_found", "false")));

            {
                StringTokenizer st = new StringTokenizer(config.getProperty("locatt_shortcut_parameters", ""));
                List<String> locattParams = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    locattParams.add(st.nextToken());
                }
                locattShortcutParameters.put("default", locattParams);
            }

            // iterate through rest of config options for more control
            for (Enumeration<?> enumeration = config.propertyNames(); enumeration.hasMoreElements();) {
                String prop = (String) enumeration.nextElement();
                // type handler setting
                if (prop.startsWith("typehandler.")) {
                    TypeHandlerEntry entry = new TypeHandlerEntry();
                    String klass = config.getProperty(prop);
                    try {
                        String pos = prop.substring("typehandler.".length());
                        entry.position = Integer.parseInt(pos);
                        entry.handler = (TypeHandler) Class.forName(klass).getConstructor().newInstance();
                        handlers.add(entry);
                    } catch (Exception e) {
                        System.err.println("Error setting " + prop + " = " + klass + ": " + e);
                    }
                }
                // html pages
                else if (prop.startsWith("query-page.")) {
                    try {
                        String host = prop.substring("query-page.".length()).toLowerCase();
                        String file = config.getProperty(prop).trim();
                        queryPages.put(host, new HTMLFile(HTDOCS, file, getServletContext()));
                    } catch (Exception e) {
                        System.err.println("Error setting query page: " + prop + "\n");
                        e.printStackTrace();
                    }
                } else if (prop.startsWith("help-page.")) {
                    try {
                        String host = prop.substring("help-page.".length()).toLowerCase();
                        String file = config.getProperty(prop).trim();
                        helpPages.put(host, new HTMLFile(HTDOCS, file, getServletContext()));
                    } catch (Exception e) {
                        System.err.println("Error setting help page: " + prop + "\n" + e);
                    }
                } else if (prop.startsWith("response-page.")) {
                    try {
                        String host = prop.substring("response-page.".length()).toLowerCase();
                        String file = config.getProperty(prop).trim();
                        responsePages.put(host, new HTMLFile(HTDOCS, file, getServletContext()));
                    } catch (Exception e) {
                        System.err.println("Error setting response page: " + prop);
                    }
                } else if (prop.startsWith("novalues-page.")) {
                    try {
                        String host = prop.substring("novalues-page.".length()).toLowerCase();
                        String file = config.getProperty(prop).trim();
                        valuesNotFoundPages.put(host, new HTMLFile(HTDOCS, file, getServletContext()));
                    } catch (Exception e) {
                        System.err.println("Error setting valuesnotfound page: " + prop);
                    }
                } else if (prop.startsWith("error-page.")) {
                    try {
                        String host = prop.substring("error-page.".length()).toLowerCase();
                        String file = config.getProperty(prop).trim();
                        errorPages.put(host, new HTMLFile(HTDOCS, file, getServletContext()));
                    } catch (Exception e) {
                        System.err.println("Error setting error page: " + prop);
                    }
                } else if (prop.startsWith("retry_auth_on_not_found.")) {
                    String host = prop.substring("retry_auth_on_not_found.".length()).toLowerCase();
                    boolean val = Boolean.valueOf(config.getProperty(prop));
                    retryAuthOnNotFound.put(host, val);
                } else if (prop.startsWith("handle_link_prefix.")) {
                    String host = prop.substring("handle_link_prefix.".length()).toLowerCase();
                    handleLinkPrefixMap.put(host, config.getProperty(prop).trim());
                } else if (prop.startsWith("locatt_shortcut_params.")) {
                    String host = prop.substring("locatt_shortcut_params.".length()).toLowerCase();
                    StringTokenizer st = new StringTokenizer(config.getProperty(prop, ""));
                    List<String> locattParams = new ArrayList<>();
                    while (st.hasMoreTokens()) {
                        locattParams.add(st.nextToken());
                    }
                    locattShortcutParameters.put(host, locattParams);
                } else if (prop.startsWith("help_redirect.")) {
                    String redirect = prop.substring("help_redirect.".length()).toLowerCase();
                    helpRedirect.put(redirect, config.getProperty(prop).trim());
                }
            }

            // copy the sorted list of handlers into a faster array
            valueHandlers = new TypeHandler[handlers.size()];
            Iterator<TypeHandlerEntry> iter = handlers.iterator();
            for (int i = 0; i < valueHandlers.length; i++) {
                valueHandlers[i] = iter.next().handler;
            }

            // init resolver and set cache
            synchronized (resolverInitLock) {
                if (resolver == null) {
                    if (handleServer == null) {
                        resolver = new HandleResolver();
                        ((HandleResolver) resolver).traceMessages = config.getProperty("trace_msgs", "false").equals("true");
                        initResolver();
                    } else {
                        resolver = new PreAuthenticatedRequestProcessor(handleServer, "HDLProxy");
                    }
                }
            }
            loadedSettings = true;
        }
    }

    public static Properties loadHdlProxyProperties(ServletContext context, ServletConfig config, boolean logConfig) throws ServletException {
        Properties res = new Properties();
        try {
            String configFileStr = null;
            if (config != null) {
                configFileStr = config.getInitParameter("config");
            }
            if (logConfig) {
                System.err.println("  base path: " + (new File(".").getCanonicalPath()));
                System.err.println("  config file: " + configFileStr);
            }
            File configFile = null;
            if (configFileStr != null) configFile = new File(configFileStr);
            if (configFile != null && configFile.exists() && configFile.canRead()) {
                if (logConfig) System.err.println("Loading settings from " + configFile.getCanonicalPath());
                InputStream in = new FileInputStream(configFile);
                res.load(in);
                in.close();
            } else {
                InputStream in = context.getResourceAsStream("/WEB-INF/hdlproxy.properties");
                if (in != null) {
                    if (logConfig) System.err.println("Loading settings from /WEB-INF/hdlproxy.properties");
                    res.load(in);
                    in.close();
                } else {
                    if (logConfig) System.err.println("Loading default settings");
                    res.load(HDLProxy.class.getResourceAsStream("resources/WEB-INF/hdlproxy.properties"));
                }
            }
            //config.list(System.err);
        } catch (IOException e) {
            throw new ServletException("Error loading servlet properties: " + e);
        }
        return res;
    }

    // for ease of override
    public void initResolver() {
        if (resolver instanceof HandleResolver) {
            boolean useCache = Boolean.valueOf(config.getProperty("enable_cache", "true")).booleanValue();
            if (useCache) {
                int maxHandles = Integer.valueOf(config.getProperty("cache_max_handles", "16384")).intValue();
                long maxTtl = Long.valueOf(config.getProperty("cache_max_ttl", "3600")).longValue();
                memCache = new MemCache(maxHandles, maxTtl);
                memCacheCertified = new MemCache(maxHandles, maxTtl);
            } else {
                memCache = null;
                memCacheCertified = null;
            }
            ((HandleResolver) resolver).setCache(memCache);
            ((HandleResolver) resolver).setCertifiedCache(memCacheCertified);
        }
    }

    private void handleFavicon(HttpServletResponse resp) throws IOException {
        handleSpecial(resp, favicon, "image/x-icon");
    }

    private void handleRobotsTxt(HttpServletResponse resp) throws IOException {
        handleSpecial(resp, robotsTxt, "text/plain");
    }

    private void handleSpecial(HttpServletResponse resp, String configValue, String contentType) throws IOException {
        if (configValue.startsWith("redirect:")) {
            String faviconURL = configValue.substring("redirect:".length());
            resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            resp.setDateHeader("Expires", System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000);
            resp.setHeader("Location", faviconURL);
            try {
                resp.getWriter().write("Favicon moved to " + faviconURL);
            } catch (Exception t) {
            }
        } else if (configValue.startsWith("servlet:")) {
            String path = configValue.substring("servlet:".length());
            InputStream in = getServletContext().getResourceAsStream(path);
            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                try {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType(contentType);
                    copyInputToOutput(in, resp.getOutputStream());
                } finally {
                    in.close();
                }
            }
        } else if (configValue.startsWith("res:")) {
            String path = configValue.substring("res:".length());
            InputStream in = HDLProxy.class.getResourceAsStream(path);
            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                try {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType(contentType);
                    copyInputToOutput(in, resp.getOutputStream());
                } finally {
                    in.close();
                }
            }
        } else {
            File file = new File(configValue);
            if (!file.exists()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                InputStream in = new FileInputStream(configValue);
                try {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType(contentType);
                    copyInputToOutput(in, resp.getOutputStream());
                } finally {
                    in.close();
                }
            }
        }
    }

    private static void copyInputToOutput(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[4096];
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
        }
    }

    @Override
    public void init() throws ServletException {
        handleServer = (net.handle.server.servletcontainer.HandleServerInterface) getServletContext().getAttribute("net.handle.server.HandleServer");
        loadSettings();
        if (handleServer != null) {
            legacyHandleServerInit();
            otherHandleServerInit();
        }
        getServletContext().setAttribute(HDLProxy.class.getName(), this);
        if (resolver instanceof HandleResolver) getServletContext().setAttribute(HandleResolver.class.getName(), resolver);
    }

    private static final String VIRTUAL_HOST_HOSTNAME = "hostname"; //in config.dct

    private void legacyHandleServerInit() {
        String dir = handleServer.getConfigDir().getAbsolutePath();
        @SuppressWarnings("unchecked")
        Vector<String> labels = (Vector<String>) handleServer.getConfig().get(HSG.INTERFACES);
        for (String label : labels) {
            if (label.startsWith("hdl_http")) {
                StreamTable intfConfig = (StreamTable) handleServer.getConfig().get(label + "_config");
                addPage(intfConfig, queryPages, dir, "query_page", "default");
                addPage(intfConfig, responsePages, dir, "response_page", "default");
                addPage(intfConfig, errorPages, dir, "error_page", "default");
                addPage(intfConfig, valuesNotFoundPages, dir, "error_page", "default");
                Object ob = config.get("virtual_hosts");
                Vector<Object> virtualHosts = null;
                if (ob instanceof Vector) {
                    @SuppressWarnings("unchecked")
                    Vector<Object> v = (Vector<Object>) ob;
                    virtualHosts = v;
                } else if (ob instanceof Hashtable) {
                    Hashtable<?, ?> virtualHt = (Hashtable<?, ?>) ob;
                    virtualHosts = new Vector<>();
                    virtualHosts.addElement(virtualHt);
                }
                for (int i = 0; virtualHosts != null && i < virtualHosts.size(); i++) {
                    // remember the Vector comes in has the structure
                    // each entry is a Hashtable, to store the
                    // "hostname", "query_page", "response_page" and "error_page"

                    Hashtable<?, ?> ht = (Hashtable<?, ?>) virtualHosts.elementAt(i);
                    String hostname = (String) ht.get(VIRTUAL_HOST_HOSTNAME);
                    if (hostname == null || hostname.length() == 0) {
                        System.err.println("The vitual host name missing in the configuration!");
                        continue; // no virtual host entry can be formed
                    }

                    addPage(intfConfig, queryPages, dir, "query_page", hostname);
                    addPage(intfConfig, responsePages, dir, "response_page", hostname);
                    addPage(intfConfig, errorPages, dir, "error_page", hostname);
                    addPage(intfConfig, valuesNotFoundPages, dir, "error_page", hostname);
                }
            }
        }
    }

    private void addPage(StreamTable whichConfig, Hashtable<String, HTMLFile> pages, String dir, String param, String hostname) {
        String page = (String) whichConfig.get(param);
        if (page != null) {
            try {
                pages.put(hostname, new HTMLFile(dir, page.trim(), getServletContext()));
            } catch (Exception e) {
                System.err.println("Error adding " + param + page + " for " + hostname + "\n" + e);
            }
        }
    }

    private void otherHandleServerInit() throws ServletException {
        StreamTable serverConfig = (StreamTable) handleServer.getConfig().get("server_config");
        if (serverConfig == null) return;
        StreamTable httpConfig = (StreamTable) serverConfig.get("http_config");
        if (httpConfig == null) return;
        if (httpConfig.containsKey("favicon")) {
            favicon = httpConfig.getStr("favicon");
        }
        if (httpConfig.containsKey("robots_txt")) {
            robotsTxt = httpConfig.getStr("robots_txt");
        }
        if (httpConfig.containsKey("remote_address_header")) {
            remoteAddressHeader = httpConfig.getStr("remote_address_header");
        }
        if (httpConfig.containsKey("remote_address_internal_proxies")) {
            remoteAddressInternalProxies = new ArrayList<>();
            StreamVector proxiesStreamVector = (StreamVector) httpConfig.get("remote_address_internal_proxies");
            for (Object proxyObj : proxiesStreamVector) {
                try {
                    remoteAddressInternalProxies.add(new CIDRUtils((String) proxyObj));
                } catch (UnknownHostException e) {
                    throw new ServletException("Couldn't parse remote_address_internal_proxies", e);
                }
            }
        }
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (handleSpecial(req, resp)) return;
        HDLServletRequest hdlReq = new HDLServletRequest(this, req, resp, resolver);
        doResponse(hdlReq);
    }

    protected boolean handleSpecial(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String servletPath = req.getServletPath();
        String pathInfo = req.getPathInfo();
        String path = (pathInfo == null) ? servletPath : (servletPath + pathInfo);
        if (favicon != null && path.startsWith("/favicon.ico")) { // short-cut favorite icon retrieval
            handleFavicon(resp);
            return true;
        } else if (path.startsWith("/robots.txt")) { // short-cut robots.txt requests
            handleRobotsTxt(resp);
            return true;
        }
        return false;
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String accept = req.getHeader("Accept");
        String contentType = req.getContentType();
        if (accept != null && accept.toUpperCase().indexOf(Common.HDL_MIME_TYPE.toUpperCase()) >= 0 && contentType != null && contentType.toUpperCase().contains(Common.HDL_MIME_TYPE.toUpperCase())) {
            RequestDispatcher dispatcher = getServletContext().getNamedDispatcher(NativeServlet.class.getName());
            if (dispatcher != null) {
                dispatcher.forward(req, resp);
                return;
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.setCharacterEncoding("UTF-8");
                resp.setContentType("text/plain");
                resp.getWriter().println("Unable to dispatch native handle request");
                return;
            }
        }

        HDLServletRequest hdl = new HDLServletRequest(this, req, resp, resolver);
        doResponse(hdl);
    }

    protected void doResponse(HDLServletRequest hdl) throws IOException, ServletException {
        if (hdl.hdl == null || hdl.hdl.length() <= 0) {
            returnQueryPage(hdl);
            return;
        }
        if (hdl.hdl.equals("help.html")) {
            returnHelpPage(hdl);
            return;
        }

        hdl.normalizeHandle();

        doResolution(hdl, 0);
    }

    /** Perform handle resolution and return a redirect or HTML page.  The
     * aliasCount parameter indicates the maximum number of redirections that
     * we should return before assuming a loop is occurring. */
    void doResolution(HDLServletRequest hdl, int aliasCount) throws IOException, ServletException {
        String action = hdl.params.getParameter(ACTION_PARAM);
        if (action == null && hdl.params.getParameter("noredirect") != null) {
            action = "showvalues";
        }

        if (action == null) { // there is no explicit action, so find a sensible default
            action = defaultAction;
        }
        action = action.toLowerCase();

        String extraString = "hdl:\"" + StringUtils.encodeURL(hdl.getUriPathAndParams()) + "\"";

        if (hdl.oldApi) {
            if (handleUnknownApi(hdl)) return;
            action = ACTION_SHOWVALUES;
        }

        if (hdl.api || ACTION_REST.equals(action)) {
            if (resolveRestfully(hdl)) return;
            action = ACTION_SHOWVALUES;
        }

        // resolve handle
        NamespaceInfo nsInfo = null;
        HandleValue vals[] = null;
        try {
            hdl.resolveHandle();

            // if we were querying non-authoritatively and we are configured to re-query
            // non-authoritative resolutions resulting in not-found responses then submit
            // the query again with the authoritative flag not set
            if (!hdl.resRequest.authoritative && hdl.resResponse != null && hdl.resResponse.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {
                Boolean retry = this.retryAuthOnNotFound.get(hdl.req.getServerName().toLowerCase());
                if (retry == null) retry = this.retryAuthOnNotFound.get("default");
                if (retry == null) retry = Boolean.FALSE;
                if (retry) {
                    hdl.resolveHandle(hdl.resRequest.requestedTypes, hdl.resRequest.requestedIndexes, hdl.resRequest.ignoreRestrictedValues, true, // authoritative
                        hdl.resRequest.certify);
                }
            }

            nsInfo = hdl.resRequest.getNamespace();

            String msg = "";
            if (nsInfo != null && nsInfo.getNamespaceStatus().equals(NamespaceInfo.STATUS_INACTIVE)) {
                // the namespace has an inactive status...
                msg = "Inactive Namespace";
                logAccess("HTTP:HDL", AbstractMessage.OC_RESOLUTION, AbstractMessage.RC_ERROR, hdl, null, extraString);
                returnErrorPage(msg, hdl, nsInfo, null, HttpServletResponse.SC_NOT_FOUND);
                return;
            } else if (hdl.resResponse == null || hdl.resResponse.responseCode != AbstractMessage.RC_SUCCESS) {
                // create a friendly message
                if (hdl.resResponse == null) {
                    msg = "Resolution Error";
                } else if (hdl.resResponse.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {
                    msg = "Not Found";
                } else if (hdl.resResponse.responseCode == AbstractMessage.RC_VALUES_NOT_FOUND) {
                    // should this not be an error?  maybe just show an empty response
                    // page?
                    msg = "No Values Found";
                    if (hdl.resRequest.requestedTypes != null && hdl.resRequest.requestedTypes.length > 0) {
                        // if the user was querying for specific types, return a special
                        // do-you-want-to-get-more-info error page
                        logAccess("HTTP:HDL", AbstractMessage.OC_RESOLUTION, hdl.resResponse.responseCode, hdl, null, extraString);
                        returnValuesNotFoundPage(msg, hdl, null);
                        return;
                    }
                } else {
                    msg = "Resolution Error";
                }
                logAccess("HTTP:HDL", AbstractMessage.OC_RESOLUTION, hdl.resResponse.responseCode, hdl, null, extraString);
                returnErrorPage(msg, hdl, nsInfo, null, BaseHandleRequestProcessingServlet.statusCodeFromResponse(hdl.resResponse));
                return;
            }
            vals = ((ResolutionResponse) hdl.resResponse).getHandleValues();
        } catch (HandleException e) {
            if (nsInfo == null) try {
                nsInfo = hdl.resRequest.getNamespace();
            } catch (Exception ex) {
            }
            String msg = "";
            if (e.getCode() == HandleException.SERVICE_NOT_FOUND) {
                int index = hdl.hdl.indexOf("/");
                String na = (index != -1) ? hdl.hdl.substring(0, index) : hdl.hdl;
                msg = "Prefix [" + na + "] Not Found";
            } else if (e.getCode() == HandleException.CANNOT_CONNECT_TO_SERVER) {
                msg = "Cannot Connect to Server";
            } else {
                msg = "System Error";
                //        e.printStackTrace();  // TODO
            }
            logAccess("HTTP:HDL", AbstractMessage.OC_RESOLUTION, AbstractMessage.RC_ERROR, hdl, null, extraString);
            hdl.exception = e;
            returnErrorPage(msg, hdl, nsInfo, null, BaseHandleRequestProcessingServlet.statusCodeFromResponse(e.toErrorResponse(hdl.resRequest)));
            return;
        }

        // if not disabled, follow aliases
        if (hdl.params.getParameter("ignore_aliases") == null) {
            for (HandleValue val : vals) {
                if (val.hasType(Common.STD_TYPE_HSALIAS)) {
                    if (aliasCount < MAX_ALIASES) {
                        hdl.hdl = Util.decodeString(val.getData());
                        hdl.modifyExpiration(val);
                        doResolution(hdl, aliasCount + 1);
                        return;
                    } else {
                        returnErrorPage("Alias chain too long", hdl, nsInfo, null, HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                }
            }
        }

        try {
            if (action.equals(ACTION_SHOWVALUES)) {
                returnResponsePage(hdl, vals);
            } else if (action.equals(ACTION_SHOWLOCS)) {
                doShowLocations(hdl, vals);
            } else /* if(action.equals(ACTION_REDIRECT)) */ {
                // the result of an alias; do a 301 redirect
                // don't try to do this for a POST, however
                if (aliasCount > 0 && (hdl.req.getMethod().equalsIgnoreCase("GET") || hdl.req.getMethod().equalsIgnoreCase("HEAD"))) {
                    String path = hdl.hdl;
                    String query = "";
                    if (hdl.req.getQueryString() != null) query = "?" + hdl.req.getQueryString();
                    hdl.sendHTTPRedirect(ResponseType.MOVED_PERMANENTLY, hdl.getURLForHandle(path, query));
                } else {
                    doRedirect(hdl, vals);
                }
            }
        } finally {
            logAccess("HTTP:HDL", AbstractMessage.OC_RESOLUTION, hdl.resResponse.responseCode, hdl, vals, extraString);
        }
    }

    private boolean handleUnknownApi(final HDLServletRequest hdl) throws IOException, ServletException {
        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher(UnknownApiServlet.class.getName());
        if (dispatcher != null) {
            dispatcher.forward(hdl.req, hdl.response);
            return true;
        }
        return false;
    }

    private boolean resolveRestfully(final HDLServletRequest hdl) throws IOException, ServletException {
        RequestDispatcher dispatcher = getServletContext().getNamedDispatcher(HandleJsonRestApiServlet.class.getName());
        if (dispatcher != null) {
            HttpServletRequest req = new HttpServletRequestWrapper(hdl.req) {
                private String requestURI;
                {
                    requestURI = super.getContextPath() + super.getServletPath();
                    if (!requestURI.endsWith("/")) requestURI += "/";
                    requestURI += StringUtils.encodeURLPath(hdl.hdl);
                }

                @Override
                public String getMethod() {
                    // POST on proxy can be treated as GET for REST API
                    String method = super.getMethod();
                    if (method.equalsIgnoreCase("GET") || method.equalsIgnoreCase("HEAD")) return method;
                    return "GET";
                }

                @Override
                public String getRequestURI() {
                    return requestURI;
                }
            };
            dispatcher.forward(req, hdl.response);
            return true;
        }
        return false;
    }

    /** Returns a browser redirect based on the queried values.  This is only
     * called if the 'noredirect' flag is not set.  This method should scan
     * the list of response handlers for the most appropriate one to handle
     * the response.
     */
    protected void doRedirect(HDLServletRequest hdl, HandleValue vals[]) throws IOException {
        // iterate over the handlers until one can handle the response
        for (TypeHandler handler : valueHandlers) {
            if (handler.canRedirect(vals)) {
                try {
                    if (handler.doRedirect(hdl, vals)) return;
                } catch (Exception e) {
                    if (e.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")) throw (IOException) e;
                    if (e.getClass().getName().equals("org.eclipse.jetty.io.EofException")) throw (IOException) e;
                    logError(RotatingAccessLog.ERRLOG_LEVEL_NORMAL, "Error showing redirect for '" + hdl.hdl + "': " + e);
                    returnErrorPage("Error showing redirect for '" + hdl.hdl + "': " + e.getMessage(), hdl, null, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    e.printStackTrace(System.err);
                    return;
                }
            }
        }

        // no handler returned a response, so return results page
        returnResponsePage(hdl, vals);
    }

    /** Displays a list of all locations to which this handle resolves.  This corresponds
     *  to the "action=show" URI parameter.
     */
    protected void doShowLocations(HDLServletRequest hdl, HandleValue vals[]) throws IOException {
        // iterate over the handlers until one can handle the response
        for (TypeHandler handler : valueHandlers) {
            if (handler.canShowLocations(vals)) {
                try {
                    XTag locations = handler.doShowLocations(hdl, vals);
                    if (locations != null && locations.getSubTagCount() > 0) {
                        // display a page showing the locations
                        hdl.response.setContentType("text/xml; charset=utf-8");
                        locations.write(hdl.response.getOutputStream());
                        return;
                    }
                } catch (Exception e) {
                    logError(RotatingAccessLog.ERRLOG_LEVEL_NORMAL, "Error showing locations for '" + hdl.hdl + "': " + e);
                    returnErrorPage("Error showing locations for '" + hdl.hdl + "': " + e.getMessage(), hdl, null, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }
            }
        }

        // no handler returned a response, so return results page
        returnResponsePage(hdl, vals);
    }

    protected void returnErrorPage(String msg, HDLServletRequest hdl, NamespaceInfo nsInfo, String trace, int statusCode) throws IOException {
        returnErrorPage(msg, hdl, nsInfo, null, trace, statusCode);
    }

    public static String getContextPath(HDLServletRequest hdl) {
        return StringUtils.cgiEscape(ServletUtil.pathMatching(hdl.req.getRequestURI(), hdl.req.getContextPath()));
    }

    private void returnValuesNotFoundPage(String msg, HDLServletRequest hdl, String trace) throws IOException {
        if (msg == null) msg = "Requested Values Not Found";
        if (trace == null) trace = "";

        HTMLFile f = null;
        try {
            f = valuesNotFoundPages.get(hdl.req.getServerName().toLowerCase());
        } catch (NullPointerException e) {
        }
        if (f == null) {
            f = valuesNotFoundPages.get("default");
        }
        if (f == null) { // no values-not-found page was found... resort to the error page
            returnErrorPage(msg, hdl, null, trace, HttpServletResponse.SC_OK);
            return;
        }

        synchronized (f) { // lame synchronization... should use a template here
            f.reset();
            f.setValue("CONTEXT_PATH", getContextPath(hdl));
            f.setValue("HANDLE_URL", hdl.getURLForHandle(hdl.hdl));
            f.setValue("HANDLE", hdl.hdl);
            f.setValue("ERROR", msg);
            f.setValue("REFERER", hdl.getReferer());
            f.setValue("TRACE", trace);

            hdl.response.setContentType("text/html; charset=utf-8");
            f.output(hdl.response.getOutputStream());
        }
    }

    protected void returnErrorPage(String msg, HDLServletRequest hdl, NamespaceInfo nsInfo, @SuppressWarnings("unused") String ref, String trace, int statusCode) throws IOException {
        hdl.response.setStatus(statusCode);

        if (msg == null) msg = "Unknown error.";
        if (trace == null) trace = "";

        HTMLFile f = null;
        try {
            f = errorPages.get(hdl.req.getServerName().toLowerCase());
        } catch (NullPointerException e) {
        }
        if (f == null) f = errorPages.get("default");
        if (f == null) {
            System.err.println("Error loading error page.");
            hdl.response.setContentType("text/plain");
            hdl.response.getWriter().println("Template page not found!\nError: " + msg);
            return;
        }

        synchronized (f) {
            f.reset();

            f.setValue("CONTEXT_PATH", getContextPath(hdl));

            f.setValue("SERVER_ERROR", hdl.exception != null && hdl.exception.getCode() == HandleException.CANNOT_CONNECT_TO_SERVER ? "Yes" : "No");

            boolean trailingSlash = hdl.hdl.endsWith("/");
            f.setValue("TRAILING_SLASH", trailingSlash ? "Yes" : "No");
            if (trailingSlash) {
                f.setValue("NOSLASH_HANDLE_URL", hdl.getURLForHandle(hdl.hdl.substring(0, hdl.hdl.length() - 1)));
            } else {
                f.setValue("NOSLASH_HANDLE_URL", hdl.getURLForHandle(hdl.hdl));
            }
            boolean prefixOnly = !hdl.hdl.contains("/") || hdl.hdl.indexOf('/') == hdl.hdl.length() - 1;
            f.setValue("PREFIX_ONLY", prefixOnly ? "Yes" : "No");

            if (nsInfo == null) nsInfo = DEFAULT_NAMESPACE_INFO;
            String contactAddr = getResponsiblePartyContactAddress(nsInfo, hdl);
            f.setValue("NS_CONTACT", contactAddr);
            f.setValue("DEFAULT_CONTACT", DEFAULT_CONTACT_EMAIL);
            if (!DEFAULT_CONTACT_EMAIL.equals(contactAddr)) {
                f.setValue("HAS_NS_CONTACT", "Yes");
            }

            String nsStatusMsg = getStatusMessage(hdl.hdl, nsInfo);
            if (nsStatusMsg != null && nsStatusMsg.trim().length() > 0) {
                f.setValue("HAS_NS_STATUS_MSG", "Yes");
                f.setValue("NS_STATUS_MSG", nsStatusMsg);
            }
            f.setValue("NS_STATUS", nsInfo.getNamespaceStatus());
            f.setValue("HANDLE", hdl.hdl);
            f.setValue("ERROR", msg);
            f.setValue("REFERER", hdl.getReferer());
            f.setValue("TRACE", trace);

            try {
                hdl.response.setContentType("text/html; charset=utf-8");
                f.output(hdl.response.getOutputStream());
            } catch (Throwable t) {
                System.err.println("Error sending response: " + t);
                t.printStackTrace();
            }
        }
    }

    private String getResponsiblePartyContactAddress(NamespaceInfo nsInfo, HDLServletRequest request) {
        try {
            String nsContactAddr = nsInfo.getResponsiblePartyContactAddress();
            if (nsContactAddr != null && !nsContactAddr.trim().isEmpty()) {
                return nsContactAddr.trim();
            }
            String prefix = Util.getZeroNAHandle(request.hdl);
            if ("0.NA/0.NA".equalsIgnoreCase(prefix)) {
                return DEFAULT_CONTACT_EMAIL;
            }
            HandleValue[] prefixValues;
            try {
                prefixValues = resolveHandle(prefix);
            } catch (HandleException e) {
                if (!Util.isSubNAHandle(prefix)) return DEFAULT_CONTACT_EMAIL;
                String prefixParent = getTopLevelPrefix(prefix);
                prefixValues = resolveHandle(prefixParent);
            }
            String prefixEmail = null;
            boolean usePrefixEmail = false;
            for (HandleValue value : prefixValues) {
                String valueType = value.getTypeAsString();
                // Email set on prefix is only used if issuer is 0.NA/0.NA.
                // In other cases, HS_NAMESPACE should be used to set prefix contact email.
                if ("EMAIL".equalsIgnoreCase(valueType)) {
                    prefixEmail = value.getDataAsString();
                }
                if ("HS_SIGNATURE".equalsIgnoreCase(valueType)) {
                    String issuer = getIssuerHandleFromSignatureValue(value);
                    if ("0.NA/0.NA".equalsIgnoreCase(issuer)) {
                        usePrefixEmail = true;
                    } else {
                        String email = getEmailFromHandleRecord(issuer);
                        if (email != null) return email.trim();
                    }
                }
            }
            if (usePrefixEmail && prefixEmail != null) {
                return prefixEmail.trim();
            }
            return DEFAULT_CONTACT_EMAIL;
        } catch (Exception e) {
            return DEFAULT_CONTACT_EMAIL;
        }
    }

    private String getIssuerHandleFromSignatureValue(HandleValue value) throws TrustException {
        JsonWebSignatureFactory factory = JsonWebSignatureFactory.getInstance();
        Gson gson = GsonUtility.getGson();
        String sig = value.getDataAsString();
        JsonWebSignature jws = factory.deserialize(sig);
        JwtClaimsSet claims = gson.fromJson(jws.getPayloadAsString(), JwtClaimsSet.class);
        return ValueReference.fromString(claims.iss).getHandleAsString();
    }

    private String getEmailFromHandleRecord(String issuer) throws HandleException {
        HandleValue[] issValues = resolveHandle(issuer);
        for (HandleValue issValue : issValues) {
            if ("EMAIL".equals(issValue.getTypeAsString())) {
                return issValue.getDataAsString();
            }
        }
        return null;
    }

    private String getTopLevelPrefix(String prefix) {
        while (Util.isSubNAHandle(prefix)) {
            prefix = Util.getParentNAOfNAHandle(prefix);
        }
        return prefix;
    }

    private HandleValue[] resolveHandle(String handle) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        ResolutionRequest resolutionRequest = new ResolutionRequest(handleBytes, null, null, null);
        // Since caller is null, this will not be logged.
        AbstractResponse response = resolver.processRequest(resolutionRequest, null);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, response.toString());
        }
        if (response instanceof ResolutionResponse) {
            ResolutionResponse resResponse = (ResolutionResponse) response;
            return resResponse.getHandleValues();
        } else {
            throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode));
        }
    }

    protected String getStatusMessage(@SuppressWarnings("unused") String hdl, NamespaceInfo nsInfo) {
        String msg = nsInfo.getStatusMessage();
        if (msg != null) return StringUtils.cgiEscape(msg.trim());
        return null;
    }

    protected void returnResponsePage(HDLServletRequest hdl, HandleValue vals[]) throws IOException {
        if (vals != null && vals.length <= 0) {
            returnValuesNotFoundPage(null, hdl, null);
            return;
        }

        HTMLFile f = responsePages.get(hdl.req.getServerName().toLowerCase());
        if (f == null) f = responsePages.get("default");

        StringBuffer page = new StringBuffer();
        if (vals != null && vals.length > 0) {
            page.append("<tr><td align=\"left\" valign=\"top\">Index</td>");
            page.append("<td align=\"left\" valign=\"top\">Type</td>");
            page.append("<td align=\"left\" valign=\"top\">Timestamp");
            page.append("</td><td align=\"left\" valign=\"top\">Data</td>");
            page.append("</tr>\n");
            for (int i = 0; i < vals.length; i++) {
                HandleValue val = vals[i];
                TypeHandler t = null;
                for (TypeHandler valueHandler : valueHandlers) {
                    if (valueHandler.canFormat(val)) {
                        t = valueHandler;
                        break;
                    }
                }
                String dataStr;
                if (t != null) dataStr = t.toHTML(hdl.hdl, val);
                else {
                    dataStr = val.getDataAsString();
                    if (looksLikeURI(dataStr)) {
                        dataStr = "<a href=\"" + StringUtils.encodeURLForAttr(dataStr) + "\">" + StringUtils.htmlEscapeWhitespace(dataStr) + "</a>";
                    } else dataStr = StringUtils.htmlEscapeWhitespace(dataStr);
                }

                page.append("<tr bgcolor=\"#" + (i % 2 == 0 ? "dddddd" : "ffffff") + "\">");
                page.append("<td align=\"left\" valign=\"top\"><b>");
                page.append(val.getIndex() + "</b>");
                page.append("</td><td align=\"left\" valign=\"top\"><b>");
                String typeAsStr = val.getTypeAsString();
                if (looksLikeURI(typeAsStr)) {
                    page.append("<a href=\"" + StringUtils.encodeURLForAttr(typeAsStr) + "\">" + StringUtils.htmlEscapeWhitespaceNonBreakingSpaces(typeAsStr) + "</a>");
                } else if (typeAsStr.indexOf("/") >= 0) {
                    page.append("<a href=\"" + StringUtils.encodeURLForAttr(hdl.getURLForHandle(typeAsStr)) + "\">" + StringUtils.htmlEscapeWhitespaceNonBreakingSpaces(typeAsStr) + "</a>");
                } else {
                    String ucType = typeAsStr.toUpperCase();
                    if (ucType.startsWith("HS_") || ucType.equals("URL") || ucType.equals("DESC") || ucType.equals("EMAIL")) {
                        page.append("<a href=\"" + StringUtils.encodeURLForAttr(hdl.getURLForHandle("0.TYPE/" + typeAsStr)) + "\">" + StringUtils.htmlEscapeWhitespaceNonBreakingSpaces(typeAsStr) + "</a>");
                    } else page.append(StringUtils.htmlEscapeWhitespaceNonBreakingSpaces(typeAsStr));
                }
                page.append("</b></td><td valign=\"top\">");
                page.append("<span style='white-space:nowrap'>");
                page.append(StringUtils.htmlEscapeWhitespaceNonBreakingSpaces(val.getNicerTimestampAsString()));
                page.append("</span>");
                page.append("</td>\n");
                page.append("<td>" + dataStr + "</td>");
                page.append("</tr>\n");
            }
        } else {
            page.append("<tr><td align=CENTER><b>No values found.</b></td></tr>");
        }

        if (f != null) {
            synchronized (f) {
                f.reset();
                f.setValue("CONTEXT_PATH", getContextPath(hdl));
                f.setValue("HANDLE", hdl.hdl);
                f.setValue("VALUES", page.toString());

                hdl.response.setContentType("text/html; charset=utf-8");
                f.output(hdl.response.getOutputStream());
            }
        }
    }

    private void returnQueryPage(HDLServletRequest hdl) throws IOException {
        HTMLFile f = queryPages.get(hdl.req.getServerName().toLowerCase());
        if (f == null) f = queryPages.get("default");
        if (f == null) {
            returnErrorPage("Empty handle invalid.", hdl, null, null, HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        hdl.response.setContentType("text/html; charset=utf-8");
        synchronized (f) {
            f.reset();
            f.setValue("CONTEXT_PATH", getContextPath(hdl));
            f.output(hdl.response.getOutputStream());
        }
    }

    private void returnHelpPage(HDLServletRequest hdl) throws IOException {
        String redirect = helpRedirect.get(hdl.req.getServerName().toLowerCase());
        if (redirect == null) redirect = helpRedirect.get("default");
        if (redirect != null) {
            hdl.sendHTTPRedirect(ResponseType.OLD_MOVED_TEMPORARILY, redirect);
            return;
        }

        HTMLFile f = helpPages.get(hdl.req.getServerName().toLowerCase());
        if (f == null) f = helpPages.get("default");
        if (f == null) {
            returnErrorPage("help.html not found!", hdl, null, null, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        hdl.response.setContentType("text/html; charset=utf-8");
        synchronized (f) {
            f.reset();
            f.setValue("CONTEXT_PATH", getContextPath(hdl));
            f.output(hdl.response.getOutputStream());
        }
    }

    public static final boolean looksLikeURI(String str) {
        if (str == null) return false;
        int sz = str.length();
        if (sz == 0) return false;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (ch == ' ' || ch == '\r' || ch == '\t' || ch == '\n') return false;
        }
        char ch = str.charAt(0);
        if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z') {
            for (int j = 1; j < sz; j++) {
                ch = str.charAt(j);
                if (ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '+' || ch == '-' || ch == '.') continue;
                if (ch == ':') return true;
                break;
            }
        }
        return false;
    }

    public void logAccess(String accessType, int oc, int rc, HDLServletRequest req, HandleValue vals[]) {
        logAccess(accessType, oc, rc, req, vals, null);
    }

    public void logAccess(String accessType, int oc, int rc, HDLServletRequest req, HandleValue vals[], String extraLogEntry) {
        if (logger == null) return;
        String addr = "";
        try {
            addr = req.getRemoteAddr();
        } catch (Throwable t) {
        }
        String referer = req.getReferer();
        String userAgent = req.req.getHeader("user-agent");
        logAccess(accessType, oc, rc, req.hdl, addr, referer, userAgent, req.getResponseTime(), vals, extraLogEntry);
    }

    public void logAccess(String accessType, int oc, int rc, String hdl, String addr, String referer, String userAgent, long responseTime, HandleValue vals[], String extraLogEntry) {
        if (logger == null) return;

        StringBuffer msg = new StringBuffer(50);
        msg.append(addr == null ? "" : addr);
        msg.append(' ');
        msg.append(accessType);
        msg.append(" \"");
        String dateStr = dateFormat.formatNow();
        msg.append(dateStr);
        msg.append("\" ");
        msg.append(oc);
        msg.append(' ');
        msg.append(rc);
        msg.append(' ');
        msg.append(responseTime);
        msg.append("ms ");
        //    msg.append("ms  "); // matches HSv8 handle server logs
        msg.append(StringUtils.encodeURLPath(hdl));

        if (logHSAdmin) {
            msg.append(" \"");
            boolean firstAdmin = true;
            for (int i = 0; vals != null && i < vals.length; i++) {
                if (!vals[i].hasType(Common.STD_TYPE_HSADMIN)) {
                    continue;
                }
                AdminRecord adm = new AdminRecord();
                try {
                    Encoder.decodeAdminRecord(vals[i].getData(), 0, adm);
                } catch (Exception e) {
                    continue;
                }
                if (!firstAdmin) msg.append(',');
                firstAdmin = false;

                msg.append(adm.adminIdIndex);
                msg.append(':');
                msg.append(StringUtils.encodeURLPath(Util.decodeString(adm.adminId)));
            }
            msg.append('"');
        }

        // log the referrer
        if (logReferrer) {
            msg.append(" \"");
            if (referer != null) msg.append(StringUtils.encodeURL(referer));
            msg.append('"');
        }

        String extraLogStr = null;
        if (extraLogEntry != null || (logUserAgent && userAgent != null)) {
            if (logUserAgent && userAgent != null) {
                if (extraLogEntry == null) extraLogEntry = "";
                else extraLogEntry += " ";
                extraLogEntry += "user-agent:\"" + quote(userAgent) + "\"";
            }
            extraLogStr = "\"" + dateStr + "\" " + extraLogEntry;
        }
        logger.logAccessAndExtra(msg.toString(), extraLogStr);
    }

    public void logError(int level, String logString) {
        if (handleServer == null) {
            if (logger != null) logger.logError(level, logString);
        } else {
            handleServer.logError(level, logString);
        }
    }

    // disused except for user-agent, prefer URL encoding
    static String quote(String s) {
        if (s == null) return null;
        StringBuilder sb = null;
        int sLen = s.length();
        char ch;
        for (int i = 0; i < sLen; i++) {
            ch = s.charAt(i);
            if (ch == '\\') {
                if (sb == null) sb = new StringBuilder(s.substring(0, i));
                sb.append("\\\\");
            } else if (ch == '\"') {
                if (sb == null) sb = new StringBuilder(s.substring(0, i));
                sb.append("\\\"");
            } else if (ch < ' ') {
                if (sb == null) sb = new StringBuilder(s.substring(0, i));
                sb.append("\\u").append(String.format("%04X", Integer.valueOf(ch)));
            } else if (sb != null) {
                sb.append(ch);
            }
        }
        if (sb == null) return s;
        return sb.toString();
    }

    public String getHandleLinkPrefix(HttpServletRequest req) {
        String prefix = handleLinkPrefixMap.get(req.getServerName().toLowerCase());
        if (prefix == null) prefix = handleLinkPrefixMap.get("default");
        if (prefix == null) {
            int port = req.getServerPort();
            boolean usePort = true;
            if ("http".equalsIgnoreCase(req.getScheme()) && port == 80) usePort = false;
            if ("https".equalsIgnoreCase(req.getScheme()) && port == 443) usePort = false;
            prefix = req.getScheme() + "://" + req.getServerName() + (usePort ? ":" + port : "") + req.getContextPath() + "/";
        }
        return prefix;
    }

    public InetAddress getRemoteInetAddress(HttpServletRequest servletReq) {
        InetAddress cachedResult = (InetAddress) servletReq.getAttribute("cachedRemoteInetAddress");
        if (cachedResult != null) return cachedResult;
        try {
            InetAddress result = InetAddress.getByName(getRemoteAddr(servletReq));
            servletReq.setAttribute("cachedRemoteInetAddress", result);
            return result;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public String getRemoteAddr(HttpServletRequest servletReq) {
        String cachedResult = (String) servletReq.getAttribute("cachedRemoteAddr");
        if (cachedResult != null) return cachedResult;
        String result = getRemoteAddrNoCaching(servletReq);
        servletReq.setAttribute("cachedRemoteAddr", result);
        return result;
    }

    private String getRemoteAddrNoCaching(HttpServletRequest servletReq) {
        String remoteAddr = servletReq.getRemoteAddr();
        if (remoteAddressHeader == null) return remoteAddr;
        String headerValue = getConcatenatedHeaderValue(servletReq, remoteAddressHeader);
        if (headerValue == null || headerValue.isEmpty()) return remoteAddr;
        List<String> proxies = listValuesFromHeaderAndRemoteAddr(headerValue, remoteAddr);
        if (remoteAddressInternalProxies == null || remoteAddressInternalProxies.isEmpty()) {
            // trust all
            return proxies.get(0);
        }
        Collections.reverse(proxies);
        for (String potentialProxy : proxies) {
            remoteAddr = potentialProxy;
            if (!isProxy(potentialProxy)) return remoteAddr;
        }
        // return innermost
        return remoteAddr;
    }

    private boolean isProxy(String potentialProxy) {
        if (remoteAddressInternalProxies == null || remoteAddressInternalProxies.isEmpty()) return true;
        try {
            BigInteger target = CIDRUtils.asBigInteger(potentialProxy);
            for (CIDRUtils proxyRange : remoteAddressInternalProxies) {
                if (proxyRange.isInRange(target)) return true;
            }
            return false;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String getConcatenatedHeaderValue(HttpServletRequest servletReq, String header) {
        Enumeration<String> e = servletReq.getHeaders(header);
        if (!e.hasMoreElements()) return null;
        StringBuilder sb = new StringBuilder();
        while (e.hasMoreElements()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.nextElement());
        }
        return sb.toString();
    }

    private static final Pattern commaSeparatedValuesPattern = Pattern.compile("\\s*,\\s*");

    private static List<String> listValuesFromHeaderAndRemoteAddr(String headerValue, String remoteAddr) {
        String[] addrs = commaSeparatedValuesPattern.split(headerValue);
        List<String> res = new ArrayList<>(addrs.length + 1);
        for (String addr : addrs) {
            if (!addr.isEmpty()) res.add(addr);
        }
        res.add(remoteAddr);
        return res;
    }

}

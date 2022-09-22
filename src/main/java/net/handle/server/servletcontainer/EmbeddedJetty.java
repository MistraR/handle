/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;

import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.SSLEngineHelper;
import net.handle.server.servletcontainer.EmbeddedJettyConfig.ConnectorConfig;
import net.handle.util.X509HSTrustManager;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.deploy.App;
import org.eclipse.jetty.deploy.DeploymentManager;
import org.eclipse.jetty.deploy.providers.WebAppProvider;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.AliasedX509ExtendedKeyManager;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.xml.XmlConfiguration;

public class EmbeddedJetty {

    private Server httpServer;
    private ContextHandlerCollection contexts;
    private DeploymentManager priorityDeploymentManager;
    private DeploymentManager deploymentManager;
    private final EmbeddedJettyConfig config;
    private File baseDir = null;
    private File webAppsBaseStorageDir = null;
    private File webAppsTempDir = null;
    private File webAppsDir = null;
    private File webAppsPriorityDir = null;
    private File jettyConfigFile = null;
    private HandleResolver sharedResolver = null;

    private static SecureRandom srand = null;
    private static Object RANDOM_LOCK = new Object();
    public static final String[] HTTPS_KEY_STORE_FILE_NAMES = { "https.jks", "https.keystore", "https.key" };

    public EmbeddedJetty(EmbeddedJettyConfig config) {
        this.config = config;
        this.baseDir = config.getBaseDir();

        if (config.getWebAppsPath() != null) {
            webAppsDir = new File(config.getWebAppsPath());
        } else {
            webAppsDir = new File(baseDir, "webapps");
        }

        if (config.getWebAppsPriorityPath() != null) {
            webAppsPriorityDir = new File(config.getWebAppsPriorityPath());
        } else {
            webAppsPriorityDir = new File(baseDir, "webapps-priority");
        }

        if (config.getWebAppsTempPath() != null) {
            webAppsTempDir = new File(config.getWebAppsTempPath());
        } else {
            webAppsTempDir = new File(baseDir, "webapps-temp");
        }

        if (config.getWebAppsStoragePath() != null) {
            webAppsBaseStorageDir = new File(config.getWebAppsStoragePath());
        } else {
            webAppsBaseStorageDir = new File(baseDir, "webapps-storage");
        }

        if (config.getJettyXmlPath() != null) {
            jettyConfigFile = new File(config.getJettyXmlPath());
        } else {
            jettyConfigFile = new File(baseDir, "jetty.xml");
        }
    }

    public void setUpHttpServer() throws Exception {
        turnOffJetty8AnnotationParserWarnings();
        if (config.isEnableDefaultHttpConfig()) {
            if (config.getConnectors().size() > 0) {
                httpServer = new Server();
                for (ConnectorConfig connectorConfig : config.getConnectors()) {
                    addConnector(connectorConfig);
                }
                contexts = new OverridingContextHandlerCollection();
                //priorityDeploymentManager = setUpDeploymentManager("webapps-priority");
                priorityDeploymentManager = setUpDeploymentManager(webAppsPriorityDir);
                if (priorityDeploymentManager != null) httpServer.addBean(priorityDeploymentManager, true);
                //deploymentManager = setUpDeploymentManager("webapps");
                deploymentManager = setUpDeploymentManager(webAppsDir);
                HashLoginService loginService = new HashLoginService("default");
                loginService.setConfig(new File(baseDir, "realm.properties").getAbsolutePath());
                httpServer.addBean(loginService);

                for (Handler handler : config.getDefaultHandlers()) {
                    contexts.addHandler(handler);
                }
                httpServer.setHandler(contexts);
                httpServer.setGracefulShutdown(3000);
                httpServer.setStopAtShutdown(false);
            }
        } else {
            httpServer = new Server();
        }
        if (httpServer != null) {
            httpServer.setSendServerVersion(false);

            if (jettyConfigFile.exists()) {
                try (FileInputStream in = new FileInputStream(jettyConfigFile)) {
                    new XmlConfiguration(in).configure(httpServer);
                }
            }
        }
    }

    private void turnOffJetty8AnnotationParserWarnings() {
        org.eclipse.jetty.util.log.Logger logger = org.eclipse.jetty.util.log.Log.getLogger(org.eclipse.jetty.annotations.AnnotationParser.class);
        if (logger instanceof org.eclipse.jetty.util.log.StdErrLog) {
            ((org.eclipse.jetty.util.log.StdErrLog) logger).setLevel(org.eclipse.jetty.util.log.StdErrLog.LEVEL_WARN + 1);
        }
    }

    public void startPriorityDeploymentManager() throws Exception {
        if (priorityDeploymentManager != null) {
            priorityDeploymentManager.start();
        }
    }

    public void startHttpServer() throws Exception {
        if (httpServer != null) {
            httpServer.start();
            if (deploymentManager != null) httpServer.addBean(deploymentManager, true);
        }
    }

    public void join() throws InterruptedException {
        if (httpServer != null) httpServer.join();
    }

    public void stopHttpServer() {
        if (httpServer != null) {
            if (deploymentManager != null && priorityDeploymentManager != null) {
                deploymentManager.undeployAll();
            }
            try {
                httpServer.stop();
                httpServer.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            httpServer = null;
        }
    }

    public void addConnector(ConnectorConfig connectorConfig) throws Exception {
        KeyManager[] keyManagers;
        if (connectorConfig.isHttpsUseSelfSignedCert() || connectorConfig.getHttpsKeyStoreFile() == null) {
            if (connectorConfig.getHttpsCertificateChain() != null) {
                keyManagers = new KeyManager[] { new net.handle.util.AutoSelfSignedKeyManager(connectorConfig.getHttpsId(), connectorConfig.getHttpsCertificateChain(), connectorConfig.getHttpsPrivKey()) };
            } else if (connectorConfig.getHttpsPubKey() != null && connectorConfig.getHttpsPrivKey() != null) {
                keyManagers = new KeyManager[] { new net.handle.util.AutoSelfSignedKeyManager(connectorConfig.getHttpsId(), connectorConfig.getHttpsPubKey(), connectorConfig.getHttpsPrivKey()) };
            } else {
                keyManagers = new KeyManager[] { new net.handle.util.AutoSelfSignedKeyManager(connectorConfig.getHttpsId()) };
            }
        } else {
            String keystorePassStr = connectorConfig.getHttpsKeyStorePassword();
            char keystorePass[] = keystorePassStr == null ? null : keystorePassStr.toCharArray();
            String keyPassStr = connectorConfig.getHttpsKeyPassword();
            char keyPass[] = keyPassStr == null ? new char[0] : keyPassStr.toCharArray();
            KeyStore httpsKeyStore = KeyStore.getInstance("JKS");
            File keystoreFile = getKeystoreFile(connectorConfig.getHttpsKeyStoreFile());
            try (FileInputStream in = new FileInputStream(keystoreFile)) {
                httpsKeyStore.load(in, keystorePass);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(httpsKeyStore, keyPass);
            keyManagers = getKeyManagers(kmf, connectorConfig.getHttpsAlias());
        }
        SSLContext sslContext = null;
        sslContext = SSLContext.getInstance("TLS");
        HandleResolver resolver = config.getResolver();
        if (resolver == null) {
            resolver = this.sharedResolver;
            if (resolver == null) {
                this.sharedResolver = new HandleResolver();
                resolver = this.sharedResolver;
            }
        }
        sslContext.init(keyManagers, new TrustManager[] { new X509HSTrustManager(resolver) }, getRandom());
        SslContextFactory sslContextFactory = new SslContextFactory();
        String clientAuth = connectorConfig.getHttpsClientAuth();
        sslContextFactory.setWantClientAuth(true);
        if ("need".equalsIgnoreCase(clientAuth) || "true".equalsIgnoreCase(clientAuth)) sslContextFactory.setNeedClientAuth(true);
        // else if("want".equalsIgnoreCase(clientAuth)) sslContextFactory.setWantClientAuth(true);
        else if ("false".equalsIgnoreCase(clientAuth)) sslContextFactory.setWantClientAuth(false);
        sslContextFactory.setSslContext(sslContext);
        sslContextFactory.setIncludeCipherSuites(SSLEngineHelper.COMPATIBILITY_CIPHER_SUITES);
        sslContextFactory.addExcludeProtocols("SSLv3");
        SelectChannelConnector connector;
        if (connectorConfig.isHttps()) connector = new SslSelectChannelConnector(sslContextFactory);
        else if (connectorConfig.isHttpOnly()) connector = new SelectChannelConnector();
        else connector = new PortUnificationSelectChannelConnector(sslContextFactory);
        connector.setPort(connectorConfig.getPort());
        InetAddress httpsListenAddress = connectorConfig.getListenAddress();
        if (httpsListenAddress != null) connector.setHost(httpsListenAddress.getHostAddress());
        connector.setMaxIdleTime(30000);
        connector.setRequestHeaderSize(8192);
        if (connectorConfig.getRedirectPort() > 0) {
            connector.setConfidentialPort(connectorConfig.getRedirectPort());
            connector.setIntegralPort(connectorConfig.getRedirectPort());
        } else if (!connectorConfig.isHttps() && !connectorConfig.isHttpOnly()) {
            connector.setConfidentialPort(connectorConfig.getPort());
            connector.setIntegralPort(connectorConfig.getPort());
        }
        httpServer.addConnector(connector);
    }

    public void addWebApp(File war, String contextPath) throws Exception {
        WebAppContext handler = new WebAppContext(war.getAbsolutePath(), contextPath);
        setUpWebAppContext(handler);
        for (String attributeName : config.getContextAttributes().keySet()) {
            handler.setAttribute(attributeName, config.getContextAttributes().get(attributeName));
        }
        handler.setExtractWAR(true);
        String warDirName = contextPath.substring(1);
        handler.setTempDirectory(new File(new File(baseDir, "webapps-temp"), warDirName));
        ContextHandlerCollection handlers = (ContextHandlerCollection) httpServer.getHandler();
        handlers.addHandler(handler);
        handler.start();
    }

    private DeploymentManager setUpDeploymentManager(File webAppsDir) {
        // return handlers in reverse order in order to allow later entry to override
        //File webAppsDir = new File(baseDir, filename);
        //if((webAppsDir.exists() && webAppsDir.isDirectory()) || webAppsDir.mkdirs()) {
        if (!webAppsDir.exists() || webAppsDir.isDirectory()) {
            WebAppProvider webAppProvider = new EmbeddedWebAppProvider();
            webAppProvider.setMonitoredDirName(webAppsDir.getAbsolutePath());
            webAppProvider.setParentLoaderPriority(false);
            webAppProvider.setExtractWars(true);
            //File webAppsTempDir = new File(baseDir, "webapps-temp");
            //webAppsTempDir.mkdirs();
            webAppProvider.setTempDir(webAppsTempDir);
            webAppProvider.setScanInterval(10);
            DeploymentManager result = new DeploymentManager();
            result.setContexts(contexts);
            for (String attributeName : config.getContextAttributes().keySet()) {
                result.setContextAttribute(attributeName, config.getContextAttributes().get(attributeName));
            }
            result.addAppProvider(webAppProvider);
            return result;
        } else {
            return null;
        }
    }

    private static KeyManager[] getKeyManagers(KeyManagerFactory kmf, String alias) throws Exception {
        KeyManager[] res = kmf.getKeyManagers();
        if (alias == null || res == null) return res;
        for (int i = 0; i < res.length; i++) {
            if (res[i] instanceof X509KeyManager) res[i] = new AliasedX509ExtendedKeyManager(alias, (X509KeyManager) (res[i]));
        }
        return res;
    }

    private File getKeystoreFile(String filename) {
        if (filename != null) {
            File res = new File(filename);
            if (res.isAbsolute()) return res;
            else return new File(baseDir, filename);
        } else {
            for (String name : HTTPS_KEY_STORE_FILE_NAMES) {
                File res = new File(baseDir, name);
                if (res.exists()) return res;
            }
            return null;
        }
    }

    private class EmbeddedWebAppProvider extends WebAppProvider {
        @Override
        public ContextHandler createContextHandler(App app) throws Exception {
            getTempDir().mkdirs();
            WebAppContext res = (WebAppContext) super.createContextHandler(app);
            File webAppStorageDir = getWebAppStorageDirForContextPath(res.getContextPath());
            res.setAttribute("net.handle.server.webapp_storage_directory", webAppStorageDir);
            setUpWebAppContext(res);
            return res;
        }

        private File getWebAppStorageDirForContextPath(String contextPath) {
            File result = null;
            String webAppName = null;
            if ("/".equals(contextPath)) {
                webAppName = "root";
            } else if (contextPath.startsWith("/")) {
                webAppName = contextPath.substring(1);
            } else {
                webAppName = contextPath;
            }
            if (webAppName != null) {
                result = new File(webAppsBaseStorageDir, webAppName);
                result.mkdirs();
            }
            return result;
        }
    }

    private void setUpWebAppContext(WebAppContext res) {
        res.setSystemClasses(removeFromArray(res.getSystemClasses(), "org.apache.commons.logging.")); // not needed since we use jcl-over-slf4j
        res.setServerClasses(prependToArray(res.getServerClasses(), "-org.eclipse.jetty.servlets."));
        for (String systemClass : config.getSystemClasses()) {
            res.addSystemClass(systemClass);
        }
        for (String serverClass : config.getServerClasses()) {
            res.addServerClass(serverClass);
        }
        res.setConfigurationClasses(addToArray(res.getConfigurationClasses(), AnnotationConfiguration.class.getName()));
        // res.setAllowNullPathInfo(true);
        res.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");

    }

    private static class OverridingContextHandlerCollection extends ContextHandlerCollection {
        @Override
        public void addHandler(Handler handler) {
            Handler[] handlers = getHandlers();
            if (handlers == null) setHandlers(new Handler[] { handler });
            else {
                Handler[] newHandlers = new Handler[handlers.length + 1];
                newHandlers[0] = handler;
                System.arraycopy(handlers, 0, newHandlers, 1, handlers.length);
                setHandlers(newHandlers);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] prependToArray(T[] array, T item) {
        List<T> list = new ArrayList<>();
        list.add(item);
        list.addAll(java.util.Arrays.asList(array));
        return list.toArray((T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), array.length + 1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] addToArray(T[] array, T item) {
        List<T> list = new ArrayList<>(java.util.Arrays.asList(array));
        list.add(item);
        return list.toArray((T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), array.length + 1));
    }

    @SuppressWarnings("unchecked")
    private static <T> T[] removeFromArray(T[] array, T item) {
        List<T> list = new ArrayList<>(java.util.Arrays.asList(array));
        int len = array.length;
        if (list.remove(item)) len--;
        return list.toArray((T[]) java.lang.reflect.Array.newInstance(array.getClass().getComponentType(), len));
    }

    /** Return a singleton SecureRandom object. */
    public static final SecureRandom getRandom() {
        if (srand != null) return srand;
        synchronized (RANDOM_LOCK) {
            if (srand != null) return srand;
            srand = new SecureRandom();
            srand.setSeed(srand.generateSeed(10));
        }
        return srand;
    }
}

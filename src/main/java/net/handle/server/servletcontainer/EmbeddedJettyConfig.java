/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.io.File;
import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Handler;

import net.handle.hdllib.HandleResolver;

public class EmbeddedJettyConfig {
    private File baseDir = null;
    private String webAppsPriorityPath = null;
    private String webAppsPath = null;
    private String webAppsStoragePath = null;
    private String webAppsTempPath = null;
    private String jettyXmlPath = null;

    private boolean enableDefaultHttpConfig = true;

    private final ConnectorConfig httpConnectorConfig = new ConnectorConfig();
    private final ConnectorConfig httpsConnectorConfig = new ConnectorConfig();
    {
        httpsConnectorConfig.setHttps(true);
    }
    private final List<ConnectorConfig> connectors = new ArrayList<>();

    private final List<Handler> defaultHandlers = new ArrayList<>();

    private List<String> systemClasses = new ArrayList<>();
    private List<String> serverClasses = new ArrayList<>();
    private Map<String, Object> contextAttributes = new HashMap<>();
    private HandleResolver resolver = null;

    private void addConnectorIfMissing(ConnectorConfig connectorConfig) {
        if (!connectors.contains(connectorConfig)) connectors.add(connectorConfig);
    }

    @Deprecated
    public void setHttpPort(int httpPort) {
        httpConnectorConfig.setPort(httpPort);
        addConnectorIfMissing(httpConnectorConfig);
    }

    @Deprecated
    public void setHttpListenAddress(InetAddress httpListenAddress) {
        httpConnectorConfig.setListenAddress(httpListenAddress);
    }

    @Deprecated
    public void setHttpsPort(int httpsPort) {
        httpsConnectorConfig.setPort(httpsPort);
        httpConnectorConfig.setRedirectPort(httpsPort);
        addConnectorIfMissing(httpsConnectorConfig);
    }

    @Deprecated
    public void setHttpsListenAddress(InetAddress httpsListenAddress) {
        httpsConnectorConfig.setListenAddress(httpsListenAddress);
    }

    @Deprecated
    public void setHttpsKeyPassword(String httpsKeyPassword) {
        httpsConnectorConfig.setHttpsKeyPassword(httpsKeyPassword);
    }

    @Deprecated
    public void setHttpsAlias(String httpsAlias) {
        httpsConnectorConfig.setHttpsAlias(httpsAlias);
    }

    @Deprecated
    public void setUseSelfSignedCert(boolean useSelfSignedCert) {
        httpsConnectorConfig.setHttpsUseSelfSignedCert(useSelfSignedCert);
    }

    @Deprecated
    public void setHttpsKeyStorePassword(String httpsKeyStorePassword) {
        httpsConnectorConfig.setHttpsKeyStorePassword(httpsKeyStorePassword);
    }

    @Deprecated
    public void setHttpsKeyStoreFile(String httpsKeyStoreFile) {
        httpsConnectorConfig.setHttpsKeyStoreFile(httpsKeyStoreFile);
    }

    @Deprecated
    public void setHttpsClientAuth(String httpsClientAuth) {
        httpsConnectorConfig.setHttpsClientAuth(httpsClientAuth);
    }

    @Deprecated
    public void setId(String id) {
        httpsConnectorConfig.setHttpsId(id);
    }

    @Deprecated
    public void setPubKey(PublicKey pubKey) {
        httpsConnectorConfig.setHttpsPubKey(pubKey);
    }

    @Deprecated
    public void setPrivKey(PrivateKey privKey) {
        httpsConnectorConfig.setHttpsPrivKey(privKey);
    }

    public boolean isEnableDefaultHttpConfig() {
        return enableDefaultHttpConfig;
    }

    public void setEnableDefaultHttpConfig(boolean enableDefaultHttpConfig) {
        this.enableDefaultHttpConfig = enableDefaultHttpConfig;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    public String getWebAppsPriorityPath() {
        return webAppsPriorityPath;
    }

    public void setWebAppsPriorityPath(String webAppsPriorityPath) {
        this.webAppsPriorityPath = webAppsPriorityPath;
    }

    public String getWebAppsPath() {
        return webAppsPath;
    }

    public void setWebAppsPath(String webAppsPath) {
        this.webAppsPath = webAppsPath;
    }

    public String getWebAppsStoragePath() {
        return webAppsStoragePath;
    }

    public void setWebAppsStoragePath(String webAppsStoragePath) {
        this.webAppsStoragePath = webAppsStoragePath;
    }

    public String getWebAppsTempPath() {
        return webAppsTempPath;
    }

    public void setWebAppsTempPath(String webAppsTempPath) {
        this.webAppsTempPath = webAppsTempPath;
    }

    public String getJettyXmlPath() {
        return jettyXmlPath;
    }

    public void setJettyXmlPath(String jettyXmlPath) {
        this.jettyXmlPath = jettyXmlPath;
    }

    public List<String> getSystemClasses() {
        return systemClasses;
    }

    public void setSystemClasses(List<String> systemClasses) {
        this.systemClasses = systemClasses;
    }

    public void addSystemClass(String systemClass) {
        systemClasses.add(systemClass);
    }

    public List<String> getServerClasses() {
        return serverClasses;
    }

    public void setServerClasses(List<String> serverClasses) {
        this.serverClasses = serverClasses;
    }

    public void addServerClass(String serverClass) {
        serverClasses.add(serverClass);
    }

    public Map<String, Object> getContextAttributes() {
        return contextAttributes;
    }

    public void setContextAttributes(Map<String, Object> contextAttributes) {
        this.contextAttributes = contextAttributes;
    }

    public void addContextAttribute(String key, Object value) {
        contextAttributes.put(key, value);
    }

    public HandleResolver getResolver() {
        return resolver;
    }

    public void setResolver(HandleResolver resolver) {
        this.resolver = resolver;
    }

    public List<Handler> getDefaultHandlers() {
        return defaultHandlers;
    }

    public void addDefaultHandler(Handler handler) {
        defaultHandlers.add(handler);
    }

    public List<ConnectorConfig> getConnectors() {
        return connectors;
    }

    public void addConnector(ConnectorConfig connectorConfig) {
        connectors.add(connectorConfig);
    }

    public static class ConnectorConfig {
        private boolean https;
        private boolean httpOnly;
        private int port;
        private InetAddress listenAddress;
        private int redirectPort;
        private boolean httpsUseSelfSignedCert;
        private String httpsKeyStorePassword;
        private String httpsKeyPassword;
        private String httpsKeyStoreFile;
        private String httpsAlias;
        private String httpsId;
        private PublicKey httpsPubKey;
        private PrivateKey httpsPrivKey;
        private X509Certificate[] httpsCertificateChain;
        private String httpsClientAuth; // "need" or "want" or "false"

        public boolean isHttps() {
            return https;
        }

        public void setHttps(boolean https) {
            this.https = https;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getRedirectPort() {
            return redirectPort;
        }

        public void setRedirectPort(int port) {
            this.redirectPort = port;
        }

        public InetAddress getListenAddress() {
            return listenAddress;
        }

        public void setListenAddress(InetAddress listenAddress) {
            this.listenAddress = listenAddress;
        }

        public boolean isHttpsUseSelfSignedCert() {
            return httpsUseSelfSignedCert;
        }

        public void setHttpsUseSelfSignedCert(boolean httpsUseSelfSignedCert) {
            this.httpsUseSelfSignedCert = httpsUseSelfSignedCert;
        }

        public String getHttpsKeyStorePassword() {
            return httpsKeyStorePassword;
        }

        public void setHttpsKeyStorePassword(String httpsKeyStorePassword) {
            this.httpsKeyStorePassword = httpsKeyStorePassword;
        }

        public String getHttpsKeyPassword() {
            return httpsKeyPassword;
        }

        public void setHttpsKeyPassword(String httpsKeyPassword) {
            this.httpsKeyPassword = httpsKeyPassword;
        }

        public String getHttpsKeyStoreFile() {
            return httpsKeyStoreFile;
        }

        public void setHttpsKeyStoreFile(String httpsKeyStoreFile) {
            this.httpsKeyStoreFile = httpsKeyStoreFile;
        }

        public String getHttpsAlias() {
            return httpsAlias;
        }

        public void setHttpsAlias(String httpsAlias) {
            this.httpsAlias = httpsAlias;
        }

        public String getHttpsClientAuth() {
            return httpsClientAuth;
        }

        public void setHttpsClientAuth(String httpsClientAuth) {
            this.httpsClientAuth = httpsClientAuth;
        }

        public String getHttpsId() {
            return httpsId;
        }

        public void setHttpsId(String httpsId) {
            this.httpsId = httpsId;
        }

        public PublicKey getHttpsPubKey() {
            return httpsPubKey;
        }

        public void setHttpsPubKey(PublicKey httpsPubKey) {
            this.httpsPubKey = httpsPubKey;
        }

        public PrivateKey getHttpsPrivKey() {
            return httpsPrivKey;
        }

        public void setHttpsPrivKey(PrivateKey httpsPrivKey) {
            this.httpsPrivKey = httpsPrivKey;
        }

        public X509Certificate[] getHttpsCertificateChain() {
            return this.httpsCertificateChain;
        }

        public void setHttpsCertificateChain(X509Certificate[] httpsCertificateChain) {
            this.httpsCertificateChain = httpsCertificateChain;
        }
    }
}

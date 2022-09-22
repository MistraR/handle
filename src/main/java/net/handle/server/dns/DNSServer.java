/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.dns;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Vector;

import net.cnri.util.StreamTable;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.HSG;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.server.AbstractServer;
import net.handle.server.Main;
import net.handle.server.NetworkInterface;
import net.handle.server.ServerLog;

/** Standalone DNS Server. */
public class DNSServer extends Main {
    public DNSServer(StreamTable configTable) throws Exception {
        super(new File("."), configTable);
        this.configTable = configTable;
    }

    @Override
    public void dumpFromPrimary(boolean deleteAll) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() throws Exception {
        if (server != null) {
            throw new HandleException(HandleException.SERVER_ERROR, "Server has already been initialized");
        }

        resolver = new HandleResolver();
        if (configTable.containsKey("tcp_timeout")) {
            int timeout = Integer.parseInt((String) configTable.get("tcp_timeout"));
            this.resolver.setTcpTimeout(timeout);
        }

        this.resolver.setCheckSignatures(true);
        this.resolver.traceMessages = configTable.getBoolean("trace_resolution") || configTable.getBoolean("trace_outgoing_messages");

        server = new Server((StreamTable) configTable.get(AbstractServer.HDLSVR_CONFIG), resolver);

        dnsConfig = new DnsConfiguration(server, (StreamTable) configTable.get(HSG.DNS_CONFIG));

        interfaces = new Vector<>(2);
        interfaces.add(NetworkInterface.getInstance(this, NetworkInterface.INTFC_DNSUDP, configTable));
        interfaces.add(NetworkInterface.getInstance(this, NetworkInterface.INTFC_DNSTCP, configTable));
    }

    @Override
    public void start() {
        for (NetworkInterface interfc : interfaces) {
            Thread t = new Thread(interfc);
            t.start();
        }
    }

    @Override
    public void shutdown() {
        for (NetworkInterface interfc : interfaces) {
            try {
                interfc.stopRunning();
            } catch (Throwable e) {
                logError(ServerLog.ERRLOG_LEVEL_REALBAD, "unable to shut down interface " + interfc + "; reason: " + e);
            }
        }

        server.shutdown();

        if (logger != null) {
            try {
                logger.shutdown();
            } catch (Exception e) {
                System.err.println("Error shutting down logger: " + e);
            }
        }

        this.dnsConfig.getNameServer().shutdown();
    }

    public class Server extends AbstractServer {
        public Server(StreamTable configTable, HandleResolver resolver) {
            super(DNSServer.this, configTable, resolver);
        }

        @Override
        public void dumpHandles() throws HandleException, IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {
            resolver.processRequest(req, callback);
        }

        @Override
        public void shutdown() {
            super.shutdown();
        }

        @Override
        public PublicKey getPublicKey() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrivateKey getPrivateKey() {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate getCertificate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public X509Certificate[] getCertificateChain() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrivateKey getCertificatePrivateKey() {
            throw new UnsupportedOperationException();
        }
    }
}

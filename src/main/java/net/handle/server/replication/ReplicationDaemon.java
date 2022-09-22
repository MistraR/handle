/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.cnri.util.FastDateFormat;
import net.cnri.util.StreamTable;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.DumpHandlesCallback;
import net.handle.hdllib.DumpHandlesRequest;
import net.handle.hdllib.DumpHandlesResponse;
import net.handle.hdllib.GenericRequest;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.ReplicationDaemonInterface;
import net.handle.hdllib.ReplicationStateInfo;
import net.handle.hdllib.RetrieveTxnRequest;
import net.handle.hdllib.RetrieveTxnResponse;
import net.handle.hdllib.SecretKeyAuthenticationInfo;
import net.handle.hdllib.ServerInfo;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Transaction;
import net.handle.hdllib.TransactionCallback;
import net.handle.hdllib.TransactionQueueListener;
import net.handle.hdllib.TransactionValidator;
import net.handle.hdllib.TransactionQueueInterface;
import net.handle.hdllib.TransactionQueuesInterface;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;
import net.handle.server.HandleServer;
import net.handle.server.ServerLog;

/*****************************************************************************
 * Thread that retrieves handle transactions from the primary servers or some other source of transactions, according to the server configuration.
 *****************************************************************************/
public class ReplicationDaemon extends Thread implements ReplicationDaemonInterface {
    public static final String REPLICATION_INTERVAL = "replication_interval";
    public static final String REPLICATION_START_TIME = "replication_start_time";
    public static final String REPLICATION_AUTH = "replication_authentication"; // set on the mirrors config. privatekey:index:handle of the mirrors
    // public key
    public static final String REPLICATION_SERVER_INFO_FILE = "txnsrcsv.bin"; // alternative to replication_sites_handle, siteinfo.bin of the of the
    // primary to pull transactions from
    public static final String REPLICATION_SERVER_INFO_JSON_FILE = "txnsrcsv.json";
    public static final String REPLICATION_STATUS_FILE = "txnstat.dct";
    public static final String REPLICATION_PRIV_KEY_FILE = "replpriv.bin"; // private key file that matches the public key in
    // replication_authentication
    public static final String REPLICATION_SECRET_KEY_FILE = "replsec.bin";
    public static final String REPLICATION_TIMEOUT = "replication_timeout";
    public static final String REPLICATION_SITES_HANDLE = "replication_sites_handle"; // set on the mirrors config. index:handle of the HS_SITE of the
    // primary to pull transactions from

    public static final String REPLICATION_SITE_HANDLE_VALUE = "replication_site_handle_value"; //set on the mirror config. index:handle of a single HS_SITE to pull from
    public static final String REPLICATION_PULL_OTHER_TRANSACTIONS = "replication_pull_other_transactions"; //set on mirror. yes|no indicates the server should pull all transactions including those that the source got from other servers.
    public static final String REPLICATION_KEEP_OTHER_TRANSACTIONS = "replication_keep_other_transactions"; // set on a mirror when you expect to pull from the mirror

    public static final String REPLICATION_SOURCES = "sources";
    public static final String REPLICATION_ACCEPT_PREFIXES = "replication_accept_prefixes"; // list of prefixes the mirror will accept when pulling
    // txns. Accept all if missing.
    public volatile boolean keepRunning = true;
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private int replicationTimeout = 300000; // default timeout of 5 minutes
    private File replicationStatusFile = null;
    private AuthenticationInfo replicationAuth;

    private ReplicationSourceSiteCollection replicationSourceSites;
    private ReplicationStateInfo replicationStateInfo;

    // time between replication refreshes default: 30 seconds
    private long replicationInterval = 30 * 1000;
    private Long replicationStartTime = null;

    private boolean initializedReplicationStatus = false;

    private final HandleServer server;
    private final boolean caseSensitive;
    private final SiteInfo thisSite;
    private final int thisServerNum;
    private boolean fileWriteNoSync = false;

    private final HandleResolver retrievalResolver = new HandleResolver();

    private NotifierInterface notifier;
    private TransactionValidator replicationValidator = null;
    private final ReplicationPrefixFilter replicationPrefixFilter;

    private boolean isPullEntireGroupTransactions = false;

    protected List<TransactionQueueListener> queueListeners = new CopyOnWriteArrayList<>();

    long lastNoPrimarySitesLoggedTimestamp = 0;

    public ReplicationDaemon(HandleServer server, StreamTable config, File configDir) throws HandleException, IOException, InvalidKeySpecException {
        this.server = server;
        this.thisSite = server.getSiteInfo();
        this.thisServerNum = server.getServerNum();
        this.caseSensitive = config.getBoolean(HandleServer.CASE_SENSITIVE);
        this.fileWriteNoSync = config.getBoolean(HandleServer.FILE_WRITE_NO_SYNC, false);
        this.redumpNeededFile = new File(configDir, "SERVER_NEEDS_REDUMP.txt");

        List<String> replicationAcceptPrefixes = getReplicationAcceptPrefixes(config);
        if (replicationAcceptPrefixes != null) {
            replicationPrefixFilter = new ReplicationPrefixFilter(replicationAcceptPrefixes);
        } else {
            replicationPrefixFilter = null;
        }

        if (config.containsKey(REPLICATION_TIMEOUT)) {
            replicationTimeout = Integer.parseInt(String.valueOf(config.get(REPLICATION_TIMEOUT)));
        } else {
            replicationTimeout = 5 * 60 * 1000; // default replication timeout is 5 mins
        }
        retrievalResolver.setTcpTimeout(replicationTimeout);

        retrievalResolver.traceMessages = server.getResolver().traceMessages;

        if (config.containsKey(REPLICATION_INTERVAL)) {
            String repIntStr = String.valueOf(config.get(REPLICATION_INTERVAL));
            try {
                replicationInterval = Long.parseLong(repIntStr);
            } catch (Exception e) {
                System.err.println("Error: invalid replication interval \"" + repIntStr + "\"; using default: " + replicationInterval + " milliseconds");
            }
        }

        String replicationStartTimeString = config.getStr(REPLICATION_START_TIME);
        if (replicationStartTimeString != null) {
            try {
                replicationStartTime = FastDateFormat.parse(replicationStartTimeString, TimeZone.getDefault());
            } catch (ParseException e) {
                throw new HandleException(HandleException.CONFIGURATION_ERROR, "Invalid " + REPLICATION_START_TIME + ": " + replicationStartTimeString, e);
            }
        }

        if (config.containsKey(REPLICATION_AUTH)) {
            String replAuthSpec = config.getStr(REPLICATION_AUTH, "");
            String replFields[] = replAuthSpec.split(":");
            if (replFields.length < 3) {
                throw new HandleException(HandleException.INVALID_VALUE, "Invalid replication auth descriptor: " + replAuthSpec);
            }
            int replHdlIdx = Integer.parseInt(replFields[1]);
            if (replFields[0].equals("privatekey")) {
                // private key replication
                byte passphrase[] = null;

                File privKeyFile = new File(configDir, REPLICATION_PRIV_KEY_FILE);
                byte privKeyBytes[] = new byte[(int) privKeyFile.length()];
                FileInputStream in = new FileInputStream(privKeyFile);
                try {
                    int r = 0;
                    int n = 0;
                    while (n < privKeyBytes.length && ((r = in.read(privKeyBytes, n, privKeyBytes.length - n)) >= 0)) {
                        n += r;
                    }
                } finally {
                    in.close();
                }

                try {
                    if (Util.requiresSecretKey(privKeyBytes)) {
                        passphrase = Util.getPassphrase("Enter the passphrase for this servers replication private key: ");
                    }
                    privKeyBytes = Util.decrypt(privKeyBytes, passphrase);
                } catch (Exception e) {
                    throw new HandleException(HandleException.INVALID_VALUE, "Error decrypting private key: " + e);
                } finally {
                    if (passphrase != null) {
                        for (int i = 0; i < passphrase.length; i++) {
                            passphrase[i] = (byte) 0;
                        }
                    }
                }

                replicationAuth = new PublicKeyAuthenticationInfo(Util.encodeString(replFields[2]), replHdlIdx, Util.getPrivateKeyFromBytes(privKeyBytes, 0));
            } else if (replAuthSpec.startsWith("secretkey:")) {
                File secKeyFile = new File(configDir, REPLICATION_SECRET_KEY_FILE);
                byte secKeyBytes[] = new byte[(int) secKeyFile.length()];
                FileInputStream in = new FileInputStream(secKeyFile);
                try {
                    int r = 0;
                    int n = 0;
                    while (n < secKeyBytes.length && ((r = in.read(secKeyBytes, n, secKeyBytes.length - n)) >= 0)) {
                        n += r;
                    }
                } finally {
                    in.close();
                }

                replicationAuth = new SecretKeyAuthenticationInfo(Util.encodeString(replFields[2]), replHdlIdx, secKeyBytes);
            } else {
                throw new HandleException(HandleException.INVALID_VALUE, "Unknown authentication type: " + replFields[0]);
            }
        } else {
            throw new HandleException(HandleException.INVALID_VALUE, "Servers using replication need to specify " + "replication authentication information");
        }

        String replicationSitesHandle = config.getStr(REPLICATION_SITES_HANDLE, null);
        String replicationSiteHandleValue = config.getStr(REPLICATION_SITE_HANDLE_VALUE, null);
        isPullEntireGroupTransactions = config.getBoolean(REPLICATION_PULL_OTHER_TRANSACTIONS);

        if (replicationSitesHandle == null || "".equals(replicationSitesHandle)) {
            if (replicationSiteHandleValue == null || "".equals(replicationSiteHandleValue)) {
                // need to read the replication source information here....
                // this needs to be read from a file that it can be written back
                // to after it is updated dynamically. The configuration should
                // include the servers public key information so that it can be
                // authenticated as the true source of the transaction handles.

                // read the primary (or replication source) site information from
                // the appropriate file. I used a site info record because we
                // basically need all of the site info (public key, admin ports, etc)
                // and there's no sense in specifying a different config file layout
                // just for this
                File replicationsSourceSiteFile = new File(configDir, REPLICATION_SERVER_INFO_FILE);
                if (!replicationsSourceSiteFile.exists()) {
                    replicationsSourceSiteFile = new File(configDir, REPLICATION_SERVER_INFO_JSON_FILE);
                }
                if (!replicationsSourceSiteFile.exists()) {
                    throw new HandleException(HandleException.CONFIGURATION_ERROR, "No replication site found (" + REPLICATION_SERVER_INFO_FILE + ")");
                }
                replicationSourceSites = new FileBasedReplicationSourceSiteCollection(replicationsSourceSiteFile);
            } else {
                //A single replication site has been specified by index:handle
                ValueReference valueReference = ValueReference.fromString(replicationSiteHandleValue);
                String replicationSiteHandle = valueReference.getHandleAsString();
                int replicationSiteIndex = valueReference.index;
                replicationSourceSites = new HandleBasedReplicationSourceSiteCollection(replicationSiteIndex, replicationSiteHandle, retrievalResolver, thisSite.servers[thisServerNum], this.server);
            }
        } else {
            replicationSourceSites = new HandleBasedReplicationSourceSiteCollection(replicationSitesHandle, retrievalResolver, thisSite.servers[thisServerNum], this.server);
        }

        if (isPullEntireGroupTransactions) {
            replicationSourceSites.refresh();
            if (replicationSourceSites.getReplicationSourceSites().size() == 0) {
                throw new HandleException(HandleException.CONFIGURATION_ERROR, REPLICATION_PULL_OTHER_TRANSACTIONS + " and no source sites found");
            }
            throwIfNotAllSitesHaveEqualOrGreaterVersion(2, 9, replicationSourceSites.getReplicationSourceSites());
        }

        replicationStatusFile = new File(configDir, REPLICATION_STATUS_FILE);

        // last transaction dates database
        if (thisSite.isPrimary || replicationSourceSites instanceof HandleBasedReplicationSourceSiteCollection || isPullEntireGroupTransactions) {
            replicationDb = new ReplicationDb(configDir, server, config);
        }
    }

    private void throwIfNotAllSitesHaveEqualOrGreaterVersion(int majorVersion, int minorVersion, List<ReplicationSourceSiteInfo> sourceSites) throws HandleException {
        for (ReplicationSourceSiteInfo sourceSite : sourceSites) {
            SiteInfo siteInfo = sourceSite.getSite();
            if (!AbstractMessage.hasEqualOrGreaterVersion(siteInfo.majorProtocolVersion, siteInfo.minorProtocolVersion, majorVersion, minorVersion)) {
                throw new HandleException(HandleException.CONFIGURATION_ERROR, REPLICATION_PULL_OTHER_TRANSACTIONS + " on but protocol version on source site " + sourceSite.getName() + " too old");
            }
        }
    }

    public void registerReplicationTransactionValidator(@SuppressWarnings("hiding") TransactionValidator replicationValidator) {
        this.replicationValidator = replicationValidator;
    }

    public ReplicationStateInfo getReplicationStateInfo() {
        return replicationStateInfo;
    }

    public void registerReplicationErrorNotifier(@SuppressWarnings("hiding") NotifierInterface notifier) {
        this.notifier = notifier;
    }

    private List<String> getReplicationAcceptPrefixes(StreamTable config) {
        Vector<?> prefixesVector = (Vector<?>) config.get(REPLICATION_ACCEPT_PREFIXES);
        if (prefixesVector == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < prefixesVector.size(); i++) {
            String prefix = String.valueOf(prefixesVector.elementAt(i));
            result.add(prefix);
        }
        return result;
    }

    private void loadInitialReplicationStatus() {
        if (replicationStatusFile.exists()) {
            try {
                StreamTable replicationConfig = new StreamTable();
                replicationConfig.readFromFile(replicationStatusFile);
                replicationStateInfo = ReplicationStateInfo.fromStreamTable(replicationConfig, replicationSourceSites.getOwnName()); // old version: replicationSites.loadReplicationStatus(replicationConfig);
            } catch (Exception e) {
                server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception while reading replication status information: " + e);
                e.printStackTrace();

            }
        } else {
            replicationStateInfo = new ReplicationStateInfo();
            replicationStateInfo.setOwnName(replicationSourceSites.getOwnName());
        }
    }

    @Override
    public StreamTable replicationStatus() throws HandleException {
        if (replicationStateInfo == null) {
            return null;
        }
        return ReplicationStateInfo.toStreamTable(replicationStateInfo);
    }

    private void saveReplicationInfo() throws HandleException {
        try {
            StreamTable replicationConfig = replicationStatus();
            replicationConfig.writeToFile(replicationStatusFile, !fileWriteNoSync);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            if (e instanceof HandleException) {
                throw (HandleException) e;
            }
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error saving replication state: " + e);
        }
    }

    public void dumpHandles(boolean deleteAll, SiteInfo[] sites) throws HandleException, IOException {
        // allow hot-update of replication site information
        replicationSourceSites.refresh();

        ReplicationSourceSiteInfo dumpSite;
        if (sites == null) {
            dumpSite = getFastestSite();
        } else {
            dumpSite = getSiteFromHandle(sites);
        }

        // stop doing all replication
        pauseReplication();
        try {
            dumpHandlesFromSite(dumpSite, deleteAll);
        } finally {
            unpauseReplication();
        }
    }

    private ReplicationSourceSiteInfo getSiteFromHandle(SiteInfo[] sites) throws HandleException {
        for (ReplicationSourceSiteInfo replicationSourceSite : replicationSourceSites.getReplicationSourceSites()) {
            if (replicationSourceSite.getSite() == null) {
                continue;
            }
            for (SiteInfo site : sites) {
                if (replicationSourceSite.getSite().equals(site)) {
                    return replicationSourceSite;
                }
            }
        }
        throw new HandleException(HandleException.INTERNAL_ERROR, "Unable to find matching site");
    }

    private ReplicationSourceSiteInfo getFastestSite() throws HandleException {
        ReplicationSourceSiteInfo dumpSite;
        ArrayList<SiteTiming> timings = new ArrayList<>();
        for (ReplicationSourceSiteInfo replicationSourceSite : replicationSourceSites.getReplicationSourceSites()) {
            if (replicationSourceSite.getSite() == null) {
                continue;
            }
            timings.add(new SiteTiming(replicationSourceSite).getTiming());
        }

        if (timings.size() <= 0) {
            throw new HandleException(HandleException.SERVICE_NOT_FOUND, "No primary servers found for handle dump");
        }

        Collections.sort(timings);
        System.err.println("Dump site timings:");
        for (SiteTiming timing : timings) {
            System.err.println("  " + timing);
        }
        dumpSite = timings.get(0).replicationSourceSite;
        return dumpSite;
    }

    private class SiteTiming implements Comparable<SiteTiming> {
        ReplicationSourceSiteInfo replicationSourceSite;
        SiteInfo site;
        long responseTime = Integer.MAX_VALUE;

        SiteTiming(ReplicationSourceSiteInfo replicationSourceSite) {
            this.replicationSourceSite = replicationSourceSite;
            this.site = replicationSourceSite.getSite();
            this.responseTime = -1;
            getTiming();
        }

        @Override
        public String toString() {
            return "time=" + responseTime + " ms;  site=" + site;
        }

        public SiteTiming getTiming() {
            responseTime = Integer.MAX_VALUE;
            long startTime = System.currentTimeMillis();
            try {
                GenericRequest req = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
                AbstractResponse resp = retrievalResolver.sendRequestToSite(req, site);
                if (resp.responseCode != AbstractMessage.RC_SUCCESS) {
                    responseTime = Integer.MAX_VALUE - 1;
                } else {
                    responseTime = System.currentTimeMillis() - startTime;
                }
            } catch (Throwable t) {
                System.err.println("Error timing connection to site: " + site);
            }
            return this;
        }

        @Override
        public int compareTo(SiteTiming o) {
            return Long.compare(responseTime, o.responseTime);
        }
    }

    @Override
    public void pauseReplication() {
        readWriteLock.readLock().lock();
    }

    @Override
    public void unpauseReplication() {
        readWriteLock.readLock().unlock();
    }

    static long getInitialDelay(long lastPullTimestamp, long now, long replicationInterval) {
        long timeSinceLastPull = now - lastPullTimestamp;
        long intialDelay = (replicationInterval - (timeSinceLastPull % replicationInterval)) % replicationInterval;
        return intialDelay;
    }

    @Override
    public synchronized void run() {
        if (replicationStartTime != null) {
            long now = System.currentTimeMillis();
            long initialDelay = getInitialDelay(replicationStartTime, now, replicationInterval);
            try {
                Thread.sleep(initialDelay);
            } catch (InterruptedException e1) {
                Thread.currentThread().interrupt();
            }
        }
        while (keepRunning) {
            readWriteLock.writeLock().lock();
            try {
                ArrayList<SiteInfo> redumpSites = null;

                try {
                    // allow hot-update of replication site information
                    replicationSourceSites.refresh();
                    if (!initializedReplicationStatus) {
                        loadInitialReplicationStatus();
                        initializedReplicationStatus = true;
                    }

                    int primarySites = 0;
                    for (ReplicationSourceSiteInfo replicationSourceSite : replicationSourceSites.getReplicationSourceSites()) {
                        SiteInfo site = replicationSourceSite.getSite();
                        if (site == null) {
                            continue;
                        }
                        primarySites++;

                        TxnCallback callback = new TxnCallback(replicationStateInfo, replicationSourceSite.getName(), site);
                        for (int i = 0; i < site.servers.length; i++) {
                            // if the queried server has no handles that will hash to us, skip it
                            if (site.hashOption == thisSite.hashOption && site.servers.length == thisSite.servers.length && thisServerNum != i) {
                                continue;
                            }

                            RetrieveTxnRequest req;
                            if (isPullEntireGroupTransactions) {
                                req = new RetrieveTxnRequest(replicationStateInfo, thisSite.hashOption, thisSite.servers.length, thisServerNum, replicationAuth);
                            } else {
                                long lastTxnId = replicationStateInfo.getLastTxnId(i + ":" + replicationSourceSite.getName());
                                long lastTimestamp = replicationStateInfo.getLastTimestamp(i + ":" + replicationSourceSite.getName());
                                req = new RetrieveTxnRequest(lastTxnId, lastTimestamp, thisSite.hashOption, thisSite.servers.length, thisServerNum, replicationAuth);
                            }
                            req.encrypt = false;
                            req.certify = true;
                            req.setSupportedProtocolVersion(site);
                            long startTime = System.nanoTime();
                            try {
                                AbstractResponse res = retrievalResolver.sendRequestToServer(req, site, site.servers[i]);

                                if (res.responseCode == AbstractMessage.RC_SUCCESS) {
                                    callback.setServerNum(i);

                                    // decode the public key to authenticate the stream
                                    PublicKey pubKey = site.servers[i].getPublicKey();

                                    int status = ((RetrieveTxnResponse) res).processStreamedPart(callback, pubKey);

                                    if (status == RetrieveTxnResponse.NEED_TO_REDUMP) {
                                        notifyAboutNeedToRedumpResponse(replicationSourceSite, i);
                                        logAboutNeedToRedumpResponse(replicationSourceSite, i);
                                        System.out.println("------------------------------------------------------------\n" + "CRITICAL: REDUMP NEEDED response from site: " + site.servers[i]
                                            + "\n------------------------------------------------------------");
                                        System.err.println("------------------------------------------------------------\n" + "CRITICAL: REDUMP NEEDED response from site: " + site.servers[i]
                                            + "\n------------------------------------------------------------");
                                        if (redumpSites == null) {
                                            redumpSites = new ArrayList<>();
                                        }
                                        redumpSites.add(site);
                                    } else if (status == RetrieveTxnResponse.SENDING_TRANSACTIONS) {
                                        saveReplicationInfo();
                                    } else {
                                        server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Unknown status code from server during replication: " + status);
                                    }

                                } else {
                                    server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unexpected response to replication request: " + res);
                                }

                                // this doesn't handle wrap-around serial numbers - maybe it should.
                                // but the numbers can go so large it shouldn't matter.
                                // The date/time/seconds could be used.

                                // This is about updating file-based source site info by asking the source server for its newest site info.
                                // For now we have decided not to do this.
                                //                                // if site info is not stored locally (e.g. it's in a handle), we take no action here.
                                //                                if (res.siteInfoSerial > replInfo.site.serialNumber && replicationSites.siteInfoIsLocal()) {
                                //                                    server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Updating replication info from version " + replInfo.site.serialNumber + " to version " + res.siteInfoSerial);
                                //                                    replInfo.getNewSiteInfoFromServers(retrievalResolver, replicationAuth);
                                //                                    saveReplicationInfo();
                                //                                    replicationSites.saveSiteInfo();
                                //                                    break;
                                //                                }

                            } catch (HandleException e) {
                                if (e.getCause() instanceof javax.net.ssl.SSLHandshakeException) {
                                    if (e.getCause().getCause() instanceof java.security.cert.CertificateException) {
                                        server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error doing replication at server: " + site.servers[i] + ": " + e);
                                        server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Note: if this problem persists, a Java upgrade may be needed");
                                        e.printStackTrace(System.err);
                                    } else {
                                        server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error doing replication at server (if occasional handshake failure, safe to ignore): " + site.servers[i] + ": " + e.getCause());
                                    }
                                } else {
                                    server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error doing replication at server: " + site.servers[i] + ": " + e);
                                    if (e.getCode() == HandleException.CANNOT_CONNECT_TO_SERVER) {
                                        // no stack trace
                                    } else if (e.getCause() instanceof java.net.SocketTimeoutException) {
                                        // no stack trace
                                    } else if (e.getCause() instanceof HandleException && ((HandleException) e.getCause()).getCode() == HandleException.CANNOT_CONNECT_TO_SERVER) {
                                        // no stack trace
                                    } else {
                                        e.printStackTrace(System.err);
                                    }
                                }
                            }
                            long endTime = System.nanoTime();
                            long duration = endTime - startTime;
                            double durationInSeconds = duration / 1000000000D;
                            if (durationInSeconds > 10.0) {
                                server.logError(ServerLog.ERRLOG_LEVEL_INFO, "Replication sequence at took " + durationInSeconds + " seconds at: " + site.servers[i]);
                            }
                            updateRedumpIsNeededStatus(redumpSites);
                        }
                    }
                    if (primarySites == 0) {
                        if (System.currentTimeMillis() > lastNoPrimarySitesLoggedTimestamp + 86400000L) {
                            lastNoPrimarySitesLoggedTimestamp = System.currentTimeMillis();
                            server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "No primary sites found to replicate!");
                        }
                    }
                } catch (Throwable t) {
                    server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Error in replication daemon: " + t);
                    t.printStackTrace(System.err);
                }
            } finally {
                readWriteLock.writeLock().unlock();
            }

            try {
                Thread.sleep(replicationInterval);
            } catch (Throwable e) {
                server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error sleeping in replication thread: " + e);
            }
        }
    }

    private void notifyAboutNeedToRedumpResponse(ReplicationSourceSiteInfo replicationSourceSite, int i) throws HandleException {
        if (notifier != null) {
            RedumpErrorMessage redumpNotification = new RedumpErrorMessage();
            redumpNotification.receivingSiteInfo = thisSite;
            redumpNotification.receivingServerNumber = thisServerNum;
            redumpNotification.sourceSiteInfo = replicationSourceSite.getSite();
            redumpNotification.sourceSiteName = replicationSourceSite.getName();
            redumpNotification.sourceServerNumber = i;
            redumpNotification.replicationStateInfo = replicationStateInfo;
            notifier.sendNotification(GsonUtility.getPrettyGson().toJson(redumpNotification), "RedumpErrorMessage");
        }
    }

    private void logAboutNeedToRedumpResponse(ReplicationSourceSiteInfo replicationSourceSite, int i) {
        RedumpErrorMessage redumpNotification = new RedumpErrorMessage();
        redumpNotification.receivingSiteInfo = thisSite;
        redumpNotification.receivingServerNumber = thisServerNum;
        redumpNotification.sourceSiteInfo = replicationSourceSite.getSite();
        redumpNotification.sourceSiteName = replicationSourceSite.getName();
        redumpNotification.sourceServerNumber = i;
        redumpNotification.replicationStateInfo = replicationStateInfo;
        System.err.println("NEED_TO_REDUMP received from remote server:");
        System.err.println(GsonUtility.getPrettyGson().toJson(redumpNotification));
    }

    private final File redumpNeededFile;

    private void updateRedumpIsNeededStatus(ArrayList<SiteInfo> redumpSites) {
        if (redumpSites == null || redumpSites.size() == 0) { // no redump is needed
            if (redumpNeededFile.exists()) {
                redumpNeededFile.delete();
            }
        } else { // a redump is needed... make sure somebody knows about it
            PrintWriter out = null;
            try {
                out = new PrintWriter(redumpNeededFile);
                out.println("******************************************************************");
                out.println("*                 REPLICATION IS OUT OF SYNC                     *");
                out.println("******************************************************************");
                out.println("The following primary servers report that our replication with");
                out.println("them is out of date: ");

                for (SiteInfo site : redumpSites) {
                    out.println("Site: " + site);
                }
                out.println("******************************************************************");
                out.close();
            } catch (IOException e) {
                System.err.println("Error updating redump-needed file: " + redumpNeededFile.getPath());
            } finally {
                if (out != null) try {
                    out.close();
                } catch (Throwable t) {
                }
            }
        }
    }

    public void shutdown() {
        keepRunning = false;
        try {
            if (replicationDb != null) {
                replicationDb.shutdown();
            }
        } catch (Throwable e) {
        }
    }

    private ReplicationDb replicationDb;

    public long adjustAndSetLastCreateOrDeleteDate(byte[] handle, long date, int priority) throws HandleException {
        if (replicationDb != null) {
            return replicationDb.adjustAndSetLastDate(handle, date, priority, false);
        }
        return date;
    }

    public long adjustAndSetLastHomeOrUnhomeDate(byte[] handle, long date, int priority) throws HandleException {
        if (replicationDb != null) {
            return replicationDb.adjustAndSetLastDate(handle, date, priority, true);
        }
        return date;
    }

    @Override
    public Iterator<byte[]> handleIterator() throws HandleException {
        if (replicationDb != null) {
            return replicationDb.iterator(false);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    @Override
    public Iterator<byte[]> naIterator() throws HandleException {
        if (replicationDb != null) {
            return replicationDb.iterator(true);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    @Override
    public Iterator<byte[]> handleIteratorFrom(byte[] startingPoint, boolean inclusive) throws HandleException {
        if (replicationDb != null) {
            return replicationDb.iteratorFrom(false, startingPoint, inclusive);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    @Override
    public Iterator<byte[]> naIteratorFrom(byte[] startingPoint, boolean inclusive) throws HandleException {
        if (replicationDb != null) {
            return replicationDb.iteratorFrom(true, startingPoint, inclusive);
        } else {
            return Collections.<byte[]>emptyList().iterator();
        }
    }

    public synchronized void dumpHandlesFromSite(ReplicationSourceSiteInfo ReplicationSourceSiteInfo) throws HandleException {
        dumpHandlesFromSite(ReplicationSourceSiteInfo, true);
    }

    /**
     * Delete all handles in the containing server and perform a redump from a server using the given replication information.
     *
     * @param replicationSourceSite
     *            the replication information for this server
     * @throws HandleException
     */
    public synchronized void dumpHandlesFromSite(ReplicationSourceSiteInfo replicationSourceSite, boolean deleteAll) throws HandleException {
        boolean success = false;
        if (replicationSourceSite == null || replicationSourceSite.getSite() == null) {
            throw new NullPointerException("attempt to dump handles using a null ReplicationSiteInfo");
        }
        SiteInfo site = replicationSourceSite.getSite();
        try {
            System.out.println("------------------------------\n" + "---- REDUMPING HANDLES!!! ----\n" + "------------------------------");
            System.err.println("------------------------------\n" + "---- REDUMPING HANDLES!!! ----\n" + "------------------------------");
            server.disable();

            // indicate that all servers need to be dumped -
            // in case we get interrupted
            replicationStateInfo = new ReplicationStateInfo();
            replicationStateInfo.setOwnName(replicationSourceSites.getOwnName());

            // delete *all* handles
            // it's ok to delete all handles, because *sites* are
            // replicated, not handles under specified prefixes.
            if (deleteAll) {
                server.getStorage().deleteAllRecords();
                if (replicationDb != null) {
                    replicationDb.deleteAll();
                }
            }

            // send dump request to *all* primary servers,
            // this could/should be parallelized
            DumpHandlesRequest req = new DumpHandlesRequest(thisSite.hashOption, thisSite.servers.length, thisServerNum, replicationAuth);
            req.setSupportedProtocolVersion(site);
            // dump the entire handle database from each primary server in that site
            for (int i = 0; i < site.servers.length; i++) {
                // skip any sites that can't possibly have any handles that hash to this server
                ServerInfo replServer = site.servers[i];
                if (site.hashOption == thisSite.hashOption && site.servers.length == thisSite.servers.length && thisServerNum != i) {
                    continue;
                }

                int resumeCount = 0;
                int resumeTimeoutMs = 5000; // 5 seconds
                AbstractResponse response = null;
                DumpHandlesResponse previousResponse = null;
                int maxAttemptsWithoutProgress = 2;
                int resumeWithoutProgressCount = 0;
                while (resumeWithoutProgressCount <= maxAttemptsWithoutProgress) {
                    try {
                        if (resumeCount == 0 || previousResponse == null) {
                            response = retrievalResolver.sendRequestToServer(req, site, replServer);
                        } else {
                            DumpHandlesRequest resumeRequest = createResumeRequest(req, previousResponse);
                            System.err.println("Sending resume dump request");
                            response = retrievalResolver.sendRequestToServer(resumeRequest, site, replServer);
                        }
                        if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                            if (previousResponse != null && (previousResponse.responseCode == AbstractMessage.RC_SUCCESS)) {
                                ((DumpHandlesResponse) response).setLastProcessedRecordType(previousResponse.getLastProcessedRecordType());
                                ((DumpHandlesResponse) response).setLastProcessedRecord(previousResponse.getLastProcessedRecord());
                            }
                            ((DumpHandlesResponse) response).processStreamedPart(new DumpHdlCallback(replicationStateInfo, replicationSourceSite.getName(), i), site.servers[i].getPublicKey());
                            success = true;
                            break; // success the dump completed so we break out of the resume loop
                        } else {
                            // System.err.println("Received non-success response to dump-handles message: "+response);
                            throw new HandleException(HandleException.REPLICATION_ERROR, "Response code " + response.responseCode + ":  " + AbstractMessage.getResponseCodeMessage(response.responseCode));
                        }
                    } catch (Exception dumpError) {
                        System.out.println("Error dumping server");
                        System.err.println("Error dumping server " + i + ": ");
                        dumpError.printStackTrace();
                        boolean makingProgress = isMakingProgress(previousResponse, response);
                        System.err.println("Making progress: " + makingProgress);
                        if (response != null && response.responseCode == AbstractMessage.RC_SUCCESS) {
                            byte lastProcessedRecordType = ((DumpHandlesResponse) response).getLastProcessedRecordType();
                            if (lastProcessedRecordType == DumpHandlesResponse.ABSOLUTELY_DONE_RECORD) {
                                // We got an exception but we still managed to process the last record so we are done.
                                success = true;
                                break;
                            } else if (response.hasEqualOrGreaterVersion(2, 8)) {
                                System.out.println("trying again...");
                                System.err.println("trying again...");
                                previousResponse = (DumpHandlesResponse) response;
                            } else {
                                System.out.println("Dump handles response interrupted and server does not support resumption.");
                                System.out.println("Dump handles response interrupted and server does not support resumption.");
                            }
                        }
                        resumeCount++;
                        if (makingProgress) {
                            resumeWithoutProgressCount = 0;
                        } else {
                            resumeWithoutProgressCount++;
                        }
                        Thread.sleep(resumeTimeoutMs);
                    }
                }
            }
            if (success) {
                // and then save new replication info.
                saveReplicationInfo();
                server.enable();
                System.out.println("------------------------------------\n" + "---------- REDUMP FINISHED ---------\n" + "------------------------------------");
                System.err.println("------------------------------------\n" + "---------- REDUMP FINISHED ---------\n" + "------------------------------------");
            } else {
                System.out.println("------------------------------------\n" + "---------- REDUMP FAILED -----------\n" + "------------------------------------");
                System.err.println("------------------------------------\n" + "---------- REDUMP FAILED -----------\n" + "------------------------------------");
            }
        } catch (Throwable e) {
            server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Error attempting to reload all handles: " + e);
            e.printStackTrace();
        }
    }

    private boolean isMakingProgress(DumpHandlesResponse previousResponse, AbstractResponse currentResponse) {
        if (currentResponse == null || currentResponse.responseCode != AbstractMessage.RC_SUCCESS) {
            return false;
        }
        byte currentProcessedRecordType = ((DumpHandlesResponse) currentResponse).getLastProcessedRecordType();
        byte[] currentProcessedRecord = ((DumpHandlesResponse) currentResponse).getLastProcessedRecord();
        System.err.println("Current record type: " + currentProcessedRecordType);
        System.err.println("Current record: " + Util.decodeString(currentProcessedRecord));
        if (previousResponse == null) {
            System.out.println("No previous response, this is the first attempt.");
            if (currentResponse.responseCode == AbstractMessage.RC_SUCCESS) {
                return true;
            } else {
                return false;
            }
        } else {
            if (currentResponse.responseCode == AbstractMessage.RC_SUCCESS) {
                byte previousProcessedRecordType = previousResponse.getLastProcessedRecordType();
                byte[] previousProcessedRecord = previousResponse.getLastProcessedRecord();
                System.err.println("Previous record type: " + previousProcessedRecordType);
                System.err.println("Previous record: " + Util.decodeString(previousProcessedRecord));
                if (currentProcessedRecordType == previousProcessedRecordType) {
                    if (!Arrays.equals(currentProcessedRecord, previousProcessedRecord)) {
                        return true;
                    } else {
                        return false;
                    }
                } else if (!isRecordTypeComesBeforeInDumpSequence(currentProcessedRecordType, previousProcessedRecordType)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    /**
     * returns true if record type a comes before record type be in the dump sequence
     */
    static boolean isRecordTypeComesBeforeInDumpSequence(byte a, byte b) {
        if (a == b) {
            return false;
        }
        List<Byte> recordTypesInOrder = new ArrayList<>();
        recordTypesInOrder.add(DumpHandlesResponse.THIS_SERVER_REPLICATION_INFO_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.OTHER_SITE_REPLICATION_INFO_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.HANDLE_DATE_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.NA_DATE_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.HANDLE_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.HOMED_PREFIX_RECORD);
        recordTypesInOrder.add(DumpHandlesResponse.ABSOLUTELY_DONE_RECORD);
        int indexOfA = recordTypesInOrder.indexOf(a);
        int indexOfB = recordTypesInOrder.indexOf(b);
        if (indexOfA < indexOfB) {
            return true;
        } else {
            return false;
        }
    }

    private DumpHandlesRequest createResumeRequest(DumpHandlesRequest originalRequest, DumpHandlesResponse previousResponse) {
        byte[] startingPoint = previousResponse.getLastProcessedRecord();
        int startingPointType = -2;
        byte lastProcessedRecordType = previousResponse.getLastProcessedRecordType();
        if (lastProcessedRecordType == DumpHandlesResponse.HANDLE_DATE_RECORD) {
            startingPointType = DumpHandlesRequest.HANDLE_REPLICATION_DB;
        } else if (lastProcessedRecordType == DumpHandlesResponse.NA_DATE_RECORD) {
            startingPointType = DumpHandlesRequest.NA_REPLICATION_DB;
        } else if (lastProcessedRecordType == DumpHandlesResponse.HANDLE_RECORD) {
            startingPointType = DumpHandlesRequest.HANDLE;
        } else if (lastProcessedRecordType == DumpHandlesResponse.HOMED_PREFIX_RECORD) {
            startingPointType = DumpHandlesRequest.NA;
        } else if (lastProcessedRecordType == DumpHandlesResponse.THIS_SERVER_REPLICATION_INFO_RECORD) {
            return originalRequest; // Start over
        } else if (lastProcessedRecordType == DumpHandlesResponse.OTHER_SITE_REPLICATION_INFO_RECORD) {
            return originalRequest; // Start over
        } else {
            return originalRequest; // The lastProcessedRecordType is unknown so assume no records were processed and just send the original request
            // again.
        }
        DumpHandlesRequest resumeRequest = new DumpHandlesRequest(originalRequest.rcvrHashType, originalRequest.numServers, originalRequest.serverNum, originalRequest.authInfo, startingPoint, startingPointType);
        return resumeRequest;
    }

    /****************************************************************************
     * Class used to process the results of DumpHandlesRequest messages. Since that request type is streamable, this class is used as the target for
     * the callback.
     ****************************************************************************/
    class DumpHdlCallback implements DumpHandlesCallback {
        @SuppressWarnings("hiding")
        private final ReplicationStateInfo replicationStateInfo;
        private final String sourceSiteName;
        private int currentServerNum = -1;
        private boolean gotSourceSiteReplicationStatus;

        DumpHdlCallback(ReplicationStateInfo replicationStateInfo, String sourceSiteName, int serverNum) {
            this.replicationStateInfo = replicationStateInfo;
            this.sourceSiteName = sourceSiteName;
            this.currentServerNum = serverNum;
            System.err.println("Starting dump of source server #" + serverNum);
        }

        @Override
        public synchronized void addHandle(byte handle[], HandleValue values[]) throws Exception {
            if (!caseSensitive) {
                handle = Util.upperCase(handle);
            }
            if (replicationPrefixFilter != null && !replicationPrefixFilter.acceptHandle(handle)) {
                System.err.println("--Handle skipped by prefix filter: " + Util.decodeString(handle));
                return;
            } else {
                System.err.println("---> " + Util.decodeString(handle));
            }
            server.getStorage().createOrUpdateRecord(handle, values);
        }

        @Override
        public synchronized void addHomedPrefix(byte authHandle[]) throws Exception {
            if (replicationPrefixFilter != null && !replicationPrefixFilter.acceptNA(authHandle)) {
                System.err.println("--NA skipped by prefix filter: " + Util.decodeString(authHandle));
                return;
            } else {
                System.err.println("---NA> " + Util.decodeString(authHandle));
            }
            server.getStorage().setHaveNA(authHandle, true);
        }

        @Override
        public synchronized void processThisServerReplicationInfo(long date, long txnId) {
            System.err.println("----received replication status: server=" + currentServerNum + " date=" + (new java.util.Date(date)) + "; last txnId: " + txnId);
            replicationStateInfo.setLastTxnId(currentServerNum + ":" + sourceSiteName, txnId);
            replicationStateInfo.setLastTimestamp(currentServerNum + ":" + sourceSiteName, date);
            gotSourceSiteReplicationStatus = true;
        }

        @Override
        public synchronized void processOtherSiteReplicationInfo(StreamTable otherReplicationConfig) throws HandleException {
            System.err.println("----getting replication info for other sites:");
            ReplicationStateInfo receivedReplicationStateInfo = ReplicationStateInfo.fromStreamTable(otherReplicationConfig, null);
            for (String name : receivedReplicationStateInfo.keySet()) {
                if (ReplicationStateInfo.isQueueNameInSiteNamed(name, sourceSiteName) && gotSourceSiteReplicationStatus) continue;
                if (replicationStateInfo.isQueueNameInOwnSite(name)) continue;
                replicationStateInfo.setLastTxnId(name, receivedReplicationStateInfo.getLastTxnId(name));
                replicationStateInfo.setLastTimestamp(name, receivedReplicationStateInfo.getLastTimestamp(name));
            }
            saveReplicationInfo();
            System.err.println("------done.");
        }

        @Override
        public synchronized void setLastCreateOrDeleteDate(byte[] handle, long date, int priority) throws HandleException {
            System.err.println("---(date)> " + Util.decodeString(handle));
            if (replicationDb != null) {
                replicationDb.setLastDate(handle, date, priority, false);
            }
        }

        @Override
        public synchronized void setLastHomeOrUnhomeDate(byte[] handle, long date, int priority) throws HandleException {
            System.err.println("---NA(date)> " + Util.decodeString(handle));
            if (replicationDb != null) {
                replicationDb.setLastDate(handle, date, priority, true);
            }
        }
    }

    interface RunnableThrowingHandleException {
        void run() throws HandleException;
    }

    // synchronization note: always in server.getWriteLock(handle)
    private void runIfMoreRecent(RunnableThrowingHandleException runnable, byte[] handle, long date, int priority, boolean isNA) throws HandleException {
        if (replicationDb == null) {
            runnable.run();
        } else {
            if (replicationDb.isMoreRecentThanLastDate(handle, date, priority, isNA)) {
                runnable.run();
                replicationDb.setLastDate(handle, date, priority, isNA);
            }
        }
    }

    private static String getSiteNameFromQueueName(String name) {
        int colon = name.indexOf(':');
        return name.substring(colon + 1);
    }

    private static int getIndexFromQueueName(String name) {
        String siteName = getSiteNameFromQueueName(name);
        int colon = siteName.indexOf(':');
        if (colon < 0) return 0;
        else return Integer.parseInt(siteName.substring(0, colon));
    }

    @Override
    public void addQueueListener(TransactionQueueListener l) {
        queueListeners.add(l);
    }

    @Override
    public void removeQueueListener(TransactionQueueListener l) {
        queueListeners.remove(l);
    }

    protected void notifyQueueListeners(Transaction txn) {
        for (TransactionQueueListener listener : queueListeners) {
            try {
                listener.transactionAdded(txn);
            } catch (Exception e) {
                System.err.println("error notifying queue listeners: " + e);
                e.printStackTrace(System.err);
            }
        }
    }

    protected void shutdownQueueListeners() {
        for (TransactionQueueListener listener : queueListeners) {
            try {
                listener.shutdown();
            } catch (Exception e) {
                System.err.println("error in queue listeners shutdown: " + e);
                e.printStackTrace(System.err);
            }
        }
    }

    /****************************************************************************
     * Class used to process the results of RetrieveTxnRequest messages. Since that request type is streamable, this class is used as the target for
     * the callback.
     ****************************************************************************/
    class TxnCallback implements TransactionCallback {
        private int currentServerNum = -1;
        @SuppressWarnings("hiding")
        private final ReplicationStateInfo replicationStateInfo;
        private final String sourceSiteName;
        private final SiteInfo sourceSite;

        public TxnCallback(ReplicationStateInfo replicationStateInfo, String sourceSiteName, SiteInfo sourceSite) {
            this.replicationStateInfo = replicationStateInfo;
            this.sourceSiteName = sourceSiteName;
            this.sourceSite = sourceSite;
        }

        public void setServerNum(int newServerNum) {
            this.currentServerNum = newServerNum;
        }

        @Override
        public void processTransaction(final Transaction txn) throws HandleException {
            processTransaction(currentServerNum + ":" + sourceSiteName, txn);
        }

        @Override
        public void processTransaction(String queueName, final Transaction txn) throws HandleException {
            TransactionValidator.ValidationResult validationResult = null;
            if (replicationValidator != null) {
                validationResult = replicationValidator.validate(txn);
            }
            if (validationResult != null && !validationResult.isValid()) {
                String logMessage = "--Denied!";
                if (validationResult.getMessage() != null) logMessage += " (" + validationResult.getMessage() + ")";
                logMessage += txn;
                if (validationResult.getReport() != null) logMessage += "\n" + validationResult.getReport();
                System.err.println(logMessage);
                if (notifier != null) {
                    try {
                        TransactionValidationErrorMessage notification = new TransactionValidationErrorMessage(txn.values, txn, server.getSiteInfo(), server.getServerNum(), sourceSite, currentServerNum, validationResult.getMessage(), validationResult.getReport());
                        String notificationJson = GsonUtility.getNewGsonBuilder().setPrettyPrinting().create().toJson(notification);
                        notifier.sendNotification(notificationJson, "TransactionValidationErrorMessage");
                    } catch (Exception e) {
                        server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception while attempting notification: " + e);
                        e.printStackTrace();
                    }
                }
            } else if (replicationPrefixFilter != null && !replicationPrefixFilter.acceptTransaction(txn)) {
                System.err.println("--Transaction skipped by filter: " + txn);
            } else {
                final HandleStorage storage = server.getStorage();
                int priority = getIndexFromQueueName(queueName);
                System.err.println("--Processing " + txn);
                final byte[] handle = caseSensitive ? txn.handle : Util.upperCase(txn.handle);
                synchronized (server.getWriteLock(handle)) {
                    switch (txn.action) {
                    case Transaction.ACTION_CREATE_HANDLE:
                    case Transaction.ACTION_UPDATE_HANDLE:
                        runIfMoreRecent(() -> {
                            storage.createOrUpdateRecord(handle, txn.values);
                        }, handle, txn.date, priority, false);
                        break;
                    case Transaction.ACTION_DELETE_HANDLE:
                        runIfMoreRecent(() -> {
                            if (!storage.deleteHandle(handle)) {
                                server.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Warning: got delete-handle transaction for non-existent handle: " + Util.decodeString(txn.handle));
                            }
                        }, handle, txn.date, priority, false);
                        break;
                    case Transaction.ACTION_HOME_NA:
                        runIfMoreRecent(() -> {
                            storage.setHaveNA(handle, true);
                            server.adjustHomedPrefix(handle, true);
                        }, handle, txn.date, priority, true);
                        break;
                    case Transaction.ACTION_UNHOME_NA:
                        runIfMoreRecent(() -> {
                            storage.setHaveNA(handle, false);
                            server.adjustHomedPrefix(handle, false);
                        }, handle, txn.date, priority, true);
                        break;
                    default:
                        server.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Encountered unknown transaction type (" + txn.action + ") during replication for handle: " + Util.decodeString(txn.handle));
                    }
                }
            }
            //get the right queue by name and add the this txn
            TransactionQueuesInterface otherTransactionQueues = server.getAllOtherTransactionQueues();
            if (otherTransactionQueues != null) {
                try {
                    TransactionQueueInterface otherTransactionQueue = otherTransactionQueues.getOrCreateTransactionQueue(queueName);
                    otherTransactionQueue.addTransaction(txn);
                } catch (Exception e) {
                    throw new HandleException(HandleException.INTERNAL_ERROR, e);
                }
            }
            notifyQueueListeners(txn);
            // Note: currently it is important that replicationStateInfo.setLastTxnId is called *after* storage is updated, to ensure
            // consistency of proxied mirroring
            replicationStateInfo.setLastTxnId(queueName, txn.txnId);
        }

        @Override
        public void setQueueLastTimestamp(String queueName, long sourceDate) {
            replicationStateInfo.setLastTimestamp(queueName, sourceDate);
        }

        @Override
        public void finishProcessing(long date) {
            setQueueLastTimestamp(currentServerNum + ":" + sourceSiteName, date);
            finishProcessing();
        }

        @Override
        public void finishProcessing() {
            // do nothing
        }
    }
}

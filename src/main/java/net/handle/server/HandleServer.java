/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamObjectToJsonConverter;
import net.cnri.util.StreamTable;
import net.handle.security.*;
import net.handle.server.replication.NotifierInterface;
import net.handle.server.replication.ReplicationDaemon;
import net.handle.server.servletcontainer.HandleServerInterface;
import net.handle.server.servletcontainer.support.PreAuthenticatedChallengeAnswerRequest;
import net.handle.server.servletcontainer.support.PreAuthenticatedChallengeResponse;
import net.handle.server.txnlog.*;
import net.handle.util.AutoSelfSignedKeyManager;
import net.handle.util.X509HSCertificateGenerator;
import net.handle.apps.simple.SiteInfoConverter;
import net.handle.hdllib.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.interfaces.*;
import javax.crypto.spec.*;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class HandleServer extends AbstractServer implements HandleServerInterface, RequestProcessor {
    private static final boolean DEBUG_AUTHENTICATION = false;

    private static final byte MSG_INTERNAL_ERROR[] = Util.encodeString("Internal Error");
    private static final byte MSG_NOT_A_PRIMARY[] = Util.encodeString("Not a primary server");
    private static final byte MSG_SERVER_TEMPORARILY_DISABLED[] = Util.encodeString("Server temporarily disabled");
    private static final byte MSG_WRONG_SERVER_HASH[] = Util.encodeString("Request was hashed incorrectly");
    private static final byte MSG_NA_NOT_HOMED_HERE[] = Util.encodeString("That prefix doesn't live here");
    private static final byte MSG_INDEXES_MUST_BE_POSITIVE[] = Util.encodeString("Value indexes must be positive");
    private static final byte MSG_EMPTY_VALUE_LIST[] = Util.encodeString("Value list was empty");
    private static final byte MSG_READ_ONLY_VALUE[] = Util.encodeString("Value is read-only");
    private static final byte MSG_NOT_A_NA_HANDLE[] = Util.encodeString("Handle is not a prefix handle");
    private static final byte MSG_INVALID_ENCODING[] = Util.encodeString("Invalid UTF8 encoding");
    private static final byte MSG_SERVER_BACKUP[] = Util.encodeString("Server can only resolve handles due to maintenance. Come back later");

    private static final byte MSG_INVALID_SESSION_OR_TIMEOUT[] = Util.encodeString("Invalid session id or session time out. Please try again.");
    private static final byte MSG_NEED_LIST_HDLS_PERM[] = Util.encodeString("This server does not support the list handles operation.");
    private static final byte MSG_NO_TXN_QUEUE[] = Util.encodeString("This server does not support replication.");
    private static final byte MSG_NO_NEXT_TXN_ID[] = Util.encodeString("Next txn id no longer supported; upgrade required");
    private static final byte SERVER_STATUS_HANDLE[] = Util.encodeString("0.SITE/status");
    private static final byte SERVER_STATUS_HDL_TYPE[] = Util.encodeString("CNRI.SERVER_STATUS");
    private static final byte REPLICATION_STATUS_HDL_TYPE[] = Util.encodeString("CNRI.REPLICATION_STATUS");

    private static final byte MSG_SESSION_REQUIRED[] = Util.encodeString("Sessions are required for administration on this server");

    public static final String CASE_SENSITIVE = "case_sensitive";
    public static final String ENABLE_STATUS_HDL = "enable_status_handle";
    public static final String STATUS_HDL_ADMINS = "status_handle_admins";
    public static final String ENABLE_MONITOR_DAEMON = "enable_monitor_daemon";
    public static final String ENABLE_HOMED_PREFIX_LOCAL_SITES = "enable_homed_prefix_na_lookup_optimization";
    public static final String SERVER_ADMIN_FULL_ACCESS = "server_admin_full_access";
    public static final String ANONYMOUS_FULL_ACCESS = "anonymous_admin_full_access";
    public static final String MAX_AUTH_TIME = "max_auth_time";
    public static final String THIS_SERVER_ID = "this_server_id";
    public static final String IS_PRIMARY = "is_primary";
    public static final String SERVER_ADMINS = "server_admins";
    public static final String HOME_ONLY_ADMINS = "home_only_admins";
    public static final String BACKUP_ADMINS = "backup_admins";
    public static final String REPLICATION_ADMINS = "replication_admins";
    public static final String DO_RECURSION = "allow_recursion";
    public static final String DO_RECURSION_FOR_LEGACY_PREFIX_REFERRAL = "perform_recursion_for_legacy_prefix_referral";
    public static final String ALLOW_NA_ADMINS = "allow_na_admins";
    public static final String READ_ONLY_TXN_QUEUE = "read_only_txn_queue";
    public static final String FILE_WRITE_NO_SYNC = "file_write_no_sync";
    public static final String ALLOW_LIST_HANDLES = "allow_list_hdls";
    public static final String PREFERRED_GLOBAL = "preferred_global";
    public static final String MAX_SESSION_TIME = "max_session_time";
    public static final String REQUIRE_SESSIONS = "require_sessions";
    public static final String ENCRYPTION_ALGORITHM = "encryption_alg";
    public static final String AUTO_HOMED_PREFIXES = "auto_homed_prefixes";
    public static final String ENABLE_TXN_QUEUE = "enable_txn_queue";

    public static final String TEMPLATE_DELIM_CONFIG_KEY = "template_delimiter";
    public static final String NAMESPACE_CONFIG_KEY = "namespace";
    public static final String NAMESPACE_OVERRIDE_CONFIG_KEY = "template_ns_override";

    public static final String DB_TXN_QUEUE_DIR = "dbtxns";
    public static final String TXN_QUEUE_DIR = "txns";
    public static final String TXN_ID_FILE = "txn_id";
    public static final String STORAGE_FILE = "handles.jdb";
    public static final String NA_STORAGE_FILE = "nas.jdb";
    public static final String CACHE_STORAGE_FILE = "cache.jdb";
    public static final String STORAGE_FILE_BACKUP = "handles.jdb.backup";
    public static final String NA_STORAGE_FILE_BACKUP = "nas.jdb.backup";
    public static final String SITE_INFO_FILE = "siteinfo.bin";
    public static final String SITE_INFO_JSON_FILE = "siteinfo.json";
    public static final String PRIVATE_KEY_FILE = "privkey.bin";
    public static final String PUBLIC_KEY_FILE = "pubkey.bin";
    public static final String DO_REPLICATION = "do_replication";

    public static final int RECURSION_LIMIT = 10;
    public static final int LIST_HANDLES_PER_MSG = 50;

    public static final String DEFAULT_ENC_ALG = "AES"; // options: DES, AES, DESEDE

    private static final int NUM_SERVER_SIGNATURES = 50;

    private static final int DEL_HANDLE_PERM[] = { AdminRecord.DELETE_HANDLE };
    private static final int ADD_HANDLE_PERM[] = { AdminRecord.ADD_HANDLE };
    private static final int READ_VAL_PERM[] = { AdminRecord.READ_VALUE };
    private static final int ADD_ADM_PERM[] = { AdminRecord.ADD_ADMIN };
    private static final int ADD_VAL_PERM[] = { AdminRecord.ADD_VALUE };
    private static final int REM_VAL_PERM[] = { AdminRecord.REMOVE_VALUE };
    private static final int REM_ADM_PERM[] = { AdminRecord.REMOVE_ADMIN };
    private static final int REM_ADM_AND_VAL_PERM[] = { AdminRecord.REMOVE_ADMIN, AdminRecord.REMOVE_VALUE };
    private static final int MOD_ADM_PERM[] = { AdminRecord.MODIFY_ADMIN };
    private static final int MOD_VAL_PERM[] = { AdminRecord.MODIFY_VALUE };
    private static final int ADM_TO_VAL_PERM[] = { AdminRecord.REMOVE_ADMIN, AdminRecord.MODIFY_VALUE };
    private static final int VAL_TO_ADM_PERM[] = { AdminRecord.MODIFY_VALUE, AdminRecord.ADD_ADMIN };

    private static final int ADD_SUB_NA_PERM[] = { AdminRecord.ADD_DERIVED_PREFIX };
    private static final int LIST_HDLS_PERM[] = { AdminRecord.LIST_HANDLES };

    private static final byte SIGN_TEST[] = Util.encodeString("Testing...1..2..3");

    //private volatile boolean keepRunning = true; // moved to AbstractServer
    private volatile boolean serverEnabled = true;
    private static int nextAuthId = 0;
    private long maxAuthTime;
    protected HandleStorage storage;
    private final ConcurrentMap<Integer, ChallengeResponseInfo> pendingAuthorizations = new ConcurrentHashMap<>();
    private boolean caseSensitive = false;
    private boolean serverAdminFullAccess = false;
    private boolean wideOpenAccessAllowed = false;
    ValueReference serverAdmins[] = {};
    ValueReference statusHandleAdmins[] = {};
    ValueReference homeOnlyAdmins[] = {};
    ValueReference backupAdmins[] = {};
    ValueReference replicationAdmins[] = {};
    private boolean requireSessions = false;

    // legacy
    private String configTemplateDelimiter = null; // overridden in constructor
    private NamespaceInfo configNamespaceInfo = null;
    private boolean configNamespaceOverride = false;

    TransactionQueuesInterface allOtherTransactionQueues = null;
    TransactionQueueInterface txnQueue;
    TransactionQueuePruner txnQueuePruner;
    boolean enableTxnQueue = true;

    private int currentSigIndex = 0;
    SiteInfo thisSite = null;
    private int thisServerNum = -1;

    private TransactionValidator replicationValidator;

    PublicKey publicKey = null;
    PrivateKey privateKey = null;
    X509Certificate hdlTcpCertificate = null;
    X509Certificate[] certificateChain = null;
    PrivateKey certificatePrivateKey = null;
    Signature[] serverSignaturesSha1 = null;
    Signature[] serverSignaturesSha256 = null;
    private boolean allowRecursiveQueries = false;
    private boolean performRecursionForOldClientsRequestingReferredPrefixes = true;
    private boolean allowNAAdmins = true;
    private boolean allowListHdls = true;
    private String preferredGlobal = null;
    boolean isPrimary = false;
    private boolean doReplication = false;
    private boolean keepOtherTransactions = false;
    private AtomicLong nextTxnId = new AtomicLong();
    private Map<Long, Long> transactionsInProgress = new ConcurrentHashMap<>();

    private ReplicationDaemon replicationDaemon;
    private long startTime = 0;
    private final AtomicLong numRequests = new AtomicLong();
    private final AtomicLong numResolutionRequests = new AtomicLong();
    private final AtomicLong numAdminRequests = new AtomicLong();
    private final AtomicLong numTxnRequests = new AtomicLong();
    private boolean enableStatusHandle = true;
    private boolean enableHomedPrefixNaLookupOptimization = true;
    private int encryptionAlgorithm = HdlSecurityProvider.ENCRYPT_ALG_AES;
    private Object lockHash[] = { new Object() };

    // a server side session manager to manage session
    private final SessionManager sessions = new SessionManager();

    private MonitorDaemon monitorDaemon;

    private int replicationPriority;
    private String replicationSiteName;

    private PublicKey getPublicKeyFromFile() {
        File publicKeyFile = new File(getConfigDir(), PUBLIC_KEY_FILE);
        PublicKey result = null;
        try {
            result = Util.getPublicKeyFromFile(publicKeyFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void buildCertificate(String id, PublicKey pubKey, PrivateKey privKey) {
        try {
            hdlTcpCertificate = X509HSCertificateGenerator.generate((String) null, pubKey, privKey);
            X509Certificate[] certChain = loadServerCertificateChainFromFile();
            boolean isDsaKey = "DSA".equals(pubKey.getAlgorithm());
            if (certChain != null && certChain.length > 0) {
                if (!isDsaKey) {
                    boolean isCertificateKeyIdenticalToServerKey = Util.equals(Util.getBytesFromPublicKey(certChain[0].getPublicKey()), Util.getBytesFromPublicKey(pubKey));
                    if (!isCertificateKeyIdenticalToServerKey) {
                        logError(ServerLog.ERRLOG_LEVEL_INFO, "Warning: certificate key from serverCertificate.pem does not match server key");
                    }
                }
                certificateChain = certChain;
                getServerCertificatePrivateKey();
            } else {
                boolean newKey = false;
                if (isDsaKey) {
                    KeyPair keyPair = generateKeyPairForCertificate();
                    pubKey = keyPair.getPublic();
                    privKey = keyPair.getPrivate();
                    newKey = true;
                }
                X509Certificate cert = generateCertificateAndWriteToFile(id, pubKey, privKey, newKey);
                certificatePrivateKey = privKey;
                certificateChain = new X509Certificate[] { cert };
            }
        } catch (Exception e) {
            e.printStackTrace();
            certificateChain = new X509Certificate[] { hdlTcpCertificate };
            certificatePrivateKey = privateKey;
        }
    }

    private X509Certificate generateCertificateAndWriteToFile(String id, PublicKey pubKey, PrivateKey privKey, boolean newKey) {
        File certFile = new File(getConfigDir(), "serverCertificate.pem");
        AutoSelfSignedKeyManager keyManager = new AutoSelfSignedKeyManager(id, pubKey, privKey);
        X509Certificate cert = keyManager.getCertificate();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(certFile), "UTF-8")) {
            X509HSCertificateGenerator.writeCertAsPem(writer, cert);
            if (newKey) {
                writeServerCertificatePrivateKeyFile(privKey);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cert;
    }

    private X509Certificate[] loadServerCertificateChainFromFile() {
        File certFile = new File(getConfigDir(), "serverCertificate.pem");
        if (certFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(certFile), "UTF-8")) {
                return X509HSCertificateGenerator.readCertChainAsPem(reader);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private KeyPair generateKeyPairForCertificate() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        return keyPair;
    }

    private void writeServerCertificatePrivateKeyFile(PrivateKey privKey) throws FileNotFoundException, IOException, Exception {
        FileOutputStream out = new FileOutputStream(new File(getConfigDir(), "serverCertificatePrivateKey.bin"));
        out.write(new byte[] { 0, 0, 0, 1 });
        out.write(Util.getBytesFromPrivateKey(privKey));
        out.close();
    }

    private void getServerCertificatePrivateKey() throws IOException, Exception, HandleException, InvalidKeySpecException {
        File privateKeyFile = new File(getConfigDir(), "serverCertificatePrivateKey.bin");
        if (privateKeyFile.exists()) {
            byte[] encKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
            byte[] secKey = null;
            if (Util.requiresSecretKey(encKeyBytes)) {
                secKey = Util.getPassphrase("Enter the passphrase for this server's HTTPS certificate private key: ");
            }
            certificatePrivateKey = Util.getPrivateKeyFromBytes(Util.decrypt(encKeyBytes, secKey));
        } else {
            certificatePrivateKey = privateKey;
        }
    }

    public X509Certificate getHdlTcpCertificate() {
        return hdlTcpCertificate;
    }

    @Override
    public X509Certificate getCertificate() {
        if (certificateChain == null || certificateChain.length == 0) return null;
        return certificateChain[0];
    }

    @Override
    public X509Certificate[] getCertificateChain() {
        return certificateChain;
    }

    @Override
    public PrivateKey getCertificatePrivateKey() {
        return certificatePrivateKey;
    }

    @Override
    public PublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    protected HandleServer() {
        // for testing
    }

    public HandleServer(Main main, StreamTable config, HandleResolver resolver) throws Exception {
        super(main, config, resolver);
        this.startTime = System.currentTimeMillis();
        this.enableStatusHandle = config.getBoolean(ENABLE_STATUS_HDL, true);
        this.enableHomedPrefixNaLookupOptimization = config.getBoolean(ENABLE_HOMED_PREFIX_LOCAL_SITES, true);

        if (config.containsKey(DO_RECURSION)) allowRecursiveQueries = config.getBoolean(DO_RECURSION);

        if (config.containsKey(DO_RECURSION_FOR_LEGACY_PREFIX_REFERRAL)) performRecursionForOldClientsRequestingReferredPrefixes = config.getBoolean(DO_RECURSION_FOR_LEGACY_PREFIX_REFERRAL);

        if (config.containsKey(PREFERRED_GLOBAL)) {
            preferredGlobal = config.getStr(PREFERRED_GLOBAL);
            Properties p = new Properties(System.getProperties());
            p.put("hdllib.preferredGlobal", preferredGlobal);
            System.setProperties(p);
        }

        initAdmins();
        initThisSiteAndServerNum();
        initKeysAndCertsAndSignatures();
        initSessionManager();

        doReplication = config.getBoolean(DO_REPLICATION, thisSite.multiPrimary || !isPrimary);

        if (doReplication) {
            replicationDaemon = new ReplicationDaemon(this, config, getConfigDir());
        }

        if (config.containsKey(ENABLE_TXN_QUEUE)) {
            enableTxnQueue = config.getBoolean(ENABLE_TXN_QUEUE);
        }
        if (enableTxnQueue) {
            File txnDir = new File(getConfigDir(), TXN_QUEUE_DIR);
            if (isPrimary) {
                // create the transaction queue.
                initTxnQueue(txnDir);
            }
            keepOtherTransactions = config.getBoolean(ReplicationDaemon.REPLICATION_KEEP_OTHER_TRANSACTIONS, true);
            if (isPrimary || keepOtherTransactions) {
                initOtherTxnQueues(txnDir);
            }
            if (txnQueue != null || allOtherTransactionQueues != null) {
                int daysToKeep = config.getInt("txnlog_num_days_to_keep", 0);
                if (daysToKeep > 0) {
                    txnQueuePruner = new TransactionQueuePruner(txnQueue, allOtherTransactionQueues, daysToKeep);
                    txnQueuePruner.start();
                }
            }
        }

        caseSensitive = config.getBoolean(CASE_SENSITIVE);

        configNamespaceOverride = config.getBoolean(NAMESPACE_OVERRIDE_CONFIG_KEY);

        configTemplateDelimiter = config.getStr(TEMPLATE_DELIM_CONFIG_KEY, "").trim();
        String configNamespaceInfoStr = config.getStr(NAMESPACE_CONFIG_KEY, null);
        if (configNamespaceInfoStr != null) {
            configNamespaceInfo = new NamespaceInfo(configNamespaceInfoStr.getBytes("UTF-8"));
        }

        // load the database/storage system
        storage = HandleStorageFactory.getStorage(getConfigDir(), config, isPrimary);

        try {
            maxAuthTime = Long.parseLong(String.valueOf(config.get(MAX_AUTH_TIME)).trim());
        } catch (Exception e) {
            System.err.println("Invalid authentication time allowance.  " + "Using default (20 seconds)");
            maxAuthTime = 20000;
        }

        // add local site info to resolver configuration to avoid trips to global
        // when we are only talking to our peers
        if (enableHomedPrefixNaLookupOptimization) {
            final SiteInfo ss[] = { thisSite };
            storage.scanNAs(handle -> resolver.getConfiguration().setLocalSites(Util.decodeString(handle), ss));
        }

        // expand the lockHash so that more write operations can happen in parallel
        lockHash = new Object[256];
        for (int i = 0; i < lockHash.length; i++) {
            lockHash[i] = new Object();
        }

        ChallengeResponse.initializeRandom();
    }

    private void initAdmins() throws Exception {
        if (config.containsKey(SERVER_ADMIN_FULL_ACCESS)) serverAdminFullAccess = config.getBoolean(SERVER_ADMIN_FULL_ACCESS);
        if (config.containsKey(ANONYMOUS_FULL_ACCESS)) wideOpenAccessAllowed = config.getBoolean(ANONYMOUS_FULL_ACCESS);
        if (config.containsKey(ALLOW_LIST_HANDLES)) allowListHdls = config.getBoolean(ALLOW_LIST_HANDLES);
        if (config.containsKey(ALLOW_NA_ADMINS)) allowNAAdmins = config.getBoolean(ALLOW_NA_ADMINS);
        serverAdmins = getAdminListFromConfig(config, SERVER_ADMINS);
        homeOnlyAdmins = getAdminListFromConfig(config, HOME_ONLY_ADMINS);
        statusHandleAdmins = getAdminListFromConfig(config, STATUS_HDL_ADMINS);
        backupAdmins = getAdminListFromConfig(config, BACKUP_ADMINS);
        replicationAdmins = getAdminListFromConfig(config, REPLICATION_ADMINS);
    }

    private void initSessionManager() {
        requireSessions = config.getBoolean(REQUIRE_SESSIONS, false);

        SessionManager.initializeSessionKeyRandom();

        sessions.checkTimeoutSession();

        int maxSessionTimeout = Common.DEFAULT_SESSION_TIMEOUT;
        if (config.containsKey(MAX_SESSION_TIME)) {
            try {
                maxSessionTimeout = Integer.parseInt(String.valueOf(config.get(MAX_SESSION_TIME)).trim());
            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Invalid session timeout allowance.  Using default (24 hours)");
            }
        }
        if (maxSessionTimeout < 60) { //less than one minute, set it to 1 minute
            maxSessionTimeout = 60;
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Adjusted session timeout allowance. Using 1 minute.");
        }
        SessionInfo.setDefaultTimeout(maxSessionTimeout);
    }

    private void initKeysAndCertsAndSignatures() throws Exception {
        String encAlg = config.getStr(ENCRYPTION_ALGORITHM, DEFAULT_ENC_ALG).toLowerCase().trim();
        if (encAlg.equals("des")) {
            encryptionAlgorithm = HdlSecurityProvider.ENCRYPT_ALG_DES;
        } else if (encAlg.equals("desede")) {
            encryptionAlgorithm = HdlSecurityProvider.ENCRYPT_ALG_DESEDE;
        } else if (encAlg.equals("aes")) {
            encryptionAlgorithm = HdlSecurityProvider.ENCRYPT_ALG_AES;
        } else {
            throw new Exception("Invalid encryption algorithm: '" + encAlg + "'; Please use either des, desede, or aes");
        }
        // this secret key bytes will be used to encrypt/decrypt both server key
        // and session exchange key
        byte secKey[] = null;
        try {
            publicKey = getPublicKeyFromFile();

            // read the private key from the private key file...
            File privateKeyFile = new File(getConfigDir(), PRIVATE_KEY_FILE);
            if (!privateKeyFile.exists() || !privateKeyFile.canRead()) {
                System.err.println("Missing or inaccessible private key file: " + privateKeyFile.getAbsolutePath());
                throw new Exception("Missing or inaccessible private key file: " + privateKeyFile.getAbsolutePath());
            }
            byte encKeyBytes[] = new byte[(int) privateKeyFile.length()];
            FileInputStream in = new FileInputStream(privateKeyFile);
            try {
                int r, n = 0;
                while (n < encKeyBytes.length && (r = in.read(encKeyBytes, n, encKeyBytes.length - n)) >= 0) {
                    n += r;
                }
            } finally {
                in.close();
            }

            byte keyBytes[] = null;
            if (Util.requiresSecretKey(encKeyBytes)) {
                // ask for a secret key to decrypt the private key get the passphrase
                // and decrypt the server's private key, it will also be used for
                // encrpting/decrypt the RSA/DH session exchange keys
                secKey = Util.getPassphrase("Enter the passphrase for this server's authentication private key: ");
            }

            keyBytes = Util.decrypt(encKeyBytes, secKey);

            try {
                privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
            } catch (Exception e) {
                System.err.println("\n**********************************************" + "****************************" + "\nError parsing private key, please make sure " + "the passphrase is correct.\n" + "***************************"
                    + "***********************************************\n");
                throw e;
            }

            //clear the private key bytes in memory
            for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) 0;

            buildCertificate(null, publicKey, privateKey);

            // create a bunch of the signature objects so that signing responses
            // doesn't become a bottleneck
            serverSignaturesSha1 = new Signature[NUM_SERVER_SIGNATURES];
            for (int i = 0; i < serverSignaturesSha1.length; i++) {
                serverSignaturesSha1[i] = Signature.getInstance(Util.getSigIdFromHashAlgId(Common.HASH_ALG_SHA1, privateKey.getAlgorithm()));
                serverSignaturesSha1[i].initSign(privateKey);
            }

            serverSignaturesSha256 = new Signature[NUM_SERVER_SIGNATURES];
            for (int i = 0; i < serverSignaturesSha256.length; i++) {
                serverSignaturesSha256[i] = Signature.getInstance(Util.getSigIdFromHashAlgId(Common.HASH_ALG_SHA256, privateKey.getAlgorithm()));
                serverSignaturesSha256[i].initSign(privateKey);
            }

            // the signature has been initialized... now let's verify that it matches
            // the signature in the site information for this server.
            PublicKey pubKey = thisSite.servers[thisServerNum].getPublicKey();

            // generate a test signature,
            serverSignaturesSha1[0].update(SIGN_TEST);
            byte testSig[] = serverSignaturesSha1[0].sign();

            // verify the test signature
            Signature verifier = Signature.getInstance(serverSignaturesSha1[0].getAlgorithm());
            verifier.initVerify(pubKey);

            verifier.update(SIGN_TEST);
            if (!verifier.verify(testSig)) {
                throw new Exception("Private key doesn't match public key from site info!");
            }

        } catch (Exception e) {
            System.err.println("Unable to initialize server signature object: " + e);
            e.printStackTrace(System.err);
            throw e;
        }
        // clear the secret key
        for (int i = 0; secKey != null && i < secKey.length; i++) {
            secKey[i] = (byte) 0;
        }
    }

    private void initThisSiteAndServerNum() throws Exception {
        // get the site info that describes this site that this server is
        // a part of
        try {
            // get the index of this server in the site info
            int thisId = Integer.parseInt((String) config.get(THIS_SERVER_ID));

            // get the site information from the site-info file
            SiteInfo site;
            File siteInfoFile = new File(getConfigDir(), SITE_INFO_FILE);
            if (!siteInfoFile.exists() || !siteInfoFile.canRead()) {
                File siteInfoJsonFile = new File(getConfigDir(), SITE_INFO_JSON_FILE);
                if (!siteInfoJsonFile.exists() || !siteInfoJsonFile.canRead()) {
                    System.err.println("Missing or inaccessible site info file: " + siteInfoFile.getAbsolutePath() + "/" + siteInfoJsonFile.getName());
                    throw new Exception("Missing or inaccessible site info file: " + siteInfoFile.getAbsolutePath() + "/" + siteInfoJsonFile.getName());
                } else {
                    byte[] siteInfoBytes = Util.getBytesFromFile(siteInfoJsonFile);
                    try {
                        site = SiteInfoConverter.convertToSiteInfo(new String(siteInfoBytes, "UTF-8"));
                    } catch (Throwable t) {
                        System.err.println("Missing or inaccessible site info file: " + siteInfoFile.getAbsolutePath() + "/" + siteInfoJsonFile.getName());
                        throw new Exception("Missing or inaccessible site info file: " + siteInfoFile.getAbsolutePath() + "/" + siteInfoJsonFile.getName(), t);
                    }
                }
            } else {
                site = new SiteInfo();
                byte siteInfoBuf[] = new byte[(int) siteInfoFile.length()];
                InputStream in = new FileInputStream(siteInfoFile);
                try {
                    int r, n = 0;
                    while ((r = in.read(siteInfoBuf, n, siteInfoBuf.length - n)) > 0) {
                        n += r;
                    }
                } finally {
                    in.close();
                }
                Encoder.decodeSiteInfoRecord(siteInfoBuf, 0, site);
            }

            thisServerNum = -1;
            for (int i = 0; i < site.servers.length; i++) {
                if (site.servers[i].serverId == thisId) {
                    thisServerNum = i;
                }
            }

            if (thisServerNum < 0) {
                throw new Exception("Server ID " + thisId + " does not exist in site!");
            }
            thisSite = site;
        } catch (Exception e) {
            System.err.println("Invalid site/server specification: " + e);
            throw e;
        }
        isPrimary = thisSite.isPrimary;
    }

    @Override
    public void start() {
        super.start();

        if (config.getBoolean(ENABLE_MONITOR_DAEMON, false)) {
            monitorDaemon = new MonitorDaemon(60, startTime, numRequests, numResolutionRequests, numAdminRequests, numTxnRequests, getConfigDir());
            monitorDaemon.start();
        }

        // start thread to purge timed-out pendingAuthorizations.
        ChallengePurgeThread cpt = new ChallengePurgeThread();
        cpt.setDaemon(true);
        cpt.setPriority(Thread.MIN_PRIORITY);
        cpt.start();

        if (isPrimary) {
            applyAutoHomedPrefixes();
        }

        if (doReplication) {
            System.err.println("starting replication thread");
            replicationDaemon.setDaemon(true);
            replicationDaemon.setPriority(Thread.MIN_PRIORITY);
            replicationDaemon.start();
        }
    }

    @Override
    public void registerInternalTransactionValidator(@SuppressWarnings("hiding") TransactionValidator replicationValidator) {
        this.replicationValidator = replicationValidator;
    }

    @Override
    public void registerReplicationTransactionValidator(@SuppressWarnings("hiding") TransactionValidator replicationValidator) {
        if (replicationDaemon != null) {
            replicationDaemon.registerReplicationTransactionValidator(replicationValidator);
        }
    }

    @Override
    public void registerReplicationErrorNotifier(NotifierInterface notifier) {
        if (replicationDaemon != null) {
            replicationDaemon.registerReplicationErrorNotifier(notifier);
        }
    }

    public TransactionQueuesInterface getAllOtherTransactionQueues() {
        return allOtherTransactionQueues;
    }

    private static List<String> getAutoHomedPrefixListFromConfig(StreamTable config) {
        List<String> result = new ArrayList<>();
        if (config.containsKey(AUTO_HOMED_PREFIXES)) {
            Vector<?> autoHomedPrefixesVect = (Vector<?>) config.get(AUTO_HOMED_PREFIXES);
            for (int i = 0; i < autoHomedPrefixesVect.size(); i++) {
                String prefixStr = String.valueOf(autoHomedPrefixesVect.elementAt(i));
                result.add(prefixStr);
            }
        }
        return result;
    }

    private void applyAutoHomedPrefixes() {
        List<String> autoHomedPrefixes = getAutoHomedPrefixListFromConfig(config);
        for (String na : autoHomedPrefixes) {
            if (HSG.DEFAULT_HOMED_PREFIX.equals(na)) continue;
            byte[] naBytes = Util.encodeString(na);
            if (!Util.hasSlash(naBytes)) naBytes = Util.convertSlashlessHandleToZeroNaHandle(naBytes);
            try {
                if (!storageHaveNA(naBytes)) {
                    doHomeNA(naBytes);
                }
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Unable to \"home\" prefix \"" + na + "\" after transaction was logged! - " + e);
                e.printStackTrace(System.err);
                return;
            }
        }
    }

    private static ValueReference[] getAdminListFromConfig(StreamTable config, String key) throws Exception {
        ValueReference[] valRefs = {};
        if (config.containsKey(key)) {
            try {
                Vector<?> adminVect = (Vector<?>) config.get(key);
                valRefs = new ValueReference[adminVect.size()];
                for (int i = 0; i < adminVect.size(); i++) {
                    String adminStr = String.valueOf(adminVect.elementAt(i));
                    valRefs[i] = ValueReference.fromString(adminStr);
                    if (valRefs[i] == null) throw new Exception("Invalid administrator ID: \"" + adminStr + "\"");
                }
            } catch (Exception e) {
                throw new Exception("Error processing administrator list \"" + key + "\": " + e);
            }
        }
        return valRefs;
    }

    public void initTxnQueue(File txnDir) throws Exception {
        // set up the directory for the replication transaction queue
        if (!txnDir.exists()) {
            txnDir.mkdirs();
        }
        if (isConcatenatedQueueNeeded(txnDir)) {
            boolean readOnly = config.getBoolean(READ_ONLY_TXN_QUEUE, false);
            TransactionQueueInterface oldQueue = new FileBasedTransactionQueue(txnDir, readOnly);
            TransactionQueueInterface currentQueue = new BdbjeTransactionQueue(txnDir, config);
            this.txnQueue = new ConcatenatedTransactionQueue(oldQueue, currentQueue);
        } else {
            this.txnQueue = new BdbjeTransactionQueue(txnDir, config);
        }
        this.nextTxnId.set(this.txnQueue.getLastTxnId());
    }

    private void initOtherTxnQueues(File txnDir) throws Exception {
        // set up the directory for the replication transaction queue
        if (!txnDir.exists()) {
            txnDir.mkdirs();
        }
        allOtherTransactionQueues = new BdbjeTransactionQueues(txnDir, txnQueue, config);
    }

    public static boolean isConcatenatedQueueNeeded(File txnDir) {
        FilenameFilter filter = new TxnQueueFilter();
        File[] queueFiles = txnDir.listFiles(filter);
        return queueFiles.length != 0;
    }

    private static class TxnQueueFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(".q"));
        }
    }

    /**
     * Tell the server to re-dump all handles from a primary handle server
     */
    @Override
    public void dumpHandles() throws HandleException, IOException {
        replicationDaemon.dumpHandles(true, null);
    }

    /** Return the siteinfo for this server. */
    @Override
    public SiteInfo getSiteInfo() {
        return thisSite;
    }

    @Override
    public int getServerNum() {
        return thisServerNum;
    }

    /** Return the server information for this server */
    @Override
    public ServerInfo getServerInfo() {
        return thisSite.servers[thisServerNum];
    }

    @Override
    public HandleStorage getStorage() {
        return this.storage;
    }

    @Override
    public ReplicationDaemonInterface getReplicationDaemon() {
        return this.replicationDaemon;
    }

    /** Get handle values, possibly modified by the template system */
    @Override
    public final byte[][] getRawHandleValuesWithTemplate(byte inHandle[], int indexList[], byte typeList[][], short recursionCount) throws HandleException {
        byte[][] values = storageGetRawHandleValues(caseSensitive ? inHandle : Util.upperCase(inHandle), indexList, typeList);
        if (values != null) return values;

        // if there is no overridden handle value, return a calculated value
        // config value overrides
        String templateDelimiter = null;
        NamespaceInfo ns = null;
        if (!configNamespaceOverride) {
            ResolutionRequest resReq = new ResolutionRequest(inHandle, null, null, null);
            // request used for service information lookup
            resReq.certify = true;
            resReq.recursionCount = recursionCount;
            // use namespace from prefix, then namespace from config.dct, then template_delimiter from config.dct
            // TODO this interacts oddly with independent handle service; must put prefix in the independent server
            try {
                ns = resolver.getNamespaceInfo(resReq);
            } catch (HandleException e) {
                // ignored
                //            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unable to get namespace info for prefix of " + Util.decodeString(inHandle));
                //            e.printStackTrace();
            }
        }
        if (ns == null) ns = configNamespaceInfo;
        if (ns != null) templateDelimiter = ns.templateDelimiter();
        if (templateDelimiter == null || templateDelimiter.length() == 0) templateDelimiter = configTemplateDelimiter;
        if (templateDelimiter == null || templateDelimiter.length() == 0) return null;

        byte[] naHandle = Util.getZeroNAHandle(inHandle);
        String naHandleStr;
        // find the first delimiter after the NA portion of the handle.
        if (Util.startsWith(naHandle, Common.NA_HANDLE_PREFIX)) naHandleStr = Util.decodeString(naHandle, Common.NA_HANDLE_PREFIX.length, naHandle.length - Common.NA_HANDLE_PREFIX.length);
        else naHandleStr = Util.decodeString(naHandle);
        String inHandleStr = Util.decodeString(inHandle);
        int startDelim = inHandleStr.indexOf(templateDelimiter, naHandleStr.length());
        if (startDelim < 0) return null;

        boolean allValues = (indexList == null || indexList.length == 0) && (typeList == null || typeList.length == 0);

        String baseHandleStr = inHandleStr.substring(0, startDelim);
        String afterDelimStr = inHandleStr.substring(startDelim + templateDelimiter.length());
        byte[] baseHandle = Util.encodeString(baseHandleStr);
        if (thisSite.servers.length > 1 && thisSite.determineServerNum(baseHandle) != thisServerNum) {
            ResolutionRequest req = new ResolutionRequest(baseHandle, null, null, null);
            req.setSupportedProtocolVersion(thisSite);
            AbstractResponse response = resolver.sendRequestToServer(req, thisSite, thisSite.determineServer(baseHandle));

            if (response.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {
                return null;
            } else if (response instanceof ErrorResponse) {
                String msg = Util.decodeString(((ErrorResponse) response).message);

                throw new HandleException(HandleException.INTERNAL_ERROR, AbstractMessage.getResponseCodeMessage(response.responseCode) + ": " + msg);
            }

            values = ((ResolutionResponse) response).values;
        } else {
            values = storageGetRawHandleValues(caseSensitive ? baseHandle : Util.upperCase(baseHandle), null, null);
        }

        // allowing template handles to be constructed even without a base...
        if (values == null) {
            if (ns == null) return null;
            values = new byte[0][];
        }

        HandleValue[] handleValues = new HandleValue[values.length];
        for (int i = 0; i < values.length; i++) {
            handleValues[i] = new HandleValue();
            Encoder.decodeHandleValue(values[i], 0, handleValues[i]);
        }
        NamespaceInfo thisNs = Util.getNamespaceFromValues(baseHandleStr, handleValues);
        if (thisNs != null) {
            thisNs.setParentNamespace(ns);
            ns = thisNs;
        }

        if (ns == null || ns.getInheritedTag(NamespaceInfo.TEMPLATE_TAG) == null) {
            if (allValues) return values;
            List<byte[]> res = new ArrayList<>(values.length);
            for (byte[] value : values) {
                byte[] clumpType = Encoder.getHandleValueType(value, 0);
                int clumpIndex = Encoder.getHandleValueIndex(value, 0);

                if (Util.isParentTypeInArray(typeList, clumpType) || Util.isInArray(indexList, clumpIndex)) {
                    res.add(value);
                }
            }
            return res.toArray(new byte[0][]);
        }

        HandleValue[] resVals = ns.templateConstruct(handleValues, inHandleStr, baseHandleStr, afterDelimStr, caseSensitive, resolver, recursionCount);
        if (resVals == null) return null;
        List<byte[]> res = new ArrayList<>(resVals.length);
        for (HandleValue resVal : resVals) {
            if (allValues || Util.isParentTypeInArray(typeList, resVal.getType()) || Util.isInArray(indexList, resVal.getIndex())) {
                byte[] buf = new byte[Encoder.calcStorageSize(resVal)];
                Encoder.encodeHandleValue(buf, 0, resVal);
                res.add(buf);
            }
        }
        return res.toArray(new byte[0][]);
    }

    /****************************************************************************
     * Thread that purges old challenges after a specified period of time
     * ////Currently hard-coded to 30 seconds - this should be configurable!!!!
     ****************************************************************************/
    private class ChallengePurgeThread extends Thread {
        @Override
        public void run() {
            while (keepRunning) {
                try {
                    Thread.sleep(30000);
                    Iterator<ChallengeResponseInfo> iter = pendingAuthorizations.values().iterator();
                    while (iter.hasNext()) {
                        ChallengeResponseInfo cri = iter.next();
                        if (cri == null || cri.hasExpired()) {
                            iter.remove();
                        }
                    }

                } catch (Throwable e) {
                    logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Error purging pending authentications: " + e);
                }
            }
        }
    }

    //check whether the session id indicate a pending challenge
    private boolean pendingChallenge(int sessionId) {
        if (sessionId < 0) return false;
        ChallengeResponseInfo crInfo = pendingAuthorizations.get(sessionId);
        if (crInfo != null) return true;
        else return false;
    }

    @Override
    public void sendResponse(ResponseMessageCallback callback, AbstractResponse response) throws HandleException {
        if (callback instanceof FixRequestIdAndThen) {
            ((FixRequestIdAndThen) callback).fixRequestId(response);
        }

        if (thisSite.isPrimary) response.authoritative = true;

        response.siteInfoSerial = thisSite.serialNumber;

        // If this is a certified request, sign the response.
        // We could be acting as a cache, so only sign the response
        //  if the cacheCertify flag is set, or if the response
        //  was handled locally (ie there is no other signature
        //  on it).
        if (response.certify && (response.cacheCertify || response.signature == null)) {

            boolean signed = false;

            //session mannered response signing: use session key, return MAC code
            //with the message
            if (response.sessionId > 0) {
                // try to use session key to certify first ...  in case of
                // OC_SESSION_EXCHANGEKEY and OC_SESSION_SETUP, the client need to wait
                // for the response to confirm the session key is established.  so
                // don't sign with the session key if the request is
                // OC_SESSION_EXCHANGEKEY or OC_SESSION_SETUP.
                ServerSideSessionInfo sssinfo = getSession(response.sessionId);

                if (sssinfo != null && response.opCode != AbstractMessage.OC_SESSION_SETUP && response.opCode != AbstractMessage.OC_SESSION_EXCHANGEKEY) {
                    try {
                        response.sessionCounter = sssinfo.getNextSessionCounter();
                        response.signMessage(sssinfo.getSessionKey());
                        signed = true;
                    } catch (Exception e) {
                        logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception signing response: " + e);
                        signed = false;
                    }
                }
            }

            if (!signed) { //if session key signing fails
                // normal way of sending certified response through signature of server
                // private key
                Signature[] serverSignatures;
                if (supportsSha256Signature(response)) {
                    serverSignatures = serverSignaturesSha256;
                } else {
                    serverSignatures = serverSignaturesSha1;
                }

                try {
                    // rotating through an array of signatures avoids a bottleneck
                    // since we have to 'synchronize' on the signature while signing
                    int sigIndex = currentSigIndex++;
                    if (sigIndex >= serverSignatures.length) sigIndex = currentSigIndex = 0;

                    Signature sig = serverSignatures[sigIndex];

                    synchronized (sig) {
                        response.signMessage(sig);
                    }
                } catch (Exception e) {
                    // If we get an error while signing the response, we return
                    // the unsigned message anyway.  Maybe an error message would
                    // be better?
                    logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception signing response: " + e);
                }
            }
        }

        callback.handleResponse(response);
    }

    private boolean supportsSha256Signature(AbstractResponse response) {
        if (response.hasEqualOrGreaterVersion(2, 11)) return true;
        if ("DSA".equals(privateKey.getAlgorithm())) return false;
        if (response.hasEqualOrGreaterVersion(2, 7)) return true;
        return false;
    }

    @Override
    public void disable() {
        serverEnabled = false;
    }

    @Override
    public void enable() {
        serverEnabled = true;
    }

    /**********************************************************************
     * Given a request object, handle the request and return a response
     **********************************************************************/
    @Override
    public void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {
        if (!serverEnabled) {
            sendServerDisabledResponse(req, callback);
            return;
        }

        // handle the request...
        processRequest(req, null, null, callback);
    }

    @Override
    public void processRequest(AbstractRequest req, InetAddress caller, ResponseMessageCallback callback) throws HandleException {
        processRequest(req, callback);
    }

    @Override
    public AbstractResponse processRequest(AbstractRequest req, InetAddress caller) throws HandleException {
        SimpleResponseMessageCallback callback = new SimpleResponseMessageCallback();
        try {
            processRequest(req, caller, callback);
        } catch (HandleException e) {
            return HandleException.toErrorResponse(req, e);
        }
        return callback.getResponse();
    }

    @Override
    public void processPreAuthenticatedRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {
        if (!serverEnabled) {
            sendServerDisabledResponse(req, callback);
            return;
        }

        // handle the request...
        if (req.authInfo == null) {
            processRequest(req, null, null, callback);
        } else {
            processRequest(req, new PreAuthenticatedChallengeResponse(), new PreAuthenticatedChallengeAnswerRequest(req.authInfo), callback);
        }
    }

    private void sendServerDisabledResponse(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {
        sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_TEMPORARILY_DISABLED));
    }

    void processRequest(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq, ResponseMessageCallback callback) throws HandleException {
        numRequests.incrementAndGet();

        setTentativeServerSideAuthenticationInfo(req, cRes, crReq);

        // if the session id doesn't indicate a challenge entry or a valid session entry
        // then it is a invalid session
        if (req.sessionId > 0 && !pendingChallenge(req.sessionId) && !validSession(req)) {
            sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_SESSION_TIMEOUT, MSG_INVALID_SESSION_OR_TIMEOUT));
            return;
        }

        // please take note that all the req.authInfo is null in server side (this method)
        // even for administrative request.
        // this is because the decode message process can't re-produce the authInfo
        // on server side.
        // please see Encoder.java, all decodeMessage methods.

        // insert the request id into session information if the request has a valid session link
        validSession(req);

        // see if we need to be primary
        if (req.isAdminRequest && !isPrimary) {
            if (keepOtherTransactions && (req.opCode == AbstractMessage.OC_RETRIEVE_TXN_LOG || req.opCode == AbstractMessage.OC_DUMP_HANDLES)) {
                // this is okay
            } else {
                sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_NOT_A_PRIMARY));
                return;
            }
        }

        // now process the request

        switch (req.opCode) {
        case AbstractMessage.OC_GET_SITE_INFO:
            sendResponse(callback, new GetSiteInfoResponse(req, thisSite));
            break;
        case AbstractMessage.OC_RESOLUTION:
            // handle a resolution request-
            numResolutionRequests.incrementAndGet();
            sendResponse(callback, doResolution((ResolutionRequest) req, cRes, crReq, false));
            break;
        case AbstractMessage.OC_LIST_HANDLES:
            doListHandles(callback, (ListHandlesRequest) req, cRes, crReq);
            break;
        case AbstractMessage.OC_LIST_HOMED_NAS:
            doListNAs(callback, (ListNAsRequest) req, cRes, crReq);
            break;
        case AbstractMessage.OC_SESSION_SETUP:
            sendResponse(callback, doSessionSetup((SessionSetupRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_SESSION_EXCHANGEKEY:
            sendResponse(callback, doKeyExchange((SessionExchangeKeyRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_SESSION_TERMINATE:
            sendResponse(callback, doSessionTerminate((GenericRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_RESPONSE_TO_CHALLENGE:
            // look up the challenge that matches this response in the
            // pendingAuthorizations table
            ChallengeResponseInfo crInfo = pendingAuthorizations.get(req.sessionId);
            if (crInfo == null || crInfo.hasExpired()) {
                sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_AUTHEN_TIMEOUT, null));
            } else {
                crInfo.challengeAccepted = true;
                processRequest(crInfo.originalRequest, crInfo.challenge, (ChallengeAnswerRequest) req, new FixRequestIdAndThen(req.requestId, callback));
            }
            break;
        case AbstractMessage.OC_VERIFY_CHALLENGE:
            sendResponse(callback, verifyChallenge((VerifyAuthRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_DUMP_HANDLES:
            sendResponse(callback, doDumpHandles((DumpHandlesRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_ADD_VALUE:
            numAdminRequests.incrementAndGet();
            sendResponse(callback, doAddValue((AddValueRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_REMOVE_VALUE:
            numAdminRequests.incrementAndGet();
            sendResponse(callback, doRemoveValue((RemoveValueRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_MODIFY_VALUE:
            numAdminRequests.incrementAndGet();
            sendResponse(callback, doModifyValue((ModifyValueRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_CREATE_HANDLE:
            numAdminRequests.incrementAndGet();
            sendResponse(callback, doCreateHandle((CreateHandleRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_DELETE_HANDLE:
            numAdminRequests.incrementAndGet();
            sendResponse(callback, doDeleteHandle((DeleteHandleRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_GET_NEXT_TXN_ID:
            sendResponse(callback, getNextTxnId((GenericRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_RETRIEVE_TXN_LOG:
            sendResponse(callback, doRetrieveTxnLog((RetrieveTxnRequest) req, cRes, crReq));
            break;
        case AbstractMessage.OC_HOME_NA:
            sendResponse(callback, doHomeNA(req, cRes, crReq));
            break;
        case AbstractMessage.OC_UNHOME_NA:
            sendResponse(callback, doUnhomeNA(req, cRes, crReq));
            break;
        case AbstractMessage.OC_BACKUP_SERVER:
            sendResponse(callback, doBackup((GenericRequest) req, cRes, crReq));
            break;
        default:
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unknown operation: " + req.opCode);
        }
    }

    private void setTentativeServerSideAuthenticationInfo(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) {
        if (req.authInfo == null) {
            if (req instanceof ChallengeAnswerRequest) {
                ChallengeAnswerRequest car = (ChallengeAnswerRequest) req;
                req.authInfo = new TentativeServerSideAuthenticationInfo(car.userIdHandle, car.userIdIndex);
            } else if (cRes != null && crReq != null) {
                req.authInfo = new TentativeServerSideAuthenticationInfo(crReq.userIdHandle, crReq.userIdIndex);
            } else {
                ServerSideSessionInfo sessionInfo = getSession(req.sessionId);
                if (sessionInfo != null && !sessionInfo.isSessionAnonymous()) {
                    req.authInfo = new TentativeServerSideAuthenticationInfo(sessionInfo.identityKeyHandle, sessionInfo.identityKeyIndex);
                }
            }
        }
    }

    private static class FixRequestIdAndThen implements ResponseMessageCallback {
        final int requestId;
        final ResponseMessageCallback callback;

        public FixRequestIdAndThen(int requestId, ResponseMessageCallback callback) {
            this.requestId = requestId;
            this.callback = callback;
        }

        public void fixRequestId(AbstractResponse message) {
            message.requestId = this.requestId;
        }

        @Override
        public void handleResponse(AbstractResponse message) throws HandleException {
            callback.handleResponse(message);
        }
    }

    class ChallengeResponseInfo {
        long timeStarted;
        int sessionId;
        ChallengeResponse challenge;
        AbstractRequest originalRequest;
        boolean challengeAccepted;

        final boolean hasExpired() {
            return timeStarted < (System.currentTimeMillis() - maxAuthTime);
        }
    }

    /************************************************************************
     * Return a message with the next consecutive transaction identifier.
     ************************************************************************/
    private final AbstractResponse getNextTxnId(GenericRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {
        return new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_NO_NEXT_TXN_ID);
    }

    private long getLatestTxnId() {
        if (txnQueue == null) return -1;
        long latest = txnQueue.getLastTxnId();
        for (long txnInProgress : transactionsInProgress.values()) {
            if (txnInProgress - 1 < latest) {
                latest = txnInProgress - 1;
            }
        }
        return latest;
    }

    /** Get the next transaction ID  */
    private final long getNextTxnId() {
        return nextTxnId.incrementAndGet();
    }

    /************************************************************************
     * Tells the server that it will now be responsible for handles under
     * the specified prefix.
     ************************************************************************/
    private final AbstractResponse doHomeNA(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        byte[] handle = req.handle;
        if (!Util.hasSlash(handle)) handle = Util.convertSlashlessHandleToZeroNaHandle(handle);
        if (!Util.startsWithCI(handle, Common.NA_HANDLE_PREFIX)) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Was asked to home non-prefix handle: '" + Util.decodeString(handle) + "' ");
            return new ErrorResponse(req, AbstractMessage.RC_INVALID_HANDLE, MSG_NOT_A_NA_HANDLE);
        }

        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, serverAdmins, homeOnlyAdmins);
        if (authError != null) return authError;

        synchronized (getWriteLock(handle)) {
            try {
                // if this NA handle hashes to this server, make a transaction for this operation
                if (thisSite.determineServerNum(handle) == thisServerNum) {
                    AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, null, Transaction.ACTION_HOME_NA);
                    if (maybeError != null) return maybeError;
                }

                storage.setHaveNA(handle, true);
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Unable to \"home\" prefix \"" + Util.decodeString(handle) + "\" after transaction was logged! - " + e);
                e.printStackTrace(System.err);
                if (e.getCode() == HandleException.STORAGE_RDONLY) {
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                } else {
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
            // add to resolver's local sites
            adjustHomedPrefix(handle, true);
        }
        // return success
        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    /************************************************************************
     * Tells the server that it will now be responsible for handles under the specified prefix.
     * Does not require a request object or challenge response objects.
     ***********************************************************************/
    private void doHomeNA(byte[] na) throws HandleException {
        synchronized (getWriteLock(na)) {
            try {
                if (thisSite.determineServerNum(na) == thisServerNum) {
                    if (!insertTransaction(na, null, Transaction.ACTION_HOME_NA)) {
                        logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Unable to save HOME-NA transaction.");
                        return;
                    }
                }
                storage.setHaveNA(na, true);
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
            adjustHomedPrefix(na, true);
        }
    }

    /************************************************************************
     * Tells the server that it will *not* be responsible for handles under
     * the specified prefix any longer.
     ************************************************************************/
    private final AbstractResponse doUnhomeNA(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        byte[] handle = req.handle;
        if (!Util.hasSlash(handle)) handle = Util.convertSlashlessHandleToZeroNaHandle(handle);
        if (!Util.startsWithCI(handle, Common.NA_HANDLE_PREFIX)) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Was asked to unhome non-prefix handle: '" + Util.decodeString(handle) + "' ");
            return new ErrorResponse(req, AbstractMessage.RC_INVALID_HANDLE, MSG_NOT_A_NA_HANDLE);
        }

        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, serverAdmins, homeOnlyAdmins);
        if (authError != null) return authError;

        synchronized (getWriteLock(handle)) {
            try {
                if (thisSite.determineServerNum(handle) == thisServerNum) {
                    AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, null, Transaction.ACTION_UNHOME_NA);
                    if (maybeError != null) return maybeError;
                }

                storage.setHaveNA(handle, false);
                if (!Util.hasSlash(req.handle) && storage.haveNA(req.handle)) {
                    storage.setHaveNA(req.handle, false);
                }
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Unable to \"unhome\" prefix \"" + Util.decodeString(handle) + "\" after transaction was logged! - " + e);
                e.printStackTrace(System.err);
                if (e.getCode() == HandleException.STORAGE_RDONLY) return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                else return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
            // remove from resolver's local sites
            adjustHomedPrefix(handle, false);
        }
        // return success
        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    public void adjustHomedPrefix(byte[] na, boolean home) {
        if (enableHomedPrefixNaLookupOptimization) {
            resolver.getConfiguration().setLocalSites(Util.decodeString(na), home ? new SiteInfo[] { thisSite } : null);
        }
    }

    /************************************************************************
     * retrieve the transaction logs starting from a certain date, for all
     * of the handles that hash according to the hash specification in
     * the RetrieveTxnRequest object.  This first authenticates the client
     * based on the replication administrators listed in the configuration
     * file.
     ************************************************************************/
    private final AbstractResponse doRetrieveTxnLog(RetrieveTxnRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        if (!enableTxnQueue) {
            return new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_NO_TXN_QUEUE);
        }
        if (req.replicationStateInfo == null && !isPrimary) {
            return new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_NOT_A_PRIMARY);
        }
        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, replicationAdmins, serverAdmins);
        if (authError != null) return authError;

        // at this point, the replication admin has been authenticated,
        // so we can begin streaming the transactions.  If the requestor
        // is hopelessly out of date, they will be notified in the
        // streaming part of the message.
        long latestCommittedTxnId = getLatestTxnId();
        if (req.replicationStateInfo != null) {
            //logError(ServerLog.ERRLOG_LEVEL_INFO, "Replicating transactions from all sources");
            String ownReplicationServerName = thisServerNum + ":" + replicationSiteName;
            return new RetrieveTxnResponse(allOtherTransactionQueues, ownReplicationServerName, latestCommittedTxnId, replicationDaemon.getReplicationStateInfo(), req, storage, caseSensitive);
        } else {
            String start = (new Date(req.lastQueryDate)).toString();
            String end = (new Date(System.currentTimeMillis())).toString();
            long count = latestCommittedTxnId - req.lastTxnId;
            if (count > 0 && latestCommittedTxnId > 0) {
                String msg = "";
                if (count == 1) {
                    msg = "Replicating 1 transaction from [" + start + "] to [" + end + "]";
                } else if (count > 1) {
                    msg = "Replicating " + count + " transactions from [" + start + "] to [" + end + "]";
                } else {
                    msg = "Replicating all transactions";
                }
                logError(ServerLog.ERRLOG_LEVEL_INFO, msg);
            }

            return new RetrieveTxnResponse(txnQueue, latestCommittedTxnId, req, storage, caseSensitive);
        }
    }

    /************************************************************************
     * retrieve the entire contents of the handle database that hash
     * according to the hash specification in the DumpHandlesRequest.
     * This first authenticates the client based on the replication
     * administrators listed in the configuration file.
     ************************************************************************/
    private final AbstractResponse doDumpHandles(DumpHandlesRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        if (!enableTxnQueue) {
            return new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_NO_TXN_QUEUE);
        }
        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, replicationAdmins, serverAdmins);
        if (authError != null) return authError;

        // at this point, the replication admin has been authenticated,
        // and the requestor is reasonably up to date, so we can begin
        // streaming the handles.
        return new DumpHandlesResponse(req, storage, txnQueue, replicationDaemon);
    }

    /************************************************************************
     * verify whether or not the response to the challenge given in the
     * request object was "signed" using the specified user's secret key.
     * Note: currently the ChallengeResponse and ChallengeAnswerRequest
     * objects are ignored here.  The user ID, challenge and response
     * are all contained in the VerifyAuthRequest object.
     ************************************************************************/
    private final AbstractResponse verifyChallenge(VerifyAuthRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {
        AbstractResponse res = errorIfNotHaveHandle(req);
        if (res != null) {
            if ((allowRecursiveQueries && req.recursive) || (performRecursionForOldClientsRequestingReferredPrefixes && isOldClientRequestingReferredPrefix(req, res))) {
                AbstractRequest clonedReq = cloneRequestForRecursion(req);
                return processRecursiveRequestAndAdaptResponse(clonedReq, req);
            }
            return res;
        }
        int handleIndex = req.handleIndex;
        byte clumps[][] = getRawHandleValuesWithTemplate(req.handle, handleIndex == 0 ? null : new int[] { req.handleIndex }, handleIndex == 0 ? Common.SECRET_KEY_TYPES : null, req.recursionCount);
        if (clumps == null || clumps.length <= 0) {
            return new VerifyAuthResponse(req, false);
        }

        // Get the encoding type (hash algorithm) of the signature
        // and use that to get the signature.
        byte authSignature[];
        byte digestAlg = req.signedResponse[0];

        boolean oldFormat = !req.hasEqualOrGreaterVersion(2, 1);

        if (oldFormat) { // the old format - just the md5 digest, no length or alg ID
            digestAlg = Common.HASH_CODE_MD5;
            authSignature = req.signedResponse;
        } else { // new format w/ one byte md5 or sha1 identifiers
            authSignature = new byte[req.signedResponse.length - 1];
            System.arraycopy(req.signedResponse, 1, authSignature, 0, req.signedResponse.length - 1);
            /**
             throw new HandleException(HandleException.MESSAGE_FORMAT_ERROR,
             "Invalid hash type in secret key signature: "+((int)digestAlg));
             */
        }

        HandleValue secretKeyValue = new HandleValue();
        for (byte[] clump : clumps) {
            Encoder.decodeHandleValue(clump, 0, secretKeyValue);
            if (handleIndex != 0 && handleIndex != secretKeyValue.getIndex()) continue;
            if (!secretKeyValue.hasType(Common.STD_TYPE_HSSECKEY)) continue;

            byte realSignature[] = Util.doMac(digestAlg, Util.concat(req.nonce, req.origRequestDigest), secretKeyValue.getData(), authSignature);

            if (realSignature != null && realSignature.length > 0 && Util.equals(realSignature, authSignature)) {
                return new VerifyAuthResponse(req, true);
            }
        }

        return new VerifyAuthResponse(req, false);
    }

    /************************************************************************
     * process a delete handle request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final AbstractResponse doDeleteHandle(DeleteHandleRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // make sure that we are supposed to take this handle...
        if (thisSite.servers.length > 1 && thisSite.determineServerNum(req.handle) != thisServerNum) {
            return new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_WRONG_SERVER_HASH);
        }

        AbstractResponse res = errorIfNotHaveHandle(req, req.handle);
        // But, we should be able to delete handles
        // that are in the database by mistake.
        if (res != null && res.responseCode != AbstractMessage.RC_SERVER_NOT_RESP) return res;

        // make sure the user has authenticated.  If not, send a challenge
        if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            return createChallenge(req);
        }

        synchronized (getWriteLock(req.handle)) {

            // check to see if the given user has permission to delete this
            try {
                // get the admin records from the handle, also check to make sure
                // that the handle exists.
                byte clumps[][] = storageGetRawHandleValues((caseSensitive ? req.handle : Util.upperCase(req.handle)), null, Common.ADMIN_TYPES);
                if (clumps == null) {
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
                }

                AbstractResponse authResp = authenticateUser(req, cRes, crReq, DEL_HANDLE_PERM, clumps);
                if (authResp != null) {
                    return authResp;
                }

            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unable to authenticate delete handle request: " + e);
                return new ErrorResponse(req, AbstractMessage.RC_AUTHEN_ERROR, null);
            }

            try {
                AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, req.handle, null, Transaction.ACTION_DELETE_HANDLE);
                if (maybeError != null) return maybeError;

                storage.deleteHandle((caseSensitive ? req.handle : Util.upperCase(req.handle)));
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error committing transaction: " + e);
                switch (e.getCode()) {
                case HandleException.HANDLE_DOES_NOT_EXIST:
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
                case HandleException.STORAGE_RDONLY:
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                default:
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
        }

        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    /** returns the offset of the value with the given index, or
     * -1 if there is no value with that index. */
    private static final int getHVByIndex(HandleValue values[], int index) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].getIndex() == index) return i;
        }
        return -1;
    }

    /************************************************************************
     * process a remove handle-value request, with the specified
     * authentication info (if any).
     ************************************************************************/
    private final AbstractResponse doRemoveValue(RemoveValueRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // make sure that we are supposed to take this handle...
        AbstractResponse res = errorIfNotHaveHandle(req);
        if (res != null) return res;

        // make sure the user has authenticated.  If not, send a challenge
        if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            return createChallenge(req);
        }

        byte handle[] = (caseSensitive ? req.handle : Util.upperCase(req.handle));

        synchronized (getWriteLock(req.handle)) {

            // get the current values...
            byte rawValues[][] = storageGetRawHandleValues(handle, null, null);
            if (rawValues == null) {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
            }

            // decode the current values
            HandleValue values[] = new HandleValue[rawValues.length];
            for (int i = 0; i < rawValues.length; i++) {
                values[i] = new HandleValue();
                Encoder.decodeHandleValue(rawValues[i], 0, values[i]);
            }

            // find the values to be removed, and the permissions needed to remove them
            // if the requestor supplied a non-existent index, then it will return an error.
            boolean needsRemAdminPerm = false;
            boolean needsRemValuePerm = false;
            int toBeRemovedIdxs[] = new int[req.indexes.length];
            for (int i = 0; i < toBeRemovedIdxs.length; i++) {
                toBeRemovedIdxs[i] = getHVByIndex(values, req.indexes[i]);
                if (toBeRemovedIdxs[i] < 0) {
                    return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
                }
                HandleValue val = values[toBeRemovedIdxs[i]];
                if (val.hasType(Common.ADMIN_TYPE)) needsRemAdminPerm = true;
                else needsRemValuePerm = true;
            }

            int neededPerms[];
            if (needsRemValuePerm && needsRemAdminPerm) neededPerms = REM_ADM_AND_VAL_PERM;
            else if (needsRemAdminPerm) neededPerms = REM_ADM_PERM;
            else neededPerms = REM_VAL_PERM;

            // authenticate the user
            AbstractResponse authResp = authenticateUser(req, cRes, crReq, neededPerms, rawValues);
            if (authResp != null) {
                return authResp;
            }

            // take the removed values out of the array
            int removed = 0;
            for (int i = 0; i < toBeRemovedIdxs.length; i++) {
                if (values[toBeRemovedIdxs[i]] != null) removed++;
                values[toBeRemovedIdxs[i]] = null;
            }

            // copy the remaining values to a new array
            HandleValue newValues[] = new HandleValue[values.length - removed];
            int j = 0;
            for (HandleValue value : values) {
                if (value != null) newValues[j++] = value;
            }

            values = newValues;

            try {
                // log the transaction
                AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, values, Transaction.ACTION_UPDATE_HANDLE);
                if (maybeError != null) return maybeError;

                // save the updated values to the database
                storage.updateValue(handle, values);

            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error committing transaction: " + e);
                switch (e.getCode()) {
                case HandleException.HANDLE_DOES_NOT_EXIST:
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
                case HandleException.STORAGE_RDONLY:
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                default:
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
        }

        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    /** Combine the toAppend permissions with the current permissions.
     * This assumes that the values of the permissions arrays are
     * immutable. */
    private static final int[] combinePerms(int current[], int toAppend[]) {
        if (current == null) return toAppend;
        if (toAppend == null) return current;
        int newPerms[] = new int[current.length + toAppend.length];
        System.arraycopy(current, 0, newPerms, 0, current.length);
        System.arraycopy(toAppend, 0, newPerms, current.length, toAppend.length);
        return newPerms;
    }

    private final AbstractResponse doModifyValue(ModifyValueRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // make sure that we are supposed to take this handle...
        AbstractResponse res = errorIfNotHaveHandle(req);
        if (res != null) return res;

        // make sure the user has authenticated.  If not, send a challenge
        // added checking up with session
        boolean isAnonymous = ((cRes == null || crReq == null) && !authenticatedSession(req));
        byte handle[] = (caseSensitive ? req.handle : Util.upperCase(req.handle));

        synchronized (getWriteLock(req.handle)) {

            // get the current values...
            byte rawValues[][] = storageGetRawHandleValues(handle, null, null);
            if (rawValues == null) {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
            }

            // decode all of the current values
            HandleValue values[] = new HandleValue[rawValues.length];
            for (int i = 0; i < rawValues.length; i++) {
                values[i] = new HandleValue();
                Encoder.decodeHandleValue(rawValues[i], 0, values[i]);
            }

            boolean needsModAdminPerm = false;
            boolean needsModValuePerm = false;
            boolean needsAdmToValPerm = false;
            boolean needsValToAdmPerm = false;

            boolean oldValIsAdmin;
            boolean newValIsAdmin;
            // find the indexes of the value to be modified.
            int toBeModifiedIdxs[] = new int[req.values.length];
            for (int i = 0; i < toBeModifiedIdxs.length; i++) {
                toBeModifiedIdxs[i] = getHVByIndex(values, req.values[i].getIndex());
                if (toBeModifiedIdxs[i] < 0) {
                    return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
                }
                HandleValue val = values[toBeModifiedIdxs[i]];
                if (!val.getAdminCanWrite()) {
                    // if write access is not allowed by administrators, nobody can write!
                    return new ErrorResponse(req, AbstractMessage.RC_INSUFFICIENT_PERMISSIONS, MSG_READ_ONLY_VALUE);
                }

                oldValIsAdmin = val.hasType(Common.ADMIN_TYPE);
                newValIsAdmin = req.values[i].hasType(Common.ADMIN_TYPE);

                if (isAnonymous) {
                    if (!val.getAnyoneCanWrite()) {
                        // if the requestor is anonymous but anonymous write access is not allowed
                        return createChallenge(req);
                    } else if (oldValIsAdmin || newValIsAdmin) {
                        // no anonymous operations are allowed on admin records
                        return createChallenge(req);
                    }
                }

                if (oldValIsAdmin && newValIsAdmin) needsModAdminPerm = true;
                else if (oldValIsAdmin && !newValIsAdmin) needsAdmToValPerm = true;
                else if (!oldValIsAdmin && newValIsAdmin) needsValToAdmPerm = true;
                else if (!oldValIsAdmin && !newValIsAdmin && !val.getAnyoneCanWrite()) needsModValuePerm = true;
            }

            int perms[] = null;
            if (needsModAdminPerm) perms = combinePerms(perms, MOD_ADM_PERM);
            if (needsModValuePerm) perms = combinePerms(perms, MOD_VAL_PERM);
            if (needsAdmToValPerm) perms = combinePerms(perms, ADM_TO_VAL_PERM);
            if (needsValToAdmPerm) perms = combinePerms(perms, VAL_TO_ADM_PERM);

            // authenticate the user...
            if (perms != null) {
                AbstractResponse authResp = authenticateUser(req, cRes, crReq, perms, rawValues);
                if (authResp != null) {
                    return authResp;
                }
            }

            // replace the old values with the new ones, and update the timestamps
            int now = (int) (System.currentTimeMillis() / 1000);
            for (int i = 0; i < toBeModifiedIdxs.length; i++) {
                values[toBeModifiedIdxs[i]] = req.values[i];
                values[toBeModifiedIdxs[i]].setTimestamp(now);
            }

            // check for duplicate index values
            for (int i = 0; i < values.length; i++) {
                for (int j = i + 1; j < values.length; j++) {
                    if (values[i].getIndex() == values[j].getIndex()) {
                        return new ErrorResponse(req, AbstractMessage.RC_INVALID_VALUE, Util.encodeString("Index conflict for " + values[j].getIndex()));
                    }
                }
            }

            try {
                // log the transaction
                AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, values, Transaction.ACTION_UPDATE_HANDLE);
                if (maybeError != null) return maybeError;

                // save the updated value to the database
                storage.updateValue(handle, values);
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error committing transaction: " + e);
                switch (e.getCode()) {
                case HandleException.HANDLE_DOES_NOT_EXIST:
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
                case HandleException.STORAGE_RDONLY:
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                default:
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
        }
        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    /************************************************************************
     * process an add handle-value request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final AbstractResponse doAddValue(AddValueRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // make sure that we are supposed to take this handle...
        AbstractResponse res = errorIfNotHaveHandle(req);
        if (res != null) return res;

        if (req.values == null || req.values.length <= 0) {
            return new ErrorResponse(req, AbstractMessage.RC_INVALID_VALUE, MSG_EMPTY_VALUE_LIST);
        }

        Arrays.sort(req.values, HandleValue.INDEX_COMPARATOR);

        for (int i = 0; i < req.values.length; i++) {
            if (req.values[i].getIndex() <= 0) {
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_VALUE, MSG_INDEXES_MUST_BE_POSITIVE);
            }
            if (i > 0 && req.values[i].getIndex() == req.values[i - 1].getIndex()) {
                return new ErrorResponse(req, AbstractMessage.RC_VALUE_ALREADY_EXISTS, Util.encodeString("Index conflict for " + req.values[i].getIndex()));
            }
        }

        // make sure the user has authenticated.  If not, send a challenge
        if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            return createChallenge(req);
        }

        byte handle[] = (caseSensitive ? req.handle : Util.upperCase(req.handle));

        boolean didOverwriteExisting;
        synchronized (getWriteLock(req.handle)) {
            // get the current values...
            byte rawValues[][] = storageGetRawHandleValues(handle, null, null);
            if (rawValues == null) {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
            }
            HandleValue[] existingValues = Encoder.decodeHandleValues(rawValues);
            Arrays.sort(existingValues, HandleValue.INDEX_COMPARATOR);

            int sharedIndex = findSharedIndex(existingValues, req.values);
            didOverwriteExisting = sharedIndex > 0;
            if (didOverwriteExisting && !req.overwriteWhenExists) {
                return new ErrorResponse(req, AbstractMessage.RC_VALUE_ALREADY_EXISTS, Util.encodeString("Index conflict for " + sharedIndex));
            }

            int now = (int) (System.currentTimeMillis() / 1000);
            List<HandleValue> result = new ArrayList<>(req.values.length + existingValues.length);

            HandleValue[] newValues = req.values;

            int[] neededPerms = getPermsAndSetTimestampsAndAccumulateResultValuesForAddValuesOperation(now, existingValues, newValues, result);

            // authenticate the user...
            if (neededPerms != null) {
                AbstractResponse authResp = authenticateUser(req, cRes, crReq, neededPerms, rawValues);
                if (authResp != null) {
                    return authResp;
                }
            }

            // combine the to-be-added values with the current values
            HandleValue values[] = result.toArray(new HandleValue[result.size()]);

            try {
                // log the transaction
                AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, values, Transaction.ACTION_UPDATE_HANDLE);
                if (maybeError != null) return maybeError;

                // save the updated value to the database
                storage.updateValue(handle, values);

            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error committing transaction: " + e);
                switch (e.getCode()) {
                case HandleException.HANDLE_DOES_NOT_EXIST:
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
                case HandleException.STORAGE_RDONLY:
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                default:
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }

        }
        AbstractResponse resp = new GenericResponse(req, AbstractMessage.RC_SUCCESS);
        resp.overwriteWhenExists = didOverwriteExisting;
        return resp;
    }

    private int[] getPermsAndSetTimestampsAndAccumulateResultValuesForAddValuesOperation(int now, HandleValue[] existingValues, HandleValue[] newValues, List<HandleValue> result) {
        boolean needsAddAdminPerm = false;
        boolean needsAddValuePerm = false;
        boolean needsModAdminPerm = false;
        boolean needsModValuePerm = false;
        boolean needsAdmToValPerm = false;
        boolean needsValToAdmPerm = false;

        int i = 0;
        int j = 0;
        while (i < existingValues.length && j < newValues.length) {
            if (existingValues[i].getIndex() < newValues[j].getIndex()) {
                result.add(existingValues[i]);
                i++;
            } else if (existingValues[i].getIndex() > newValues[j].getIndex()) {
                HandleValue newValue = newValues[j];
                newValue.setTimestamp(now);
                result.add(newValue);
                if (newValue.hasType(Common.ADMIN_TYPE)) {
                    needsAddAdminPerm = true;
                } else {
                    needsAddValuePerm = true;
                }
                j++;
            } else {
                HandleValue existingValue = existingValues[i];
                HandleValue newValue = newValues[j];
                newValue.setTimestamp(now);
                if (existingValue.equalsIgnoreTimestamp(newValue)) {
                    result.add(existingValue);
                } else {
                    boolean oldValIsAdmin = existingValue.hasType(Common.ADMIN_TYPE);
                    boolean newValIsAdmin = newValue.hasType(Common.ADMIN_TYPE);
                    if (oldValIsAdmin && newValIsAdmin) {
                        needsModAdminPerm = true;
                    } else if (oldValIsAdmin && !newValIsAdmin) {
                        needsAdmToValPerm = true;
                    } else if (!oldValIsAdmin && newValIsAdmin) {
                        needsValToAdmPerm = true;
                    } else if (!oldValIsAdmin && !newValIsAdmin && !existingValue.getAnyoneCanWrite()) {
                        needsModValuePerm = true;
                    }
                    result.add(newValue);
                }
                i++;
                j++;
            }
        }

        for (; i < existingValues.length; i++) {
            result.add(existingValues[i]);
        }
        for (; j < newValues.length; j++) {
            HandleValue newValue = newValues[j];
            newValue.setTimestamp(now);
            result.add(newValue);
            if (newValue.hasType(Common.ADMIN_TYPE)) {
                needsAddAdminPerm = true;
            } else {
                needsAddValuePerm = true;
            }
        }

        int neededPerms[] = null;
        if (needsModAdminPerm) neededPerms = combinePerms(neededPerms, MOD_ADM_PERM);
        if (needsModValuePerm) neededPerms = combinePerms(neededPerms, MOD_VAL_PERM);
        if (needsAdmToValPerm) neededPerms = combinePerms(neededPerms, ADM_TO_VAL_PERM);
        if (needsValToAdmPerm) neededPerms = combinePerms(neededPerms, VAL_TO_ADM_PERM);
        if (needsAddAdminPerm) neededPerms = combinePerms(neededPerms, ADD_ADM_PERM);
        if (needsAddValuePerm) neededPerms = combinePerms(neededPerms, ADD_VAL_PERM);
        return neededPerms;
    }

    private static int findSharedIndex(HandleValue[] existingValues, HandleValue[] newValues) {
        int i = 0;
        int j = 0;
        while (i < existingValues.length && j < newValues.length) {
            if (existingValues[i].getIndex() < newValues[j].getIndex()) {
                i++;
            } else if (existingValues[i].getIndex() > newValues[j].getIndex()) {
                j++;
            } else {
                return existingValues[i].getIndex();
            }
        }
        return -1;
    }

    /**
     * Used by doCreateHandle and doListHandle to retrieve the prefix handle admin values.
     * If allowNAAdmins is set to false in config.dct, it will always return an
     * empty array.
     *
     * @param na The prefix handle to retrieve admin values from.
     *
     * @return null on error, otherwise prefix handle admin values.
     **/
    private final byte[][] getNAAdminValues(byte na[]) throws HandleException {
        if (!allowNAAdmins) return new byte[0][];

        ResolutionRequest authRequest = new ResolutionRequest(na, Common.ADMIN_TYPES, null, null);

        // this must be certified - oh yes!
        authRequest.certify = true;

        // get the admin records from the prefix handle
        AbstractResponse response = resolver.processRequest(authRequest);

        if (response.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {
            // If we got here, we think we're responsible for this NA;
            // so give it an extra chance to really exist
            authRequest.authoritative = true;
            authRequest.clearBuffers();
            response = resolver.processRequest(authRequest);
        }

        if (response.getClass() != ResolutionResponse.class) {
            return null;
        }

        return ((ResolutionResponse) response).values;
    }

    /**
     * Returns null if authentication and authorization was successful and
     * an ErrorResponse otherwise.
     *
     * TODO: Always returns RC_INVALID_ADMIN on error, even if it should
     *       return RC_INSUFFICIENT_PERMISSIONS.
     **/
    private final AbstractResponse authenticateUser(AbstractRequest req, ChallengeResponse challenge, ChallengeAnswerRequest response, int operationIDs[], byte values[][]) throws HandleException {
        // set up the identity handle and index here for two cases:
        // 1. for Challenge Answer Request (in response),
        //    the identity is response.userIdHandle and response.userIdIndex
        // 2. for session information
        //    the identity is in the session info identityHandle, identityIndex

        // if we are allowing wide-open access, shortcut the authorization process
        if (wideOpenAccessAllowed) return null;

        byte[] identityHandle = null;
        int identityIndex = -1;
        ServerSideSessionInfo sessionInfo = getSession(req.sessionId);

        if (challenge != null && response != null) {
            identityHandle = response.userIdHandle;
            identityIndex = response.userIdIndex;
        } else {
            // there was no challenge/response, so get the client ID from the
            // session information, if any
            if (sessionInfo == null) {
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_TIMEOUT, MSG_INVALID_SESSION_OR_TIMEOUT);
            }
            identityHandle = sessionInfo.identityKeyHandle;
            identityIndex = sessionInfo.identityKeyIndex;
        }

        ValueReference thisAdmin = new ValueReference(identityHandle, identityIndex);

        // if the server has been configured to require sessions in order to perform
        // administration then reject any requests that are not contained in a session
        // and are not attempting to set up a session
        if (requireSessions && sessionInfo == null && req.opCode != AbstractMessage.OC_SESSION_SETUP) {
            System.err.println("rejecting non-session request: " + req + "; session: " + getSession(req.sessionId));
            return new ErrorResponse(req, AbstractMessage.RC_INVALID_CREDENTIAL, MSG_SESSION_REQUIRED);
        }

        try {
            Vector<ValueReference> valuesTraversed = new Vector<>();
            boolean hasAdminAccess = adminHasPermission(thisAdmin, operationIDs, values, valuesTraversed);
            if (!hasAdminAccess && mightGetNewIndexForAuthentication(response, sessionInfo)) {
                AbstractResponseAndIndex verifyResp = verifyIdentityAndGetIndex(challenge, response, req);
                if (verifyResp.getResponse() != null) return verifyResp.getResponse();
                if (verifyResp.getIndex() != 0) {
                    thisAdmin = new ValueReference(thisAdmin.handle, verifyResp.getIndex());
                    hasAdminAccess = valuesTraversed.contains(thisAdmin);
                    if (hasAdminAccess) return null;
                }
            }
            if (hasAdminAccess) {
                return verifyIdentity(challenge, response, req);
            } else {
                // the client is not referenced as an administrator, either directly or indirectly
                if (DEBUG_AUTHENTICATION) {
                    HandleValue[] handleValues = null;
                    if (values != null) {
                        handleValues = new HandleValue[values.length];
                        for (int i = 0; i < values.length; i++) {
                            handleValues[i] = new HandleValue();
                            Encoder.decodeHandleValue(values[i], 0, handleValues[i]);
                        }
                    }
                    System.err.println(new Date() + " authenticateUser INVALID_ADMIN: " + identityIndex + ":" + Util.decodeString(identityHandle) + " session:" + req.sessionId + " handle:" + Util.decodeString(req.handle) + " ops:"
                        + java.util.Arrays.toString(operationIDs) + " values:" + java.util.Arrays.toString(handleValues));
                }
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_ADMIN, null);

            }
        } catch (Exception e) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error authenticating: " + e);
            return new ErrorResponse(req, AbstractMessage.RC_AUTHEN_ERROR, null);
        }
    }

    private boolean adminHasPermission(ValueReference thisAdmin, int[] operationIDs, byte[][] values, Vector<ValueReference> valuesTraversed) {
        boolean hasAdminAccess = false;
        Vector<ValueReference> valuesToTraverse = new Vector<>();

        if (serverAdminFullAccess) {
            // the current client has not already been found in the list of admins, so check
            // the server admins if serverAdminFullAccess flag is turned on in config file
            for (int i = 0; serverAdmins != null && i < serverAdmins.length; i++) {
                try {
                    if (serverAdmins[i].isMatchedBy(thisAdmin)) {
                        hasAdminAccess = true;
                        break;
                    } else {
                        valuesToTraverse.addElement(serverAdmins[i]);
                    }
                } catch (Exception e) {
                    logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error checking for server admin: " + e);
                }
            }
            if (!hasAdminAccess) {
                hasAdminAccess = isAdminInGroup(thisAdmin, valuesToTraverse, valuesTraversed);
            }
        }

        if (!hasAdminAccess) {
            // look for HS_ADMIN records that give the user permission to perform
            // the requested operation
            try {
                HandleValue tmpValue = new HandleValue();
                AdminRecord admin = new AdminRecord();

                // look for admin values with the proper permissions
                if (values != null) {
                    for (byte[] value : values) {
                        try {
                            Encoder.decodeHandleValue(value, 0, tmpValue);
                            if (!tmpValue.hasType(Common.ADMIN_TYPE)) {
                                continue;
                            }

                            // extract the admin record from the handle value
                            Encoder.decodeAdminRecord(tmpValue.getData(), 0, admin);

                        } catch (Exception e) {
                            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error decoding possible admin value: " + e);
                            continue;
                        }
                        boolean adminRecordIsRelevant = true;
                        for (int p = 0; p < operationIDs.length; p++) {
                            if (!admin.perms[operationIDs[p]]) { // doesn't have permission
                                adminRecordIsRelevant = false;
                                break;
                            }
                        }
                        if (!adminRecordIsRelevant) {
                            continue;
                        }

                        ValueReference adminValRef = new ValueReference(admin.adminId, admin.adminIdIndex);
                        // if this admin record refers directly to the requestor's user ID,
                        // then they are allowed access (provided their authentication
                        // can be verified).
                        if (adminValRef.isMatchedBy(thisAdmin)) {
                            // the given administrator handle/index was specifically listed as an
                            // administrator
                            hasAdminAccess = true;
                            break;

                        } else {
                            // this admin record didn't directly refer to the current user, but
                            // it could be a reference to an admin group.
                            valuesToTraverse.addElement(adminValRef);
                        }
                    }
                }
            } catch (Throwable e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error authenticating: " + e);
            }

            if (!hasAdminAccess) {
                hasAdminAccess = isAdminInGroup(thisAdmin, valuesToTraverse, valuesTraversed);
            }
        }
        return hasAdminAccess;
    }

    private static boolean permsContains(int[] perms, int perm) {
        if (perms == null) return false;
        for (int foundPerm : perms) {
            if (foundPerm == perm) {
                return true;
            }
        }
        return false;
    }

    /************************************************************************
     * process a create handle request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final AbstractResponse doCreateHandle(CreateHandleRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        byte[] handle = req.handle;
        if (req.mintNewSuffix) {
            HandleSuffixMinter minter = new HandleSuffixMinter(storage, caseSensitive);
            String handleInitialPortion = Util.decodeString(handle);
            String mintedHandle = minter.mintNextSuffix(handleInitialPortion);
            byte[] mintedHandleBytes = Util.encodeString(mintedHandle);
            handle = mintedHandleBytes;
        }

        // make sure that we are supposed to take this handle...
        AbstractResponse res = errorIfNotHaveHandle(req, handle);
        if (res != null) return res;

        // make sure the handle is valid utf8
        if (!Util.isValidString(handle, 0, handle.length)) {
            return new ErrorResponse(req, AbstractMessage.RC_INVALID_HANDLE, MSG_INVALID_ENCODING);
        }

        // check for duplicate handle index values
        for (int i = 0; i < req.values.length; i++) {
            if (req.values[i].getIndex() <= 0) {
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_VALUE, MSG_INDEXES_MUST_BE_POSITIVE);
            }
            for (int j = i + 1; j < req.values.length; j++) {
                if (req.values[i].getIndex() == req.values[j].getIndex()) {
                    return new ErrorResponse(req, AbstractMessage.RC_VALUE_ALREADY_EXISTS, Util.encodeString("Index conflict for " + req.values[j].getIndex()));
                }
            }
        }

        // process the new handle values
        int now = (int) (System.currentTimeMillis() / 1000);
        for (int i = 0; req.values != null && i < req.values.length; i++) {
            req.values[i].setTimestamp(now);
        }

        // if this is a derived prefix handle, we get the admin information
        // from the parent prefix handle.  Otherwise, we get the admin info
        // from the prefix handle.
        boolean isSubNAHandle = Util.isSubNAHandle(handle);
        byte[][] naVals;
        Exception adminGroupException = null;
        try {
            byte na[] = isSubNAHandle ? Util.getParentNAOfNAHandle(handle) : Util.getZeroNAHandle(handle);
            naVals = getNAAdminValues(na);
            if (naVals == null) {
                //ss: allow null admin-values so that server-admin can be used for authentication...
                //return new ErrorResponse(req, AbstractMessage.RC_AUTHEN_ERROR, null);
            }
        } catch (Exception e) {
            adminGroupException = e;
            naVals = null;
        }

        boolean didOverwriteExisting = false;
        synchronized (getWriteLock(handle)) {
            byte[][] rawValues = storageGetRawHandleValues((caseSensitive ? handle : Util.upperCase(handle)), null, req.overwriteWhenExists ? null : Common.ADMIN_TYPES);
            if (!req.overwriteWhenExists && rawValues != null) {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_ALREADY_EXISTS, null);
            }

            // make sure the user has authenticated.  If not, send a challenge
            // if an authenticated session is found, don't send a challenge

            if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
                return createChallenge(req);
            }

            int[] neededPerms = null;

            if (rawValues != null) {
                didOverwriteExisting = true;
                HandleValue[] existingValues = Encoder.decodeHandleValues(rawValues);
                HandleValue[] newValues = req.values.clone();

                neededPerms = getPermsAndSetTimestampsForCreationWithOverwrite(existingValues, newValues);
            }

            // authenticate the user
            boolean authorized = false;
            if (rawValues != null) {
                if (neededPerms == null) authorized = true;
                else if (!permsContains(neededPerms, AdminRecord.ADD_ADMIN)) {
                    AbstractResponse authResp = authenticateUser(req, cRes, crReq, neededPerms, rawValues);
                    authorized = authResp == null;
                }
                if (!authorized) {
                    AbstractResponse authResp = authenticateUser(req, cRes, crReq, ADD_ADM_PERM, rawValues);
                    authorized = authResp == null;
                }
                if (!authorized) {
                    AbstractResponse authResp = authenticateUser(req, cRes, crReq, DEL_HANDLE_PERM, rawValues);
                    if (authResp != null) {
                        if (naVals == null) {
                            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unable to find admin group while creating handle");
                            if (adminGroupException != null) {
                                adminGroupException.printStackTrace();
                            }
                        }
                        return authResp;
                    }
                }
            }
            if (!authorized) {
                AbstractResponse authResp = authenticateUser(req, cRes, crReq, isSubNAHandle ? ADD_SUB_NA_PERM : ADD_HANDLE_PERM, naVals);
                if (authResp != null) {
                    if (naVals == null) {
                        logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unable to find admin group while creating handle");
                        if (adminGroupException != null) {
                            adminGroupException.printStackTrace();
                        }
                    }
                    return authResp;
                }
            }

            // add the new handle to the database if it isn't already in there
            try {
                byte action = rawValues == null ? Transaction.ACTION_CREATE_HANDLE : Transaction.ACTION_UPDATE_HANDLE;
                AbstractResponse maybeError = validateAndInsertTransactionReturnResponseIfError(req, handle, req.values, action);
                if (maybeError != null) return maybeError;

                if (rawValues == null) {
                    storage.createHandle((caseSensitive ? handle : Util.upperCase(handle)), req.values);
                } else {
                    storage.updateValue((caseSensitive ? handle : Util.upperCase(handle)), req.values);
                }
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error committing transaction: " + e);
                switch (e.getCode()) {
                case HandleException.HANDLE_ALREADY_EXISTS:
                    return new ErrorResponse(req, AbstractMessage.RC_HANDLE_ALREADY_EXISTS, null);
                case HandleException.STORAGE_RDONLY:
                    return new ErrorResponse(req, AbstractMessage.RC_SERVER_BACKUP, MSG_SERVER_BACKUP);
                default:
                    return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
                }
            } finally {
                transactionsInProgress.remove(Thread.currentThread().getId());
            }
        }

        //AbstractResponse resp = new GenericResponse(req, AbstractMessage.RC_SUCCESS);
        AbstractResponse resp = new CreateHandleResponse(req, handle);
        resp.overwriteWhenExists = didOverwriteExisting;
        resp.mintNewSuffix = req.mintNewSuffix;
        return resp;
    }

    private int[] getPermsAndSetTimestampsForCreationWithOverwrite(HandleValue[] existingValues, HandleValue[] newValues) {
        boolean needsAddAdminPerm = false;
        boolean needsAddValuePerm = false;
        boolean needsRemAdminPerm = false;
        boolean needsRemValuePerm = false;
        boolean needsModAdminPerm = false;
        boolean needsModValuePerm = false;
        boolean needsAdmToValPerm = false;
        boolean needsValToAdmPerm = false;
        Arrays.sort(existingValues, HandleValue.INDEX_COMPARATOR);
        Arrays.sort(newValues, HandleValue.INDEX_COMPARATOR);
        int i = 0;
        int j = 0;
        while (i < existingValues.length && j < newValues.length) {
            if (existingValues[i].getIndex() < newValues[j].getIndex()) {
                boolean oldValIsAdmin = existingValues[i].hasType(Common.ADMIN_TYPE);
                if (oldValIsAdmin) needsRemAdminPerm = true;
                else needsRemValuePerm = true;
                i++;
            } else if (existingValues[i].getIndex() > newValues[j].getIndex()) {
                boolean newValIsAdmin = newValues[j].hasType(Common.ADMIN_TYPE);
                if (newValIsAdmin) needsAddAdminPerm = true;
                else needsAddValuePerm = true;
                j++;
            } else {
                if (existingValues[i].equalsIgnoreTimestamp(newValues[j])) {
                    // don't overwrite values which have not changed
                    newValues[j].setTimestamp(existingValues[i].getTimestamp());
                } else {
                    boolean oldValIsAdmin = existingValues[i].hasType(Common.ADMIN_TYPE);
                    boolean newValIsAdmin = newValues[j].hasType(Common.ADMIN_TYPE);
                    if (oldValIsAdmin && newValIsAdmin) {
                        needsModAdminPerm = true;
                    } else if (oldValIsAdmin && !newValIsAdmin) {
                        needsAdmToValPerm = true;
                    } else if (!oldValIsAdmin && newValIsAdmin) {
                        needsValToAdmPerm = true;
                    } else if (!oldValIsAdmin && !newValIsAdmin && !existingValues[i].getAnyoneCanWrite()) {
                        needsModValuePerm = true;
                    }
                }
                i++;
                j++;
            }
        }
        for (; i < existingValues.length; i++) {
            boolean oldValIsAdmin = existingValues[i].hasType(Common.ADMIN_TYPE);
            if (oldValIsAdmin) needsRemAdminPerm = true;
            else needsRemValuePerm = true;
        }
        for (; j < newValues.length; j++) {
            boolean newValIsAdmin = newValues[j].hasType(Common.ADMIN_TYPE);
            if (newValIsAdmin) needsAddAdminPerm = true;
            else needsAddValuePerm = true;
        }
        int[] neededPerms = null;
        if (needsModAdminPerm) neededPerms = combinePerms(neededPerms, MOD_ADM_PERM);
        if (needsModValuePerm) neededPerms = combinePerms(neededPerms, MOD_VAL_PERM);
        if (needsAdmToValPerm) neededPerms = combinePerms(neededPerms, ADM_TO_VAL_PERM);
        if (needsValToAdmPerm) neededPerms = combinePerms(neededPerms, VAL_TO_ADM_PERM);
        if (needsAddAdminPerm) neededPerms = combinePerms(neededPerms, ADD_ADM_PERM);
        if (needsAddValuePerm) neededPerms = combinePerms(neededPerms, ADD_VAL_PERM);
        if (needsRemAdminPerm) neededPerms = combinePerms(neededPerms, REM_ADM_PERM);
        if (needsRemValuePerm) neededPerms = combinePerms(neededPerms, REM_VAL_PERM);
        return neededPerms;
    }

    @Override
    public AbstractResponse errorIfNotHaveHandle(AbstractRequest req) throws HandleException {
        return errorIfNotHaveHandle(req, req.handle);
    }

    public AbstractResponse errorIfNotHaveHandle(AbstractRequest req, byte[] handle) throws HandleException {
        if (thisSite != null && thisSite.servers.length > 1 && thisSite.determineServerNum(handle) != thisServerNum) {
            return new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_WRONG_SERVER_HASH);
        }
        boolean haveHandle = false;
        try {
            haveHandle = storageHaveNA(Util.getZeroNAHandle(handle));
            if (!haveHandle && Util.isSubNAHandle(handle)) haveHandle = storageHaveDerivedPrefixesOnly(handle);
        } catch (HandleException e) {
            return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
        }
        if (!haveHandle) {
            return new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_NA_NOT_HOMED_HERE);
        }
        if (Util.isSubNAHandle(handle)) {
            AbstractResponse prefixReferralResponse = getPrefixReferralResponseIfAppropriate(req, handle);
            if (prefixReferralResponse == null) return null;
            if (req.opCode == AbstractMessage.OC_RESOLUTION || req.opCode == AbstractMessage.OC_VERIFY_CHALLENGE) return prefixReferralResponse;
            boolean oldClient = !AbstractMessage.hasEqualOrGreaterVersion(req.suggestMajorProtocolVersion, req.suggestMinorProtocolVersion, 2, 10);
            if (!oldClient) return prefixReferralResponse;
        }

        return null;
    }

    private final AbstractResponse getPrefixReferralResponseIfAppropriate(AbstractRequest req, byte[] handle) throws HandleException {
        // TODO: this code assumes all prefixes in storage, so won't work in a "split" root server!
        if (req.doNotRefer) return null;
        byte[][] clumps = getRawHandleValuesWithTemplate(handle, null, null, req.recursionCount);
        if (clumps != null) return null;
        byte[] ancestorHandle = Util.getParentNAOfNAHandle(handle);
        while (true) {
            clumps = getRawHandleValuesWithTemplate(ancestorHandle, null, Common.DERIVED_PREFIX_SITE_AND_SERVICE_HANDLE_TYPES, req.recursionCount);
            if (clumps != null && clumps.length > 0) {
                //                return new ServiceReferralResponse(req, AbstractResponse.RC_PREFIX_REFERRAL, handle, null);
                clumps = publicValuesOnly(clumps);
                if (clumps.length > 0) {
                    return new ServiceReferralResponse(req, AbstractMessage.RC_PREFIX_REFERRAL, new byte[0], clumps);
                }
            }
            if (!Util.isSubNAHandle(ancestorHandle)) {
                return null;
            }
            ancestorHandle = Util.getParentNAOfNAHandle(ancestorHandle);
        }
    }

    /************************************************************************
     * process a resolution request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final AbstractResponse doResolution(ResolutionRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq, boolean isInternal) throws HandleException {
        byte[] handle = req.handle;
        // this is a hook to enable returning computed values (server status, etc)
        // from requests for certain handles
        if (Util.equalsCI(handle, SERVER_STATUS_HANDLE)) {
            if (enableStatusHandle) {
                return doSiteStatusResolutionRequest(req, cRes, crReq);
            } else {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
            }
        }

        if (!Util.hasSlash(handle)) handle = Util.convertSlashlessHandleToZeroNaHandle(handle);

        byte clumps[][] = null;
        AbstractResponse res = errorIfNotHaveHandle(req, handle);
        if (res == null) {
            try {
                clumps = getRawHandleValuesWithTemplate(handle, req.requestedIndexes, req.requestedTypes, req.recursionCount);
            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(getClass()) + ": error getting values: " + e);
                e.printStackTrace(System.err);
                return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
            }
            if (clumps == null) {
                return new ErrorResponse(req, AbstractMessage.RC_HANDLE_NOT_FOUND, null);
            } else if (clumps.length == 0) {
                return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
            } else if (isInternal) {
                return new ResolutionResponse(req, req.handle, clumps);
            } else {
                return checkReadAccess(req, clumps, cRes, crReq);
            }
        } else {
            // we don't have the handle, need to resolve it
            if ((allowRecursiveQueries && req.recursive) || (performRecursionForOldClientsRequestingReferredPrefixes && isOldClientRequestingReferredPrefix(req, res))) {
                AbstractRequest clonedReq = cloneRequestForRecursion(req);
                return processRecursiveRequestAndAdaptResponse(clonedReq, req);
            } else {
                // the request was non-recursive, so we should just return an
                // error instead of resolving the handle ourselves.
                return res;
            }
        }
    }

    private AbstractRequest cloneRequestForRecursion(AbstractRequest req) {
        AbstractRequest clonedReq = req.clone();
        clonedReq.recursionCount++;
        clonedReq.recursive = false;
        clonedReq.clearBuffers();
        clonedReq.suggestMajorProtocolVersion = Common.MAJOR_VERSION;
        clonedReq.suggestMinorProtocolVersion = Common.MINOR_VERSION;
        return clonedReq;
    }

    private AbstractResponse processRecursiveRequestAndAdaptResponse(AbstractRequest clonedReq, AbstractRequest origReq) throws HandleException {
        if (clonedReq.recursionCount > RECURSION_LIMIT) return new ErrorResponse(origReq, AbstractMessage.RC_RECURSION_COUNT_TOO_HIGH, null);
        try {
            AbstractResponse resp = resolver.processRequest(clonedReq);
            resp.setSupportedProtocolVersion(origReq);
            resp.suggestMajorProtocolVersion = Common.MAJOR_VERSION;
            resp.suggestMinorProtocolVersion = Common.MINOR_VERSION;
            resp.clearBuffers();
            if (resp.returnRequestDigest) resp.takeDigestOfRequest(origReq);
            return resp;
        } catch (Exception e) {
            return HandleException.toErrorResponse(origReq, e);
        }
    }

    private boolean isOldClientRequestingReferredPrefix(AbstractRequest req, AbstractResponse maybePrefixReferralResponse) {
        if (maybePrefixReferralResponse.responseCode != AbstractMessage.RC_PREFIX_REFERRAL) return false;
        boolean oldClient = !AbstractMessage.hasEqualOrGreaterVersion(req.suggestMajorProtocolVersion, req.suggestMinorProtocolVersion, 2, 10);
        return oldClient;
    }

    private static byte[][] publicValuesOnly(byte[][] clumps) throws HandleException {
        HandleValue[] values = Encoder.decodeHandleValues(clumps);
        int numSecret = 0;
        for (int i = 0; i < values.length; i++) {
            if (!values[i].getAnyoneCanRead()) {
                clumps[i] = null;
                numSecret++;
            }
        }
        if (numSecret == 0) return clumps;
        byte[][] res = new byte[clumps.length - numSecret][];
        int index = 0;
        for (byte[] clump : clumps) {
            if (clump != null) res[index++] = clump;
        }
        return res;
    }

    private AbstractResponse doSiteStatusResolutionRequest(ResolutionRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // construct and return a set of handles values containing the status of
        // the server
        if (req.ignoreRestrictedValues) {
            return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);
        }

        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, serverAdmins, statusHandleAdmins);
        if (authError != null) return authError;

        List<HandleValue> vals = new ArrayList<>();

        StringBuffer status = new StringBuffer();
        Runtime jr = Runtime.getRuntime();
        status.append("freemem=").append(jr.freeMemory());
        status.append("; totalmem=").append(jr.totalMemory());
        status.append("; maxmem=").append(jr.maxMemory());
        status.append("; runtime=").append(System.currentTimeMillis() - startTime);
        status.append("; numreqs=").append(numRequests);
        vals.add(new HandleValue(1, SERVER_STATUS_HDL_TYPE, Util.encodeString(status.toString())));

        if (monitorDaemon != null) {
            String monitorDaemonStatus = monitorDaemon.getStatusString();
            if (monitorDaemonStatus != null) {
                vals.add(new HandleValue(2, SERVER_STATUS_HDL_TYPE, Util.encodeString(monitorDaemonStatus)));
            }
        }

        JsonObject replicationInfo = new JsonObject();
        if (enableTxnQueue && isPrimary) {
            long latestTxnId = this.getLatestTxnId();
            replicationInfo.addProperty("latestTxnId", latestTxnId);
        }
        if (replicationDaemon != null) {
            StreamTable replicationStatus = replicationDaemon.replicationStatus();
            if (replicationStatus != null) {
                JsonElement replicationStatusAsJsonElement = StreamObjectToJsonConverter.toJson(replicationStatus);
                replicationInfo.add("replicationStatus", replicationStatusAsJsonElement);
            }
        }
        Gson gson = GsonUtility.getGson();
        String replicationInfoAsJsonString = gson.toJson(replicationInfo);
        vals.add(new HandleValue(3, REPLICATION_STATUS_HDL_TYPE, Util.encodeString(replicationInfoAsJsonString)));

        byte valBytes[][] = new byte[vals.size()][];
        for (int i = 0; i < valBytes.length; i++) {
            HandleValue val = vals.get(i);
            val.setAnyoneCanRead(false);
            valBytes[i] = new byte[Encoder.calcStorageSize(val)];
            Encoder.encodeHandleValue(valBytes[i], 0, val);
        }

        return new ResolutionResponse(req, req.handle, valBytes);
    }

    private AbstractResponse returnErrorOrChallengeIfRequestNotAuthorized(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq, ValueReference[]... adminLists) throws HandleException {
        try {
            if (wideOpenAccessAllowed) return null;

            //create challenge when there is no challenge answer info, or it is not a valid session
            if ((cRes == null || crReq == null) && !authenticatedSession(req)) return createChallenge(req);

            ValueReference thisAdmin = null;
            ServerSideSessionInfo sessionInfo = getSession(req.sessionId);
            if (crReq != null) {
                thisAdmin = new ValueReference(crReq.userIdHandle, crReq.userIdIndex);
            } else {
                //a valid session is assumed
                if (sessionInfo != null) {
                    thisAdmin = new ValueReference(sessionInfo.identityKeyHandle, sessionInfo.identityKeyIndex);
                } else return new ErrorResponse(req, AbstractMessage.RC_SESSION_TIMEOUT, MSG_INVALID_SESSION_OR_TIMEOUT);
            }

            // if the server has been configured to require sessions in order to perform
            // administration then reject any requests that are not contained in a session
            // and are not attempting to set up a session
            if (requireSessions && sessionInfo == null && req.opCode != AbstractMessage.OC_SESSION_SETUP) {
                System.err.println("rejecting non-session request: " + req + "; session: " + getSession(req.sessionId));
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_CREDENTIAL, MSG_SESSION_REQUIRED);
            }

            boolean verified = false;
            Vector<ValueReference> valuesTraversed = new Vector<>();
            boolean hasPermission = isAdminInLists(thisAdmin, valuesTraversed, adminLists);
            if (!hasPermission && mightGetNewIndexForAuthentication(crReq, sessionInfo)) {
                AbstractResponseAndIndex verifyResp = verifyIdentityAndGetIndex(cRes, crReq, req);
                if (verifyResp.getResponse() != null) return verifyResp.getResponse();
                verified = true;
                if (verifyResp.getIndex() != 0) {
                    thisAdmin = new ValueReference(thisAdmin.handle, verifyResp.getIndex());
                    hasPermission = valuesTraversed.contains(thisAdmin);
                }
            }

            if (!hasPermission) {
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_ADMIN, null);
            }
            if (verified) return null;

            // If we got to here, then they are in the admin list for this server
            // Now we just need to verify that they are who they claim to be.
            AbstractResponse verifyResp = verifyIdentity(cRes, crReq, req);
            return verifyResp;
        } catch (HandleException e) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Unable to authenticate request: " + e);
            return new ErrorResponse(req, AbstractMessage.RC_AUTHEN_ERROR, null);
        }
    }

    private static boolean mightGetNewIndexForAuthentication(ChallengeAnswerRequest crReq, ServerSideSessionInfo sessionInfo) {
        if (crReq != null && Common.PUBLIC_KEY_TYPE.equals(crReq.authType) && crReq.userIdIndex == 0) return true;
        if (sessionInfo != null && sessionInfo.identityKeyIndex == 0) return true;
        return false;
    }

    private boolean isAdminInLists(ValueReference thisAdmin, Vector<ValueReference> valuesTraversed, ValueReference[]... adminLists) {
        Vector<ValueReference> valuesToTraverse = new Vector<>();
        boolean hasPermission = false;
        outer: for (ValueReference[] adminList : adminLists) {
            for (int i = 0; i < adminList.length; i++) {
                ValueReference admin = adminList[i];
                if (admin.isMatchedBy(thisAdmin)) {
                    hasPermission = true;
                    break outer;
                } else {
                    valuesToTraverse.addElement(admin);
                }
            }
        }
        if (!hasPermission) {
            hasPermission = isAdminInGroup(thisAdmin, valuesToTraverse, valuesTraversed);
        }
        return hasPermission;
    }

    /************************************************************************
     * process a list-handles request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final void doListHandles(ResponseMessageCallback callback, ListHandlesRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        byte[] handle = req.handle;
        if (!caseSensitive) handle = Util.upperCase(handle);
        else handle = Util.upperCasePrefix(handle);
        if (!Util.hasSlash(handle)) handle = Util.convertSlashlessHandleToZeroNaHandle(handle);

        // make sure the server allows the list handles permission
        if (!allowListHdls) {
            sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_OPERATION_NOT_SUPPORTED, MSG_NEED_LIST_HDLS_PERM));
            return;
        }

        // make sure that we are supposed to take this handle...
        if (!storageHaveNA(handle)) {
            if (!isSpecialDerivedPrefixMarker(handle)) {
                sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_NA_NOT_HOMED_HERE));
                return;
            } else {
                if (!storage.haveNA(Common.ROOT_HANDLE)) {
                    sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_SERVER_NOT_RESP, MSG_NA_NOT_HOMED_HERE));
                    return;
                }
            }
        }

        // make sure the user has authenticated.  If not, send a challenge
        if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            sendResponse(callback, createChallenge(req));
            return;
        }

        try {
            // authenticate the user
            byte vals[][] = null;
            Exception exception = null;
            try {
                vals = getNAAdminValues(handle);
            } catch (Exception e) {
                exception = e;
            }
            AbstractResponse authResp = authenticateUser(req, cRes, crReq, LIST_HDLS_PERM, vals);
            if (authResp != null) {
                if (exception != null) throw exception;
                sendResponse(callback, authResp);
                return;
            }
        } catch (Exception e) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Auth error on list-handles request: " + e);
            sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_AUTHEN_ERROR, null));
            return;
        }

        // At this point, the requestor has authenticated as an admin
        // of the NA with permission to list handles.  We will now begin
        // sending back list handle responses...
        ListHandlesResponse response = new ListHandlesResponse(req, null);
        byte handles[][] = new byte[LIST_HANDLES_PER_MSG][];
        int numHandles = 0;
        boolean noHandles = true;
        Enumeration<byte[]> listEnum = storage.getHandlesForNA(handle);
        try {
            while (listEnum.hasMoreElements()) {
                noHandles = false;
                handles[numHandles++] = listEnum.nextElement();
                if (numHandles >= LIST_HANDLES_PER_MSG) {
                    response.handles = handles;
                    response.clearBuffers();
                    response.continuous = listEnum.hasMoreElements();
                    numHandles = 0;
                    sendResponse(callback, response);
                }
            }
        } catch (RuntimeException e) {
            if (e.getCause() instanceof HandleException) throw (HandleException) (e.getCause());
            else throw e;
        } finally {
            if (listEnum instanceof Closeable) {
                try {
                    ((Closeable) listEnum).close();
                } catch (Exception e) {
                }
            }
        }

        if (noHandles || numHandles > 0) {
            byte tmpHandles[][] = new byte[numHandles][];
            System.arraycopy(handles, 0, tmpHandles, 0, numHandles);
            response.handles = tmpHandles;
            response.clearBuffers();
            response.continuous = false;
            numHandles = 0;
            sendResponse(callback, response);
        }
    }

    private boolean isSpecialDerivedPrefixMarker(byte[] handle) {
        return Util.startsWithCI(handle, Common.SPECIAL_DERIVED_MARKER);
    }

    /************************************************************************
     * process a list-NAs request
     ************************************************************************/
    private final void doListNAs(ResponseMessageCallback callback, ListNAsRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        // make sure the user has authenticated.  If not, send a challenge
        if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            sendResponse(callback, createChallenge(req));
            return;
        }

        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, serverAdmins, homeOnlyAdmins);
        if (authError != null) {
            sendResponse(callback, authError);
            return;
        }

        // At this point, the requestor has authenticated as an admin.We will now begin
        // sending back list NAs responses...
        ListNAsResponse response = new ListNAsResponse(req, null);

        HomedPrefixAccumulator homedPrefixAccumulator = new HomedPrefixAccumulator(storage);
        List<byte[]> homedPrefixes = homedPrefixAccumulator.getHomedPrefixes();
        Iterator<byte[]> listIter = homedPrefixes.iterator();

        byte handles[][] = new byte[LIST_HANDLES_PER_MSG][];
        int numHandles = 0;
        boolean errorSendingResponse = false;
        boolean noHandles = true;
        while (!errorSendingResponse && listIter.hasNext()) {
            noHandles = false;
            handles[numHandles++] = listIter.next();
            if (numHandles >= LIST_HANDLES_PER_MSG) {
                response.handles = handles;
                response.clearBuffers();
                response.continuous = listIter.hasNext();
                numHandles = 0;
                try {
                    sendResponse(callback, response);
                } catch (Throwable t) {
                    errorSendingResponse = true;
                    System.err.println("Error sending response to list-NAs request: " + t);
                }
            }
        }

        if ((noHandles || numHandles > 0) && !errorSendingResponse) {
            byte tmpHandles[][] = new byte[numHandles][];
            System.arraycopy(handles, 0, tmpHandles, 0, numHandles);
            response.handles = tmpHandles;
            response.clearBuffers();
            response.continuous = false;
            numHandles = 0;
            sendResponse(callback, response);
        }
    }

    /***************************************************************************
     * Check if the specified user has read access to the given handle values.
     * If so, then return a resolution response.
     * Otherwise, return a challenge to the user to authenticate.
     ***************************************************************************/
    private final AbstractResponse checkReadAccess(ResolutionRequest req, byte clumps[][], ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        if (req.ignoreRestrictedValues) {
            // the request is only asking for the publicly readable values
            int numUnrestricted = 0;
            for (byte[] clump : clumps) {
                if ((Encoder.getHandleValuePermissions(clump, 0) & Encoder.PERM_PUBLIC_READ) != 0) numUnrestricted++;
            }

            if (numUnrestricted == 0) return new ErrorResponse(req, AbstractMessage.RC_VALUES_NOT_FOUND, null);

            byte unrestrictedVals[][] = new byte[numUnrestricted][];
            numUnrestricted--;
            for (int i = clumps.length - 1; i >= 0; i--) {
                if ((Encoder.getHandleValuePermissions(clumps[i], 0) & Encoder.PERM_PUBLIC_READ) != 0) unrestrictedVals[numUnrestricted--] = clumps[i];
            }

            return new ResolutionResponse(req, req.handle, unrestrictedVals);
        }

        // if we got here, the user wants to be authenticated if necessary
        boolean needsauth = req.sessionId != 0;
        if (!needsauth) {
            for (byte[] clump : clumps) {
                byte perms = Encoder.getHandleValuePermissions(clump, 0);
                if ((perms & Encoder.PERM_PUBLIC_READ) == 0) {
                    if ((perms & Encoder.PERM_ADMIN_READ) == 0) {
                        // neither admin nor public read access is allowed...
                        return new ErrorResponse(req, AbstractMessage.RC_INSUFFICIENT_PERMISSIONS, null);
                    }
                    needsauth = true;
                    break;
                }
            }
        }

        if (!needsauth) {
            // no authentication needed...
            return new ResolutionResponse(req, req.handle, clumps);

        } else if ((cRes == null || crReq == null) && !authenticatedSession(req)) {
            // needs authentication, none provided; send a challenge to authenticate
            return createChallenge(req);
        } else {
            // needs authentication, some provided
            byte adminClumps[][] = getRawHandleValuesWithTemplate(req.handle, Common.ADMIN_INDEXES, Common.ADMIN_TYPES, req.recursionCount);
            AbstractResponse authResp = authenticateUser(req, cRes, crReq, READ_VAL_PERM, adminClumps);
            if (authResp != null) return authResp;
            else return new ResolutionResponse(req, req.handle, clumps);
        }
    }

    /*****************************************************************************
     *
     *  Get the next transaction ID.  If we are unable to get the next transaction ID
     *  (for example, if the txn ID server is not responding) then we throw an exception.
     *
     */

    /** Sets the replication priority for transactions on this server */
    @Override
    public void setReplicationPriority(int i) {
        replicationPriority = i;
    }

    /** Sets the name for the replication site this server is in */
    public void setReplicationSiteName(String name) {
        replicationSiteName = name;
        if (replicationDaemon != null) {
            ReplicationStateInfo replicationStateInfo = replicationDaemon.getReplicationStateInfo();
            if (replicationStateInfo != null) {
                replicationStateInfo.setOwnName(name);
            }
        }
    }

    private ErrorResponse validateAndInsertTransactionReturnResponseIfError(AbstractRequest req, byte handle[], HandleValue[] values, byte action) throws HandleException {
        if (replicationValidator != null) {
            Transaction txn = new Transaction(0, handle, values, action, 0);
            try {
                TransactionValidator.ValidationResult result = replicationValidator.validate(txn);
                if (!result.isValid()) {
                    String message = "Transaction is invalid according to policy";
                    if (result.getMessage() != null) message += ": " + result.getMessage();
                    return new ErrorResponse(req, AbstractMessage.RC_INVALID_VALUE, Util.encodeString(message));
                }
                if (result.getReport() != null) {
                    logError(ServerLog.ERRLOG_LEVEL_INFO, GsonUtility.getPrettyGson().toJson(result.getReport()));
                }
            } catch (HandleException e) {
                logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Error validating transaction");
                e.printStackTrace();
                return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
            }
        }
        if (!insertTransaction(handle, values, action)) {
            return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
        }
        return null;
    }

    private final boolean insertTransaction(byte handle[], HandleValue[] values, byte action) {
        if (!enableTxnQueue) return true;
        long date = System.currentTimeMillis();
        if (replicationDaemon != null) {
            try {
                switch (action) {
                case Transaction.ACTION_CREATE_HANDLE:
                case Transaction.ACTION_UPDATE_HANDLE:
                case Transaction.ACTION_DELETE_HANDLE:
                    date = replicationDaemon.adjustAndSetLastCreateOrDeleteDate(handle, date, replicationPriority);
                    break;
                case Transaction.ACTION_HOME_NA:
                case Transaction.ACTION_UNHOME_NA:
                    date = replicationDaemon.adjustAndSetLastHomeOrUnhomeDate(handle, date, replicationPriority);
                    break;
                default: //no-op
                }
            } catch (Throwable e) {
                logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Unable to save transation in replication database");
                e.printStackTrace();
            }
        }
        try {
            long thisTxnId = getNextTxnId();
            transactionsInProgress.put(Thread.currentThread().getId(), thisTxnId);
            txnQueue.addTransaction(thisTxnId, handle, values, action, date);
        } catch (Throwable e) {
            logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Unable to insert transaction into queue");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private final AbstractResponse createChallenge(AbstractRequest req) throws HandleException {
        boolean validSessionReq = validSession(req);

        if (validSessionReq) {
            ChallengeResponseInfo formerCri = pendingAuthorizations.get(req.sessionId);
            if (formerCri != null && req.requestId == formerCri.originalRequest.requestId) {
                // same request duplicated, possibly over UDP; just re-use previous challenge
                return formerCri.challenge;
            }
        }

        ChallengeResponseInfo cri = new ChallengeResponseInfo();
        cri.timeStarted = System.currentTimeMillis();
        cri.challenge = new ChallengeResponse(req);
        cri.originalRequest = req;

        //use session id if the req is in a valid session
        if (validSessionReq) {
            cri.sessionId = req.sessionId;
        } else {
            cri.sessionId = getNextSessionId();
        }

        cri.challenge.sessionId = cri.sessionId;
        ChallengeResponseInfo formerCri = pendingAuthorizations.put(cri.sessionId, cri);
        if (formerCri != null) {
            // already was a pending auth in this session
            try {
                if (!formerCri.challengeAccepted && !formerCri.hasExpired()) {
                    logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Warning: clobbering pending authorization of " + formerCri.originalRequest);
                }
            } catch (Exception e) {
            }
        }

        return cri.challenge;
    }

    private static synchronized int getNextSessionId() {
        return ++nextAuthId;
    }

    /**
     * Determines if the administrator handle/index combination included in the
     * admin parameter is listed in a group (aka HS_VLIST) that is included in
     * the valuesToTraverse Vector (of ValueReference objects).  ValueReferences
     * from the valuesToTraverse Vector are resolved and the contents of any HS_VLIST
     * values are added to the valuesToTraverse vector if they aren't already in
     * valuesTraversed.  If an exact match for the admin ValueReference is found
     * then this returns true.  Otherwise it returns false.
     */
    private final boolean isAdminInGroup(ValueReference admin, Vector<ValueReference> valuesToTraverse, Vector<ValueReference> valuesTraversed) {
        while (valuesToTraverse.size() > 0) {
            ValueReference val = valuesToTraverse.elementAt(0);
            valuesToTraverse.removeElementAt(0);

            if (valuesTraversed.contains(val)) {
                continue;
            }

            valuesTraversed.addElement(val);
            if (val.index == 0) continue;

            ResolutionRequest req = new ResolutionRequest(val.handle, null, new int[] { val.index }, null);
            HandleValue groupValue = null;
            try {
                req.certify = true;
                AbstractResponse response;
                // if the group handle lives on this server, find it locally
                // instead of talking to ourself via the network.
                if (errorIfNotHaveHandle(req) == null) {
                    // allow authenticating using secret HS_VLIST
                    req.ignoreRestrictedValues = false;
                    response = doResolution(req, null, null, true);
                } else {
                    response = resolver.processRequest(req);
                }

                if (response.responseCode == AbstractMessage.RC_SUCCESS && response.opCode == AbstractMessage.OC_RESOLUTION) {
                    ResolutionResponse resResponse = (ResolutionResponse) response;
                    groupValue = new HandleValue();

                    for (byte[] value : resResponse.values) {
                        Encoder.decodeHandleValue(value, 0, groupValue);
                        if (!groupValue.hasType(Common.STD_TYPE_HSVALLIST)) continue;
                        ValueReference valuesInGroup[] = Encoder.decodeValueReferenceList(groupValue.getData(), 0);
                        for (int i = 0; i < valuesInGroup.length; i++) {
                            if (valuesInGroup[i].isMatchedBy(admin)) {
                                return true;
                            } else if (!valuesToTraverse.contains(valuesInGroup[i]) && !valuesTraversed.contains(valuesInGroup[i])) {
                                valuesToTraverse.addElement(valuesInGroup[i]);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                System.err.println("Error trying to resolve possible group: " + e);
                e.printStackTrace(System.err);
            }
        }

        if (DEBUG_AUTHENTICATION) System.err.println("" + new Date() + " isAdminInGroup fails: " + admin + " " + valuesTraversed);
        return false;
    }

    /************************************************************************
     * Verify that the person who signed the response actually is who they
     * claim to be, and that the given response is a reply to the given
     * challenge.  Returns null if the requestor's identity was verified.
     * Otherwise it returns the error message that should be sent back to
     * the requestor.

     * Now added verify identity from session info
     ************************************************************************/
    @Override
    public final AbstractResponse verifyIdentity(ChallengeResponse cRes, ChallengeAnswerRequest crReq, AbstractRequest origReq) throws HandleException {
        if (crReq instanceof PreAuthenticatedChallengeAnswerRequest) return null;
        return verifyIdentityAndGetIndex(cRes, crReq, origReq).getResponse();
    }

    @Override
    public final AbstractResponseAndIndex verifyIdentityAndGetIndex(ChallengeResponse cRes, ChallengeAnswerRequest crReq, AbstractRequest origReq) throws HandleException {
        if (crReq instanceof PreAuthenticatedChallengeAnswerRequest) return new AbstractResponseAndIndex(0, null);
        if (cRes == null && crReq == null && authenticatedSession(origReq)) {
            // no challenge/response and there apparently was a session established
            ServerSideSessionInfo sssinfo = getSession(origReq.sessionId);
            if (sssinfo == null) {
                return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_SESSION_TIMEOUT, MSG_INVALID_SESSION_OR_TIMEOUT));
            }

            // the signature of the origReq shall be a HS_MAC types credential
            if (origReq.signature == null || origReq.signature.length <= 0) {
                //send a challenage response
                return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_INVALID_CREDENTIAL, Util.encodeString("Session request missing MAC code.")));
            }

            byte[] sessionKey = sssinfo.getSessionKey();
            try {
                if (sessionKey != null && origReq.verifyMessage(sessionKey)) {
                    try {
                        sssinfo.addSessionCounter(origReq.sessionCounter, true);
                    } catch (HandleException e) {
                        if (e.getCode() == HandleException.DUPLICATE_SESSION_COUNTER) {
                            return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_SESSION_MESSAGE_REJECTED, Util.encodeString(e.getMessage())));
                        } else throw e;
                    }
                    return new AbstractResponseAndIndex(sssinfo.getIndexOfAuthenticatedSession(), null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error verifying session key:" + e);
            }
            return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_INVALID_SESSION_KEY, Util.encodeString("Session authentication failed.")));
        }

        if (cRes != null && crReq != null && Util.equals(crReq.authType, Common.SECRET_KEY_TYPE)) {
            // there was a response to a challenge and it was based on a secret key

            // we verify secret keys by asking the responsible server if the
            // given response (md5 hash of the secret_key+nonce+request_digest)
            // is valid for the given challenge.

            VerifyAuthRequest vaReq = new VerifyAuthRequest(crReq.userIdHandle, cRes.nonce, cRes.requestDigest, cRes.rdHashType, crReq.signedResponse, crReq.userIdIndex, null);

            vaReq.certify = true;
            vaReq.setSupportedProtocolVersion(crReq);

            // if the callers identity handle lives on this server, find it locally
            // instead of talking to ourself via the network.
            AbstractResponse response;
            if (errorIfNotHaveHandle(vaReq) == null) {
                response = verifyChallenge(vaReq, null, null);
            } else {
                response = resolver.processRequest(vaReq);
            }

            if (response instanceof VerifyAuthResponse) {
                if (((VerifyAuthResponse) response).isValid) {

                    //if this is a valid session,
                    //set the client authenticated flag to true.
                    //for the first time request, MAC wasn't checked, so check here.
                    if (validSession(origReq)) {

                        setSessionAuthenticated(origReq, crReq, true);

                        boolean MACpass = true;
                        ServerSideSessionInfo ssinfo = getSession(origReq.sessionId);
                        if (ssinfo == null) {
                            return new AbstractResponseAndIndex(0, null);
                        }
                        if (ssinfo.getSessionKey() != null) {
                            try {
                                MACpass = origReq.verifyMessage(ssinfo.getSessionKey());

                            } catch (Exception e) {
                                System.err.println("Error verifying the original request MAC code:" + e);
                                MACpass = false;
                            }
                        }
                        if (MACpass) {
                            try {
                                ssinfo.addSessionCounter(crReq.sessionCounter, true);
                                // no need to enforce uniqueness; even if origReq is a dupe, it's a brand-new authentication
                                ssinfo.addSessionCounter(origReq.sessionCounter, false);
                            } catch (HandleException e) {
                                if (e.getCode() == HandleException.DUPLICATE_SESSION_COUNTER) {
                                    return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_SESSION_MESSAGE_REJECTED, Util.encodeString(e.getMessage())));
                                } else throw e;
                            }
                            return new AbstractResponseAndIndex(0, null);
                        } else return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_INVALID_SESSION_KEY, Util.encodeString("The session key authentication failed.")));
                    }

                    return new AbstractResponseAndIndex(0, null);
                } else {
                    if (crReq.signedResponse[0] > Common.HASH_CODE_SHA1 && !response.hasEqualOrGreaterVersion(cRes.majorProtocolVersion, cRes.minorProtocolVersion) && !response.hasEqualOrGreaterVersion(2, 7)) {
                        cRes.suggestMajorProtocolVersion = response.suggestMajorProtocolVersion;
                        cRes.suggestMinorProtocolVersion = response.suggestMinorProtocolVersion;
                        //cRes.setSupportedProtocolVersion(response);
                        cRes.clearBuffers();
                        return new AbstractResponseAndIndex(0, cRes);
                    }
                }
            }
            return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_AUTHENTICATION_FAILED, null));

        } else if (cRes != null && crReq != null && Util.equals(crReq.authType, Common.PUBLIC_KEY_TYPE)) {
            // there was a response to a challenge and it was based on a public key

            // verify that the challenge response was signed by the private key
            // associated with the administrators public key.

            // first retrieve the public key (checking server signatures, of course)
            ResolutionRequest req = new ResolutionRequest(crReq.userIdHandle, crReq.userIdIndex > 0 ? null : Common.PUBLIC_KEY_TYPES, crReq.userIdIndex > 0 ? new int[] { crReq.userIdIndex } : null, null);
            req.certify = true;
            AbstractResponse response = null;
            // if the callers identity handle lives on this server, find it locally
            // instead of talking to ourself via the network.
            if (errorIfNotHaveHandle(req) == null) {
                // allow authenticating using secret HS_PUBKEY, even though probably not useful
                req.ignoreRestrictedValues = false;
                response = doResolution(req, null, null, true);
            } else {
                response = resolver.processRequest(req);
            }

            if (response.getClass() == ResolutionResponse.class) {
                ResolutionResponse rresponse = (ResolutionResponse) response;
                HandleValue values[] = rresponse.getHandleValues();
                if (values == null || values.length < 1) {
                    if (DEBUG_AUTHENTICATION) logError(ServerLog.ERRLOG_LEVEL_REALBAD, "No values kills auth " + crReq.userIdIndex + ":" + Util.decodeString(crReq.userIdHandle) + ": " + response);
                    return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_AUTHENTICATION_FAILED, null));
                }
                try {
                    int offset = 0;
                    byte hashAlgId[] = Encoder.readByteArray(crReq.signedResponse, offset);
                    offset += Encoder.INT_SIZE + hashAlgId.length;

                    // get the actual bytes of the signature
                    byte sigBytes[] = Encoder.readByteArray(crReq.signedResponse, offset);
                    offset += Encoder.INT_SIZE + sigBytes.length;

                    boolean verified = false;
                    int newIndex = 0;
                    Arrays.sort(values, HandleValue.INDEX_COMPARATOR);
                    for (HandleValue value : values) {
                        if (crReq.userIdIndex > 0 && crReq.userIdIndex != value.getIndex()) continue;
                        if (!Util.equals(Common.PUBLIC_KEY_TYPE, value.getType())) continue;
                        try {
                            // decode the public key
                            PublicKey pubKey = Util.getPublicKeyFromBytes(value.getData(), 0);

                            // get a signature object based on the public key
                            Signature sig = Signature.getInstance(Util.getSigIdFromHashAlgId(hashAlgId, pubKey.getAlgorithm()));
                            sig.initVerify(pubKey);

                            // verify the signature
                            sig.update(cRes.nonce);
                            sig.update(cRes.requestDigest);

                            if (sig.verify(sigBytes)) {
                                verified = true;
                                newIndex = value.getIndex();
                                break;
                            }
                        } catch (Exception e) {
                            if (DEBUG_AUTHENTICATION) {
                                logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception prevents verify index " + value.getIndex() + " for " + crReq.userIdIndex + ":" + Util.decodeString(crReq.userIdHandle) + ":");
                                e.printStackTrace();
                            }
                        }
                    }

                    if (verified) {
                        if (newIndex == crReq.userIdIndex) newIndex = 0;

                        //if this is a valid session,
                        //set the client authenticated flag to true.
                        //for the first time request, MAC wasn't checked, so check here.
                        if (validSession(origReq)) {

                            setSessionAuthenticated(origReq, crReq, true);

                            boolean MACpass = true;
                            ServerSideSessionInfo ssinfo = getSession(origReq.sessionId);
                            if (ssinfo == null) {
                                return new AbstractResponseAndIndex(newIndex, null);
                            }
                            if (ssinfo.getSessionKey() != null) {
                                try {
                                    MACpass = origReq.verifyMessage(ssinfo.getSessionKey());
                                } catch (Exception e) {
                                    System.err.println("Error verifying the original request MAC code:" + e);
                                    MACpass = false;
                                }
                            }
                            if (MACpass) {
                                try {
                                    ssinfo.addSessionCounter(crReq.sessionCounter, true);
                                    // no need to enforce uniqueness; even if origReq is a dupe, it's a brand-new authentication
                                    ssinfo.addSessionCounter(origReq.sessionCounter, false);
                                } catch (HandleException e) {
                                    if (e.getCode() == HandleException.DUPLICATE_SESSION_COUNTER) {
                                        return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_SESSION_MESSAGE_REJECTED, Util.encodeString(e.getMessage())));
                                    } else throw e;
                                }
                                ssinfo.setIndexOfAuthenticatedSession(newIndex);
                                return new AbstractResponseAndIndex(newIndex, null);
                            } else return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_INVALID_SESSION_KEY, Util.encodeString("The session key authentication failed.")));
                        }

                        return new AbstractResponseAndIndex(newIndex, null);
                    } else {
                        if (DEBUG_AUTHENTICATION) logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Verify failure kills auth " + crReq.userIdIndex + ":" + Util.decodeString(crReq.userIdHandle));
                        return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_AUTHENTICATION_FAILED, null));
                    }
                } catch (Exception e) {
                    if (DEBUG_AUTHENTICATION) {
                        logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception kills auth " + crReq.userIdIndex + ":" + Util.decodeString(crReq.userIdHandle) + ":");
                        e.printStackTrace();
                    }
                    return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_AUTHENTICATION_FAILED, null));
                }
            } else {
                if (DEBUG_AUTHENTICATION) logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Bad response kills auth " + crReq.userIdIndex + ":" + Util.decodeString(crReq.userIdHandle) + ": " + response);
                return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_AUTHENTICATION_FAILED, null));
            }
        } else {
            return new AbstractResponseAndIndex(0, new ErrorResponse(origReq, AbstractMessage.RC_INVALID_CREDENTIAL, null));
        }
    }

    /************************************************************************
     * Backup the server's database.
     * This first authenticates the client based on the server
     * administrators listed in the configuration file.
     ************************************************************************/
    private final AbstractResponse doBackup(GenericRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq) throws HandleException {
        AbstractResponse authError = returnErrorOrChallengeIfRequestNotAuthorized(req, cRes, crReq, backupAdmins, serverAdmins);
        if (authError != null) return authError;

        try {
            storage.checkpointDatabase();
        } catch (Exception e) {
            logError(ServerLog.ERRLOG_LEVEL_FATAL, "Error backup server: " + e);
            return new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_INTERNAL_ERROR);
        }

        return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
    }

    private final AbstractResponse doSessionTerminate(GenericRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {

        //check client authentication later! use session info, for session is still available right now..
        ServerSideSessionInfo ssinfo = getSession(req.sessionId);
        if (ssinfo != null) {

            boolean authenticated = false;
            try {
                authenticated = req.verifyMessage(ssinfo.getSessionKey());
            } catch (Exception e) {
                System.err.println(e.getMessage());
                authenticated = false;
            }

            if (!authenticated) {
                return new ErrorResponse(req, AbstractMessage.RC_INVALID_SESSION_KEY, Util.encodeString("Invalid session key."));
            }

            try {
                // no need to enforce uniqueness... if this message is a duplicate, the session is already terminated!
                ssinfo.addSessionCounter(req.sessionCounter, false);
            } catch (HandleException e) {
                if (e.getCode() == HandleException.DUPLICATE_SESSION_COUNTER) {
                    return new ErrorResponse(req, AbstractMessage.RC_SESSION_MESSAGE_REJECTED, Util.encodeString(e.getMessage()));
                } else throw e;
            }

            sessions.removeSession(req.sessionId);
            return new GenericResponse(req, AbstractMessage.RC_SUCCESS);
        } else {
            return new ErrorResponse(req, AbstractMessage.RC_SESSION_TIMEOUT, Util.encodeString("Can not get session info."));
        }
    }

    private final AbstractResponse doSessionSetup(SessionSetupRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {
        SessionSetupResponse rsp = new SessionSetupResponse(req, null);

        PublicKey pubKey = null; // the exchange key for SessionInfo
        byte sessionKey[] = null; // the exchange key for SessionInfo
        boolean oldClient = !AbstractMessage.hasEqualOrGreaterVersion(req.suggestMajorProtocolVersion, req.suggestMinorProtocolVersion, (byte) 2, (byte) 2);
        int sessionKeyAlg = oldClient ? HdlSecurityProvider.ENCRYPT_ALG_DES : encryptionAlgorithm;

        if (req.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_HDL) {
            try {
                HandleValue vals[] = resolver.resolveHandle(Util.decodeString(req.exchangeKeyHandle), null, new int[] { req.exchangeKeyIndex });
                HandleValue pubkeyval = null;
                for (int i = 0; vals != null && i < vals.length; i++) {
                    if (vals[i].hasType(Common.PUBLIC_KEY_TYPE)) {
                        pubkeyval = vals[i];
                        break;
                    }
                }
                if (pubkeyval == null) {
                    logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error initializing session with hdl cipher: no key found.");
                    return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("No key found in key exchange handle."));
                }
                if (vals == null) throw new AssertionError();
                pubKey = Util.getPublicKeyFromBytes(vals[0].getData(), 0);
                sessionKey = HdlSecurityProvider.getInstance().generateSecretKey(sessionKeyAlg);
                byte encryptKey[] = Util.substring(sessionKey, Encoder.INT_SIZE);
                rsp.data = Util.encrypt(pubKey, oldClient ? encryptKey : sessionKey, rsp.majorProtocolVersion, rsp.minorProtocolVersion);
                sessionKey = encryptKey;
            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error initializing session with hdl cipher: " + e);
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Error performing hdl cipher key exchange."));
            }
        } else if (req.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_CLIENT) {
            try {
                pubKey = Util.getPublicKeyFromBytes(req.publicKey, 0);
                sessionKey = HdlSecurityProvider.getInstance().generateSecretKey(sessionKeyAlg);
                byte encryptKey[] = Util.substring(sessionKey, Encoder.INT_SIZE);
                rsp.data = Util.encrypt(pubKey, oldClient ? encryptKey : sessionKey, rsp.majorProtocolVersion, rsp.minorProtocolVersion);
                sessionKey = encryptKey;
            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error initializing client cipher session: " + e);
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Error performing client cipher key exchange."));

            }
        } else if (req.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_SERVER) {
            if (!(this.publicKey instanceof RSAPublicKey)) {
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("KEY_EXCHANGE_CIPHER_SERVER not supported"));
            }
            try {
                rsp.data = Util.getBytesFromPublicKey(this.publicKey);
            } catch (Exception e) {
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error initializing server cipher session: " + e);
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Error initializing server cipher session: " + e));
            }
        } else if (req.keyExchangeMode == Common.KEY_EXCHANGE_DH) {
            try {
                pubKey = Util.getPublicKeyFromBytes(req.publicKey, 0);
                HdlSecurityProvider provider = HdlSecurityProvider.getInstance();
                DHPublicKey pub = (DHPublicKey) pubKey;
                DHParameterSpec dhSpec = pub.getParams();
                KeyPair kp = provider.generateDHKeyPair(dhSpec.getP(), dhSpec.getG());

                DHPrivateKey priv = (DHPrivateKey) kp.getPrivate();

                oldClient = !AbstractMessage.hasEqualOrGreaterVersion(req.suggestMajorProtocolVersion, req.suggestMinorProtocolVersion, (byte) 2, (byte) 4);
                if (!oldClient) {
                    byte[] pubKeyBytes = Util.getBytesFromPublicKey(kp.getPublic());
                    rsp.data = new byte[Encoder.INT_SIZE + pubKeyBytes.length];
                    Encoder.writeInt(rsp.data, 0, encryptionAlgorithm);
                    System.arraycopy(pubKeyBytes, 0, rsp.data, Encoder.INT_SIZE, pubKeyBytes.length);
                    sessionKey = provider.getKeyFromDH(pub, priv, encryptionAlgorithm);
                    sessionKey = Util.substring(sessionKey, Encoder.INT_SIZE);
                    sessionKeyAlg = encryptionAlgorithm;
                } else {
                    rsp.data = Util.getBytesFromPublicKey(kp.getPublic());
                    sessionKey = provider.getDESKeyFromDH(pub, priv);
                    sessionKeyAlg = HdlSecurityProvider.ENCRYPT_ALG_DES;
                }
            } catch (Exception e) {
                e.printStackTrace();
                logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Error initializing DH session: " + e);
                return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Error encoding public session key"));
            }
        } else {
            return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Unrecognized key exchange mode"));
        }
        int sessionId = getNextSessionId();
        rsp.sessionId = sessionId;

        ServerSideSessionInfo sinfo = new ServerSideSessionInfo(sessionId, sessionKey, req.identityHandle, req.identityIndex, sessionKeyAlg, pubKey, req.keyExchangeMode, rsp.majorProtocolVersion, rsp.minorProtocolVersion);
        // set other attributes of the session
        //sinfo.setEncryptionAlgorithmCode(sessionKeyAlg);
        sinfo.setTimeOut(req.timeout);
        sinfo.setEncryptedMesssageFlag(req.encryptAllSessionMsg);
        sinfo.setAuthenticateMessageFlag(req.authAllSessionMsg);
        sessions.addSession(sinfo);

        // store the request id of SESSION_SETUP_REQUEST into this modified session
        // info
        return rsp;
    }

    /**
     * client has sent the session key to finish a KEY_EXCHANGE_CIPHER_SERVER.
     **/
    private final AbstractResponse doKeyExchange(SessionExchangeKeyRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {

        if (!validSession(req)) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Bad server-cipher key exchange request");
            return new ErrorResponse(req, AbstractMessage.RC_SESSION_TIMEOUT, MSG_INVALID_SESSION_OR_TIMEOUT);
        }

        ServerSideSessionInfo sinfo = sessions.getSession(req.sessionId);

        if (sinfo.keyExchangeMode != Common.KEY_EXCHANGE_CIPHER_SERVER) {
            logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Bad server-cipher key exchange request");
            return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Invalid session id. Session failed."));
        }

        boolean oldClient = !req.hasEqualOrGreaterVersion((byte) 2, (byte) 2);
        byte[] encSessionKey = req.getEncryptedSessionKey();
        byte[] sessionKey = null;

        // this resonse will have protocol version the lesser of client and server
        AbstractResponse successResponse = new GenericResponse(req, AbstractMessage.RC_SUCCESS);

        // decrypt with server rsa private key
        try {
            sessionKey = Util.decrypt(privateKey, encSessionKey, successResponse.majorProtocolVersion, successResponse.minorProtocolVersion);
        } catch (Exception e) {
            return new ErrorResponse(req, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Can't decrypt client session key."));
        }

        int sessionKeyAlg;
        if (!oldClient) {
            // the caller is capable of encoding the encryption algorithm
            sessionKeyAlg = Encoder.readInt(sessionKey, 0);
            sessionKey = Util.substring(sessionKey, Encoder.INT_SIZE);
        } else {
            sessionKeyAlg = HdlSecurityProvider.ENCRYPT_ALG_DES;
        }

        // replace the session key in the session info
        sinfo.setSessionKey(sessionKey);
        sinfo.setEncryptionAlgorithmCode(sessionKeyAlg);
        return successResponse;
    }

    //used for any request.
    private boolean validSession(AbstractRequest req) {
        if (req.sessionId == 0) return false;

        return getSession(req.sessionId) != null;
    }

    // used to check for admin request:
    // if the session has been "challege response" authenticated, return true;
    // otherwise return false
    private boolean authenticatedSession(AbstractRequest req) {
        // if we are allowing wide-open access, shortcut the authentication process
        if (wideOpenAccessAllowed) return true;

        if (req.sessionId == 0) return false;

        ServerSideSessionInfo sssinfo = getSession(req.sessionId);
        return sssinfo != null && !sssinfo.isSessionAnonymous() && sssinfo.clientAuthenticated;
    }

    //set the session given by session id as client authenticated.
    //please take note that all the req.authInfo is null in server side (this method)
    //even for administrative request.
    //this is because the decode message process can't re-produce the authInfo
    //on server side.
    //please see Encoder.java, all decodeMessage methods.
    //we can only compare the auth handle and auth index in challenge answer request
    //and in the session info.
    private void setSessionAuthenticated(AbstractRequest req, ChallengeAnswerRequest caReq, boolean authenticated) {
        ServerSideSessionInfo ssinfo = null;
        if (req != null && req.sessionId > 0) {
            ssinfo = getSession(req.sessionId);
        }
        if (ssinfo != null) {
            if (authenticated) {
                //we have to have the identityHandle and index not null

                if (ssinfo.identityKeyHandle == null || ssinfo.identityKeyIndex < 0) {
                    //can not set the authenticated flag
                    return; //don't set the authenticated flag to an anonymous session
                } else {
                    //if the session has identity handle and index set,
                    //check if they match in the request
                    if (!Util.equals(ssinfo.identityKeyHandle, caReq.userIdHandle) || ssinfo.identityKeyIndex != caReq.userIdIndex) {
                        return; //don't set the authenticated flag to true if the identity doesn't match in the req
                    }
                }
            }

            //set the authenticated flag
            ssinfo.clientAuthenticated = authenticated;
            if (DEBUG_AUTHENTICATION) System.err.println(new Date() + " session " + ssinfo.sessionId + " authenticated " + ssinfo.identityKeyIndex + ":" + Util.decodeString(ssinfo.identityKeyHandle));
        }
        return;
    }

    @Override
    public ServerSideSessionInfo getSession(int sessionId) {
        return sessions.getSession(sessionId);
    }

    /** Returns an object that can be synchronized in order to avoid performing
     * conflicting operations on the given handle.
     * This write lock always wraps any access to storage or replicationDb.
     * */
    public Object getWriteLock(byte hdl[]) {
        // quickly construct an index into the lockHash which is based on
        // the local part of the handle (case insensitive)
        int result = 1;
        boolean inSuffix = false;
        for (byte element : hdl) {
            if (!inSuffix) {
                if (element == '/') inSuffix = true;
                continue;
            }
            result = 31 * result + element;
            if (element >= 'a' && element <= 'z') {
                result += Util.CASE_DIFF;
            }
        }
        result = Math.abs(result);
        return lockHash[result % lockHash.length];
    }

    @Override
    public final void shutdown() {
        keepRunning = false;
        if (monitorDaemon != null) monitorDaemon.shutdown();
        if (replicationDaemon != null) replicationDaemon.shutdown();
        if (sessions != null) sessions.shutdown();
        if (allOtherTransactionQueues != null) allOtherTransactionQueues.shutdown();
        if (txnQueue != null) txnQueue.shutdown();
        if (storage != null) storage.shutdown();
        if (txnQueuePruner != null) txnQueuePruner.stop();
    }

    private boolean storageHaveNA(byte[] authHandle) throws HandleException {
        if (storage.haveNA(authHandle)) return true;
        // may error in legacy storage modules
        try {
            return storage.haveNA(Util.getSuffixPart(authHandle));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean storageHaveDerivedPrefixesOnly(byte[] prefix) throws HandleException {
        prefix = Util.upperCase(prefix);
        int slash = Util.indexOf(prefix, (byte) '/');
        for (int i = prefix.length - 1; i > slash; i--) {
            if (prefix[i] == '.') {
                if (storage.haveNA(Util.concat(Common.NA_HANDLE_PREFIX, Util.substring(prefix, 0, i)))) return true;
            }
        }
        return false;
    }

    // workaround for custom storage modules
    @Override
    public byte[][] storageGetRawHandleValues(byte[] handle, int[] indexList, byte[][] typeList) throws HandleException {
        try {
            return storage.getRawHandleValues(handle, indexList, typeList);
        } catch (HandleException e) {
            if (e.getCode() == HandleException.HANDLE_DOES_NOT_EXIST) return null;
            throw e;
        }
    }
}

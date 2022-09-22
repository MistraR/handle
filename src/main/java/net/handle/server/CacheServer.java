/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import java.util.*;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;

public class CacheServer extends AbstractServer {
    private static final byte MSG_NOT_A_PRIMARY[] = Util.encodeString("Server is read-only");
    private static final byte MSG_CHALLENGE_NOT_FOUND[] = Util.encodeString("Challenge not found");

    public static final String THIS_SERVER_ID = "this_server_id";
    public static final String SERVER_ADMINS = "server_admins";

    public static final String SITE_INFO_FILE = "siteinfo.bin";
    public static final String PRIVATE_KEY_FILE = "privkey.bin";
    public static final String CACHE_STORAGE_FILE = "cache.jdb";

    public static final int RECURSION_LIMIT = 10;

    private static final byte SIGN_TEST[] = Util.encodeString("Testing...1..2..3");

    // private boolean keepRunning = true; // moved to AbstractServer
    private ValueReference serverAdmins[];
    // private Cache cache;

    private SiteInfo thisSite = null;
    private int thisServerNum = -1;
    private Signature serverSignature = null;
    private PublicKey pubKey = null;
    private PrivateKey privKey = null;

    public CacheServer(Main main, StreamTable config, HandleResolver resolver) throws Exception {
        super(main, config, resolver);

        // get the site_info that describes the site that this server is a part of
        try {
            // get the index of this server in the site info
            int thisId = Integer.parseInt((String) config.get(THIS_SERVER_ID));

            // get the site information from the site-info file
            SiteInfo site = new SiteInfo();
            File siteInfoFile = new File(main.getConfigDir(), SITE_INFO_FILE);
            if (!siteInfoFile.exists() || !siteInfoFile.canRead()) {
                throw new Exception("Missing or inaccessible site info file: " + siteInfoFile.getAbsolutePath());
            }
            byte siteInfoBuf[] = new byte[(int) siteInfoFile.length()];
            InputStream in = new FileInputStream(siteInfoFile);
            try {
                int r, n = 0;
                while ((r = in.read(siteInfoBuf, n, siteInfoBuf.length - n)) > 0)
                    n += r;
            } finally {
                in.close();
            }
            Encoder.decodeSiteInfoRecord(siteInfoBuf, 0, site);
            thisServerNum = -1;
            for (int i = 0; i < site.servers.length; i++) {
                if (site.servers[i].serverId == thisId) {
                    thisServerNum = i;
                }
            }
            if (thisServerNum < 0) {
                throw new Exception("Server ID " + thisId + " not found in site_info record!");
            }
            thisSite = site;
        } catch (Exception e) {
            System.err.println("Invalid site/server specification: " + e);
            throw e;
        }

        try {
            // read the private key from the private key file...
            File privateKeyFile = new File(main.getConfigDir(), PRIVATE_KEY_FILE);
            if (!privateKeyFile.exists() || !privateKeyFile.canRead()) {
                System.err.println("Missing or inaccessible private key file: " + privateKeyFile.getAbsolutePath());
                System.err.println("Run hdl-keygen to generate a new set of keys");
                throw new Exception("Missing or inaccessible private key file: " + privateKeyFile.getAbsolutePath());
            }
            byte[] encKeyBytes = new byte[(int) privateKeyFile.length()];
            FileInputStream in = new FileInputStream(privateKeyFile);
            try {
                int n = 0;
                int r;
                while (n < encKeyBytes.length && (r = in.read(encKeyBytes, n, encKeyBytes.length - n)) >= 0) {
                    n += r;
                }
            } finally {
                in.close();
            }

            // get the passphrase to decrypt the server's private key if necessary
            byte secKey[] = null;
            if (Util.requiresSecretKey(encKeyBytes)) {
                secKey = Util.getPassphrase("Enter the passphrase for this server's private key: ");
            }

            // decrypt the key
            byte keyBytes[] = Util.decrypt(encKeyBytes, secKey);
            for (int i = 0; secKey != null && i < secKey.length; i++)
                secKey[i] = (byte) 0;

            // decode the key
            privKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
            serverSignature = Signature.getInstance(Util.getSigIdFromHashAlgId(Common.HASH_ALG_SHA1, privKey.getAlgorithm()));
            serverSignature.initSign(privKey);

            for (int i = 0; i < keyBytes.length; i++)
                keyBytes[i] = (byte) 0;

            // the signature has been initialized... now let's verify that it matches
            // the signature in the site information for this server.
            pubKey = Util.getPublicKeyFromBytes(thisSite.servers[thisServerNum].publicKey, 0);

            // generate a test signature
            serverSignature.update(SIGN_TEST);
            byte testSig[] = serverSignature.sign();

            // verify the test signature
            Signature verifier = Signature.getInstance(serverSignature.getAlgorithm());
            verifier.initVerify(pubKey);

            verifier.update(SIGN_TEST);
            if (!verifier.verify(testSig)) {
                throw new Exception("Private key doesn't match public key from site info!");
            }

        } catch (Exception e) {
            throw e;
        }

        if (config.containsKey(SERVER_ADMINS)) {
            try {
                Vector<?> adminVect = (Vector<?>) config.get(SERVER_ADMINS);
                serverAdmins = new ValueReference[adminVect.size()];
                for (int i = 0; i < adminVect.size(); i++) {
                    String adminStr = String.valueOf(adminVect.elementAt(i));
                    int colIdx = adminStr.indexOf(':');
                    if (colIdx <= 0) throw new Exception("Invalid server administrator ID: \"" + adminStr + "\"");
                    serverAdmins[i] = new ValueReference(Util.encodeString(adminStr.substring(colIdx + 1)), Integer.parseInt(adminStr.substring(0, colIdx)));
                }
            } catch (Exception e) {
                throw new Exception("Error processing server administrator list: " + e);
            }
        } else {
            ////throw new Exception("Server administrator list is required");
        }

        //    cache = new JDBCache(new File(main.getConfigDir(), CACHE_STORAGE_FILE));
        //
        //    resolver.setCache(cache);
    }

    /**
     * Tell the server to re-dump all handles from a primary handle server
     */
    @Override
    public void dumpHandles() throws HandleException {
        throw new HandleException(HandleException.CONFIGURATION_ERROR, "cannot dump handles to a caching server");
    }

    /**********************************************************************
     * Given a request object, handle the request and return a response
     **********************************************************************/
    @Override
    public void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException {

        // handle the request...
        processRequest(req, null, null, callback);
    }

    private void sendResponse(ResponseMessageCallback callback, AbstractResponse response) throws HandleException {
        response.siteInfoSerial = thisSite.serialNumber;

        // if this is a "certified" request, sign the response...
        if (response.certify) {
            //// this is a huge potential bottleneck, because the
            //// signature object is not threadsafe.  A set of
            //// round-robin rotating signatures would be nice.

            try {
                synchronized (serverSignature) {
                    response.signMessage(serverSignature);
                }
            } catch (Exception e) {
                main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception signing response: " + e);
            }
        }
        callback.handleResponse(response);
    }

    private void processRequest(AbstractRequest req, ChallengeResponse cRes, ChallengeAnswerRequest crReq, ResponseMessageCallback callback) throws HandleException {
        switch (req.opCode) {
        case AbstractMessage.OC_GET_SITE_INFO:
            sendResponse(callback, new GetSiteInfoResponse(req, thisSite));
            return;
        case AbstractMessage.OC_RESOLUTION:
            // handle a resolution request
            sendResponse(callback, doResolution((ResolutionRequest) req, cRes, crReq));
            return;
        case AbstractMessage.OC_LIST_HANDLES:
        case AbstractMessage.OC_ADD_VALUE:
        case AbstractMessage.OC_REMOVE_VALUE:
        case AbstractMessage.OC_MODIFY_VALUE:
        case AbstractMessage.OC_CREATE_HANDLE:
        case AbstractMessage.OC_DELETE_HANDLE:
        case AbstractMessage.OC_GET_NEXT_TXN_ID:
        case AbstractMessage.OC_RETRIEVE_TXN_LOG:
        case AbstractMessage.OC_DUMP_HANDLES:
        case AbstractMessage.OC_VERIFY_CHALLENGE:
        case AbstractMessage.OC_HOME_NA:
        case AbstractMessage.OC_UNHOME_NA:
        case AbstractMessage.OC_BACKUP_SERVER:
        case AbstractMessage.OC_SESSION_SETUP:
        case AbstractMessage.OC_SESSION_EXCHANGEKEY:
        case AbstractMessage.OC_SESSION_TERMINATE:
            sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_ERROR, MSG_NOT_A_PRIMARY));
            return;
        case AbstractMessage.OC_RESPONSE_TO_CHALLENGE:
            sendResponse(callback, new ErrorResponse(req, AbstractMessage.RC_AUTHEN_TIMEOUT, MSG_CHALLENGE_NOT_FOUND));
            return;
        default:
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unknown operation: " + req.opCode);

        }
    }

    /************************************************************************
     * process a resolution request, with the specified authentication
     * info (if any).
     ************************************************************************/
    private final AbstractResponse doResolution(ResolutionRequest req, @SuppressWarnings("unused") ChallengeResponse cRes, @SuppressWarnings("unused") ChallengeAnswerRequest crReq) throws HandleException {
        try {
            req.recursionCount++;

            if (req.recursionCount > RECURSION_LIMIT) return new ErrorResponse(req, AbstractMessage.RC_RECURSION_COUNT_TOO_HIGH, null);

            req.clearBuffers(); // very inefficient, but necessary since recursionCount changed

            return resolver.processRequest(req);
        } catch (Exception e) {
            // logged as ERRLOG_LEVEL_EVERYTHING because otherwise all service-not-found
            // exceptions will fill up the log file when web spiders hit the http interface
            main.logError(ServerLog.ERRLOG_LEVEL_EVERYTHING, String.valueOf(getClass()) + ": error getting values: " + e + " for request " + req);
            if (e instanceof HandleException) throw (HandleException) e;
            else return new ErrorResponse(req, AbstractMessage.RC_ERROR, Util.encodeString(String.valueOf(e)));
        }
    }

    @Override
    public void shutdown() {
        keepRunning = false;
    }

    @Override
    public PublicKey getPublicKey() {
        return pubKey;
    }

    @Override
    public PrivateKey getPrivateKey() {
        return privKey;
    }

    @Override
    public X509Certificate[] getCertificateChain() {
        return null;
    }

    @Override
    public X509Certificate getCertificate() {
        return null;
    }

    @Override
    public PrivateKey getCertificatePrivateKey() {
        return null;
    }
}

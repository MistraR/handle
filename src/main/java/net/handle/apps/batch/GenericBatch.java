/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import net.handle.apps.simple.SiteInfoConverter;
import net.handle.hdllib.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.security.*;

public class GenericBatch {

    public static final String ENCODING = "UTF8";
    public static final String AUTH_STR = "AUTHENTICATE";
    public static final String SECKEY_STR = "SECKEY";
    public static final String PUBKEY_STR = "PUBKEY";
    public static final String CREATE_STR = "CREATE";
    public static final String DELETE_STR = "DELETE";
    public static final String ADD_STR = "ADD";
    public static final String MODIFY_STR = "MODIFY";
    public static final String REMOVE_STR = "REMOVE";
    public static final String HOME_STR = "HOME";
    public static final String UNHOME_STR = "UNHOME";
    public static final String SEPA_STR = " ";
    public static final String NEW_LINE = "\n";
    public static final String ADMIN_STR = "ADMIN";
    public static final String FILE_STR = "FILE";
    public static final String LIST_STR = "LIST";
    public static boolean debug = false;

    //session related
    public static final String SESSION_STR = "SESSIONSETUP";

    private final HandleResolver resolver;
    private ClientSessionTracker sessionTracker = null;
    private long lineNum = 0;
    private long totalAcc = 0;
    private long succAcc = 0;
    private AuthenticationInfo authInfo = null;
    private PrintWriter log = null;
    private BufferedReader batchReader = null;
    private CreateHandleRequest createReq = null;
    private DeleteHandleRequest deleteReq = null;
    private AddValueRequest addReq = null;
    private RemoveValueRequest removeReq = null;
    private ModifyValueRequest modifyReq = null;
    private GenericRequest homeNAReq = null;
    private GenericRequest siteReq = null;
    private volatile boolean stopFlag = false;

    public GenericBatch(BufferedReader batchReader, AuthenticationInfo authInfo, PrintWriter log) throws Exception {
        this(batchReader, authInfo, log, new HandleResolver());
    }

    public GenericBatch(BufferedReader batchReader, AuthenticationInfo authInfo, PrintWriter log, HandleResolver resolver) throws Exception {
        this.resolver = resolver;
        if (debug) this.resolver.traceMessages = true;
        this.sessionTracker = resolver.getSessionTracker();
        if (sessionTracker == null) {
            sessionTracker = new ClientSessionTracker();
            SessionSetupInfo sessionInfo = new SessionSetupInfo();
            sessionInfo.encrypted = true;
            sessionTracker.setSessionSetupInfo(sessionInfo);
            //        this.resolver.setSessionTracker(sessionTracker);
        }
        this.batchReader = batchReader;
        this.authInfo = authInfo;
        if (log != null) {
            this.log = log;
        } else {
            this.log = new PrintWriter(new OutputStreamWriter(System.out, ENCODING), true);
            System.err.println("Batch process prints log on stdout ...");
        }

        createReq = new CreateHandleRequest(null, null, null);
        deleteReq = new DeleteHandleRequest(null, null);
        addReq = new AddValueRequest(null, (HandleValue[]) null, null);
        removeReq = new RemoveValueRequest(null, (int[]) null, null);
        modifyReq = new ModifyValueRequest(null, (HandleValue[]) null, null);
        homeNAReq = new GenericRequest((byte[]) null, -1, null);
        siteReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
    }

    /*
     * <batch_record> ::= ['AUTHENTICATE' <sepa> <authentication_info>] | ['CREATE' <sepa> <create_handle>] |
     *               ['DELETE' <sepa> <delete_handle>] | ['ADD' <sepa> <add_values>] |
     *               ['REMOVE' <sepa> <remove_values>] | ['MODIFY' <sepa> <modify_values>] |
     *               ['HOME' <sepa> <home_unhome_handles>] | ['UNHOME' <sepa> <home_unhome_handles>]
     */
    public void processBatch() throws Exception {
        String line;
        long startTime = System.currentTimeMillis();
        try {
            log.println("Start Time: " + (new java.util.Date()));
            log.flush();
            while (!stopFlag) {
                //read line
                line = batchReader.readLine();
                if (line == null) break;
                lineNum++;
                line.trim();
                if (line.length() <= 0) continue;

                String commandStr = "";
                String restLine = "";

                //process batch line
                int sepaInd = line.indexOf(SEPA_STR);
                if (sepaInd < 1) {
                    commandStr = line.trim().toUpperCase();
                } else {
                    commandStr = line.substring(0, sepaInd).trim().toUpperCase();
                    restLine = line.substring(sepaInd + 1, line.length()).trim();
                }

                if (commandStr.equals(AUTH_STR)) {
                    AuthenticationInfo tmpAuth = getAuthInfoFromBatch(restLine);
                    if (tmpAuth != null) {
                        authInfo = tmpAuth;
                    }
                    continue;
                }
                if (commandStr.equals(CREATE_STR)) {
                    totalAcc++;
                    processCreate(restLine);
                    continue;
                }
                if (commandStr.equals(DELETE_STR)) {
                    totalAcc++;
                    processDelete(restLine);
                    continue;
                }
                if (commandStr.equals(ADD_STR)) {
                    totalAcc++;
                    processAdd(restLine);
                    continue;
                }
                if (commandStr.equals(REMOVE_STR)) {
                    totalAcc++;
                    processRemove(restLine);
                    continue;
                }
                if (commandStr.equals(MODIFY_STR)) {
                    totalAcc++;
                    processModify(restLine);
                    continue;
                }
                if (commandStr.equals(HOME_STR)) {
                    processHomeNA(restLine, true);
                    continue;
                }
                if (commandStr.equals(UNHOME_STR)) {
                    processHomeNA(restLine, false);
                    continue;
                }

                //session related
                if (commandStr.equals(SESSION_STR)) {
                    SessionSetupInfo sinfo = getSessionSetupInfo();
                    if (sinfo != null) {
                        sessionTracker.setSessionSetupInfo(sinfo);
                    }
                    continue;
                }

                log.println("==>INVALID[" + lineNum + "]: error in command line");
                continue;
            }
        } finally {
            long endTime = System.currentTimeMillis();
            log.println("Successes/Total Entries: " + succAcc + "/" + totalAcc);
            log.println("Batch File Lines: " + lineNum);
            log.println("Finish Time: " + (new java.util.Date()));
            log.println("This batch took " + ((endTime - startTime) / 1000) + " seconds to complete " + "at an average speed of " + (totalAcc / ((endTime - startTime) / 1000.0)) + " operations/second");
        }
    }

    public void stopBatch() {
        if (debug) System.err.println("Stop batch process ...");
        stopFlag = true;
    }

    /*
     * <authentication_info> ::= <seckey>|<pubkey>
     * <seckey> ::= 'SECKEY' ':' <index> ':' <handle> <new_line> <password>
     * <pubkey> ::= 'PUBKEY' ':' <index> ':' <handle> <new_line> <privkey_file> ['|' <passphrase>]
     */
    private AuthenticationInfo getAuthInfoFromBatch(String line) {
        String keyStr;
        int index;
        String handleStr;
        try {
            StringTokenizer token = new StringTokenizer(line, ":");
            keyStr = token.nextToken().trim().toUpperCase();
            index = Integer.parseInt(token.nextToken().trim());
            handleStr = token.nextToken().trim();

            if (keyStr.equals(SECKEY_STR)) {
                String password = readLine();
                if (password == null) throw new Exception("Secret key without password");
                SecretKeyAuthenticationInfo seckeyAuthInfo = new SecretKeyAuthenticationInfo(Util.encodeString(handleStr), index, Util.encodeString(password));
                return seckeyAuthInfo;
            } else if (keyStr.equals(PUBKEY_STR)) {
                String inLine = readLine();
                if (inLine == null) throw new Exception("Private key without key file");
                int pipeInd = inLine.indexOf("|");
                File keyFile;
                String passphrase = null;
                if (pipeInd <= 0) {
                    keyFile = new File(inLine.trim());
                } else {
                    keyFile = new File(inLine.substring(0, pipeInd).trim());
                    passphrase = inLine.substring(pipeInd + 1).trim();
                }

                byte[] rawKey = new byte[(int) keyFile.length()];
                InputStream in = new FileInputStream(keyFile);
                int n = 0;
                int r = 0;
                while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0) {
                    n += r;
                }
                in.close();
                byte[] keyBytes = passphrase == null ? Util.decrypt(rawKey, new byte[0]) : Util.decrypt(rawKey, Util.encodeString(passphrase));

                PrivateKey privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
                PublicKeyAuthenticationInfo pubkeyAuthInfo = new PublicKeyAuthenticationInfo(Util.encodeString(handleStr), index, privateKey);
                return pubkeyAuthInfo;
            } else {
                log.println("==>INVALID[" + lineNum + "]: error in authentication lines");
                return null;
            }
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: error in authentication: " + e.toString());
            return null;
        }
    }

    /*
     * <create_handle> ::= <handle> <new_line> <handle_values>
     */
    private void processCreate(String handleStr) {
        HandleValue[] values;
        AbstractResponse response;

        if (handleStr == null || handleStr.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in handle name string");
            return;
        }

        values = readHandleValueArray();
        if (values == null) {
            log.println("==>INVALID[" + lineNum + "]: no handle values for " + handleStr);
            return;
        }

        createReq.authInfo = this.authInfo;
        createReq.handle = Util.encodeString(handleStr);
        createReq.values = values;
        createReq.clearBuffers();
        response = null;
        try {
            createReq.sessionTracker = sessionTracker;
            response = resolver.processRequest(createReq);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                succAcc++;
                log.println("==>SUCCESS[" + lineNum + "]: create:" + handleStr);
            } else {
                log.println("==>FAILURE[" + lineNum + "]: create:" + handleStr + ": " + response);
            }
        } catch (Exception e) {
            log.println("==>FAILURE[" + lineNum + "]: create:" + handleStr + ": " + e.getMessage());
        }
    }

    /*
     * <delete_handle> ::= <handle>
     */
    private void processDelete(String handleStr) {
        AbstractResponse response;

        if (handleStr == null || handleStr.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in handle name string");
            return;
        }

        deleteReq.authInfo = this.authInfo;
        deleteReq.handle = Util.encodeString(handleStr);
        deleteReq.clearBuffers();
        response = null;
        try {
            deleteReq.sessionTracker = sessionTracker;
            response = resolver.processRequest(deleteReq);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                succAcc++;
                log.println("==>SUCCESS[" + lineNum + "]: delete:" + handleStr);
            } else {
                log.println("==>FAILURE[" + lineNum + "]: delete:" + handleStr + ": " + response);
            }
        } catch (Exception e) {
            log.println("==>FAILURE[" + lineNum + "]: delete:" + handleStr + ": " + e.getMessage());
        }
    }

    /*
     * <add_values> ::= <handle> <new_line> <handle_values>
     */
    private void processAdd(String handleStr) {
        HandleValue[] values;
        AbstractResponse response;

        if (handleStr == null || handleStr.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in handle name string");
            return;
        }

        values = readHandleValueArray();
        if (values == null) {
            log.println("==>INVALID[" + lineNum + "]: no handle values for " + handleStr);
            return;
        }

        addReq.authInfo = this.authInfo;
        addReq.handle = Util.encodeString(handleStr);
        addReq.values = values;
        addReq.clearBuffers();
        response = null;
        try {
            addReq.sessionTracker = sessionTracker;
            response = resolver.processRequest(addReq);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                succAcc++;
                log.println("==>SUCCESS[" + lineNum + "]: add values:" + handleStr);
            } else {
                log.println("==>FAILURE[" + lineNum + "]: add values:" + handleStr + ": " + response);
            }
        } catch (Exception e) {
            log.println("==>FAILURE[" + lineNum + "]: add values:" + handleStr + ": " + e.getMessage());
        }
    }

    /*
     * <modify_values> ::= <handle> <new_line> <handle_values>
     */
    private void processModify(String handleStr) {
        HandleValue[] values;
        AbstractResponse response;

        if (handleStr == null || handleStr.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in handle name string");
            return;
        }

        values = readHandleValueArray();
        if (values == null) {
            log.println("==>INVALID[" + lineNum + "]: no handle values for " + handleStr);
            return;
        }

        modifyReq.authInfo = this.authInfo;
        modifyReq.handle = Util.encodeString(handleStr);
        modifyReq.values = values;
        modifyReq.clearBuffers();
        response = null;
        try {
            modifyReq.sessionTracker = sessionTracker;
            response = resolver.processRequest(modifyReq);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                succAcc++;
                log.println("==>SUCCESS[" + lineNum + "]: modify values:" + handleStr);
            } else {
                log.println("==>FAILURE[" + lineNum + "]: modify values:" + handleStr + ": " + response);
            }
        } catch (Exception e) {
            log.println("==>FAILURE[" + lineNum + "]: modify values:" + handleStr + ": " + e.getMessage());
        }
    }

    /*
     * <remove_values> ::= <indexes> ':' <handle>
     */
    private void processRemove(String line) {
        AbstractResponse response;
        int[] indexes;

        if (line == null || line.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in remove handle line");
            return;
        }

        int colonInd = line.indexOf(":");
        if (colonInd <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in indexes string");
            return;
        }
        indexes = readIndexArray(line.substring(0, colonInd));
        if (indexes == null) {
            log.println("==>INVALID[" + lineNum + "]: error in indexes string");
            return;
        }

        String handleStr = line.substring(colonInd + 1).trim();
        if (handleStr == null || handleStr.length() == 0) {
            log.println("==>INVALID[" + lineNum + "]: no handle name at remove handle line");
            return;
        }

        removeReq.authInfo = this.authInfo;
        removeReq.handle = Util.encodeString(handleStr);
        removeReq.indexes = indexes;
        removeReq.clearBuffers();
        response = null;
        try {
            removeReq.sessionTracker = sessionTracker;
            response = resolver.processRequest(removeReq);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                succAcc++;
                log.println("==>SUCCESS[" + lineNum + "]: remove values:" + handleStr);
            } else {
                log.println("==>FAILURE[" + lineNum + "]: remove values:" + handleStr + ": " + response);
            }
        } catch (Exception e) {
            log.println("==>FAILURE[" + lineNum + "]: remove values:" + handleStr + ": " + e.getMessage());
        }
    }

    /*
     * do home and unhome NA handle on servers
     * <home_unhome_handle> ::= <ip> ':' <port> ':' <protocol> <new_line>
     *                          <na_handle>* <blank_line>
     */
    private void processHomeNA(String line, boolean homeFlag) {
        String ipstr;
        int port;
        String protocol;
        AbstractResponse response;

        if (line == null || line.length() <= 0) {
            log.println("==>INVALID[" + lineNum + "]: error in homeNA handle line");
            return;
        }

        try {
            //contact with home server

            Pattern ipv6Regex = Pattern.compile("\\[(.+)\\]:(\\d+):(\\w+)");
            Pattern ipv4Regex = Pattern.compile("([0-9\\.]+):(\\d+):(\\w+)");

            Matcher ipv6Matcher = ipv6Regex.matcher(line);
            Matcher ipv4Matcher = ipv4Regex.matcher(line);

            if (ipv6Matcher.matches()) {
                ipstr = ipv6Matcher.group(1);
                port = Integer.parseInt(ipv6Matcher.group(2));
                protocol = ipv6Matcher.group(3);
            } else if (ipv4Matcher.matches()) {
                ipstr = ipv4Matcher.group(1);
                port = Integer.parseInt(ipv4Matcher.group(2));
                protocol = ipv4Matcher.group(3);
            } else {
                log.println("==>INVALID[" + lineNum + "]: invalid address:port:protocol specification.");
                return;
            }

            InetAddress svrAddr = InetAddress.getByName(ipstr);

            siteReq.certify = false; // do not ask server to sign return message, as we can't check it
            response = null;
            if (protocol.toUpperCase().equals("TCP")) response = resolver.sendHdlTcpRequest(siteReq, svrAddr, port);
            else if (protocol.toUpperCase().equals("UDP")) response = resolver.sendHdlUdpRequest(siteReq, svrAddr, port);
            else if (protocol.toUpperCase().equals("HTTP")) response = resolver.sendHttpRequest(siteReq, svrAddr, port);
            else {
                log.println("==>INVALID[" + lineNum + "]: error in protocol string");
                return;
            }

            SiteInfo siteInfo = null;
            if (response != null && response.responseCode == AbstractMessage.RC_SUCCESS) {
                siteInfo = ((GetSiteInfoResponse) response).siteInfo;
            } else {
                log.println("==>INVALID[" + lineNum + "]: error in home/unhome NA handle, invalide server: " + response);
                return;
            }

            if (!siteInfo.isPrimary) {
                log.println("==>INVALID[" + lineNum + "]: error in home/unhome NA handle, invalide server: not primary server");
                return;
            }

            homeNAReq.authInfo = this.authInfo;
            homeNAReq.isAdminRequest = true;
            homeNAReq.certify = true;
            //homeNAReq.majorProtocolVersion = 2;
            //homeNAReq.minorProtocolVersion = 0;

            if (homeFlag) homeNAReq.opCode = AbstractMessage.OC_HOME_NA;
            else homeNAReq.opCode = AbstractMessage.OC_UNHOME_NA;

            boolean flag;
            while (true) {
                String handleStr = readLine();
                if (handleStr == null || handleStr.length() <= 0) break;
                totalAcc++;

                byte[] naHandle = Util.encodeString(handleStr);
                if (!Util.startsWithCI(naHandle, Common.NA_HANDLE_PREFIX)) {
                    log.println("==>INVALID[" + lineNum + "]: invalid NA handle name: " + handleStr);
                    continue;
                }
                homeNAReq.handle = naHandle;
                homeNAReq.clearBuffers();
                homeNAReq.sessionTracker = sessionTracker;
                try {
                    flag = true;
                    for (ServerInfo server : siteInfo.servers) {
                        response = resolver.sendRequestToServer(homeNAReq, siteInfo, server);
                        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                            log.println("==>FAILURE[" + lineNum + "]: home/unhome:" + handleStr + ": " + response);
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        log.println("==>SUCCESS[" + lineNum + "]: home/unhome:" + handleStr);
                        succAcc++;
                    }
                } catch (HandleException e) {
                    log.println("==>FAILURE[" + lineNum + "]: home/unhome:" + handleStr + ": " + e.getMessage());
                } catch (Exception e) {
                    log.println("==>INVALID[" + lineNum + "]: error in home/unhome:" + handleStr + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: error in home/unhome:" + e.getMessage());
        }
    }

    //process Session Setup command
    /*
     * <session setup> ::= 'USESESSION:' <flag> <space> 'IDENTITY:' <index> ':' <handle> <newline>
     *                     <pubExchangeKeyFormat> <newline>
     *                     <privExchangeKeyFormat> <newline>
     *                     [<sessionOptionsFlags>  <space>] ['TIMEOUT:' <time in hours>]
     *
     * <pubExchangeKeyFormat> ::= <pubExchangeKeyFile> | <pubExchangeKeyRef>
     * <pubExchangeKeyFile>   ::= 'PUBEXNGKEYFILE:' <file path>
     * <pubExchangeKeyRef>    ::= 'PUBEXNGKEYREF:' <index> ':' <handle>
     *
     * <privExchangeKeyFormat> ::= 'PRIVEXNGKEYFILE:' <file path> [<space> 'PSPHRASE:'<passphrase>]
     *
     * <sessionOptionsFlags> ::= 'OPTIONS:'<encrypted> <authenticated> <session fails, use challenge response model>
     *
     * Examples:
     * SESSIONSETUP
     * USESESSION:1
     * PUBEXNGKEYFILE:c:\hs\bin\RSAPubKey.bin
     * PRIVEXNGKEYFILE:c:\hs\bin\RSAPrivKey.bin
     * PASSPHRASE:mypassphrase
     * OPTIONS:111
     * TIMEOUT:24
     *
     * or, to use key ref for public exchange key form (use authenitication info falls into this category
     * SESSIONSETUP
     * USESESSION:1
     * PUBEXNGKEYREF:301:TST/tst1
     * PRIVEXNGKEYFILE:c:\hs\bin\RSAPrivKey.bin
     * PASSPHRASE:mypassphrase
     * OPTIONS:111
     * TIMEOUT:24
     */
    private final String sessionFlagToken = "USESESSION:";
    private final String sessionPubExngKeyFileToken = "PUBEXNGKEYFILE:";
    private final String sessionPubExngKeyRefToken = "PUBEXNGKEYREF:";
    private final String sessionPrivExngKeyFileToken = "PRIVEXNGKEYFILE:";
    private final String sessionPrivExngKeyPasspraseToken = "PASSPHRASE:";
    private final String sessionOptionsToken = "OPTIONS:";
    private final String sessionTimeoutToken = "TIMEOUT:";

    private SessionSetupInfo getSessionSetupInfo() {
        boolean useSession = false;
        byte[] pubkeyBytes = null;
        byte[] exchangeKeyHandle = null;
        int exchangeKeyIndex = -1;
        PrivateKey privateKey = null;
        String privKeyFile = null;
        String passpraze = null;
        boolean encrypted = false;
        boolean authenticated = false;
        int timeout = -1; //in seconds. see SessionSetupJPanel.java

        try {
            String sessionLine = readLine();
            while (sessionLine != null) {
                //System.out.println("session line is " + sessionLine);

                String sessionLinePrefix = sessionLine.substring(0, sessionLine.indexOf(":") + 1).trim().toUpperCase();
                String sessionLineContent = sessionLine.substring(sessionLine.indexOf(":") + 1).trim();
                // System.out.println("session prefix is " + sessionLinePrefix);
                // System.out.println("session content is " + sessionLineContent);

                if (sessionLinePrefix.startsWith(sessionFlagToken)) {

                    int flag = Integer.parseInt(sessionLineContent);
                    if (flag > 0) useSession = true;
                    else useSession = false;

                } else if (sessionLinePrefix.startsWith(sessionPubExngKeyFileToken)) {

                    pubkeyBytes = Util.getBytesFromFile(new File(sessionLineContent));

                } else if (sessionLinePrefix.startsWith(sessionPubExngKeyRefToken)) {

                    int indexPos = sessionLineContent.indexOf(':');
                    exchangeKeyIndex = Integer.parseInt(sessionLineContent.substring(0, indexPos).trim());

                    String hdlString = sessionLineContent.substring(indexPos + 1, sessionLineContent.length()).trim();
                    exchangeKeyHandle = Util.encodeString(hdlString);

                } else if (sessionLinePrefix.startsWith(sessionPrivExngKeyFileToken)) {
                    privKeyFile = sessionLineContent;

                } else if (sessionLinePrefix.startsWith(sessionPrivExngKeyPasspraseToken)) {
                    passpraze = sessionLineContent;

                } else if (sessionLinePrefix.startsWith(sessionOptionsToken)) {
                    try {
                        if (sessionLineContent.charAt(0) == '1') encrypted = true;
                        else encrypted = false;
                        if (sessionLineContent.charAt(1) == '1') authenticated = true;
                        else authenticated = false;
                    } catch (Exception e) {
                        log.println("==>INVALID [" + lineNum + "]: Error specifying session options." + e);
                    }
                } else if (sessionLinePrefix.startsWith(sessionTimeoutToken)) {
                    try {
                        timeout = Integer.parseInt(sessionLineContent) * 60 * 60;
                    } catch (Exception e) {
                        log.println("==>INVALID [" + lineNum + "]: Error specifying session time out." + e + " Default or previous value will be used.");
                        timeout = -1;
                    }
                } else {
                    log.println("==>INVALID [" + lineNum + "]: Not predefined session line encounted.");
                } /* if */

                //read next line
                sessionLine = readLine();
            } /* while */

            //if public key double defined
            if (pubkeyBytes != null && exchangeKeyHandle != null && exchangeKeyIndex == -1) {
                log.println("==>INVALID[" + lineNum + "]: error in sessionsetup lines, public exchange key dupli-defined. Session not setup.");
                return null;
            }

            // form the private exchange key
            if (privKeyFile != null) {
                byte[] rawKey = Util.getBytesFromFile(new File(privKeyFile));
                byte secretKey[] = null;
                try {
                    if (Util.requiresSecretKey(rawKey)) {
                        if (passpraze == null) {
                            log.println("==>INVALID[" + lineNum + "]: error in sessionsetup lines, passphrase for private exchange key missed.  Session not setup.");
                            return null;
                        }
                        secretKey = Util.encodeString(passpraze);
                    }
                    byte keyBytes[] = Util.decrypt(rawKey, secretKey);

                    try {
                        privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
                    } catch (Exception e) {
                        log.println("==>INVALID[" + lineNum + "]: error in sessionsetup lines, passphrase for private exchange key wrong! " + e + " Session not setup.");
                        return null;
                    }
                } catch (Throwable e) {
                    log.println("==>INVALID[" + lineNum + "]: error in sessionsetup lines, can't decrypt private exchange key!" + e + " Session not setup.");
                    return null;
                }
            }

            // if the useSession flag is false then this is all for naught
            if (!useSession) return null;

            // make up the ne session setup information
            SessionSetupInfo ssinfo = null;
            if (pubkeyBytes != null && privateKey != null) {
                ssinfo = new SessionSetupInfo(Common.KEY_EXCHANGE_CIPHER_CLIENT, pubkeyBytes, privateKey);
            } else if (exchangeKeyHandle != null && exchangeKeyIndex >= 0 && privateKey != null) {
                ssinfo = new SessionSetupInfo(exchangeKeyHandle, exchangeKeyIndex, privateKey);
            } else {
                //use Diffie-Hellman
                ssinfo = new SessionSetupInfo();
            }
            ssinfo.authenticated = authenticated;
            ssinfo.encrypted = encrypted;
            if (timeout > 0) {
                ssinfo.timeout = timeout;
            }
            return ssinfo;

        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: error in session setup: " + e.getMessage());
            return null;
        }
    }

    private String readLine() throws Exception {
        String line = batchReader.readLine();
        if (line == null) return null;
        line = line.trim();
        if (line.length() <= 0) return null;
        return line;
    }

    /*
     * <indexes> ::= <index> ',' <indexes>
     */
    private int[] readIndexArray(String line) {
        try {
            StringTokenizer st = new StringTokenizer(line, ",");
            int[] indexes = new int[st.countTokens()];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = Integer.parseInt(st.nextToken().trim());
            }
            return indexes;
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * <handle_values> ::= <handle_value>* <blank_line>
     * <handle_value> ::= <index> <sepa> <value_type> <sepa> <value_ttl> <sepa>
     *                    <value_permissions> <sepa> <value_data>
     * <value_type> ::= <string>
     * <value_ttl> ::= <integer>
     * <value_permissions> ::= <admin_read> <admin_write> <public_read> <public_write>
     * <value_data> ::= ['ADMIN' <sepa> <admin_record>] | ['UTF8' <sepa> <string>] |
     *                  ['LIST' <sepa> <reference_list>] | ['FILE' <sepa> <file_path>]
     * <file_path> ::= <string>
     * <admin_record> ::= <index> ':' <admin_permissions> ':' <handle>
     * <admin_permissions> ::= <add_handle> <delete_handle> <add_na_handle> <delete_na_handle>
     *                         <modify_value> <remove_value> <add_value> <read_value>
     *                         <modify_admin> <remove_admin> <add_admin> <list_handles>
     * <reference_list> ::= <reference>*
     * <reference> ::= <index> ':' <handle> ';'
     */
    private HandleValue[] readHandleValueArray() {
        Vector<HandleValue> vt = new Vector<>();
        try {
            while (true) {
                //read line
                String line = batchReader.readLine();
                if (line == null) break;
                line.trim();
                if (line.length() <= 0) break;

                //process line
                HandleValue hv = readHandleValue(line);
                if (hv == null) return null;
                else vt.addElement(hv);
            }

            if (vt.size() < 1) return null;
            HandleValue[] values = new HandleValue[vt.size()];
            for (int i = 0; i < vt.size(); i++) {
                values[i] = vt.elementAt(i);
            }
            return values;
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: " + e.getMessage());
            return null;
        }
    }

    private HandleValue readHandleValue(String line) {
        try {
            StringTokenizer st = new StringTokenizer(line, SEPA_STR);
            HandleValue hv = new HandleValue();

            //read index
            try {
                hv.setIndex(Integer.parseInt(st.nextToken().trim()));
            } catch (Exception e) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value index string");
                return null;
            }
            //read type
            hv.setType(Util.encodeString(st.nextToken().trim()));

            //read ttl
            try {
                hv.setTTL(Integer.parseInt(st.nextToken().trim()));
            } catch (Exception e) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value ttl string");
                return null;
            }

            // read permission
            String substr = st.nextToken().trim();
            if (substr.length() < 4) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value permission string");
                return null;
            } else {
                hv.setAdminCanRead(substr.charAt(0) == '1');
                hv.setAdminCanWrite(substr.charAt(1) == '1');
                hv.setAnyoneCanRead(substr.charAt(2) == '1');
                hv.setAnyoneCanWrite(substr.charAt(3) == '1');
            }

            // read handle value data
            substr = st.nextToken().trim().toUpperCase();

            if (substr.equals(ENCODING)) { // UTF8

                hv.setData(Util.encodeString(st.nextToken(NEW_LINE).trim()));

            } else if (substr.equals(ADMIN_STR)) { //ADMIN
                AdminRecord record = new AdminRecord();
                //read admin_index
                try {
                    record.adminIdIndex = Integer.parseInt(st.nextToken(":").trim());
                } catch (Exception e) {
                    log.println("==>INVALID[" + lineNum + "]: error in admin index string");
                    return null;
                }
                //read admin_permissions
                substr = st.nextToken(":").trim();
                if (substr.length() != 12) {
                    log.println("==>INVALID[" + lineNum + "]: error in admin permission string");
                    return null;
                }
                for (int i = 0; i < substr.length(); i++) {
                    if (substr.charAt(i) == '1') record.perms[i] = true;
                    else record.perms[i] = false;
                }
                //read admin_handle
                substr = st.nextToken(NEW_LINE);
                record.adminId = Util.encodeString(substr.substring(1).trim());
                hv.setData(Encoder.encodeAdminRecord(record));

            } else if (substr.equals(FILE_STR)) { //FILE
                String filename = st.nextToken(NEW_LINE).trim();
                File file = new File(filename);
                if (!file.exists() || !file.canRead()) {
                    log.println("==>INVALID[" + lineNum + "]: error public key file: " + filename);
                    return null;
                }

                InputStream in = null;
                byte[] rawKey = new byte[(int) file.length()];
                in = new FileInputStream(file);
                int n = 0;
                int r = 0;
                while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0) {
                    n += r;
                }
                in.close();
                // for HS_SITE accept siteinfo.json
                if (hv.hasType(Common.SITE_INFO_TYPE) && !Util.looksLikeBinary(rawKey)) {
                    SiteInfo site = SiteInfoConverter.convertToSiteInfo(new String(rawKey, "UTF-8"));
                    rawKey = Encoder.encodeSiteInfoRecord(site);
                }
                hv.setData(rawKey);
            } else if (substr.equals(LIST_STR)) { //LIST
                int index;
                String handleStr;
                Vector<ValueReference> vt = new Vector<>();

                substr = st.nextToken(NEW_LINE).trim();
                StringTokenizer stt = new StringTokenizer(substr, ";");
                while (stt.hasMoreTokens()) {
                    try {
                        index = Integer.parseInt(stt.nextToken(";:").trim());
                    } catch (Exception e) {
                        log.println("==>INVALID[" + lineNum + "]: error in admin index string");
                        return null;
                    }

                    handleStr = stt.nextToken(";");
                    handleStr = handleStr.substring(1).trim();
                    if (handleStr == null || handleStr.length() <= 0) {
                        log.println("==>INVALID[" + lineNum + "]: error admin handle string");
                        return null;
                    }

                    vt.addElement(new ValueReference(Util.encodeString(handleStr), index));
                }

                if (vt.size() < 1) return null;
                ValueReference[] refs = new ValueReference[vt.size()];
                for (int j = 0; j < refs.length; j++) {
                    refs[j] = vt.elementAt(j);
                }
                hv.setData(Encoder.encodeValueReferenceList(refs));
            } else { //OTHER
                log.println("==>INVALID[" + lineNum + "]: error in handle data type string: '" + substr + "'");
                return null;
            }

            return hv;
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: " + e.getMessage());
            return null;
        }
    }

    public static void printUsage() {
        System.err.println("Usage: java net.handle.apps.batch.GenericBatch" + " <batchfile> [<LogFile>] [-verbose]");
    }

    public static void main(String args[]) {
        BufferedReader reader = null;
        if (args.length < 1 || ("-verbose".equals(args[0]) && args.length == 1)) {
            printUsage();
        } else {
            PrintWriter log = null;
            String batchfile = null;
            String logfile = null;
            if ("-verbose".equals(args[0])) {
                debug = true;
                batchfile = args[1];
                if (args.length > 2) logfile = args[2];
            } else {
                batchfile = args[0];
                if (args.length > 1) {
                    if ("-verbose".equals(args[1])) {
                        debug = true;
                        if (args.length > 2) logfile = args[2];
                    } else {
                        logfile = args[1];
                        if (args.length > 2 && "-verbose".equals(args[2])) debug = true;
                    }
                }
            }
            try {
                System.err.println("Batch(" + batchfile + ") process started ...");
                FileInputStream f = new FileInputStream(batchfile);
                reader = new BufferedReader(new InputStreamReader(f, "UTF-8"));
                if (logfile != null) log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logfile), "UTF-8"), true);
                GenericBatch batch = new GenericBatch(reader, null, log);
                batch.processBatch();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            } finally {
                if (reader != null) try {
                    reader.close();
                } catch (Exception e1) {
                }
                if (log != null) try {
                    log.close();
                } catch (Exception e) {
                }
                System.err.println("Batch process finished");
            }
        }
    }
}

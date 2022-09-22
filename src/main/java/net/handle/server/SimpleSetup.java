/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamTable;
import net.handle.apps.simple.SiteInfoConverter;
import net.handle.hdllib.*;

import java.security.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/*******************************************************************************
 *
 * Object to get the handle server configuration and write it to a file,
 * generating keys as needed.
 *
 ******************************************************************************/

public abstract class SimpleSetup {
    private static final String DEFAULT_YES = "y"; // Arguments to getBoolean()
    private static final String DEFAULT_NO = "n";

    private static String DEFAULT_INTERVAL = HSG.MONTHLY; // For log saves
    private static final int NO_DEFAULT = -1; // Arguments to getInteger()
    private static final int NO_LIMIT = -1;
    // I/O
    private static BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private static PrintStream out = System.out;
    private static PrintStream err = System.err;

    /******************************************************************************
     * Main setup (i.e., configuration) routine.
     */

    public static void main(String argv[]) {
        try { // Catch any exception in the whole routine
            // INVOCATION & ENVIRONMENT
            // Insist on 1 argument: config-directory name
            if (argv.length < 1 || argv[0].length() <= 0) {
                err.println("ERROR:  You must specify a configuration directory.");
                return;
            }
            // Create the specified directory if it doesn't exist

            String configDirName = argv[0];

            File configDir = new File(configDirName);
            if (!configDir.exists()) configDir.mkdirs();

            // Greet the user
            out.println("\nTo configure your new Handle server, please answer" + "\nthe questions which follow; default answers, shown in " + "\n[square brackets] when available, can be chosen by " + "\npressing Enter.\n");

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // SERVER TYPE (regular/caching)

            //      prompt = "\nWill this be a regular or caching Handle server?\n"
            //             + "\n  1 - Regular Handle Server (recommended)"
            //             + "\n  2 - Caching Handle Server\n"
            //             + "\nPlease choose 1 or 2 and press Enter";
            //
            //      int serverType = ( getInteger(prompt, 1, 1, 2) == 1 ) ?
            //                        HSG.SVR_TYPE_SERVER : HSG.SVR_TYPE_CACHE;

            int serverType = HSG.SVR_TYPE_SERVER;

            // Ask whether or not this will be a primary server
            boolean isPrimary = getBoolean("Will this be a \"primary\" server (ie, not a mirror of another server)?", DEFAULT_YES);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // IP ADDRESS
            boolean isDualStack = getBoolean("Will this be a dual-stack server (accessible on both IPv6 and IPv4)?", DEFAULT_NO);

            InetAddress[] externalAddr = new InetAddress[2];
            InetAddress[] bindAddr = new InetAddress[2];

            if (!isDualStack) {
                externalAddr[0] = getIPAddress("Through what network-accessible IP address should clients connect to this server?", null, true, HSG.IP_EITHER_VERSION);
                bindAddr[0] = getIPAddress("If different, enter the IP address to which the server should bind.", externalAddr[0], false, HSG.IP_EITHER_VERSION);
            } else {
                externalAddr[0] = getIPAddress("Through what network-accessible IPv6 address should clients connect to this server?", null, true, HSG.IP_VERSION_6);
                bindAddr[0] = getIPAddress("If different, enter the IPv6 address to which the server should bind.", externalAddr[0], false, HSG.IP_VERSION_6);

                externalAddr[1] = getIPAddress("Through what network-accessible IPv4 address should clients connect to this server?", null, true, HSG.IP_VERSION_4);
                bindAddr[1] = getIPAddress("If different, enter the IPv4 address to which the server should bind.", externalAddr[1], false, HSG.IP_VERSION_4);
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

            // PORTS
            // TCP/UPD
            int port = getInteger("Enter the (TCP/UDP) port number this server will listen to", HSG.DEFAULT_TCP_UDP_PORT, HSG.LOWEST_PORT, HSG.HIGHEST_PORT);
            // HTTP
            int httpPort = getInteger("What port number will the HTTP interface be listening to?", HSG.DEFAULT_HTTP_PORT, HSG.LOWEST_PORT, HSG.HIGHEST_PORT);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // CONFIGURE LOGGING

            boolean logAccesses = getBoolean("Would you like to log all accesses to this server?", DEFAULT_YES);

            String interval = DEFAULT_INTERVAL;
            //String weekday       = HSG.NOT_APPL;             // Default: not applicable
            //String logSavingTime = null;
            //String savedLogDir   = null;
            //File logStoringDir   = null;

            if (logAccesses) {
                System.out.println("\nPlease indicate whether log files should be automatically" + "\nrotated, and if so, how often.");
                interval = getInterval();
            }
            if (!(interval.equals(HSG.NEVER))) {
                if (interval.equals(HSG.MONTHLY)) {
                    System.out.println("\nNOTE: Log rotation will be done on the first of each month.");
                }
                //else if (interval.equals(HSG.WEEKLY)) {
                //  weekday = getWeekday();
                //}

                //logSavingTime =
                //       getHHMMSS("Select a time of day for the saving and restarting (HH:MM:SS)\n",
                //                "00:00:00");

                //savedLogDir = getAbsolutePath("Enter the full pathname of the directory "+
                //        "where saved logs should be stored",
                //        configDirName );

                // Create the specified directory if it doesn't exist
                //logStoringDir = new File(savedLogDir);
                //if (!logStoringDir.exists())
                //  logStoringDir.mkdirs();
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // KEY-FILE and REPLICATION-SITE DATA

            File replPrivKeyFile = new File(configDir, "replpriv.bin");
            File replPubKeyFile = new File(configDir, HSG.REPLICATION_PUB_KEY_FILE_NAME);
            File adminPrivKeyFile = new File(configDir, "admpriv.bin");
            File adminPubKeyFile = new File(configDir, HSG.ADMIN_PUB_KEY_FILE_NAME);
            File replicationSiteFile = new File(configDir, "txnsrcsv.bin");

            boolean generateReplKeys = true;
            boolean generateAdminKeys = isPrimary;

            String replicationAdminStr = "";
            String replicationAuthStr = null;

            SiteInfo replicationSite = null;
            String homedPrefix = null;

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

            // REGULAR-SERVER DATA
            if (serverType == HSG.SVR_TYPE_SERVER) {
                if (isPrimary) {
                    replicationAdminStr = HSG.DEFAULT_REPLICATION_GROUP;
                    homedPrefix = HSG.DEFAULT_HOMED_PREFIX;
                } else {
                    // Get option to generate a key pair for replication authentication
                    if (replPrivKeyFile.exists() && replPubKeyFile.exists()) generateReplKeys = getBoolean("Replication keys already exist, do you want to create new ones? ", DEFAULT_NO);

                    // Specify IP address and port, or site info, for replication source
                    // then retrieve the site info from the source server.
                    out.println("\nSince this is a secondary (\"mirror\") server, you need to" + "\nspecify the primary site from which this server will get" + "\nits handles.  You will be asked to specify the IP of a server"
                        + "\nfrom the primary site, and the port it listens to.  This" + "\nprogram will then contact that server and request the site" + "\ndata needed for downloading handles.");

                    boolean needReplicationInfo = true;

                    if (replicationSiteFile.exists()) {
                        out.println("\n\n\nWARNING: You have already configured a primary site from" + "\nwhich this server is to get its handles. CHANGING THIS SETTING" + "\nWILL REQUIRE THAT THIS SERVER RE-DOWNLOAD ALL OF THE HANDLES"
                            + "\nFROM THE PRIMARY SITE.\n\n");

                        needReplicationInfo = getBoolean("  Would you like to specify a different primary site?", DEFAULT_NO);
                    }

                    while (needReplicationInfo) {
                        // Infinite loop until "break" on success
                        InetAddress replSrcAddr;
                        String line = responseToPrompt("Enter the address of a primary server (enter 'manual' to configure manually)");

                        try {
                            if (line.length() <= 0) throw new Exception("Got empty input");
                            if ("manual".equalsIgnoreCase(line.trim())) {
                                out.println("\n\n\nWARNING: You have will need to manually create a txnsrcsv.bin" + "\nfile or add a \"replication_sites_handle\" value to the server's" + "\nconfiguration file.\n\n");
                                break;
                            }
                            replSrcAddr = InetAddress.getByName(line);
                        } catch (Exception e) {
                            out.println("Invalid address: \"" + line + "\"; Reason: " + e + ".  Try again.");
                            continue;
                        }

                        int replSrcPort = getInteger("Enter the port number of the same primary server (" + line + ")", HSG.DEFAULT_TCP_UDP_PORT, HSG.LOWEST_PORT, HSG.HIGHEST_PORT);
                        try {
                            GenericRequest req = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);

                            HandleResolver resolver = new HandleResolver();
                            AbstractResponse resp = resolver.sendHdlTcpRequest(req, replSrcAddr, replSrcPort, null);
                            if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                                replicationSite = ((GetSiteInfoResponse) resp).siteInfo;
                                break;
                            }
                            throw new Exception("Unexpected response from primary: " + resp);
                        } catch (Exception e) {
                            out.println("Error retrieving replication site info: " + e);
                            continue;
                        }
                    }
                    replicationAuthStr = HSG.DEFAULT_REPLICATION_ID;
                }
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // SITE-SPECIFIC DATA

            out.println("\nEach handle site has a version/serial number assigned" + "\nto it.  This is so that a client can tell if a particular" + "\nsite's configuration has changed since the last time it"
                + "\naccessed a server in the site.  Every time you modify a site" + "\n(by changing an IP address, port, or adding a server, etc), " + "\nyou should increment the version/serial number for that site.");

            int siteVersion;
            while (true) {
                // Can't use simple integer comparison (in getInteger())
                // to enforce maximum here because of possible integer
                // overflow; use the hex mask test below instead.

                siteVersion = getInteger("Enter the version/serial number of this site", 1, 1, NO_LIMIT); // ???

                // Limit to two bytes
                if ((siteVersion & 0xffff0000) == 0) break; // Got an acceptable one, leave loop

                out.println("Invalid input: \"" + siteVersion + "\" (value out of 2-byte range).");
            }

            String siteDescription = responseToPrompt("Please enter a short description of this server/site");

            String orgName = "";
            while (orgName.length() == 0)
                orgName = responseToPrompt("Please enter the name of your organization");

            String contactName, contactPhone, contactEmail;
            if (orgName.equalsIgnoreCase("cnri")) {
                contactName = contactPhone = contactEmail = "";
            } else {
                contactName = responseToPrompt("Please enter the name of a contact person\n" + "for " + orgName + " (optional) [(none)]");
                contactPhone = getContactPhone(contactName, orgName);
                contactEmail = getContactEmail(contactName, orgName);
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // DISABLE OR ENABLE UDP?

            out.println("\nThe handle server can communicate via UDP and/or TCP sockets." + "\nSince UDP messages are blocked by many network firewalls, you may" + "\nwant to disable UDP services if you are behind such a firewall.");

            boolean disableUDP = getBoolean("  Do you need to disable UDP services?", DEFAULT_NO);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // GENERATE KEY FILES
            // (if necessary)
            boolean generateKeys = true;

            File privKeyFile = new File(configDir, "privkey.bin");
            File pubKeyFile = new File(configDir, "pubkey.bin");

            if (privKeyFile.exists() && pubKeyFile.exists()) generateKeys = getBoolean("Server keys already exist, do you want to create new ones? ", DEFAULT_NO);
            // Do the deal...
            if (generateKeys) generateKeys(pubKeyFile, privKeyFile, "Server Certification");

            if (serverType == HSG.SVR_TYPE_SERVER) {
                if (!isPrimary && generateReplKeys) generateKeys(replPubKeyFile, replPrivKeyFile, "Replication Authentication");

                if ((generateAdminKeys) && (adminPubKeyFile.exists()) && (adminPrivKeyFile.exists())) generateAdminKeys = getBoolean("Administrator keys already exist, do you want to create new ones? ", DEFAULT_NO);

                if (generateAdminKeys) generateKeys(adminPubKeyFile, adminPrivKeyFile, "Administration");
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // SITE DATA
            SiteInfo siteInfo;
            if (!isDualStack) {
                out.println("Generating site info record...");
                SiteInfo siteInfoBuf = new SiteInfo(siteVersion, // int
                    isPrimary, // boolean
                    false, // boolean
                    SiteInfo.HASH_TYPE_BY_ALL, // byte
                    siteDescription, // String
                    externalAddr[0], // InetAddress
                    port, // int
                    httpPort, // int
                    pubKeyFile, // File
                    disableUDP // boolean
                );
                // Create siteinfo file
                boolean createBinFile = false;
                try {
                    String json = SiteInfoConverter.convertToJson(siteInfoBuf);
                    FileOutputStream siteOut = new FileOutputStream(new File(configDir, HSG.SITE_INFO_JSON_FILE_NAME));
                    siteOut.write(json.getBytes("UTF-8"));
                    siteOut.close();
                } catch (Throwable t) {
                    createBinFile = true;
                }
                File binFile = new File(configDir, HSG.SITE_INFO_FILE_NAME);
                if (createBinFile || binFile.exists()) {
                    FileOutputStream siteOut = new FileOutputStream(binFile);
                    siteOut.write(Encoder.encodeSiteInfoRecord(siteInfoBuf));
                    siteOut.close();
                }
                siteInfo = siteInfoBuf;
            } else {
                out.println("Generating site info record...");
                SiteInfo siteInfo4 = new SiteInfo(siteVersion, // int
                    isPrimary, // boolean
                    false, // boolean
                    SiteInfo.HASH_TYPE_BY_ALL, // byte
                    siteDescription, // String
                    externalAddr[1], // InetAddress
                    externalAddr[0], port, // int
                    httpPort, // int
                    pubKeyFile, // File
                    disableUDP // boolean
                );

                // Create siteinfo file
                boolean createBinFile = false;
                try {
                    String json = SiteInfoConverter.convertToJson(siteInfo4);
                    FileOutputStream siteOut = new FileOutputStream(new File(configDir, HSG.SITE_INFO_JSON_FILE_NAME));
                    siteOut.write(json.getBytes("UTF-8"));
                    siteOut.close();
                } catch (Throwable t) {
                    createBinFile = true;
                }
                File binFile = new File(configDir, HSG.SITE_INFO_FILE_NAME);
                if (createBinFile || binFile.exists()) {
                    FileOutputStream siteOut = new FileOutputStream(binFile);
                    siteOut.write(Encoder.encodeSiteInfoRecord(siteInfo4));
                    siteOut.close();
                }
                siteInfo = siteInfo4;
            }
            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // CONTACT-DATA FILE

            StreamTable contactData = ConfigCommon.contactDataTable(orgName, contactName, contactPhone, contactEmail);

            File contactDataFile = new File(configDir, HSG.SITE_CONTACT_DATA_FILE_NAME);
            contactData.writeToFile(contactDataFile);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // REPLICATION DATA FILES
            // (SVR_TYPE_SERVER Mirror sites only)
            if (replicationSite != null) ConfigCommon.writeReplicationSiteFile(configDirName, HSG.TXN_STAT_FILE_NAME, replicationSiteFile, replicationSite);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // CONFIGURATION FILE
            /*
             *
             * try { config.readFromFile(new File(configDir, HSG.CONFIG_FILE_NAME)); }
             * catch (Exception e) {}
             *
             */
            boolean serverAdminFullAccess = true;
            StreamTable config = ConfigCommon.configuration(serverType, disableUDP, port, logAccesses, // boolean
                bindAddr, // InetAddress
                HSG.THREAD_COUNT, httpPort,

                interval, // "Never", "Monthly", "Weekly", "Daily"
                //weekday,             // "Sunday".."Saturday",
                //logSavingTime,       // HH:MM:SS
                //savedLogDir,

                HSG.CASE_INSENSITIVE, HSG.MAX_AUTH_TIME, HSG.MAX_SESSION_TIME, 1, // serverId
                isPrimary, HSG.DEFAULT_SERVER_ADMIN, replicationAdminStr, replicationAuthStr, homedPrefix, isDualStack, serverAdminFullAccess);

            File configFile = new File(configDir, HSG.CONFIG_FILE_NAME);
            if (configFile.exists()) {
                out.println("\nYour pre-existing config.dct has been moved to config.dct.before_setup.");
                out.println("Please ensure that any manual changes you had made are preserved.");
                Files.move(configFile.toPath(), configFile.toPath().resolveSibling("config.dct.before_setup"), StandardCopyOption.REPLACE_EXISTING);
            }
            config.writeToFile(configFile);

            // Set up admin.war
            File webappsDir = new File(configDir, "webapps");
            boolean copyAdminWar = true;
            if (webappsDir.exists()) {
                out.println("\nYour server already has a webapps directory for Java servlets." + "\nThis Handle software distribution comes with an admin.war servlet" + "\nwhich provides a browser-based admin tool.  This admin.war can"
                    + "\nbe copied into your server which will replace any existing" + "\nadmin.war.  This is recommended unless you believe your existing" + "\nadmin.war to be newer.");

                copyAdminWar = getBoolean("  Would you like to copy this admin.war into your server?", DEFAULT_YES);
            }
            if (copyAdminWar) {
                try {
                    webappsDir.mkdirs();
                    URL url = SimpleSetup.class.getProtectionDomain().getCodeSource().getLocation();
                    File jarFile = new File(url.toURI());
                    File adminWarFile = new File(jarFile.getParentFile().getParentFile(), "admin.war");
                    File adminWarDestFile = new File(webappsDir, "admin.war");
                    copyFile(adminWarFile, adminWarDestFile);
                } catch (Exception e) {
                    out.println("\n\n\nWARNING: Error adding admin.war to server:" + "\n" + e + "\nYou will have to add admin.war to the webapps subdirectory manually.\n\n");
                }
            }

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
            // FINISH UP:
            // create site bundle (for regular server), print message
            // according to server type
            String finalMessage = null;

            if (serverType == HSG.SVR_TYPE_SERVER) {
                ConfigCommon.createSiteBundle(configDirName, // String
                    HSG.SITE_BUNDLE_ZIPFILE_NAME, // String
                    isPrimary, // boolean
                    replicationAdminStr, // String
                    adminPubKeyFile, // File
                    replPubKeyFile, // File
                    replicationAuthStr, // String
                    siteInfo, // SiteInfo
                    contactDataFile, // File
                    isDualStack);

                finalMessage = "\nYou have finished configuring your (" + ((isPrimary) ? "primary" : "mirror") + ") Handle service.\n" + "\nThis service now needs to be registered with your prefix "
                    + "\nadministrator.  Organizations credentialed by DONA to " + "\nregister prefixes are listed at the dona.net website. " + "\n\nIf your prefix administrator is CNRI, go to "
                    + "\nhttp://hdl.handle.net/20.1000/111 to register to " + "\nbecome a resolution service provider and then upload " + "\nyour newly created sitebndl.zip file. Please read the "
                    + "\ninstructions on this page carefully. When the handle " + "\nadministrator receives your file, a prefix will be " + "\ncreated and you will receive notification via email."
                    + "\n\nPlease send all questions to your prefix administrator, " + "\nif CNRI at hdladmin@cnri.reston.va.us.\n\n";

            } else { // SVR_TYPE_CACHE
                finalMessage = "You have finished configuring your caching Handle server.\n" + "\nYou can now start your server then test it by pointing" + "\na web browser at http://" + Util.rfcIpRepr(externalAddr[0]) + ":" + httpPort
                    + "/" + "\nand entering a handle.\n";
            }

            out.println("\n-------------------------------------------------------\n" + finalMessage);

            //- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
        } catch (Exception e) {
            err.println("Error setting up the server:\n" + e);
            e.printStackTrace(err);
        }
    }

    /**
     * Generate a key pair.
     */
    private static final void generateKeys(File pubKeyFile, File privKeyFile, String purpose) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(HSG.KEY_ALGORITHM);

        out.println("\nGenerating keys for: " + purpose);
        kpg.initialize(HSG.KEY_STRENGTH);
        KeyPair keys = kpg.generateKeyPair();

        out.println("\nThe private key that is about to be generated should be stored" + "\nin an encrypted form on your computer. Encryption of the" + "\nprivate key requires that you choose a secret passphrase that"
            + "\nwill need to be entered whenever the server is started." + "\nNote: Your private key may be stored unencrypted if you so choose." + "\nPlease take all precautions to make sure that only authorized"
            + "\nusers can read your private key.");

        boolean encrypt = getBoolean("  Would you like to encrypt your private key?", DEFAULT_YES);

        byte secKey[] = null;

        if (encrypt) {
            while (true) {
                // Read the passphrase and use it to encrypt the private key
                secKey = Util.getPassphrase("\nPlease enter the private key passphrase for " + purpose + ": ");
                byte secKey2[] = Util.getPassphrase("\nPlease re-enter the private key passphrase: ");
                if (!Util.equals(secKey, secKey2)) {
                    err.println("\nPassphrases do not match!  Try again.\n");
                    continue;
                } else {
                    break;
                }
            }
        }

        // Get the bytes making up the private key
        PrivateKey priv = keys.getPrivate();
        byte keyBytes[] = Util.getBytesFromPrivateKey(priv);

        byte encKeyBytes[] = null;
        if (encrypt) { // Encrypt the private key bytes
            encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
            for (int i = 0; i < keyBytes.length; i++)
                keyBytes[i] = (byte) 0;
            if (secKey == null) throw new AssertionError();
            for (int i = 0; i < secKey.length; i++)
                secKey[i] = (byte) 0;
        } else {
            encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_NONE);
        }

        // Save the private key to a file

        FileOutputStream keyOut = new FileOutputStream(privKeyFile);
        keyOut.write(encKeyBytes);
        keyOut.close();

        // Save the public key to a file
        PublicKey pub = keys.getPublic();
        keyOut = new FileOutputStream(pubKeyFile);
        keyOut.write(Util.getBytesFromPublicKey(pub));
        keyOut.close();
    }

    /******************************************************************************
     * Get IP address for server
     */
    private static InetAddress getIPAddress(String prompt, InetAddress defaultAddr, boolean mustBeAccessible, int protocol) throws Exception {
        String localAddress = "";
        try {
            if (defaultAddr != null) {
                localAddress = Util.rfcIpRepr(defaultAddr);
            } else {
                localAddress = getLocalHostUnderProtocol(protocol);
            }
        } catch (Exception e) {
            localAddress = "";
        }

        if (localAddress.length() > 0) prompt = prompt + " [" + localAddress + "]";

        while (true) // I.e., until "break" on success
        {
            String line = responseToPrompt(prompt);
            if ((line.equals("")) && (localAddress.length() > 0)) line = localAddress;

            try {
                InetAddress listenAddr = InetAddress.getByName(line);
                if (mustBeAccessible && listenAddr.getHostAddress().startsWith("127.")) throw new Exception("you must enter an address that is accessible from the network");
                if (protocol == HSG.IP_VERSION_6 && !(listenAddr instanceof Inet6Address)) throw new Exception("address is not a valid IPv6 address");
                else if (protocol == HSG.IP_VERSION_4 && !(listenAddr instanceof Inet4Address)) throw new Exception("address is not a valid IPv4 address");

                return listenAddr; // Success
            } catch (Exception e) {
                out.println("Invalid address (" + e + "), please try again.");
            }
        }
    }

    private static String getLocalHostUnderProtocol(int protocol) throws UnknownHostException {
        if (protocol == HSG.IP_EITHER_VERSION) return Util.rfcIpRepr(InetAddress.getLocalHost());
        String localAddress;
        String oldProp = System.getProperty("java.net.preferIPv6Addresses", "false");
        if (protocol == HSG.IP_VERSION_6) System.setProperty("java.net.preferIPv6Addresses", "true");
        else System.setProperty("java.net.preferIPv6Addresses", "false");
        try {
            // Oracle JDK only checks the property once.  So here we force it to change its stored value.
            java.lang.reflect.Field preferIPv6Address = InetAddress.class.getDeclaredField("preferIPv6Address");
            preferIPv6Address.setAccessible(true);
            preferIPv6Address.setBoolean(null, protocol == HSG.IP_VERSION_6);
        } catch (Exception e) {
        }
        localAddress = Util.rfcIpRepr(InetAddress.getLocalHost());
        System.setProperty("java.net.preferIPv6Addresses", oldProp);
        try {
            java.lang.reflect.Field preferIPv6Address = InetAddress.class.getDeclaredField("preferIPv6Address");
            preferIPv6Address.setAccessible(true);
            preferIPv6Address.setBoolean(null, Boolean.valueOf(oldProp));
        } catch (Exception e) {
        }
        return localAddress;
    }

    /******************************************************************************
     * Obtain and return a validated telephone number
     */

    private static String getContactPhone(String person, String org) throws Exception {
        String phoneNumber = null, prompt = "Please enter the telephone number of ";

        if (!((person == null) || (person.equals("")))) prompt = prompt + person + " or of ";

        prompt = prompt + org + " (optional) [(none)]";

        // validatePhoneNumber() returns null on error
        while (phoneNumber == null)
            phoneNumber = validatePhoneNumber(responseToPrompt(prompt));

        return phoneNumber;
    }

    /******************************************************************************
     * Check the validity of a phone number; if it's valid, put out a message
     * and return a normalized version of it; otherwise, put out a message and
     * return null.
     *
     */
    private static String validatePhoneNumber(String phoneNumber) {
        String problem = null, newPhoneNumber = "";

        int openParenPosition = -1, closeParenCount = 0;

        phoneNumber = phoneNumber.trim();

        if (phoneNumber.equals("")) // Empty is OK for optional field
            return "";

        for (int i = 0; i < phoneNumber.length(); i++) {
            char c = phoneNumber.charAt(i);

            if (!(ConfigCommon.validPhoneNumberChar(c))) problem = "contains illegal character '" + c + "'";
            else if (c == '-') problem = ((i == 0) ? "begins with a dash" : (phoneNumber.charAt(i - 1) == '-') ? "contains consecutive hyphens" : null);
            else if (c == '(') {
                if (openParenPosition > -1) // Already had one
                    problem = "contains more than one left parenthesis";
                else openParenPosition = i;
            } else if (c == ')') problem = ((i == 0) ? "begins with a right parenthesis"
                : (openParenPosition == -1) ? "contains unmatched right parenthesis" : (openParenPosition == (i - 1)) ? "contains empty parentheses" : (++closeParenCount > 1) ? "contains more than one right parenthesis" : null);
            if (problem != null) break;
        }

        if ((problem == null) // Didn't already detect a problem
            && (openParenPosition > -1) // Had an open paren...
            && (closeParenCount == 0) // ...but no close paren
        ) problem = "containes unmatched left parenthesis";

        if (problem != null) {
            out.println("\nTelephone number " + problem + ", please try again.\n");
            return null;
        }

        for (int i = 0; i < phoneNumber.length(); i++) {
            char c = phoneNumber.charAt(i);

            if ((c == '(') || ((c == ' ') && ((newPhoneNumber.endsWith(" ")) || (newPhoneNumber.endsWith("-")))) // Already ends with dash or space
            ) continue; // Throw it away

            if (c == ')') newPhoneNumber = newPhoneNumber + '-'; // Change to dash
            else {
                if ((c == '-') && (newPhoneNumber.endsWith(" "))) newPhoneNumber = newPhoneNumber.trim(); // Drop space before dash
                newPhoneNumber = newPhoneNumber + c;
            }
        }

        if (!(phoneNumber.equals(newPhoneNumber))) {
            out.println("\nF.Y.I.: Changing telephone number format to '" + newPhoneNumber + "'.\n");
            phoneNumber = newPhoneNumber;
        }

        return phoneNumber;
    }

    /******************************************************************************
     * Obtain and return a validated email address
     */
    private static String getContactEmail(String person, String org) throws Exception {
        String emailAddress = null, prompt = "Please enter the email address of ";

        if (!((person == null) || (person.equals("")))) prompt = prompt + person + " or of ";

        prompt = prompt + org;

        while (badEmailAddress(emailAddress = responseToPrompt(prompt))) {
        } // Loop until it's good

        return emailAddress;
    }

    /******************************************************************************
     * Check the validity of an email address; if it's bad, put out a message and
     * return true; otherwise, return false.
     */
    private static boolean badEmailAddress(String emailAddress) {
        emailAddress = emailAddress.trim();
        // Required: present?
        if (emailAddress.length() == 0) return true; // Don't need message: prompt will be repeated.

        String message = null, problem = null;

        int atCount = 0, dotCount = 0, atIndex = -1;

        for (int i = 0; i < emailAddress.length(); i++)
            if (emailAddress.charAt(i) == '@') {
                if (atCount != 0) // Already had one
                {
                    problem = "too many '@' characters";
                    break;
                }
                atCount++;
                atIndex = i;
            } else if ((atCount > 0) && (emailAddress.charAt(i) == '.')) {
                if (atIndex == (i - 1)) {
                    problem = "'.' immediately after '@'";
                    break;
                }
                dotCount++;
            }

        if (problem == null) problem = (atCount == 0) ? "no '@' character" : (dotCount == 0) ? "no '.' character in segment after '@' character" : null;

        if (problem == null) return false; // Email address is valid

        message = "Invalid email address (" + problem + "), please try again.";
        out.println(message);

        return true;
    }

    /******************************************************************************
     * Prompt the user for a positive integer (within specified limits, if any).
     * No default is provided the user if defaultAnswer is NO_DEFAULT.  No minimum
     * enforcement if minimum is NO_LIMIT. No maximum enforcement if maximum is
     * NO_LIMIT.
     */
    private static final int getInteger(String prompt, int defaultAnswer, int minimum, int maximum) throws Exception {
        if ((prompt == null) || (prompt.length() < 1) || ((defaultAnswer != NO_DEFAULT) && (defaultAnswer < 0)) || ((minimum != NO_LIMIT) && (minimum < 0)) || ((maximum != NO_LIMIT) && (maximum < 0)))
            throw new Exception("PROGRAMMING ERROR: getInteger(" + prompt + ", " + minimum + ", " + maximum + ")");

        String promptString = prompt.trim();

        if (defaultAnswer != NO_DEFAULT) promptString = promptString + " [" + defaultAnswer + "]";

        String finalInstruction = ""; // Pablum for possible spoon-feeding below
        if (minimum != NO_LIMIT) finalInstruction = finalInstruction + " greater than " + (minimum - 1);
        if (maximum != NO_LIMIT) {
            if (finalInstruction.length() > 0) finalInstruction = finalInstruction + " and";
            finalInstruction = finalInstruction + " less than " + (maximum + 1);
        }

        while (true) {
            String line = responseToPrompt(promptString);

            if (line.length() == 0) // User just hit Enter
            {
                if (defaultAnswer != NO_DEFAULT) return defaultAnswer;
            } else {
                try {
                    int number = Integer.parseInt(line);

                    if ((number >= 0) && ((minimum == NO_LIMIT) || (number >= minimum)) && ((maximum == NO_LIMIT) || (number <= maximum))) return number; // Success

                    throw new Exception(number + " is unacceptable.");
                } catch (Exception e) {
                    out.println("ERROR: " + e);
                }
            }
            // Should have returned: spoon-feed the user

            out.println("\nPlease enter a positive number" + finalInstruction + ".");
        }
    }

    /******************************************************************************
     * Prompt the user for a yes/no answer; return a boolean translation of the
     * answer (yes == true, no == false), using defaultAnswer if user simply
     * hits Enter.
     */
    private static final boolean getBoolean(String prompt, String defaultAnswer) throws Exception {
        if ((prompt == null) || (prompt.length() < 1) || (defaultAnswer == null) || (!((defaultAnswer.equals(DEFAULT_NO)) || (defaultAnswer.equals(DEFAULT_YES)))))
            throw new Exception("PROGRAMMING ERROR: getBoolean(" + prompt + ", " + defaultAnswer + ")");

        while (true) {
            String line = responseToPrompt(prompt + "(y/n) [" + defaultAnswer + "]").toUpperCase();

            if (line.length() == 0) // User just hit Enter
                return defaultAnswer.equals(DEFAULT_YES);

            if ((line.equals("N")) || (line.equals("NO"))) return false;

            if ((line.equals("Y")) || (line.equals("YES"))) return true;

            out.println("\nUnrecognized response, try again.");
        }
    }

    /******************************************************************************
     * Output a newline, prompt the user, get and return a trimmed response.
     */
    private static final String responseToPrompt(String prompt) throws IOException {
        out.print("\n" + prompt + ": ");
        out.flush();
        return in.readLine().trim();
    }

    /**
     * Prompt the user for an interval; if user simply hits Enter, return
     * DEFAULT_INTERVAL, else return the first acceptable response.
     */
    private static final String getInterval() throws Exception {
        String prompt = "(\"N\" (" + HSG.NEVER + "), " + "\"M\" (" + HSG.MONTHLY + "), " + "\"W\" (" + HSG.WEEKLY + "), or " + "\"D\" (" + HSG.DAILY + "))? [" + DEFAULT_INTERVAL + "] ";

        while (true) { // I.e., until return on success
            String line = responseToPrompt(prompt);

            if (line.length() == 0) return DEFAULT_INTERVAL; // User just hit Enter, use default
            if (line.toUpperCase().equals("N") || line.toUpperCase().equals(HSG.NEVER)) return HSG.NEVER;
            if (line.toUpperCase().equals("M") || line.toUpperCase().equals(HSG.MONTHLY)) return HSG.MONTHLY;
            if (line.toUpperCase().equals("W") || line.toUpperCase().equals(HSG.WEEKLY)) return HSG.WEEKLY;
            if (line.toUpperCase().equals("D") || line.toUpperCase().equals(HSG.DAILY)) return HSG.DAILY;

            out.println("\nUnrecognized response.\n");
        }
    }

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        FileInputStream source = null;
        FileOutputStream destination = null;
        try {
            source = new FileInputStream(sourceFile);
            destination = new FileOutputStream(destFile);
            byte[] buf = new byte[8192];
            int r;
            while ((r = source.read(buf)) > 0) {
                destination.write(buf, 0, r);
            }
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

}

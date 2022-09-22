/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.security.PrivateKey;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import net.cnri.util.SimpleCommandLine;
import net.handle.apps.batch.BatchUtil;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.GenericRequest;
import net.handle.hdllib.GetSiteInfoResponse;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleRecord;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;

public class LoadHandlesFromJson {

    private static void printHelp() {
        System.out.println("-f\tThe path to the input json file containing handle records.");
        System.out.println();
        System.out.println("-site\tHandle or handle reference for the site to be contacted.");
        System.out.println();
        System.out.println("-sitefile\tPath to a siteinfo file for the site to be contacted.");
        System.out.println();
        System.out.println("-i\tIP address of the handle server (alternative to -site/-sitefile).");
        System.out.println();
        System.out.println("-p\tPort of the handle server (alternative to -site/-sitefile).");
        System.out.println();
        System.out.println("-authid\tThe index:handle of the identity performing the operation.");
        System.out.println();
        System.out.println("-privatekey\tPath to the private key for the identity performing the operation.");
        System.out.println();
    }

    private static Gson gson = GsonUtility.getPrettyGson();
    private static HandleResolver resolver = new HandleResolver();
    private static String inputFileName = null;
    private static String privateKeyFileName = null;
    private static PrivateKey authPrivateKey = null;
    private static AuthenticationInfo authInfo = null;
    private static SiteInfo site;

    public static void main(String[] args) throws Exception {
        SimpleCommandLine cl = new SimpleCommandLine("f", "i", "p", "site", "sitefile", "authid", "privatekey");
        cl.parse(args);

        if (cl.hasOption("f")) {
            inputFileName = cl.getOptionArgument("f");
        } else {
            System.out.println("Missing f");
            printHelp();
            return;
        }

        site = getSiteFromOptions(cl);
        if (site == null) return;

        String authId;
        if (cl.hasOption("authid")) {
            authId = cl.getOptionArgument("authid");
        } else {
            System.out.println("Missing authid");
            printHelp();
            return;
        }

        if (cl.hasOption("privatekey")) {
            privateKeyFileName = cl.getOptionArgument("privatekey");
            authPrivateKey = getLocalPrivateKey(privateKeyFileName, "Enter the passphrase to decrypt the private signing key: ");
            ValueReference authValueRef = ValueReference.fromString(authId);
            authInfo = new PublicKeyAuthenticationInfo(authValueRef.handle, authValueRef.index, authPrivateKey);
        } else {
            System.out.println("Missing privatekey");
            printHelp();
            return;
        }

        File inputFile = new File(inputFileName);
        @SuppressWarnings("resource")
        JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"));
        try {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("homedPrefixes".equals(name)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        String prefix = reader.nextString();
                        homePrefixOnService(prefix);
                        System.out.println("Homed: " + prefix);
                    }
                    reader.endArray();
                    continue;
                }
                if ("handleRecords".equals(name)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String handle = reader.nextName();
                        HandleRecord handleRecord = readNextHandleRecord(reader);
                        try {
                            createHandleRecordOnService(handleRecord);
                            System.out.println("Created: " + handle);
                        } catch (HandleException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } finally {
            reader.close();
        }
    }

    private static SiteInfo getSiteFromOptions(SimpleCommandLine cl) throws Exception {
        if (cl.hasOption("sitefile")) {
            if (cl.hasOption("site") || cl.hasOption("i") || cl.hasOption("p")) {
                System.out.println("Cannot have both -site and -sitefile, or either and -i or -p");
                return null;
            }
            return Util.getSiteFromFile(cl.getOptionArgument("sitefile"));
        } else {
            if (cl.hasOption("site")) {
                if (cl.hasOption("i") || cl.hasOption("p")) {
                    System.out.println("Cannot have both -site and -sitefile, or either and -i or -p");
                    return null;
                }
                String siteId = cl.getOptionArgument("site");
                return BatchUtil.getSite(siteId, resolver);
            } else {
                String ipAddress;
                if (cl.hasOption("i")) {
                    ipAddress = cl.getOptionArgument("i");
                } else {
                    printHelp();
                    return null;
                }
                InetAddress addr = InetAddress.getByName(ipAddress);

                int port;
                if (cl.hasOption("p")) {
                    port = Integer.parseInt(cl.getOptionArgument("p"));
                } else {
                    printHelp();
                    return null;
                }
                GenericRequest siReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
                AbstractResponse response = resolver.sendHdlTcpRequest(siReq, addr, port);

                if (response != null && response.responseCode == AbstractMessage.RC_SUCCESS) {
                    return ((GetSiteInfoResponse) response).siteInfo;
                } else {
                    throw new Exception("Unable to retrieve site information from server");
                }
            }
        }
    }

    private static void homePrefixOnService(String prefix) throws HandleException {
        GenericRequest req = new GenericRequest(Util.encodeString(prefix), AbstractMessage.OC_HOME_NA, authInfo);
        req.certify = true;
        req.isAdminRequest = true;
        for (int i = 0; i < site.servers.length; i++) {
            AbstractResponse response = resolver.sendRequestToServer(req, site, site.servers[i]);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                throw HandleException.ofResponse(response);
            }
        }
    }

    public static void createHandleRecordOnService(HandleRecord handleRecord) throws HandleException {
        String handle = handleRecord.getHandle();
        byte[] handleBytes = Util.encodeString(handle);
        CreateHandleRequest request = new CreateHandleRequest(handleBytes, handleRecord.getValuesAsArray(), authInfo);
        request.overwriteWhenExists = true;
        AbstractResponse response = resolver.sendRequestToSite(request, site);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw HandleException.ofResponse(response);
        }
    }

    public static HandleRecord readNextHandleRecord(JsonReader reader) {
        HandleRecord handleRecord = (HandleRecord) gson.fromJson(reader, HandleRecord.class);
        return handleRecord;
    }

    public static PrivateKey getLocalPrivateKey(String key, String prompt) throws Exception {
        File privateKeyFile = new File(key);
        byte encKeyBytes[] = new byte[(int) privateKeyFile.length()];
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

        byte keyBytes[] = null;
        byte secKey[] = null;
        if (Util.requiresSecretKey(encKeyBytes)) {
            secKey = Util.getPassphrase(prompt);
        }

        keyBytes = Util.decrypt(encKeyBytes, secKey);
        for (int i = 0; secKey != null && i < secKey.length; i++) {// clear the secret key
            secKey[i] = (byte) 0;
        }

        PrivateKey privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
        return privateKey;
    }
}

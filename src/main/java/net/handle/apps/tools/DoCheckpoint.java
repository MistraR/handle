/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.apps.simple.SiteInfoConverter;
import net.handle.hdllib.*;

import java.io.*;
import java.security.*;

/*
  Command-line tool to send a checkpoint/backup command to all of the
  handle servers in a specified site.

  Execution is done with the following command:
  <pre>
  java net.handle.apps.tools.DoCheckpoint [siteinfo] [admin-hdl] [admin-index] [keytype] [key]
  </pre>
  Where keytype can be either PRIVATE or SECRET.  If PRIVATE, then key is the
  name of a file where the private key is stored.  If SECRET, then key is the
  secret key itself.
 */
public class DoCheckpoint {
    private AuthenticationInfo authInfo = null;
    private SiteInfo siteToCheckpoint = null;
    private static HandleResolver resolver = new HandleResolver();

    public static void printUsageAndExit() {
        System.err.println("java net.handle.apps.tools.DoCheckpoint " + "[siteinfo] [admin-hdl] [admin-index] [keytype] [key]");
        System.err.println("key-type is either PRIVATE or SECRET");
        System.exit(1);
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length != 5) {
            printUsageAndExit();
        }
        SiteInfo siteInfo = createSiteInfo(argv[0]);
        AuthenticationInfo auth = createAuth(argv[1], argv[2], argv[3], argv[4]);
        if (auth == null) {
            printUsageAndExit();
        }
        DoCheckpoint checkpointer = new DoCheckpoint(auth, siteInfo);
        checkpointer.doIt();
    }

    private static SiteInfo createSiteInfo(String siteInfoFileName) throws Exception {
        File siteFile = new File(siteInfoFileName);
        byte buf[] = new byte[(int) siteFile.length()];
        try (FileInputStream in = new FileInputStream(siteFile)) {
            int n = 0;
            int r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) >= 0) {
                n += r;
            }
        }
        SiteInfo siteInfo = new SiteInfo();
        if (Util.looksLikeBinary(buf)) {
            Encoder.decodeSiteInfoRecord(buf, 0, siteInfo);
        } else {
            siteInfo = SiteInfoConverter.convertToSiteInfo(new String(buf, "UTF-8"));
        }
        return siteInfo;
    }

    private static AuthenticationInfo createAuth(String id, String idIdx, String type, String key) throws Exception {
        if (type.equals("PRIVATE")) {
            File privateKeyFile = new File(key);
            byte encKeyBytes[] = new byte[(int) privateKeyFile.length()];
            try (FileInputStream in = new FileInputStream(privateKeyFile)) {
                int n = 0;
                int r;
                while (n < encKeyBytes.length && (r = in.read(encKeyBytes, n, encKeyBytes.length - n)) >= 0) {
                    n += r;
                }
            }

            byte keyBytes[] = null;
            byte secKey[] = null;
            if (Util.requiresSecretKey(encKeyBytes)) {
                // get the passphrase and decrypt the server's private key
                secKey = Util.getPassphrase("Enter the passphrase for the private key: ");
            }

            keyBytes = Util.decrypt(encKeyBytes, secKey);
            for (int i = 0; secKey != null && i < secKey.length; i++) // clear the secret key
                secKey[i] = (byte) 0;

            PrivateKey privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
            return new PublicKeyAuthenticationInfo(Util.encodeString(id), Integer.parseInt(idIdx), privateKey);
        } else if (type.equals("SECRET")) {
            return new SecretKeyAuthenticationInfo(Util.encodeString(id), Integer.parseInt(idIdx), Util.encodeString(key));
        } else {
            return null;
        }
    }

    public DoCheckpoint(AuthenticationInfo auth, SiteInfo siteInfo) {
        this.authInfo = auth;
        this.siteToCheckpoint = siteInfo;
    }

    public void doIt() {
        AbstractRequest req = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_BACKUP_SERVER, this.authInfo);
        req.isAdminRequest = true;
        req.certify = true;
        for (int i = 0; i < siteToCheckpoint.servers.length; i++) {
            try {
                System.out.println("----------------------------------------------------");
                System.out.println("Checkpointing server " + i + ": " + siteToCheckpoint.servers[i]);
                req.clearBuffers();
                AbstractResponse resp = resolver.sendRequestToServer(req, siteToCheckpoint, siteToCheckpoint.servers[i]);
                if (resp == null || resp.responseCode != AbstractMessage.RC_SUCCESS) {
                    System.err.println("Error sending backup message: " + resp);
                }
            } catch (Throwable t) {
                System.err.println("Error checkpointing server " + i + ": " + t);
                t.printStackTrace(System.err);
            }
        }
    }

}

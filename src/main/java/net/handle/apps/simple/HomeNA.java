/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import net.handle.hdllib.*;

import java.io.*;
import java.net.*;
import java.security.*;

/**
 * Simple tool for homing NAs.
 * Uses public key authentication.
 **/
public class HomeNA {

    public static void main(String argv[]) {
        if (argv.length < 6) {
            System.err.println("usage: java net.handle.apps.simple.HomeNA <auth hdl> <auth index> <privkey> <server ip> <server tcp port> <NA handle1> [<NA handle2>...]");
            System.exit(-1);
        }

        String authHdl = argv[0];
        int authIndex = Integer.parseInt(argv[1]);
        String privKeyFile = argv[2];
        String serverIp = argv[3];
        int port = Integer.parseInt(argv[4]);
        String nas[] = new String[argv.length - 5];
        System.arraycopy(argv, 5, nas, 0, nas.length);

        // First we need to read the private key in from disk
        byte[] key = null;
        try {
            File f = new File(privKeyFile);
            try (FileInputStream fs = new FileInputStream(f)) {
                key = Util.getBytesFromInputStream(fs);
            }
        } catch (Throwable t) {
            System.err.println("Cannot read private key " + privKeyFile + ": " + t);
            System.exit(-1);
        }

        // A HandleResolver object is used not just for resolution, but for
        // all handle operations(including create)
        HandleResolver resolver = new HandleResolver();
        resolver.traceMessages = true;

        // Check to see if the private key is encrypted.  If so, read in the
        // user's passphrase and decrypt.  Finally, convert the byte[]
        // representation of the private key into a PrivateKey object.
        PrivateKey privkey = null;
        byte secKey[] = null;
        try {
            if (Util.requiresSecretKey(key)) {
                secKey = Util.getPassphrase("passphrase: ");
            }
            key = Util.decrypt(key, secKey);
            privkey = Util.getPrivateKeyFromBytes(key, 0);
        } catch (Throwable t) {
            System.err.println("Can't load private key in " + privKeyFile + ": " + t);
            System.exit(-1);
        }

        try {
            // Create a PublicKeyAuthenticationInfo object to pass to HandleResolver.
            // This is constructed with the admin handle, index, and PrivateKey as
            // arguments.
            PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(authHdl.getBytes("UTF8"), authIndex, privkey);

            // get site info for server
            InetAddress svrAddr = InetAddress.getByName(serverIp);
            GenericRequest siReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
            //      siReq.certify = true;
            //      resolver.setCheckSignatures(false);
            AbstractResponse response = resolver.sendHdlTcpRequest(siReq, svrAddr, port);

            SiteInfo siteInfo = null;

            if (response != null && response.responseCode == AbstractMessage.RC_SUCCESS) {
                siteInfo = ((GetSiteInfoResponse) response).siteInfo;
            } else {
                throw new Exception("Unable to retrieve site information from server");
            }
            if (!siteInfo.isPrimary) {
                throw new Exception("Given server is not a primary server.");
            }

            // prepare the home na message
            resolver.setCheckSignatures(true);
            resolver.traceMessages = true;

            // send it
            for (int naIdx = 0; naIdx < nas.length; naIdx++) {
                GenericRequest req = new GenericRequest(Util.encodeString(nas[naIdx]), AbstractMessage.OC_HOME_NA, auth);
                req.majorProtocolVersion = 2;
                req.minorProtocolVersion = 0;
                req.certify = true;
                req.isAdminRequest = true;

                for (int i = 0; i < siteInfo.servers.length; i++) {
                    response = resolver.sendRequestToServer(req, siteInfo, siteInfo.servers[i]);
                    if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                        throw new Exception(String.valueOf(response));
                    }
                }
            }

        } catch (Throwable t) {
            System.err.println("\nError: " + t);
        }
    }

}

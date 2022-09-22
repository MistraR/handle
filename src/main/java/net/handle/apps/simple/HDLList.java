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
import java.security.*;

public class HDLList implements ResponseMessageCallback {

    public static void main(String argv[]) {
        if (argv.length != 4 && argv.length != 5) {
            System.err.println("usage: java net.handle.apps.simple.HDLList [-s] <auth handle> <auth index> <privkey> <prefix>");
            System.err.println("use option -s to list all handle values");
            System.exit(-1);
        }
        @SuppressWarnings("unused")
        HDLList h = new HDLList(argv);
    }

    HandleResolver resolver = new HandleResolver();
    boolean showValues = false;

    public HDLList(String argv[]) {
        if (argv.length > 4) {
            String[] newargv = new String[argv.length - 1];
            int newindex = 0;
            for (int i = 0; i < argv.length; i++) {
                if ("-s".equals(argv[i])) {
                    showValues = true;
                } else {
                    newargv[newindex++] = argv[i];
                }
            }
            argv = newargv;
        }

        byte[] key = null;
        try {
            File f = new File(argv[2]);
            try (FileInputStream fs = new FileInputStream(f)) {
                key = Util.getBytesFromInputStream(fs);
            }
        } catch (Throwable t) {
            System.err.println("Cannot read private key " + argv[2] + ": " + t);
            System.exit(-1);
        }

        resolver.traceMessages = true;
        PrivateKey privkey = null;
        byte secKey[] = null;
        try {
            if (Util.requiresSecretKey(key)) {
                secKey = Util.getPassphrase("passphrase: ");
            }
            key = Util.decrypt(key, secKey);
            privkey = Util.getPrivateKeyFromBytes(key, 0);
        } catch (Throwable t) {
            System.err.println("Can't load private key in " + argv[2] + ": " + t);
            System.exit(-1);
        }

        ResolutionRequest resReq = new ResolutionRequest(Util.encodeString(argv[3] + "/test"), null, null, null);
        System.err.println("finding local sites for " + resReq);
        SiteInfo sites[];
        try {
            sites = resolver.findLocalSites(resReq);
        } catch (HandleException e) {
            e.printStackTrace();
            return;
        }

        PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(Util.encodeString(argv[0]), Integer.valueOf(argv[1]).intValue(), privkey);
        ListHandlesRequest req = new ListHandlesRequest(Util.convertSlashlessHandleToZeroNaHandle(Util.encodeString(argv[3])), auth);
        // send a list-handles request to each server in the site
        for (int i = 0; i < sites[0].servers.length; i++) {
            try {
                ServerInfo server = sites[0].servers[i];
                resolver.sendRequestToServer(req, sites[0], server, this);
            } catch (Throwable t) {
                System.err.println("\nHDLError: " + t);
                // t.printStackTrace();
            }
        }
    }

    @Override
    public void handleResponse(AbstractResponse response) {
        resolver.traceMessages = false;

        if (response instanceof ListHandlesResponse) {
            try {
                ListHandlesResponse lhResp = (ListHandlesResponse) response;
                byte handles[][] = lhResp.handles;
                for (int i = 0; i < handles.length; i++) {
                    String sHandle = Util.decodeString(handles[i]);
                    if (showValues) System.out.println();
                    System.out.println(sHandle);
                    if (showValues) {
                        try {
                            HandleValue[] values = resolver.resolveHandle(sHandle);
                            for (int j = 0; j < values.length; j++) {
                                System.out.println(values[j]);
                            }
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }

                }
            } catch (Exception e) {
                System.err.println("Error: " + e);
                e.printStackTrace(System.err);
            }

        } else if (response.responseCode != AbstractMessage.RC_AUTHENTICATION_NEEDED) {
            System.err.println(response);
        }
    }

}

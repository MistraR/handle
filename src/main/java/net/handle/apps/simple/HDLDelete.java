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

/**
 * Simple tool for deleting handles en masse from the command-line
 **/
public class HDLDelete {

    public static void main(String argv[]) throws Exception {
        if (argv.length != 4) {
            System.err.println("usage: java net.handle.apps.simple.HDLDelete <auth handle> <auth index> <privkey> <filename>");
            System.err.println(" <auth handle> is the handle that contains your public key");
            System.err.println(" <auth index> is the index of your public key in that handle");
            System.err.println(" <privkey> is the file containing your private key");
            System.err.println(" <filename> is the file containing the handles to delete");
            System.err.println(" Note:  if <filename> is '-' then the handles to delete will be read as input (stdin)");
            System.exit(-1);
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

        HandleResolver resolver = new HandleResolver();
        resolver.setSessionTracker(new ClientSessionTracker());
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
        PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(Util.encodeString(argv[0]), Integer.parseInt(argv[1]), privkey);

        BufferedReader rdr = null;
        if (argv[3].equals("-")) {
            rdr = new BufferedReader(new InputStreamReader(System.in));
        } else {
            rdr = new BufferedReader(new InputStreamReader(new FileInputStream(argv[3]), "UTF-8"));
        }

        String line;
        while ((line = rdr.readLine()) != null) {
            try {
                line = line.trim();
                DeleteHandleRequest req = new DeleteHandleRequest(Util.encodeString(line), auth);
                AbstractResponse response = resolver.processRequest(req);
                if (response == null || response.responseCode != AbstractMessage.RC_SUCCESS) {
                    System.out.println("error deleting '" + line + "': " + response);
                } else {
                    System.out.println("deleted " + line);
                }
            } catch (Throwable t) {
                System.out.println("error deleting '" + line + "': " + t);
                t.printStackTrace(System.err);
            }
        }
        rdr.close();
    }

}

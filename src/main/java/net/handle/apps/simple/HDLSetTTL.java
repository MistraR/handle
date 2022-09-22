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
 * Simple tool for changing the TTLs for a bunch of handles
 */
public class HDLSetTTL {

    public static void main(String argv[]) throws Exception {
        if (argv.length < 4) {
            System.err.println("usage: java net.handle.apps.simple.HDLSetTTL " + "<auth handle> <privkey> <new TTL> <handle file>");
            System.exit(-1);
        }

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (FileInputStream fs = new FileInputStream(new File(argv[1]))) {
            byte buf[] = new byte[1024];
            int r;
            while ((r = fs.read(buf)) >= 0)
                bout.write(buf, 0, r);
        } catch (Throwable t) {
            System.err.println("Cannot read private key " + argv[1] + ": " + t);
            System.exit(-1);
        }
        byte key[] = bout.toByteArray();

        PrivateKey privkey = null;
        byte secKey[] = null;
        try {
            if (Util.requiresSecretKey(key)) {
                secKey = Util.getPassphrase("passphrase: ");
            }
            key = Util.decrypt(key, secKey);
            privkey = Util.getPrivateKeyFromBytes(key, 0);
        } catch (Throwable t) {
            System.err.println("Can't load private key in " + argv[1] + ": " + t);
            System.exit(-1);
        }
        PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(Util.encodeString(argv[0]), 300, privkey);

        int newTTL = Integer.parseInt(argv[2].trim());
        HandleResolver resolver = new HandleResolver();

        BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(new File(argv[3])), "UTF-8"));
        while (true) {
            String hdl = rdr.readLine();
            if (hdl == null) break;
            hdl = hdl.trim();
            if (hdl.length() <= 0) continue;

            ResolutionRequest rreq = new ResolutionRequest(Util.encodeString(hdl), null, null, null);
            rreq.authoritative = true;
            AbstractResponse rresp = resolver.processRequest(rreq);
            HandleValue values[] = null;
            if (rresp != null && rresp instanceof ResolutionResponse) {
                values = ((ResolutionResponse) rresp).getHandleValues();
                for (int i = 0; values != null && i < values.length; i++) {
                    if (values[i] != null) {
                        values[i].setTTL(newTTL);
                    }
                }
            } else {
                System.err.println("Error response for hdl '" + hdl + "': " + rresp);
                continue;
            }

            ModifyValueRequest modreq = new ModifyValueRequest(rreq.handle, values, auth);
            AbstractResponse modresp = resolver.processRequest(modreq);
            if (modresp != null && modresp.responseCode == AbstractMessage.RC_SUCCESS) {
                System.err.println("success: changed " + hdl); // hooray!
            } else {
                System.err.println("Error modifying handle '" + hdl + "': " + modresp);
                continue;
            }
        }
        rdr.close();
    }

}

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
import java.util.*;
import java.security.*;

/**
 * Simple tool for changing the service handle for a whole bunch of prefixes.
 */
public class HDLSetServiceHandle {

    public static void main(String argv[]) throws Exception {
        if (argv.length < 4) {
            System.err.println("usage: java net.handle.apps.simple.HDLSetServiceHandle " + "<auth handle> <privkey> <service handle> <prefixfile>");
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

        HandleResolver resolver = new HandleResolver();
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

        String serviceHandle = argv[2].trim();

        BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(new File(argv[3])), "UTF-8"));
        while (true) {
            String prefix = rdr.readLine();
            if (prefix == null) break;
            prefix = prefix.trim();
            if (prefix.length() <= 0) continue;

            ResolutionRequest rreq = new ResolutionRequest(Util.convertSlashlessHandleToZeroNaHandle(Util.encodeString(prefix)), null, null, null);
            rreq.authoritative = true;
            AbstractResponse rresp = resolver.processRequest(rreq);
            HandleValue servHdlvalue = null;
            HandleValue values[] = null;
            if (rresp != null && rresp instanceof ResolutionResponse) {
                values = ((ResolutionResponse) rresp).getHandleValues();
                for (int i = 0; values != null && i < values.length; i++) {
                    if (values[i] != null && values[i].hasType(Common.STD_TYPE_HSSERV)) {
                        servHdlvalue = values[i];
                        break;
                    }
                }
            } else {
                System.err.println("Error response for prefix '" + prefix + "': " + rresp);
                continue;
            }

            if (servHdlvalue == null) {
                // there is no HS_SERV value, add one and remove any HS_SITE values.
                HandleValue newServValue = new HandleValue(getFirstAvailableIndex(values), Common.STD_TYPE_HSSERV, Util.encodeString(serviceHandle));
                AddValueRequest addreq = new AddValueRequest(rreq.handle, newServValue, auth);
                AbstractResponse addresp = resolver.processRequest(addreq);
                if (addresp != null && addresp.responseCode == AbstractMessage.RC_SUCCESS) {
                    // hooray!
                } else {
                    System.err.println("Error adding HS_SERV for prefix '" + prefix + "': " + addresp);
                    continue;
                }
            } else {
                // there is already an HS_SERV value, replace it
                servHdlvalue.setData(Util.encodeString(serviceHandle));
                ModifyValueRequest modreq = new ModifyValueRequest(rreq.handle, servHdlvalue, auth);
                AbstractResponse modresp = resolver.processRequest(modreq);
                if (modresp != null && modresp.responseCode == AbstractMessage.RC_SUCCESS) {
                    // hooray!
                } else {
                    System.err.println("Error modifying HS_SERV for prefix '" + prefix + "': " + modresp);
                    continue;
                }
            }

            // remove any extra HS_SITE values
            Vector<HandleValue> valuesToRemove = new Vector<>();
            for (int i = 0; values != null && i < values.length; i++) {
                if (values[i] != null && values[i].hasType(Common.STD_TYPE_HSSITE)) {
                    valuesToRemove.addElement(values[i]);
                }
            }
            if (valuesToRemove.size() > 0) {
                HandleValue indexesToRemove[] = new HandleValue[valuesToRemove.size()];
                for (int i = 0; i < valuesToRemove.size(); i++) {
                    indexesToRemove[i] = valuesToRemove.elementAt(i);
                    indexesToRemove[i].setType(Util.encodeString("X-HS_SITE"));
                }
                ModifyValueRequest modreq = new ModifyValueRequest(rreq.handle, indexesToRemove, auth);
                AbstractResponse modresp = resolver.processRequest(modreq);
                if (modresp != null && modresp.responseCode == AbstractMessage.RC_SUCCESS) {
                    // hooray!
                } else {
                    System.err.println("Error removing extra HS_SITE values from prefix '" + prefix + "': " + modresp);
                    continue;
                }
            }
        }
        rdr.close();
    }

    private static final int getFirstAvailableIndex(HandleValue values[]) {
        int idx = 1;
        while (true) {
            boolean hasIndex = false;
            for (int i = 0; !hasIndex && values != null && i < values.length; i++) {
                if (values[i] != null && values[i].getIndex() == idx) {
                    hasIndex = true;
                    break;
                }
            }
            if (!hasIndex) return idx;
            idx++;
            if (idx >= 100 && idx < 1000) idx = 1000;
        }
    }

}

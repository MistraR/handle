/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import java.net.*;
import java.io.*;
import net.handle.hdllib.*;

public abstract class GetSiteInfo {

    public static final void main(String argv[]) throws Exception {
        if (argv.length < 3) {
            System.err.println("usage: java net.handle.apps.tools.GetSiteInfo <server> <port> <output-file>");
            System.exit(1);
        }

        String addrStr = argv[0];
        String portStr = argv[1];
        String outputFileStr = argv[2];

        InetAddress addr = InetAddress.getByName(addrStr);
        int port = Integer.parseInt(portStr);

        GenericRequest req = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);

        HandleResolver resolver = new HandleResolver();
        AbstractResponse aresponse;
        try {
            aresponse = resolver.sendHdlTcpRequest(req, addr, port);
        } catch (HandleException e) {
            aresponse = resolver.sendHttpRequest(req, addr, port);
        }
        FileOutputStream out = null;
        if (aresponse.responseCode == AbstractMessage.RC_SUCCESS) {
            GetSiteInfoResponse response = (GetSiteInfoResponse) aresponse;
            out = new FileOutputStream(outputFileStr);
            out.write(Encoder.encodeSiteInfoRecord(response.siteInfo));
            out.close();
        } else {
            System.err.println("Error: got unexpected response: " + aresponse);
        }
    }

}

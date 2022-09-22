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

public abstract class GetRootInfo {

    public static final void main(String argv[]) throws Exception {
        if (argv.length < 3) {
            System.err.println("usage: java net.handle.apps.tools.GetRootInfo <root-server> <port> <output-file>");
            System.exit(1);
        }

        String rootAddrStr = argv[0];
        String rootPortStr = argv[1];
        String outputFileStr = argv[2];

        InetAddress addr = InetAddress.getByName(rootAddrStr);
        int port = Integer.parseInt(rootPortStr);

        ResolutionRequest req = new ResolutionRequest(Common.ROOT_HANDLE, null, null, null);

        AbstractResponse aresponse = (new HandleResolver()).sendHdlTcpRequest(req, addr, port, null);
        FileOutputStream out = null;
        if (aresponse.responseCode == AbstractMessage.RC_SUCCESS) {
            ResolutionResponse response = (ResolutionResponse) aresponse;
            out = new FileOutputStream(outputFileStr);
            out.write(Encoder.encodeGlobalValues(response.getHandleValues()));
            out.close();
        } else {
            System.err.println("Error: got response: " + aresponse);
        }
    }

}

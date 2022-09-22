/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import net.handle.hdllib.*;
import java.net.*;

public class JUtil {
    public static byte[] getByteIP(String str) {
        try {
            InetAddress addr = InetAddress.getByName(str);
            byte addr1[] = addr.getAddress();
            byte addr2[] = new byte[Common.IP_ADDRESS_LENGTH];
            for (int i = 0; i < Common.IP_ADDRESS_LENGTH; i++)
                addr2[i] = (byte) 0;
            System.arraycopy(addr1, 0, addr2, addr2.length - addr1.length, addr1.length);
            return addr2;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    public static String getAddressString(byte[] ip) {
        StringBuffer sb = new StringBuffer();
        if (ip == null) return null;
        for (int i = 0; i < ip.length; i++) {
            if (ip[i] == 0 && sb.length() <= 0) continue;
            if (sb.length() > 0) sb.append('.');
            sb.append(0x00ff & ip[i]);
        }
        return sb.toString();
    }
}

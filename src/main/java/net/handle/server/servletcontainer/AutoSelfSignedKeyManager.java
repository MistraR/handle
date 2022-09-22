/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

@Deprecated
public class AutoSelfSignedKeyManager extends net.handle.util.AutoSelfSignedKeyManager {

    public AutoSelfSignedKeyManager(String id, PublicKey pubKey, PrivateKey privKey) {
        super(id, pubKey, privKey);
    }

    public AutoSelfSignedKeyManager(String id, X509Certificate cert, PrivateKey privKey) {
        super(id, cert, privKey);
    }

    public AutoSelfSignedKeyManager(String id, X509Certificate[] chain, PrivateKey privKey) {
        super(id, chain, privKey);
    }

    public AutoSelfSignedKeyManager(String id) throws Exception {
        super(id);
    }
    
}

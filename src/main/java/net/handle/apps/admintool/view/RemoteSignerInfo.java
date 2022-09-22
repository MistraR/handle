/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

public class RemoteSignerInfo {

    public String issuer;
    public String baseUri;
    public String username;
    public String password;
    public String privateKeyId;
    public String privateKeyPassphrase;

    public RemoteSignerInfo(String signerHandleId, String baseUri, String username, String password, String privateKeyId, String privateKeyPassphrase) {
        this.issuer = signerHandleId;
        this.baseUri = baseUri;
        this.username = username;
        this.password = password;
        this.privateKeyId = privateKeyId;
        this.privateKeyPassphrase = privateKeyPassphrase;
    }
}

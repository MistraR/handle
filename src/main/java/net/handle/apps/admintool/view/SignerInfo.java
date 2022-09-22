/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.ValueReference;
import net.handle.hdllib.trust.HandleClaimsSet;
import net.handle.hdllib.trust.HandleSigner;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.TrustException;

public class SignerInfo {

    private final RemoteSignerInfo remoteSignerInfo;
    private final PublicKeyAuthenticationInfo localSignerInfo;

    public SignerInfo(RemoteSignerInfo remoteSignerInfo) {
        this.remoteSignerInfo = remoteSignerInfo;
        this.localSignerInfo = null;
    }

    public SignerInfo(PublicKeyAuthenticationInfo localSignerInfo) {
        this.remoteSignerInfo = null;
        this.localSignerInfo = localSignerInfo;
    }

    public RemoteSignerInfo getRemoteSignerInfo() {
        return remoteSignerInfo;
    }

    public PublicKeyAuthenticationInfo getLocalSignerInfo() {
        return localSignerInfo;
    }

    public boolean isRemoteSigner() {
        if (remoteSignerInfo != null) return true;
        else return false;
    }

    public boolean isLocalSigner() {
        if (localSignerInfo != null) return true;
        else return false;
    }

    public ValueReference getUserValueReference() {
        if (isLocalSigner()) {
            return localSignerInfo.getUserValueReference();
        } else {
            return ValueReference.fromString(remoteSignerInfo.issuer);
        }
    }

    public JsonWebSignature signClaimsSet(HandleClaimsSet claims) throws TrustException {
        HandleSigner handleSigner = new HandleSigner();
        JsonWebSignature jws;
        if (isLocalSigner()) {
            jws = handleSigner.signClaims(claims, getLocalSignerInfo().getPrivateKey());
        } else {
            @SuppressWarnings("hiding")
            RemoteSignerInfo remoteSignerInfo = getRemoteSignerInfo();
            String baseUri = remoteSignerInfo.baseUri;
            String username = remoteSignerInfo.username;
            String password = remoteSignerInfo.password;
            String privateKeyId = remoteSignerInfo.privateKeyId;
            String privateKeyPassphrase = remoteSignerInfo.privateKeyPassphrase;
            jws = handleSigner.signClaimsRemotely(claims, baseUri, username, password, privateKeyId, privateKeyPassphrase);
        }
        return jws;
    }
}

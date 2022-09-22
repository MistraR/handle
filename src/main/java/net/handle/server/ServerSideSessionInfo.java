/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.security.*;
import net.handle.hdllib.*;
import net.handle.security.HdlSecurityProvider;

public class ServerSideSessionInfo extends SessionInfo {
    public PublicKey exchangeKey;
    public int keyExchangeMode = Common.KEY_EXCHANGE_NONE;

    public int timeLastUsed; //last time the session is accessed

    //for promoting the anonymous session.
    //only set to true after the challenge response model signature is verified
    public boolean clientAuthenticated = false;

    private int indexOfAuthenticatedSession;

    @Deprecated
    public ServerSideSessionInfo(int sessionid, byte[] sessionkey, byte[] identityHandle, int identityindex, PublicKey key, int keyExchangeMode, int majorProtocolVersion, int minorProtocolVersion) {
        this(sessionid, sessionkey, identityHandle, identityindex, HdlSecurityProvider.ENCRYPT_ALG_DES, key, keyExchangeMode, majorProtocolVersion, minorProtocolVersion);
    }

    public ServerSideSessionInfo(int sessionid, byte[] sessionkey, byte[] identityHandle, int identityindex, int algorithmCode, PublicKey key, int keyExchangeMode, int majorProtocolVersion, int minorProtocolVersion) {
        super(sessionid, sessionkey, identityHandle, identityindex, algorithmCode, majorProtocolVersion, minorProtocolVersion);
        this.exchangeKey = key;
        this.keyExchangeMode = keyExchangeMode;
    }

    public int getIndexOfAuthenticatedSession() {
        return indexOfAuthenticatedSession;
    }

    public void setIndexOfAuthenticatedSession(int indexOfAuthenticatedSession) {
        this.indexOfAuthenticatedSession = indexOfAuthenticatedSession;
    }
}

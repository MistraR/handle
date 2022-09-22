/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.ChallengeResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.Util;

public class TentativeServerSideAuthenticationInfo extends AuthenticationInfo {
    public static final byte[] AUTH_TYPE = Util.encodeString(TentativeServerSideAuthenticationInfo.class.getName());

    private final byte[] userIdHandle;
    private final int userIdIndex;

    public TentativeServerSideAuthenticationInfo(byte[] userIdHandle, int userIdIndex) {
        this.userIdHandle = userIdHandle;
        this.userIdIndex = userIdIndex;
    }

    @Override
    public byte[] getAuthType() {
        return AUTH_TYPE;
    }

    @Override
    public byte[] authenticate(ChallengeResponse challenge, AbstractRequest request) throws HandleException {
        throw new HandleException(HandleException.INTERNAL_ERROR, getClass().getName() + ".authenticate() should not be called");
    }

    @Override
    public byte[] getUserIdHandle() {
        return userIdHandle;
    }

    @Override
    public int getUserIdIndex() {
        return userIdIndex;
    }

}

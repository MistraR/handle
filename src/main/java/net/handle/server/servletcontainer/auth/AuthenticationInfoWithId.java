/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.auth;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Util;

public class AuthenticationInfoWithId {
    private final String id;
    private final AuthenticationInfo authInfo;

    public AuthenticationInfoWithId(AuthenticationInfo authInfo) {
        this(authInfo.getUserIdIndex() + ":" + Util.decodeString(authInfo.getUserIdHandle()), authInfo);
    }

    public AuthenticationInfoWithId(String id, AuthenticationInfo authInfo) {
        this.id = id;
        this.authInfo = authInfo;
    }

    public String getId() {
        return id;
    }

    public AuthenticationInfo getAuthInfo() {
        return authInfo;
    }
}

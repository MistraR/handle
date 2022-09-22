/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.support;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.ChallengeAnswerRequest;

public class PreAuthenticatedChallengeAnswerRequest extends ChallengeAnswerRequest {
    public PreAuthenticatedChallengeAnswerRequest(AuthenticationInfo authInfo) {
        super(authInfo.getAuthType(), authInfo.getUserIdHandle(), authInfo.getUserIdIndex(), null, authInfo);
    }
}

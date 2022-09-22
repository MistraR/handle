/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;

public class BatchDeleteHandles {
    private final SiteInfo site;
    private final AuthenticationInfo authInfo;
    private final HandleResolver resolver;

    public BatchDeleteHandles(SiteInfo site, AuthenticationInfo authInfo, HandleResolver resolver) {
        this.site = site;
        this.authInfo = authInfo;
        this.resolver = resolver;
    }

    public List<String> deleteHandles(List<String> handlesToDelete) {
        List<String> fails = new ArrayList<>();
        for (String handle : handlesToDelete) {
            byte[] handleBytes = Util.encodeString(handle);
            AbstractRequest deleteHandleRequest = new DeleteHandleRequest(handleBytes, authInfo);
            AbstractResponse response;
            try {
                response = resolver.sendRequestToSite(deleteHandleRequest, site);
                if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                    fails.add(handle);
                } else {
                    System.out.println("DELETED: " + handle);
                }
            } catch (HandleException e) {
                fails.add(handle);
                e.printStackTrace();
            }
        }
        return fails;
    }
}

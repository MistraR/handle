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
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.SiteInfo;

public class BatchUnhomePrefixes {

    private final SiteInfo site;
    private final AuthenticationInfo authInfo;
    private final HandleResolver resolver;

    public BatchUnhomePrefixes(SiteInfo site, AuthenticationInfo authInfo, HandleResolver resolver) {
        this.site = site;
        this.authInfo = authInfo;
        this.resolver = resolver;
    }

    public List<String> unhomePrefixes(List<String> prefixesToUnhome) {
        List<String> fails = new ArrayList<>();
        for (String prefix : prefixesToUnhome) {
            try {
                AbstractResponse response = BatchUtil.unhomePrefix(prefix, resolver, authInfo, site);
                if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                    fails.add(prefix);
                } else {
                    System.out.println("UNHOMED: " + prefix);
                }
            } catch (HandleException e) {
                fails.add(prefix);
                e.printStackTrace();
            }
        }
        System.out.println("The following prefixes could not be unhomed");
        BatchUtil.writeHandlesToConsole(fails);
        return fails;
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import java.util.List;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.apps.batch.HandleRecordOperationInterface;
import net.handle.apps.batch.BatchUtil;

public class AliasRemoverOperation implements HandleRecordOperationInterface {

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        List<HandleValue> aliases = BatchUtil.getValuesOfType(values, "HS_ALIAS");

        if (aliases.size() > 1) {
            throw new HandleException(HandleException.INTERNAL_ERROR, handle + " has more than one alias");
        }
        if (aliases.size() == 0) {
            return;
        }

        HandleValue alias = aliases.get(0);
        AbstractResponse response = BatchUtil.removeValueRequest(handle, alias, resolver, authInfo, site);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, response.toString());
        }

    }

}

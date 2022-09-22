/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import java.util.Map;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.apps.batch.HandleRecordOperationInterface;
import net.handle.apps.batch.BatchUtil;

public class AliasAdderOperation implements HandleRecordOperationInterface {

    public Map<String, String> aliasMap;

    public AliasAdderOperation(Map<String, String> aliasMap) {
        this.aliasMap = aliasMap;
    }

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        int availableIndex = BatchUtil.lowestAvailableIndex(values);
        String alias = aliasMap.get(handle);
        if (alias == null) {
            throw new HandleException(HandleException.INTERNAL_ERROR, handle + " does not have an alias in map.");
        }
        AbstractResponse response = BatchUtil.addAliasToHandleRecord(handle, alias, availableIndex, resolver, authInfo, site);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, response.toString());
        }
    }

}

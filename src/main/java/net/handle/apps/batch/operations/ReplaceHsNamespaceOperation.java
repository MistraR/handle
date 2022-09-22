/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import java.util.List;

import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;
import net.handle.apps.batch.HandleRecordOperationInterface;
import net.handle.apps.batch.BatchUtil;

public class ReplaceHsNamespaceOperation implements HandleRecordOperationInterface {

    private final byte[] bytes;

    public ReplaceHsNamespaceOperation(String replacementNamespaceString) {
        bytes = Util.encodeString(replacementNamespaceString);
    }

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        List<HandleValue> existingNamespaceValues = BatchUtil.getValuesOfType(values, "HS_NAMESPACE");
        if (existingNamespaceValues.size() != 1) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Handle does not have exactly one HS_NAMESPACE");
        }
        HandleValue existingNamespaceValue = existingNamespaceValues.get(0);
        existingNamespaceValue.setData(bytes);
        AbstractResponse response = BatchUtil.modifyHandleValue(handle, existingNamespaceValue, resolver, authInfo, site);
        BatchUtil.throwIfNotSuccess(response);
    }
}

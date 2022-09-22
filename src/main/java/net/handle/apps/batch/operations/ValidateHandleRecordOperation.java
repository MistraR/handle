/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleRecord;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.trust.HandleRecordTrustVerifier;
import net.handle.apps.batch.HandleRecordOperationInterface;

public class ValidateHandleRecordOperation implements HandleRecordOperationInterface {

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {

        HandleRecordTrustVerifier handleRecordTrustVerifier = new HandleRecordTrustVerifier(resolver);
        handleRecordTrustVerifier.setThrowing(true);
        HandleRecord handleRecord = new HandleRecord(handle, values);
        boolean validates = handleRecordTrustVerifier.validateHandleRecord(handleRecord);

        if (!validates) {
            throw new HandleException(HandleException.SECURITY_ALERT);
        }
    }

}

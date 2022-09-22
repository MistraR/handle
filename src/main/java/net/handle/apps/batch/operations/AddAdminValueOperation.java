/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import net.handle.apps.batch.BatchUtil;
import net.handle.apps.batch.HandleRecordOperationInterface;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AdminRecord;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;

public class AddAdminValueOperation implements HandleRecordOperationInterface {

    public AdminRecord admin;
    public int nextIndex;

    public AddAdminValueOperation(AdminRecord admin, int nextIndex) {
        this.admin = admin;
        this.nextIndex = nextIndex;
    }

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        HandleValue adminValue = new HandleValue();
        adminValue.setData(Encoder.encodeAdminRecord(admin));
        int newIndex = BatchUtil.getNextIndex(values, nextIndex);
        adminValue.setIndex(newIndex);
        AbstractResponse response = BatchUtil.addHandleValue(handle, adminValue, resolver, authInfo, site);
        BatchUtil.throwIfNotSuccess(response);
    }

}

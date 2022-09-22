/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import net.handle.hdllib.*;

public class HDLShowAdmin {

    public static final void main(String argv[]) throws Exception {
        String handleStr = argv[0];
        byte handle[] = Util.encodeString(handleStr);

        // if this is a derived prefix handle, we get the admin information
        // from the parent prefix.  Otherwise, we get the admin info
        // from the prefix handle.
        boolean isSubNAHandle = Util.isSubNAHandle(handle);
        System.err.println(" is sub-NA handle: " + isSubNAHandle);

        byte na[] = isSubNAHandle ? Util.getParentNAOfNAHandle(handle) : Util.getZeroNAHandle(handle);
        System.err.println("responsible auth handle: " + Util.decodeString(na));

        byte vals[][] = getNAAdminValues(na);
        if (vals == null) {
            System.err.println("Unable to find admin group while creating handle");
            return;
        }

        HandleValue value = new HandleValue();
        for (int i = 0; i < vals.length; i++) {
            Encoder.decodeHandleValue(vals[i], 0, value);
            System.err.println("value: " + value);
        }

    }

    public static byte[][] getNAAdminValues(byte na[]) throws Exception {
        ResolutionRequest authRequest = null;
        authRequest = new ResolutionRequest(na, Common.ADMIN_TYPES, null, null);

        // this must be certified - oh yes!
        authRequest.certify = true;

        // get the admin records from the prefix handle
        AbstractResponse response = (new HandleResolver()).processRequest(authRequest);

        if (response.getClass() != ResolutionResponse.class) {
            return null;
        }

        return ((ResolutionResponse) response).values;
    }

}

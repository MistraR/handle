/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy.handlers;

import net.cnri.util.StringUtils;
import net.handle.hdllib.*;
import net.handle.apps.servlet_proxy.*;

public class Email implements TypeHandler {

    /**
     * Return true iff this TypeHandler can send a redirect to the client
     * based on the given set of HandleValues.
     */
    @Override
    public boolean canRedirect(HandleValue values[]) {
        return false;
    }

    /**
     * Return true iff this TypeHandler can format the data from the given
     * HandleValue for a human client.
     */
    @Override
    public boolean canFormat(HandleValue value) {
        return value.hasType(Common.STD_TYPE_EMAIL);
    }

    @Override
    public String toHTML(String handle, HandleValue value) {
        String data = value.getDataAsString();
        return "<a href=\"mailto:" + StringUtils.encodeURLPath(data) + "\">" + StringUtils.cgiEscape(data) + "</a>";
    }

    @Override
    public boolean doRedirect(HDLServletRequest req, HandleValue values[]) {
        return false;
    }

    /** Return true iff this handler can display a list of locations to which this handle
     * refers. */
    @Override
    public boolean canShowLocations(HandleValue values[]) {
        return false;
    }

    /**
     * Display a menu of locations to which this handle refers.  A nonnegative return value
     * indicates that the servlet should not invoke doResponse on any subsequent type
     * handlers, and is the position in values[] of the first value displayed in the response.
     **/
    @Override
    public net.cnri.simplexml.XTag doShowLocations(HDLServletRequest req, HandleValue values[]) throws Exception {
        return null;
    }

}

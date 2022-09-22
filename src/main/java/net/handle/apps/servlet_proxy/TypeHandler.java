/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import net.handle.hdllib.*;
import net.cnri.simplexml.XTag;

/**
 * Interface implemented by classes that want to be able to translate handle
 * values into responses for handles that include values with a particular
 * type or set of types.
 */
public interface TypeHandler {

    /**
     * Return true iff this TypeHandler can send a redirect to the client
     * based on the given set of HandleValues.
     */
    public boolean canRedirect(HandleValue values[]);

    /**
     * Return true iff this TypeHandler can format the data from the given
     * HandleValue for a human client.
     */
    public boolean canFormat(HandleValue value);

    /**
     * return a html string suitable for display in response page
     **/
    public String toHTML(String handle, HandleValue value);

    /**
     * Do redirect here, if need be.  a true return value indicates
     * that the servlet should not invoke doResponse on any subsequent type
     * handlers.
     **/
    public boolean doRedirect(HDLServletRequest request, HandleValue values[]) throws Exception;

    /** Return true iff this handler can display a list of locations to which this handle
     * refers. */
    public boolean canShowLocations(HandleValue values[]);

    /**
     * Return an XML element containing a sub-element for each location referred to by this
     * handle.
     **/
    public XTag doShowLocations(HDLServletRequest req, HandleValue values[]) throws Exception;

}

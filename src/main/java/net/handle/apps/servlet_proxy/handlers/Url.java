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
import java.io.IOException;
import java.io.OutputStreamWriter;
import net.handle.apps.servlet_proxy.*;
import net.handle.apps.servlet_proxy.HDLServletRequest.ResponseType;

public class Url implements TypeHandler {

    static final byte[] zeroDotTypeUrl = { '0', '.', 'T', 'Y', 'P', 'E', '/', 'U', 'R', 'L' };

    /**
     * Return true if the handle values contain a URL value or a value with
     * a sub-type of URL.
     */
    @Override
    public boolean canRedirect(HandleValue values[]) {
        if (values == null) return false;
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].hasType(Common.STD_TYPE_URL) || values[i].hasType(zeroDotTypeUrl)) return true;
        }
        return false;
    }

    /**
     * Return true iff this TypeHandler can format the data from the given
     * HandleValue for a human client.
     */
    @Override
    public boolean canFormat(HandleValue value) {
        return value != null && (value.hasType(Common.STD_TYPE_URL) || value.hasType(zeroDotTypeUrl));
    }

    @Override
    public String toHTML(String handle, HandleValue value) {
        String data = StringUtils.cgiEscape(value.getDataAsString());
        return "<a href=\"" + data + "\">" + data + "</a>";
    }

    @Override
    public boolean doRedirect(HDLServletRequest req, HandleValue values[]) throws IOException {
        if (values == null) return false;

        String redirectURL = null;

        // first check for base URL values
        HandleValue val;
        byte valType[];
        for (HandleValue value : values) {
            val = value;
            if (val == null) continue;
            valType = val.getType();
            if (valType == null) continue;
            if (Util.equalsCI(valType, Common.STD_TYPE_URL) || Util.equalsCI(valType, zeroDotTypeUrl)) {
                req.modifyExpiration(val);
                redirectURL = val.getDataAsString();
                break;
            }
        }

        // if no simple URL value exists, look for URL sub-types
        if (redirectURL == null) {
            for (HandleValue value : values) {
                val = value;
                if (val == null) continue;
                if (val.hasType(Common.STD_TYPE_URL) || val.hasType(zeroDotTypeUrl)) {
                    req.modifyExpiration(val);
                    redirectURL = val.getDataAsString();
                    break;
                }
            }
        }

        if (redirectURL == null) {
            // no value was found with type URL or any subtypes
            return false;
        }

        String urlSuffix = req.params.getParameter("urlappend");
        if (urlSuffix == null) urlSuffix = "";
        // already decoded
        //    else urlSuffix = StringUtils.decodeURLIgnorePlus(urlSuffix);

        // send a redirect to the URL, with any suffix provided by the user
        try {
            // don't use sendRedirect(), because it tries to be smart and
            // occasionally mangles the uri(e.g. on mailto's)
            // response.sendRedirect(url+suffix);
            req.sendHTTPRedirect(ResponseType.DEFAULT_RESPONSE_TYPE, redirectURL + urlSuffix);
            // print out terse page to avoid tomcat's redirect message for
            // things like mailto where a separate viewer is spawned
            req.response.setContentType("text/html; charset=utf-8");
            String escapedURL = StringUtils.cgiEscape(redirectURL + urlSuffix);
            OutputStreamWriter out = new OutputStreamWriter(req.response.getOutputStream(), "UTF-8");
            out.write("\n<HTML><HEAD><TITLE>Handle Redirect</TITLE></HEAD>");
            out.write("\n<BODY><A HREF=\"" + escapedURL + "\">");
            out.write(escapedURL + "</A></BODY></HTML>");
            out.close();
            return true;
        } catch (Exception e) {
            System.out.println("Error in Url.doRedirect for " + req.hdl + ": " + e);
        }
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

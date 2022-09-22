/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * This is a wrapper class to support ';' as a CGI delimiter.
 */
public class HttpParams {
    Map<String, List<String>> params = new HashMap<>();

    public String getParameter(String name) {
        List<String> v = params.get(name);
        if (v == null) return null;
        else return v.get(0);
    }

    public void replaceParameterValue(String name, String newValue) {
        List<String> v = new ArrayList<>();
        v.add(newValue);
        params.put(name, v);
    }

    public String[] getParameterValues(String name) {
        List<String> v = params.get(name);
        if (v == null) return null;
        return v.toArray(new String[v.size()]);
    }

    public void addParameter(String name, String val) {
        List<String> v = params.get(name);
        if (v == null) {
            v = new ArrayList<>();
            params.put(name, v);
        }
        v.add(val);
    }

    public void addParameters(String name, String vals[]) {
        for (String val : vals) {
            addParameter(name, val);
        }
    }

    public void clear() {
        params.clear();
    }

    public Map<String, List<String>> getMap() {
        return params;
    }
}

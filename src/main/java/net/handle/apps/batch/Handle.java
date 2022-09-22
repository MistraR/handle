/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import java.util.Arrays;
import java.util.List;

public class Handle {

    private final String handle;

    public Handle(String handle) {
        this.handle = handle;
    }

    public static Handle fromString(String handle) {
        return new Handle(handle);
    }

    public String getPrefix() {
        int firstSlash = handle.indexOf("/");
        String prefix = handle.substring(0, firstSlash);
        return prefix;
    }

    public String getSuffix() {
        int firstSlash = handle.indexOf("/");
        String suffix = handle.substring(firstSlash + 1);
        return suffix;
    }

    public List<String> getDotSeparatedComponentsOfSuffix() {
        String suffix = getSuffix();
        String[] tokens = suffix.split("\\.");
        return Arrays.asList(tokens);
    }

    public boolean isNa() {
        if (handle.toUpperCase().startsWith("0.NA/")) {
            return true;
        } else {
            return false;
        }
    }

}

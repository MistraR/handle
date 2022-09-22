/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.util;

/**
 * @deprecated Replaced by net.cnri.util.TemplateException
 */
@Deprecated
public class TemplateException extends net.cnri.util.TemplateException {
    public TemplateException(String str) {
        super(str);
    }

    public TemplateException() {
        super();
    }
}

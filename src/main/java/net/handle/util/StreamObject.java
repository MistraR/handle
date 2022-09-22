/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * @deprecated Replaced by net.cnri.util.StreamObject
 */
@Deprecated
public interface StreamObject {
    public boolean isStreamTable();

    public boolean isStreamVector();

    public void readFrom(String str) throws StringEncodingException, IOException;

    public void readFrom(Reader str) throws StringEncodingException, IOException;

    public void readTheRest(Reader str) throws StringEncodingException, IOException;

    public String writeToString();

    public void writeTo(Writer out) throws IOException;
}

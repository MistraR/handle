/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.util;

import java.io.IOException;
import java.io.Writer;

/**
 * Class which wraps another {@code Writer} in order to write only ASCII.  Non-ASCII characters are JSON-encoded as &#x5C;u####.
 * Suggested for use with Gson as in
 * <pre>
 *     gson.toJson(json, new AsciiJsonWriter(new BufferedWriter(writer)));
 * </pre>
 */
public class AsciiJsonWriter extends Writer {
    private final Writer writer;

    public AsciiJsonWriter(Writer writer) {
        this.writer = writer;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        int start = off;
        for (int i = off; i < off + len; i++) {
            char ch = cbuf[i];
            if (cbuf[i] > 0x7F) {
                if (start < i) writer.write(cbuf, start, i - start);
                jsonEncode(ch);
                start = i + 1;
            }
        }
        if (start < off + len) writer.write(cbuf, start, off + len - start);
    }

    private void jsonEncode(char ch) throws IOException {
        writer.write("\\u");
        String s = Integer.toHexString(ch);
        for (int i = s.length(); i < 4; i++) {
            writer.write('0');
        }
        writer.write(s);
    }
}

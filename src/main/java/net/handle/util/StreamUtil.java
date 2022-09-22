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
 * @deprecated Replaced by net.cnri.util.StreamUtil
 */
@Deprecated
public abstract class StreamUtil {

    /**************************************************************
     * Read from the specified reader until a non-whitespace
     * character is read.  When a non-whitespace character is read,
     * return it.
     **************************************************************/
    public static char getNonWhitespace(Reader in) throws IOException {
        char ch;
        while (true) {
            ch = (char) in.read();
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == ',' || ch == ';' || ch == '\t') {
                continue;
            }
            return ch;
        }
    }

    /**************************************************************
     * This function reads in a string given that the string is
     * not delimited with a quote.  It will read in anything up to
     * but not including anything that might delimit a word.
     **************************************************************/
    public static String readUndelimitedString(Reader in, char firstChar) throws IOException {
        StringBuffer sb = new StringBuffer("" + firstChar);
        char ch;
        while (true) {
            ch = (char) in.read();
            if (ch == ' ' || ch == 10 || ch == 13 || ch == ',' || ch == ';' || ch == -1 || ch == '=' || ch == '\t') {
                return sb.toString();
            } else {
                sb.append(ch);
            }
        }
    }

    /***********************************************************************
     * This function reads in a string token assuming the first qoute (")
     * has been read already.
     ***********************************************************************/
    public static String readString(Reader in) throws StringEncodingException, IOException {
        StringBuffer results = new StringBuffer("");
        char ch;
        while (true) {

            ch = (char) in.read();
            if (ch == -1) {
                throw new StringEncodingException("Unexpected End of String");
            } else if (ch == '\\') {
                ch = (char) in.read();
                if (ch == 'n') {
                    results.append('\n');
                } else {
                    results.append(ch);
                }
            } else if (ch == '"') {
                return results.toString();
            } else {
                results.append(ch);
            }
        }
    }

    /*****************************************************************
     * Escape all of the "special" characters in the given string and
     * return the result.
     *****************************************************************/
    public static String XencodeString(String str) {
        //-- This probably could use some optimization.
        StringBuffer sb = new StringBuffer("\"");
        int i, n = str.length();
        char ch;
        for (i = 0; i < n; i++) {
            ch = str.charAt(i);
            if (ch == '"' || ch == '\\') sb.append('\\');
            sb.append(ch);
        }
        sb.append("\"");
        return sb.toString();
    }

    public static void writeEncodedString(Writer out, String str) throws IOException {
        int n = str.length();
        char ch;
        out.write('"');
        for (int i = 0; i < n; i++) {
            ch = str.charAt(i);
            if (ch == '"' || ch == '\\') out.write('\\');
            out.write(ch);
        }
        out.write("\"");
    }

    public static void XwriteString(String str, Writer out) throws IOException {
        int i, n = str.length();
        char ch;
        out.write('"');
        for (i = 0; i < n; i++) {
            ch = str.charAt(i);
            if (ch == '"') out.write('\\');
            out.write(ch);
        }
        out.write('"');
    }
}

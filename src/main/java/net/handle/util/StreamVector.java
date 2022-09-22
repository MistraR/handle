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
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Vector;

/**
 * @deprecated Replaced by net.cnri.util.StreamVector
 */
@Deprecated
@SuppressWarnings({"rawtypes", "unchecked"})
public class StreamVector extends Vector implements StreamObject {

    @Override
    public boolean isStreamTable() {
        return false;
    }

    @Override
    public boolean isStreamVector() {
        return true;
    }

    public char startingDelimiter() {
        return '(';
    }

    @Override
    public void readFrom(String str) throws StringEncodingException {
        Reader in = new StringReader(str);
        try {
            readFrom(in);
        } catch (IOException e) {
            throw new StringEncodingException("IO exception: " + e.toString());
        } finally {
            try {
                in.close();
            } catch (Exception e) {
            }
        }
    }

    public Object deepClone() {
        StreamVector newVector = new StreamVector();
        for (Enumeration e = elements(); e.hasMoreElements();) {
            Object item = e.nextElement();
            try {
                if (item instanceof DeepClone) {
                    item = ((DeepClone) item).deepClone();
                }
            } catch (Exception ex) {
                System.out.println("Exception cloning item in StreamVector: " + e);
            }
            newVector.addElement(item);
        }
        return newVector;
    }

    @Override
    public void readFrom(Reader str) throws StringEncodingException, IOException {
        //Read the whitespace.
        //Get first character.  If it is not a '{' throw an exception.
        //If it is, call readTheRest on it.
        char ch;
        while (true) {
            ch = StreamUtil.getNonWhitespace(str);
            if (ch == '(') {
                readTheRest(str);
            } else {
                throw new StringEncodingException("Expected '(', got '" + ch + "'");
            }
        }
    }

    @Override
    public void writeTo(Writer out) throws IOException {
        out.write("(\n");
        Object val;
        for (int i = 0; i < size(); i++) {
            val = elementAt(i);
            //-- Let's see if the object will respond to the encoding methods.
            //-- If so, we will tell it to encode itself, if not, we will
            //-- encode it as a String.
            if (val instanceof StreamObject) {
                ((StreamObject) val).writeTo(out);
            } else {
                StreamUtil.writeEncodedString(out, String.valueOf(val));
                out.write("\n");
            }
        }
        out.write(")\n");
    }

    @Override
    public String writeToString() {
        StringWriter out = new StringWriter();
        try {
            writeTo(out);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return out.toString();
    }

    @Override
    public void readTheRest(Reader str) throws StringEncodingException, IOException {
        char ch;
        removeAllElements();
        while (true) {
            // Let's read the value.

            Object obj;
            ch = StreamUtil.getNonWhitespace(str);
            switch (ch) {
            case '"':
                obj = StreamUtil.readString(str);
                break;
            case '{':
                StreamTable valTable = new StreamTable();
                valTable.readTheRest(str);
                obj = valTable;
                break;
            case '(':
                StreamVector vector = new StreamVector();
                vector.readTheRest(str);
                obj = vector;
                break;
            case ')':
                return;
            case (char) -1:
                throw new StringEncodingException("Unexpected end of input " + "while reading Vector.");
            default:
                obj = StreamUtil.readUndelimitedString(str, ch);
            }
            addElement(obj);
        }
    }
}

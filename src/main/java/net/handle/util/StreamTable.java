/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.util;

import java.io.*;
import java.util.*;

/**
 * @deprecated Replaced by net.cnri.util.StreamTable
 */
@Deprecated
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public class StreamTable extends Hashtable implements StreamObject, DeepClone {

    @Override
    public boolean isStreamTable() {
        return true;
    }

    @Override
    public boolean isStreamVector() {
        return false;
    }

    public char startingDelimiter() {
        return '{';
    }

    /************************************************************
     * copy a (reference to) all values in this table to the
     * specified table.
     ************************************************************/
    public void merge(Hashtable ht) {
        for (Enumeration e = ht.keys(); e.hasMoreElements();) {
            Object key = e.nextElement();
            put(key, ht.get(key));
        }
    }

    @Override
    public Object deepClone() {
        StreamTable newTable = new StreamTable();
        for (Enumeration e = keys(); e.hasMoreElements();) {
            Object key = e.nextElement();
            Object value = get(key);
            try {
                if (key instanceof DeepClone) {
                    key = ((DeepClone) key).deepClone();
                }
            } catch (Exception ex) {
                System.out.println("Exception cloning key in StreamTable: " + ex);
            }
            try {
                if (value instanceof DeepClone) {
                    value = ((DeepClone) value).deepClone();
                }
            } catch (Exception ex) {
                System.out.println("Exception cloning value in StreamTable: " + ex);
            }
            newTable.put(key, value);
        }
        return newTable;
    }

    public Object get(Object key, Object defaultVal) {
        Object o = super.get(key);
        if (o == null) return defaultVal;
        return o;
    }

    public String getStr(Object key, String defaultVal) {
        Object o = super.get(key);
        if (o == null) return defaultVal;
        return String.valueOf(o);
    }

    public String getStr(Object key) {
        return getStr(key, null);
    }

    public boolean getBoolean(Object key, boolean defaultVal) {
        String val = getStr(key, defaultVal ? "yes" : "no").toLowerCase();
        if (val.startsWith("y") || val.startsWith("t")) {
            return true;
        } else if (val.startsWith("n") || val.startsWith("f")) {
            return false;
        } else {
            return defaultVal;
        }
    }

    public boolean getBoolean(Object key) {
        return getBoolean(key, false);
    }

    public long getLong(Object key, long defaultVal) {
        Object val = get(key);
        if (val == null) return defaultVal;
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Exception e) {
            System.err.println("Invalid long value: " + val);
        }
        return defaultVal;
    }

    public int getInt(Object key, int defaultVal) {
        Object val = get(key);
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            System.err.println("Invalid int value: " + val);
        }
        return defaultVal;
    }

    public void readFrom(InputStream in) throws StringEncodingException, IOException {
        readFrom(new InputStreamReader(in, "UTF-8"));
    }

    @Override
    public void readFrom(String str) throws StringEncodingException {
        Reader in = new StringReader(str);
        try {
            readFrom(in);
        } catch (IOException e) {
            throw new StringEncodingException("IO exception: " + e.toString());
        }
    }

    @Override
    public void readFrom(Reader str) throws StringEncodingException, IOException {
        //Read the whitespace.
        //Get first character.  If it is not a '{' throw and exception.
        //If it is, call readTheRest on it.
        char ch = StreamUtil.getNonWhitespace(str);
        if (ch == '{') {
            readTheRest(str);
        } else {
            throw new StringEncodingException("Expected {, got " + ch);
        }
    }

    public void readFromFile(File file) throws StringEncodingException, java.io.IOException {
        Reader in = new InputStreamReader(new FileInputStream(file), "UTF-8");
        readFrom(in);
        in.close();
    }

    /** Returns all of the keys to the hashtable that are java.lang.String objects. */
    public String[] getStringKeys() {
        Vector stringKeyVect = new Vector();
        for (Enumeration e = keys(); e.hasMoreElements();) {
            Object obj = e.nextElement();
            if (obj instanceof String) stringKeyVect.addElement(obj);
        }
        String stringKeys[] = new String[stringKeyVect.size()];
        stringKeyVect.copyInto(stringKeys);
        return stringKeys;
    }

    public void readFromFile(String fileName) throws StringEncodingException, java.io.IOException {
        readFromFile(new File(fileName));
    }

    public void writeToFile(String fileName) throws StringEncodingException, java.io.IOException {
        writeToFile(new File(fileName));
    }

    public void writeToFile(File file) throws StringEncodingException, java.io.IOException {
        Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        writeTo(out);
        out.close();
    }

    @Override
    public void readTheRest(Reader str) throws StringEncodingException, java.io.IOException {
        char ch;
        clear();
        String key;
        while (true) {
            ch = StreamUtil.getNonWhitespace(str);
            if (ch == '"') {
                key = StreamUtil.readString(str);
            } else if (ch == '}') {
                return;
            } else if (ch == -1) {
                throw new StringEncodingException("Unexpected end of input in " + "StreamTable.");
            } else {
                key = StreamUtil.readUndelimitedString(str, ch);
            }

            ch = StreamUtil.getNonWhitespace(str);
            if (ch != '=') {
                throw new StringEncodingException("Expected \"=\" ");
            }

            Object obj;
            ch = StreamUtil.getNonWhitespace(str);
            if (ch == '"') {
                obj = StreamUtil.readString(str);
            } else if (ch == '{') {
                StreamTable valTable = new StreamTable();
                valTable.readTheRest(str);
                obj = valTable;
            } else if (ch == '(') {
                StreamVector vector = new StreamVector();
                vector.readTheRest(str);
                obj = vector;
            } else if (ch == -1) {
                throw new StringEncodingException("Unexpected end of input: " + "Expected value for key: '" + key + "'");
            } else {
                obj = StreamUtil.readUndelimitedString(str, ch);
            }
            put(key, obj);
        }
    }

    public void put(String key, boolean boolVal) {
        put(key, boolVal ? "yes" : "no");
    }

    public void put(String key, int intVal) {
        put(key, String.valueOf(intVal));
    }

    public void put(String key, long longVal) {
        put(key, String.valueOf(longVal));
    }

    @Override
    public synchronized String toString() {
        return writeToString();
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
    public void writeTo(Writer out) throws java.io.IOException {
        out.write("{\n");
        for (Enumeration en = this.keys(); en.hasMoreElements();) {
            Object key = en.nextElement();
            //Output the key as a string.
            StreamUtil.writeEncodedString(out, String.valueOf(key));
            out.write(" = ");

            Object val = get(key);
            //-- Let's see if the object will respond to the encoding methods.
            //-- If so, we will tell it to encode itself, if not, we will
            //-- encode it as a String.
            if (val instanceof StreamObject) ((StreamObject) val).writeTo(out);
            else StreamUtil.writeEncodedString(out, String.valueOf(val));
            out.write("\n");
        }

        out.write("}\n");
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;

import net.handle.hdllib.Util;

public class ResourceRecord {
    /*
     * 1 A 4-bytes
     * 2 NS DomainName *A
     * 3 MD DomainName *A
     * 4 MF DomainName *A
     * 5 CNAME DomainName
     * 6 SOA DomainName DomainName 4-bytes 4-bytes 4-bytes 4-bytes 4-bytes
     * 7 MB DomainName *A
     * 8 MG DomainName
     * 9 MR DomainName
     * 10 NULL opaque
     * 11 WKS opaque (to us)
     * 12 PTR DomainName
     * 13 HINFO string string
     * 14 MINFO DomainName DomainName
     * 15 MX 2-bytes DomainName *A
     * 16 TXT any number of strings
     *
     * --- only compress in above ---
     *
     * 17 RP DomainName DomainName *optional TXT for second, but not if .
     * 18 AFSDB 2-bytes DomainName *A
     * 19 X25 string
     * 20 ISDN one or two strings
     * 21 RT 2-bytes DomainName *A, X25, ISDN
     * 24 SIG 18-bytes DomainName more-bytes
     * 26 PX 2-bytes DomainName DomainName
     * 28 AAAA 16-bytes
     * 30 NXT DomainName more-bytes
     * 33 SRV 2-bytes 2-bytes 2-bytes DomainName *A
     * 35 NAPTR 2-bytes 2-bytes string string string DomainName *if flags has S then SRV, and their A; if A then A; seems best to ignore
     *
     * --- decompress in above ---
     *
     * 36 KX 2-bytes DomainName *A
     * 38 A6 1-byte (128 minus first byte bits)-bytes DomainName *A6 & NS
     * 39 DNAME DomainName *NS
     *
     * --- note domain names in above ---
     */

    public static final int TYPE_A = 1;
    public static final int TYPE_NS = 2;
    public static final int TYPE_MD = 3;
    public static final int TYPE_MF = 4;
    public static final int TYPE_CNAME = 5;
    public static final int TYPE_SOA = 6;
    public static final int TYPE_MB = 7;
    public static final int TYPE_MG = 8;
    public static final int TYPE_MR = 9;
    public static final int TYPE_NULL = 10;
    public static final int TYPE_WKS = 11;
    public static final int TYPE_PTR = 12;
    public static final int TYPE_HINFO = 13;
    public static final int TYPE_MINFO = 14;
    public static final int TYPE_MX = 15;
    public static final int TYPE_TXT = 16;
    public static final int TYPE_RP = 17;
    public static final int TYPE_AFSDB = 18;
    public static final int TYPE_X25 = 19;
    public static final int TYPE_ISDN = 20;
    public static final int TYPE_RT = 21;
    public static final int TYPE_SIG = 24;
    public static final int TYPE_PX = 26;
    public static final int TYPE_AAAA = 28;
    public static final int TYPE_NXT = 30;
    public static final int TYPE_SRV = 33;
    public static final int TYPE_NAPTR = 35;
    public static final int TYPE_KX = 36;
    public static final int TYPE_SPF = 99;

    public static final int CLASS_IN = 1;

    /* Resource Data is divided into tokens, each coded as an integer.  Positive is a number of bytes, presented for 2/4 as unsigned integer, otherwise byte[].*/
    private static final int DATA_TOKEN_OPAQUE = 0;
    private static final int DATA_TOKEN_STRING = -1;
    private static final int DATA_TOKEN_MULTIPLE_STRINGS = -2;
    private static final int DATA_TOKEN_DOMAIN_NAME = -3;

    private static final int[] DATA_DOMAIN_NAME = new int[] { DATA_TOKEN_DOMAIN_NAME };
    private static final int[] DATA_MX = new int[] { 2, DATA_TOKEN_DOMAIN_NAME };
    private static final int[] DATA_RP = new int[] { DATA_TOKEN_DOMAIN_NAME, DATA_TOKEN_DOMAIN_NAME };
    private static final int[] DATA_SOA = new int[] { DATA_TOKEN_DOMAIN_NAME, DATA_TOKEN_DOMAIN_NAME, 4, 4, 4, 4, 4 };
    private static final int[] DATA_TXT = new int[] { DATA_TOKEN_MULTIPLE_STRINGS };
    private static final int[] DATA_OPAQUE = new int[] { DATA_TOKEN_OPAQUE };
    private static final int[] DATA_SIG = new int[] { 18, DATA_TOKEN_DOMAIN_NAME, DATA_TOKEN_OPAQUE };
    private static final int[] DATA_PX = new int[] { 2, DATA_TOKEN_DOMAIN_NAME, DATA_TOKEN_DOMAIN_NAME };
    private static final int[] DATA_NXT = new int[] { DATA_TOKEN_DOMAIN_NAME, DATA_TOKEN_OPAQUE };
    private static final int[] DATA_SRV = new int[] { 2, 2, 2, DATA_TOKEN_DOMAIN_NAME };
    private static final int[] DATA_NAPTR = new int[] { 2, 2, DATA_TOKEN_STRING, DATA_TOKEN_STRING, DATA_TOKEN_STRING, DATA_TOKEN_DOMAIN_NAME };

    private int[] expectedDataTokenTypes() {
        switch (type) {
        case TYPE_NS:
        case TYPE_MD:
        case TYPE_MF:
        case TYPE_CNAME:
        case TYPE_MB:
        case TYPE_MG:
        case TYPE_MR:
        case TYPE_PTR:
            return DATA_DOMAIN_NAME;
        case TYPE_SOA:
            return DATA_SOA;
        case TYPE_HINFO:
        case TYPE_TXT:
        case TYPE_X25:
        case TYPE_ISDN:
        case TYPE_SPF:
            return DATA_TXT;
        case TYPE_MINFO:
        case TYPE_RP:
            return DATA_RP;
        case TYPE_MX:
        case TYPE_AFSDB:
        case TYPE_RT:
            return DATA_MX;
        case TYPE_SIG:
            return DATA_SIG;
        case TYPE_PX:
            return DATA_PX;
        case TYPE_NXT:
            return DATA_NXT;
        case TYPE_SRV:
            return DATA_SRV;
        case TYPE_NAPTR:
            return DATA_NAPTR;
        default:
            return DATA_OPAQUE;
        }
    }

    private final DomainName owner;
    private final int type;
    private final int klass;
    private int ttl;
    private Object[] dataTokens;
    private final int[] dataTokenTypes;

    public ResourceRecord(DomainName owner, int type, int klass, int ttl, String data) throws ParseException {
        this.owner = owner;
        this.type = type;
        this.klass = klass;
        this.ttl = ttl;
        this.dataTokenTypes = expectedDataTokenTypes();
        parseMasterFileData(data);
    }

    public ResourceRecord(DomainName owner, int type, int klass, int ttl, byte[] data, int offset, int length) throws ParseException {
        this.owner = owner;
        this.type = type;
        this.klass = klass;
        this.ttl = ttl;
        this.dataTokenTypes = expectedDataTokenTypes();
        parseWireData(data, offset, length);
    }

    public ResourceRecord(DomainName owner, int ttl, ResourceRecord rr) {
        this.owner = owner;
        this.type = rr.type;
        this.klass = rr.klass;
        this.ttl = ttl;
        this.dataTokenTypes = rr.dataTokenTypes;
        this.dataTokens = rr.dataTokens;
    }

    public DomainName getOwner() {
        return owner;
    }

    public int getType() {
        return type;
    }

    public int getKlass() {
        return klass;
    }

    public int getTTL() {
        return ttl;
    }

    /** We allow setting the TTL when fixing the TTLs of an RRSet */
    void setTTL(int ttl) {
        this.ttl = ttl;
    }

    private static String[] tokenizeMasterFileFormatData(String data) {
        ArrayList<String> res = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean whitespace = true; // currently parsing whitespace
        boolean comment = false; // currently parsing a ; comment
        boolean quote = false; // currently in quotes ""
        boolean slash = false; // currently right after a slash
        boolean output = false; // are we ready to output a token?
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            boolean isNewline = ch == '\n';

            // comments continue until newline
            if (comment && !isNewline) continue;

            // now not in comment, treat newline as whitespace
            comment = false;
            if (isNewline) ch = ' ';
            // parens, used in master files to indicate a multi-line data, are whitespace to us
            if (!quote && !slash && !comment && (ch == '(' || ch == ')')) ch = ' ';
            // skipping over whitespace
            if (whitespace && ch == ' ') continue;

            if (slash) {
                buf.append('\\'); //remove slashes later, we'll need them
                buf.append(ch);
                slash = false;
                output = isNewline;
            } else if (quote) {
                buf.append(ch);
                slash = ch == '\\';
                quote = ch != '"';
                output = isNewline;
            } else {
                if (ch == ' ') {
                    output = true;
                } else if (ch == ';') {
                    if (!whitespace) {
                        output = true;
                    }
                    comment = true;
                    whitespace = true;
                } else {
                    whitespace = false;
                    buf.append(ch);
                    slash = ch == '\\';
                    quote = ch == '"';
                }
            }

            if (output) {
                res.add(buf.toString());
                buf = new StringBuilder();
                whitespace = true;
                quote = false;
                slash = false;
                output = false;
            }
        }
        if (!whitespace) {
            res.add(buf.toString());
        }
        return res.toArray(new String[res.size()]);
    }

    /** Parse a DNS character string (e.g. label); translate \. and \123 etc. */
    public static byte[] parseMasterFileDataString(String s) throws ParseException {
        byte[] label = Util.encodeString(s);
        int next = 0;
        int i = 0;
        while (i < label.length) {
            if (label[i] == '\\') {
                if (i + 1 >= label.length) throw new ParseException("Error parsing " + s);
                if (label[i + 1] >= '0' && label[i + 1] <= '9') {
                    if (i + 3 < label.length && label[i + 2] >= '0' && label[i + 2] <= '9' && label[i + 3] >= '0' && label[i + 3] <= '9') {
                        int replacement = (label[i + 1] - '0') * 100 + (label[i + 2] - '0') * 10 + (label[i + 3] - '0');
                        label[next] = (byte) replacement;
                        next++;
                        i += 4;
                        continue;
                    } else {
                        throw new ParseException("Error parsing " + s);
                    }
                }
                label[next] = label[i + 1];
                next++;
                i += 2;
                continue;
            }
            next++;
            i++;
        }
        if (next < label.length) {
            byte[] oldLabel = label;
            label = new byte[next];
            System.arraycopy(oldLabel, 0, label, 0, next);
        }
        return label;
    }

    private void parseMasterFileData(String data) throws ParseException {
        String[] tokens = tokenizeMasterFileFormatData(data);
        if (tokens.length >= 2 && "\\#".equals(tokens[0])) {
            try {
                @SuppressWarnings("unused")
                int rdlen = Integer.parseInt(tokens[1]);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected length after \\#");
            }
            StringBuilder hex = new StringBuilder();
            for (int i = 2; i < tokens.length; i++) {
                hex.append(tokens[i]);
            }
            // currently not validating rdlen
            parseWireData(Util.encodeHexString(hex.toString()));
            return;
        }

        if (tokens.length == 1 && (type == TYPE_A || type == TYPE_AAAA)) {
            dataTokens = new byte[1][];
            try {
                dataTokens[0] = InetAddress.getByName(tokens[0]).getAddress();
            } catch (Exception e) {
                e.printStackTrace();
                throw new ParseException("Couldn't parse this address: " + tokens[0]);
            }
            return;
        }

        ArrayList<Object> parsedTokens = new ArrayList<>(tokens.length);
        int typei = 0;
        for (String token : tokens) {
            if (typei >= dataTokenTypes.length) throw new ParseException("Couldn't parse " + data + " for type " + type);
            int thisType = dataTokenTypes[typei];
            try {
                switch (thisType) {
                case 4:
                    parsedTokens.add((int) Long.parseLong(token));
                    break;
                case 2:
                    parsedTokens.add(Integer.parseInt(token));
                    break;
                case DATA_TOKEN_STRING:
                case DATA_TOKEN_MULTIPLE_STRINGS:
                    byte[] s = parseMasterFileDataString(token);
                    if (thisType == DATA_TOKEN_STRING && s.length > 255) throw new ParseException("string too long");
                    int offset = 0;
                    while (s.length - offset > 255) {
                        byte[] firstPart = new byte[255];
                        System.arraycopy(s, offset, firstPart, 0, 255);
                        parsedTokens.add(firstPart);
                        offset += 255;
                    }
                    if (offset > 0) {
                        byte[] lastPart = new byte[s.length - offset];
                        System.arraycopy(s, offset, lastPart, 0, s.length - offset);
                        s = lastPart;
                    }
                    parsedTokens.add(s);
                    break;
                case DATA_TOKEN_DOMAIN_NAME:
                    parsedTokens.add(DomainName.ofString(token));
                    break;
                default:
                    throw new ParseException("Can't parse this type " + type);
                }
            } catch (NumberFormatException e) {
                throw new ParseException("Expected number in " + token);
            }
            if (thisType != DATA_TOKEN_MULTIPLE_STRINGS) typei++;
        }
        if (typei < dataTokenTypes.length && dataTokenTypes[typei] != DATA_TOKEN_MULTIPLE_STRINGS) throw new ParseException("data incomplete");

        dataTokens = parsedTokens.toArray();
    }

    private void parseWireData(byte[] data) throws ParseException {
        parseWireData(data, 0, data.length);
    }

    private void parseWireData(byte[] data, int offset, int length) throws ParseException {
        if (dataTokenTypes == DATA_OPAQUE) {
            dataTokens = new byte[1][];
            dataTokens[0] = new byte[length];
            try {
                System.arraycopy(data, offset, dataTokens[0], 0, length);
            } catch (Exception e) {
                throw new ParseException("Exception parsing opaque resource record data: " + e);
            }
            return;
        }
        ArrayList<Object> parsedTokens = new ArrayList<>(dataTokenTypes.length);
        int typei = 0;
        int end = offset + length;
        try {
            while (true) {
                int thisType = dataTokenTypes[typei];
                switch (thisType) {
                case 4:
                    int val = ((data[offset++] & 0xFF) << 24) | ((data[offset++] & 0xFF) << 16) | ((data[offset++] & 0xFF) << 8) | (data[offset++] & 0xFF);
                    parsedTokens.add(val);
                    break;
                case 2:
                    val = ((data[offset++] & 0xFF) << 8) | (data[offset++] & 0xFF);
                    parsedTokens.add(val);
                    break;
                case DATA_TOKEN_STRING:
                case DATA_TOKEN_MULTIPLE_STRINGS:
                    byte len = data[offset++];
                    byte[] token = new byte[len];
                    System.arraycopy(data, offset, token, 0, len);
                    offset += len;
                    parsedTokens.add(token);
                    break;
                case DATA_TOKEN_DOMAIN_NAME:
                    int[] offsetArr = new int[] { offset };
                    parsedTokens.add(DomainName.parseWire(data, offsetArr));
                    offset = offsetArr[0];
                    break;
                case DATA_TOKEN_OPAQUE:
                    token = new byte[end - offset];
                    System.arraycopy(data, offset, token, 0, end - offset);
                    offset = end;
                    parsedTokens.add(token);
                    break;
                default: // must be positive; fixed-length opaque data
                    token = new byte[thisType];
                    System.arraycopy(data, offset, token, 0, thisType);
                    offset += thisType;
                    parsedTokens.add(token);
                    break;
                }
                if (thisType != DATA_TOKEN_MULTIPLE_STRINGS) typei++;
                if (offset >= end) break;
                if (typei >= dataTokenTypes.length) throw new ParseException("too much data");
            }
        } catch (IndexOutOfBoundsException e) {
            throw new ParseException("IndexOutOfBoundsException parsing resource data");
        }
        if (typei < dataTokenTypes.length && dataTokenTypes[typei] != DATA_TOKEN_MULTIPLE_STRINGS) throw new ParseException("expected more data");
        dataTokens = parsedTokens.toArray();
    }

    public static ResourceRecord parseWire(byte[] wire, int[] offsetArr) throws ParseException {
        DomainName owner = DomainName.parseWire(wire, offsetArr);
        int offset = offsetArr[0];
        if (offset + 10 > wire.length) throw new ParseException("IndexOutOfBounds parsing resource record");
        int type = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int klass = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int ttl = ((wire[offset++] & 0xFF) << 24) | ((wire[offset++] & 0xFF) << 16) | ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int rdlen = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        if (offset + rdlen > wire.length) throw new ParseException("IndexOutOfBounds parsing resource record");
        ResourceRecord rr = new ResourceRecord(owner, type, klass, ttl, wire, offset, rdlen);
        offset += rdlen;
        offsetArr[0] = offset;
        return rr;
    }

    public int appendToWireWithCompression(OutputStream wire, int offset, Map<DomainName, Integer> compressionTable) throws IOException {
        offset = owner.appendToWireWithCompression(wire, offset, compressionTable);
        wire.write((type >> 8) & 0xFF);
        wire.write(type & 0xFF);
        wire.write((klass >> 8) & 0xFF);
        wire.write(klass & 0xFF);
        wire.write((ttl >> 24) & 0xFF);
        wire.write((ttl >> 16) & 0xFF);
        wire.write((ttl >> 8) & 0xFF);
        wire.write(ttl & 0xFF);
        offset += 8;
        int rdlenOffset = offset;
        offset += 2;
        ByteArrayOutputStream dataOut = new ByteArrayOutputStream();
        int typei = 0;
        for (Object dataToken : dataTokens) {
            int thisType = dataTokenTypes[typei];
            switch (thisType) {
            case 4:
                int i = (Integer) dataToken;
                dataOut.write((i >> 24) & 0xFF);
                dataOut.write((i >> 16) & 0xFF);
                dataOut.write((i >> 8) & 0xFF);
                dataOut.write(i & 0xFF);
                offset += 4;
                break;
            case 2:
                i = (Integer) dataToken;
                dataOut.write((i >> 8) & 0xFF);
                dataOut.write(i & 0xFF);
                offset += 2;
                break;
            case DATA_TOKEN_STRING:
            case DATA_TOKEN_MULTIPLE_STRINGS:
                byte[] s = (byte[]) dataToken;
                dataOut.write(s.length);
                offset++;
                dataOut.write(s);
                offset += s.length;
                break;
            case DATA_TOKEN_DOMAIN_NAME:
                offset = ((DomainName) dataToken).appendToWireWithCompression(dataOut, offset, compressionTable, this.requiresCompression());
                break;
            case DATA_TOKEN_OPAQUE:
            default:
                s = (byte[]) dataToken;
                dataOut.write(s);
                offset += s.length;
                break;
            }
            if (thisType != DATA_TOKEN_MULTIPLE_STRINGS) typei++;
        }
        int rdlen = offset - rdlenOffset - 2;
        wire.write((rdlen >> 8) & 0xFF);
        wire.write(rdlen & 0xFF);
        wire.write(dataOut.toByteArray());
        return offset;
    }

    private Question _question;

    public Question getKey() {
        if (_question == null) {
            _question = new Question(owner, type, klass);
        }
        return _question;
    }

    public DomainName nameFromData() {
        for (int i = 0; i < dataTokenTypes.length; i++) {
            if (dataTokenTypes[i] == DATA_TOKEN_DOMAIN_NAME) return (DomainName) dataTokens[i];
        }
        return null;
    }

    public InetAddress addressFromData() {
        if (type == TYPE_A || type == TYPE_AAAA) {
            try {
                return InetAddress.getByAddress((byte[]) dataTokens[0]);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public int minimumFromSOAData() {
        if (type == TYPE_SOA && dataTokens.length > 7 && (dataTokens[6] instanceof Integer)) {
            return ((Integer) dataTokens[6]).intValue();
        } else {
            return 0;
        }
    }

    /* for debugging */
    void writeData(OutputStream dataOut) throws IOException {
        int typei = 0;
        for (Object dataToken : dataTokens) {
            int thisType = dataTokenTypes[typei];
            switch (thisType) {
            case 4:
                int i = (Integer) dataToken;
                dataOut.write((i >> 24) & 0xFF);
                dataOut.write((i >> 16) & 0xFF);
                dataOut.write((i >> 8) & 0xFF);
                dataOut.write(i & 0xFF);
                break;
            case 2:
                i = (Integer) dataToken;
                dataOut.write((i >> 8) & 0xFF);
                dataOut.write(i & 0xFF);
                break;
            case DATA_TOKEN_STRING:
            case DATA_TOKEN_MULTIPLE_STRINGS:
                byte[] s = (byte[]) dataToken;
                dataOut.write(s.length);
                dataOut.write(s);
                break;
            case DATA_TOKEN_DOMAIN_NAME:
                ((DomainName) dataToken).appendToWireWithCompression(dataOut, 0, null);
                break;
            case DATA_TOKEN_OPAQUE:
            default:
                s = (byte[]) dataToken;
                dataOut.write(s);
                break;
            }
            if (thisType != DATA_TOKEN_MULTIPLE_STRINGS) typei++;
        }
    }

    public boolean requiresDecompression() {
        return type < TYPE_KX;
    }

    public boolean requiresCompression() {
        return type <= TYPE_TXT;
    }

    private final Question[] noQuestions = new Question[0];

    public Question[] additionalSectionProcessing() {
        switch (type) {
        case TYPE_NS:
        case TYPE_MD:
        case TYPE_MF:
        case TYPE_MB:
        case TYPE_MX:
        case TYPE_AFSDB:
        case TYPE_SRV:
        case TYPE_KX:
            for (int pos = 0; pos < dataTokenTypes.length; pos++) {
                if (dataTokenTypes[pos] == DATA_TOKEN_DOMAIN_NAME) {
                    DomainName name = (DomainName) dataTokens[pos];
                    return new Question[] { new Question(name, TYPE_A, klass), new Question(name, TYPE_AAAA, klass) };
                }
            }
            return noQuestions;
        case TYPE_RT:
            DomainName name = (DomainName) dataTokens[1];
            return new Question[] { new Question(name, TYPE_A, klass), new Question(name, TYPE_AAAA, klass), new Question(name, TYPE_X25, klass), new Question(name, TYPE_ISDN, klass) };
        case TYPE_RP:
            name = (DomainName) dataTokens[1];
            if (name != DomainName.ROOT) return new Question[] { new Question(name, TYPE_TXT, klass) };
            else return noQuestions;
        default:
            return noQuestions;
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.io.IOException;
import java.io.OutputStream;
import java.net.IDN;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import net.handle.hdllib.Util;

public class DomainName {
    public static final String HEX_ENCODING_PREFIX = "hx--";

    public static final DomainName ROOT = new DomainName(new byte[0][]);

    private final byte[][] labels;

    public DomainName(byte[][] labels) {
        this.labels = labels;
    }

    /** Return a byte array of labels */
    public byte[][] getLabels() {
        if (labels == null) return null;
        if (labels.length == 0) return ROOT.labels;
        byte[][] res = new byte[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            res[i] = new byte[labels[i].length];
            System.arraycopy(labels[i], 0, res[i], 0, labels[i].length);
        }
        return res;
    }

    /** Returns an ASCII string as would appear in a master file. */
    @Override
    public String toString() {
        if (labels.length == 0) return ".";
        StringBuilder sb = new StringBuilder();
        for (byte[] label : labels) {
            for (byte b : label) {
                if (b <= 32 || b >= 127) {
                    sb.append('\\');
                    sb.append(b < 0 ? b + 128 : b);
                } else {
                    if (b == '"' || b == '#' || b == '\'' || b == '(' || b == ')' || b == '.' || b == ';' || b == '@' || b == '\\') {
                        sb.append('\\');
                    }
                    sb.append((char) b);
                }
            }
            sb.append('.');
        }
        return sb.toString();
    }

    /** Parses an UTF8 string as would appear in a IDN-capable master file;
     * it does NOT create hx-- labels */
    public static DomainName ofString(String s) throws ParseException {
        if (s == null) throw new NullPointerException();
        if ("".equals(s) || ".".equals(s)) return ROOT;

        boolean potentialIDNA = true;

        ArrayList<String> ss = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                ss.add(s.substring(start, i));
                start = i + 1;
            } else if (s.charAt(i) == '\\') {
                i++;
                if (i >= s.length()) throw new ParseException("Domain name " + s + " ends with slash");
                if (s.charAt(i) >= '0' && s.charAt(i) <= '9') potentialIDNA = false;
            }
        }
        if (start < s.length()) ss.add(s.substring(start));

        byte[][] newLabels = new byte[ss.size()][];
        for (int j = 0; j < ss.size(); j++) {
            newLabels[j] = ResourceRecord.parseMasterFileDataString(ss.get(j));

            try {
                if (potentialIDNA) newLabels[j] = Util.encodeString(IDN.toASCII(Util.decodeString(newLabels[j]), IDN.ALLOW_UNASSIGNED));
            } catch (Exception e) {
                throw new ParseException(e.toString());
            }
            if (newLabels[j].length > 63) throw new ParseException("label too long");
        }
        return new DomainName(newLabels);
    }

    public static final boolean isHexByte(byte ch1, byte ch2) {
        return ((ch1 >= '0' && ch1 <= '9') || (ch1 >= 'a' && ch1 <= 'z') || (ch1 >= 'A' && ch1 <= 'Z')) && ((ch2 >= '0' && ch2 <= '9') || (ch2 >= 'a' && ch2 <= 'z') || (ch2 >= 'A' && ch2 <= 'Z'));
    }

    public static final byte decodeHexByte(byte ch1, byte ch2) {
        char newch1 = (char) ((ch1 >= '0' && ch1 <= '9') ? ch1 - '0' : ((ch1 >= 'a' && ch1 <= 'z') ? ch1 - 'a' + 10 : ch1 - 'A' + 10));
        char newch2 = (char) ((ch2 >= '0' && ch2 <= '9') ? ch2 - '0' : ((ch2 >= 'a' && ch2 <= 'z') ? ch2 - 'a' + 10 : ch2 - 'A' + 10));
        return (byte) (newch1 << 4 | newch2);
    }

    /** Converts to handle, using IDNA (xn--), as well as hx-- encoding (which is like percent encoding only it uses hyphen).
     * Order is reversed and '/' is used as separator in place of '.' */
    public byte[] toHandle(String handlePrefix, int lengthOfAssumedDomain) throws ParseException {
        if (labels.length < lengthOfAssumedDomain) throw new IllegalArgumentException();
        if (labels.length == lengthOfAssumedDomain) return Util.encodeString(handlePrefix);
        StringBuilder sb = new StringBuilder(handlePrefix);
        for (int i = labels.length - lengthOfAssumedDomain - 1; i >= 0; i--) {
            sb.append('/');
            String labelString = Util.decodeString(labels[i]);
            String converted;
            try {
                converted = IDN.toUnicode(labelString, IDN.ALLOW_UNASSIGNED);
            } catch (Exception e) {
                throw new ParseException(e.toString());
            }
            if (converted.length() > 4 && HEX_ENCODING_PREFIX.equals(converted.substring(0, 4))) {
                byte[] bytes = Util.encodeString(converted.toString());
                byte[] newBytes = new byte[bytes.length - 4];
                int size = 0;
                for (int j = 4; j < bytes.length; j++) {
                    if (bytes[j] != '-') newBytes[size] = bytes[j];
                    else {
                        if (j + 2 >= bytes.length || !isHexByte(bytes[j + 1], bytes[j + 2])) throw new ParseException("Error in hx-- parsing: " + converted);
                        newBytes[size] = decodeHexByte(bytes[j + 1], bytes[j + 2]);
                        j += 2;
                    }
                    size += 1;
                }
                byte[] newLabel = new byte[size];
                System.arraycopy(newBytes, 0, newLabel, 0, size);
                sb.append(Util.decodeString(newLabel));
            } else {
                sb.append(converted);
            }
        }
        return Util.encodeString(sb.toString());
    }

    private byte[][] _casefoldedLabels = null;

    private byte[][] getCasefoldedLabels() {
        if (_casefoldedLabels != null) return _casefoldedLabels;
        _casefoldedLabels = new byte[labels.length][];
        for (int i = 0; i < labels.length; i++) {
            _casefoldedLabels[i] = new byte[labels[i].length];
            for (int j = 0; j < labels[i].length; j++) {
                if (labels[i][j] >= 'A' && labels[i][j] <= 'Z') _casefoldedLabels[i][j] = (byte) (labels[i][j] - 'A' + 'a');
                else _casefoldedLabels[i][j] = labels[i][j];
            }
        }
        return _casefoldedLabels;
    }

    private DomainName _parent;

    public DomainName parent() {
        if (labels.length == 0) return null;
        if (labels.length == 1) return ROOT;
        if (_parent != null) return _parent;
        byte[][] res = new byte[labels.length - 1][];
        System.arraycopy(labels, 1, res, 0, labels.length - 1);
        DomainName dn = new DomainName(res);
        if (_casefoldedLabels != null) {
            dn._casefoldedLabels = new byte[labels.length - 1][];
            System.arraycopy(_casefoldedLabels, 1, dn._casefoldedLabels, 0, labels.length - 1);
        }
        _parent = dn;
        return dn;
    }

    /** Assumes name is already an ancestor of this; returns the child of name that is also an ancestor of this */
    public DomainName ancestorChildOf(DomainName name) {
        if (this.labels.length + 1 > name.labels.length) return null;
        if (this.labels.length + 1 == name.labels.length) return name;
        byte[][] newlabels = new byte[labels.length + 1][];
        System.arraycopy(labels, 0, newlabels, 1, labels.length);
        newlabels[0] = name.labels[name.labels.length - this.labels.length - 1];
        return new DomainName(newlabels);
    }

    public boolean descendsFrom(DomainName ancestor) {
        if (this.labels.length < ancestor.labels.length) return false;
        for (int i = 0; i < ancestor.labels.length; i++) {
            if (!Util.equals(getCasefoldedLabels()[i + this.labels.length - ancestor.labels.length], ancestor.getCasefoldedLabels()[i])) return false;
        }
        return true;
    }

    private static final byte[] ASTERISK_LABEL = new byte[] { '*' };

    /* returns the sibling of this which has an asterisk at head */
    public DomainName asteriskLabelSibling() {
        if (labels.length == 0) return null;
        byte[][] newlabels = new byte[labels.length][];
        System.arraycopy(labels, 1, newlabels, 1, labels.length - 1);
        newlabels[0] = ASTERISK_LABEL;
        return new DomainName(newlabels);
    }

    public int length() {
        return labels.length;
    }

    /** Returns a DomainName and changes the offset stored in offsetArr[0] */
    public static DomainName parseWire(byte[] wire, int[] offsetArr) throws ParseException {
        ArrayList<byte[]> labels = new ArrayList<>();
        offsetArr[0] = parseWire(wire, offsetArr[0], labels, 0, false);
        if (labels.size() == 0) return ROOT;
        else return new DomainName(labels.toArray(new byte[labels.size()][]));
    }

    private static int parseWire(byte[] wire, int offset, ArrayList<byte[]> labels, int totallen, boolean lastWasPointer) throws ParseException {
        if (totallen > 255) throw new ParseException("DomainName too long; perhaps a pointer loop?");
        try {
            while (true) {
                if ((wire[offset] & 0xC0) != 0) {
                    if ((wire[offset] & 0xC0) != 0xC0) throw new ParseException("Unexpected label type");
                    int pointer = ((wire[offset++] & 0x3F) << 8) | (wire[offset++] & 0xFF);
                    // two pointers in a row is silly; disallow it (well... at least require that they each point backwards)
                    if (lastWasPointer && pointer >= (offset - 2)) throw new ParseException("Possible pointer loop?");
                    parseWire(wire, pointer, labels, totallen, true);
                    return offset;
                }
                byte len = wire[offset++];
                if (len == 0) break;
                byte[] newlabel = new byte[len];
                System.arraycopy(wire, offset, newlabel, 0, len);
                labels.add(newlabel);
                offset += len;
                totallen += len + 1;
                lastWasPointer = false;
            }

            return offset;
        } catch (IndexOutOfBoundsException e) {
            throw new ParseException("IndexOutOfBoundsException parsing domain name");
        }
    }

    public int appendToWireWithCompression(OutputStream wire, int offset, Map<DomainName, Integer> compressionTable) throws IOException {
        return appendToWireWithCompression(wire, offset, compressionTable, true);
    }

    public int appendToWireWithCompression(OutputStream wire, int offset, Map<DomainName, Integer> compressionTable, boolean compress) throws IOException {
        DomainName dn = this;
        while (dn.labels.length > 0) {
            Integer pointerObj = null;
            if (compress && compressionTable != null) pointerObj = compressionTable.get(dn);
            int pointer = pointerObj != null ? pointerObj : -1;
            if (pointer < 0 || pointer >= 0x4000) {
                if (compressionTable != null && offset < 0x4000) compressionTable.put(dn, offset);
                byte[] label = dn.labels[0];
                wire.write(label.length);
                offset++;
                wire.write(label);
                offset += label.length;
                dn = dn.parent();
            } else {
                pointer = pointer | 0xC000;
                wire.write((pointer & 0xFF00) >>> 8);
                wire.write(pointer & 0x00FF);
                offset += 2;
                return offset;
            }
        }
        wire.write(0);
        offset++;
        return offset;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DomainName)) return false;
        DomainName that = (DomainName) obj;
        if (this.labels.length != that.labels.length) return false;
        return (Arrays.deepEquals(this.getCasefoldedLabels(), that.getCasefoldedLabels()));
    }

    private Integer _hashcode = null;

    @Override
    public int hashCode() {
        if (_hashcode == null) {
            _hashcode = Arrays.deepHashCode(this.getCasefoldedLabels());
        }
        return _hashcode;
    }

}

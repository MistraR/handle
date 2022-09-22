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
import java.util.Map;

public class Question {
    private final DomainName name;
    private final int type;
    private final int klass;

    public Question(DomainName name, int type, int klass) {
        this.name = name;
        this.type = type;
        this.klass = klass;
    }

    public DomainName getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public int getKlass() {
        return klass;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Question)) return false;
        Question other = (Question) obj;
        return (this.name.equals(other.name) && this.type == other.type && this.klass == other.klass);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + name.hashCode();
        result = 31 * result + type;
        result = 31 * result + klass;
        return result;
    }

    public static Question parseWire(byte[] wire, int[] offsetArr) throws ParseException {
        DomainName owner = DomainName.parseWire(wire, offsetArr);
        int offset = offsetArr[0];
        if (offset + 4 > wire.length) throw new ParseException("IndexOutOfBounds parsing question");
        int type = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int klass = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        offsetArr[0] = offset;
        return new Question(owner, type, klass);
    }

    public int appendToWireWithCompression(OutputStream wire, int offset, Map<DomainName, Integer> compressionTable) throws IOException {
        offset = name.appendToWireWithCompression(wire, offset, compressionTable);
        wire.write((type >> 8) & 0xFF);
        wire.write(type & 0xFF);
        wire.write((klass >> 8) & 0xFF);
        wire.write(klass & 0xFF);
        return offset + 4;
    }

    public boolean isAnswerFor(Question question) {
        if (question == null) return false;
        if (this.getKlass() == question.getKlass() && this.getName().equals(question.getName())) {
            if (question.getType() == Message.QTYPE_ANY) return true;
            else if (question.getType() == Message.QTYPE_MAILA) return type == ResourceRecord.TYPE_MD || type == ResourceRecord.TYPE_MF;
            else if (question.getType() == Message.QTYPE_MAILB) return type == ResourceRecord.TYPE_MB || type == ResourceRecord.TYPE_MG || type == ResourceRecord.TYPE_MR;
            else return this.getType() == question.getType();
        } else return false;
    }
}

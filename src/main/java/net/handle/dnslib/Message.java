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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Message implements Cloneable {
    public static final byte RC_OK = 0, RC_FORMAT_ERROR = 1, RC_SERVER_ERROR = 2, RC_NAME_ERROR = 3, RC_NOT_IMPLEMENTED = 4, RC_REFUSED = 5;

    public static final int META_TYPE_OPT = 41;
    public static final int QTYPE_IXFR = 251;
    public static final int QTYPE_AXFR = 252;
    public static final int QTYPE_MAILB = 253;
    public static final int QTYPE_MAILA = 254;
    public static final int QTYPE_ANY = 255;

    public int id;
    private boolean isQueryResponse;
    private byte opcode;
    boolean authAnswer;
    boolean truncated;
    public boolean recursionDesired;
    private boolean recursionAvailable;
    private byte responseCode;
    private Question questions[];
    MultiRRSet answer = new MultiRRSet();
    MultiRRSet authority = new MultiRRSet();
    MultiRRSet additional = new MultiRRSet();
    ResourceRecord ednsOptRecord;

    /** Extra information set by a nameserver to indicate which additional records should be kept even if we try leaving authoritative authority information out */
    protected Set<Question> additionalFromAnswerOrReferral;

    private static final Random RANDOM = new Random();

    public static final ResourceRecord SMALL_EDNS;
    public static final ResourceRecord LARGE_EDNS;
    public static final ResourceRecord SMALL_EDNS_BADVERS;
    public static final ResourceRecord LARGE_EDNS_BADVERS;
    static {
        byte[] empty = new byte[0];
        try {
            SMALL_EDNS = new ResourceRecord(DomainName.ROOT, Message.META_TYPE_OPT, 512, 0, empty, 0, 0);
            LARGE_EDNS = new ResourceRecord(DomainName.ROOT, Message.META_TYPE_OPT, 4096, 0, empty, 0, 0);
            SMALL_EDNS_BADVERS = new ResourceRecord(DomainName.ROOT, Message.META_TYPE_OPT, 512, 0x01000000, empty, 0, 0);
            LARGE_EDNS_BADVERS = new ResourceRecord(DomainName.ROOT, Message.META_TYPE_OPT, 4096, 0x01000000, empty, 0, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Message() {
    }

    public static Message copy(Message message) {
        try {
            return (Message) message.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public Message(Question q, boolean recursionDesired, boolean useEDNS) {
        id = RANDOM.nextInt(65536);
        questions = new Question[] { q };
        if (useEDNS) ednsOptRecord = LARGE_EDNS;
        this.recursionDesired = recursionDesired;
    }

    public byte getOpcode() {
        return opcode;
    }

    public Question getQuestion() {
        if (questions == null || questions.length != 1) return null;
        return questions[0];
    }

    public String getQuestionNameAsString() {
        if (questions != null && questions.length >= 1) return questions[0].getName().toString();
        return null;
    }

    public Question getQuestionAfterCNAME() {
        Question question = getQuestion();
        if (question == null) return null;

        Question nextQuestion = question;
        boolean changed;
        boolean answered;
        do {
            changed = false;
            answered = false;
            question = nextQuestion;
            for (RRSet rrs : answer.values()) {
                Question key = rrs.getKey();
                if (key.isAnswerFor(question)) {
                    answered = true;
                    break;
                }
                if (key.getKlass() == question.getKlass() && key.getType() == ResourceRecord.TYPE_CNAME && key.getName().equals(question.getName())) {
                    for (ResourceRecord rr : rrs) {
                        changed = true;
                        nextQuestion = new Question(rr.nameFromData(), question.getType(), question.getKlass());
                    }
                }
            }
        } while (changed && !answered);

        return question;
    }

    public boolean getRecursionDesired() {
        return recursionDesired;
    }

    private int _ttl = Integer.MAX_VALUE;

    /** Returns the minimum ttl of all records in the Message.  The Message must be frozen once this is called, except that
     *  the id and recursionDesired fields can be changed to facilitate caching.
     */
    public int getTTL() {
        int res = _ttl;
        if (res < Integer.MAX_VALUE) return res;

        int ncache = 0;
        Question question = getQuestionAfterCNAME();
        boolean soa = false;
        boolean ns = false;
        boolean nodata = true;
        for (RRSet rrs : answer.values()) {
            int ttl = rrs.getTTL();
            if (ttl < res) res = ttl;
            if (nodata && rrs.getKey().isAnswerFor(question)) nodata = false;
        }
        for (RRSet rrs : authority.values()) {
            int ttl = rrs.getTTL();
            if (ttl < res) res = ttl;
            if (rrs.getType() == ResourceRecord.TYPE_SOA) {
                soa = true;
                for (ResourceRecord rr : rrs) {
                    ncache = rr.minimumFromSOAData();
                }
            }
            if (rrs.getType() == ResourceRecord.TYPE_NS) {
                ns = true;
            }
        }
        for (RRSet rrs : additional.values()) {
            int ttl = rrs.getTTL();
            if (ttl < res) res = ttl;
        }
        int rc = getExtendedResponseCode();
        if (((rc == RC_OK && nodata && (soa || !ns)) || rc == RC_NAME_ERROR) && ncache < res) res = ncache;

        _ttl = res;
        return res;
    }

    public int parseWire(byte[] wire) throws ParseException {
        int offset = 0;
        if (wire.length < 12) throw new ParseException("not enough room of for DNS message header");
        id = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int flags = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        isQueryResponse = (flags & 0x8000) != 0;
        opcode = (byte) ((flags & 0x7800) >> 11);
        authAnswer = (flags & 0x0400) != 0;
        truncated = (flags & 0x0200) != 0;
        recursionDesired = (flags & 0x0100) != 0;
        recursionAvailable = (flags & 0x0080) != 0;
        responseCode = (byte) (flags & 0x000F);

        int qcount = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int anscount = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int authcount = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);
        int addcount = ((wire[offset++] & 0xFF) << 8) | (wire[offset++] & 0xFF);

        int[] offsetArr = new int[] { offset };

        questions = new Question[qcount];
        for (int i = 0; i < qcount; i++) {
            questions[i] = Question.parseWire(wire, offsetArr);
        }
        answer.clear();
        for (int i = 0; i < anscount; i++) {
            answer.add(ResourceRecord.parseWire(wire, offsetArr));
        }
        authority.clear();
        for (int i = 0; i < authcount; i++) {
            authority.add(ResourceRecord.parseWire(wire, offsetArr));
        }
        additional.clear();
        ednsOptRecord = null;
        for (int i = 0; i < addcount; i++) {
            ResourceRecord rr = ResourceRecord.parseWire(wire, offsetArr);
            if (rr.getType() == META_TYPE_OPT) {
                if (ednsOptRecord == null) {
                    ednsOptRecord = rr;
                    continue;
                }
            }
            additional.add(rr);
        }

        return offsetArr[0];
    }

    public int getUDPPayloadSize() {
        if (ednsOptRecord == null) return 512;
        return ednsOptRecord.getKlass();
    }

    public int getEDNSVersion() {
        if (ednsOptRecord == null) return -1;
        return (ednsOptRecord.getTTL() & 0x00FF0000) >> 16;
    }

    public int getExtendedResponseCode() {
        if (ednsOptRecord == null) return responseCode;
        return ((ednsOptRecord.getTTL() & 0xFF000000) >>> 20) | responseCode;
    }

    /** can only set unextended code */
    public void setResponseCode(byte responseCode) {
        this.responseCode = responseCode;
        if (ednsOptRecord == null || (ednsOptRecord.getTTL() & 0xFF000000) == 0) return;
        int ttl = ednsOptRecord.getTTL() & 0x00FFFFFF;
        ResourceRecord newEDNSOptRecord = new ResourceRecord(ednsOptRecord.getOwner(), ttl, ednsOptRecord);
        ednsOptRecord = newEDNSOptRecord;
    }

    /** Initial filter for both resolvers and nameservers.  If this returns null, it means to ignore the query.
     * If it returns a message with an rcode other than OK, it means return that message.
     * Otherwise the returned message can be modified in its AA bit, its RCODE, and have resource records added.
     */
    public static Message initialResponse(Message query, boolean recursionAvailable, boolean largeEDNS) {
        if (query.isQueryResponse) return null;
        Message msg = new Message();
        msg.id = query.id;
        msg.isQueryResponse = true;
        msg.opcode = query.opcode;
        msg.recursionDesired = query.recursionDesired;
        msg.recursionAvailable = recursionAvailable;

        if (query.truncated) msg.responseCode = RC_FORMAT_ERROR;
        else if (query.opcode != 0) msg.responseCode = RC_NOT_IMPLEMENTED;
        else if (query.questions.length != 1) msg.responseCode = RC_FORMAT_ERROR;
        else if (query.questions[0].getType() == META_TYPE_OPT) msg.responseCode = RC_FORMAT_ERROR;
        else if (query.questions[0].getType() == QTYPE_AXFR || query.questions[0].getType() == QTYPE_IXFR) msg.responseCode = RC_REFUSED;
        else if (query.questions[0].getKlass() != ResourceRecord.CLASS_IN) msg.responseCode = RC_REFUSED;
        else if (query.ednsOptRecord != null) {
            if (query.ednsOptRecord.getOwner() != DomainName.ROOT) {
                msg.responseCode = RC_FORMAT_ERROR;
            } else {
                for (Question q : query.additional.keySet()) {
                    if (q.getType() == META_TYPE_OPT) {
                        msg.responseCode = RC_FORMAT_ERROR;
                        break;
                    }
                }
            }
        }

        msg.questions = query.questions;
        int queryEDNSVersion = query.getEDNSVersion();
        if (queryEDNSVersion > 0) msg.ednsOptRecord = largeEDNS ? LARGE_EDNS_BADVERS : SMALL_EDNS_BADVERS;
        else if (queryEDNSVersion == 0) msg.ednsOptRecord = largeEDNS ? LARGE_EDNS : SMALL_EDNS;

        return msg;
    }

    public int appendToWire(OutputStream wire) throws IOException {
        return appendToWire(wire, new PartialResults(), 0xFFFF, false);
    }

    private byte[] _cachedDatagram;

    private byte[] getCachedDatagram(int udpPayloadSize) {
        if (_cachedDatagram == null || _cachedDatagram.length > udpPayloadSize || _cachedDatagram.length < 3) return null;
        byte[] res = _cachedDatagram.clone();
        res[0] = (byte) ((id >> 8) & 0xFF);
        res[1] = (byte) (id & 0xFF);
        int flags = res[2] & 0xFF;
        if (recursionDesired) flags = flags | 0x01;
        if (!recursionDesired) flags = flags & ~0x01;
        res[2] = (byte) flags;
        return res;
    }

    private void setCachedDatagram(byte[] buf) {
        _cachedDatagram = buf;
    }

    /** Returns a byte array representing the on-wire message.  The Message must be frozen once this is called, except that
     *  the id and recursionDesired fields can be changed to facilitate caching.
     */
    public byte[] getDatagram(int udpPayloadSize) throws IOException {
        byte[] cachedDatagram = getCachedDatagram(udpPayloadSize);
        if (cachedDatagram != null) return cachedDatagram;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PartialResults partial = new PartialResults();
        int offset = appendToWire(out, partial, udpPayloadSize, false);
        if (partial.lastGoodOffset == offset) {
            byte[] res = out.toByteArray();
            setCachedDatagram(res);
            return res;
        }

        int anscount = 0;
        for (RRSet rrs : answer.values()) {
            anscount += rrs.size();
        }

        // if didn't get all answers, we are done.  Could truncate a CNAME chain, but how likely it that?
        if (partial.anscount < anscount) {
            return truncationResponse();
        }

        int authcount = 0;
        for (RRSet rrs : authority.values()) {
            authcount += rrs.size();
        }

        if (partial.authcount < authcount || (ednsOptRecord != null && partial.addcount == 0)) {
            if (authorityIsOmittable()) {
                // we can omit the authority records and try again
                authcount = 0;
                out.reset();
                partial = new PartialResults();
                offset = appendToWire(out, partial, udpPayloadSize, true);
                if (partial.lastGoodOffset == offset) return out.toByteArray();
            }

            if (partial.authcount < authcount || (ednsOptRecord != null && partial.addcount == 0)) {
                return truncationResponse();
            }
        }
        // not really truncated, we're just dropping additional information
        byte[] res = new byte[partial.lastGoodOffset];
        System.arraycopy(out.toByteArray(), 0, res, 0, partial.lastGoodOffset);
        res[10] = (byte) ((partial.addcount >> 8) & 0xFF);
        res[11] = (byte) (partial.addcount & 0xFF);
        return res;
    }

    private class PartialResults {
        int lastGoodOffset;
        int anscount;
        int authcount;
        int addcount;
    }

    private int appendToWire(OutputStream wire, PartialResults partial, int udpPayloadSize, boolean omitAuthority) throws IOException {
        int offset = 0;
        wire.write((id >> 8) & 0xFF);
        wire.write(id & 0xFF);
        int flags = 0;
        if (isQueryResponse) flags = flags | 0x80;
        flags = flags | ((opcode & 0x0F) << 3);
        if (authAnswer) flags = flags | 0x04;
        if (truncated) flags = flags | 0x02;
        if (recursionDesired) flags = flags | 0x01;
        wire.write(flags);
        flags = 0;
        if (recursionAvailable) flags = flags | 0x80;
        flags = flags | (responseCode & 0x0F);
        wire.write(flags);

        int qcount = questions.length;
        int anscount = 0;
        for (RRSet rrs : answer.values()) {
            anscount += rrs.size();
        }
        int authcount = 0;
        if (!omitAuthority) {
            for (RRSet rrs : authority.values()) {
                authcount += rrs.size();
            }
        }
        Collection<RRSet> additionalRRSets;
        if (!omitAuthority || additionalFromAnswerOrReferral == null) {
            additionalRRSets = additional.values();
        } else {
            List<RRSet> list = new ArrayList<>();
            for (Question q : additionalFromAnswerOrReferral) {
                RRSet rrs = additional.get(q);
                if (rrs != null) list.add(rrs);
            }
            additionalRRSets = list;
        }
        int addcount = 0;
        for (RRSet rrs : additionalRRSets) {
            addcount += rrs.size();
        }
        if (ednsOptRecord != null) addcount += 1;

        wire.write((qcount >> 8) & 0xFF);
        wire.write(qcount & 0xFF);
        wire.write((anscount >> 8) & 0xFF);
        wire.write(anscount & 0xFF);
        wire.write((authcount >> 8) & 0xFF);
        wire.write(authcount & 0xFF);
        wire.write((addcount >> 8) & 0xFF);
        wire.write(addcount & 0xFF);
        offset += 12;

        Map<DomainName, Integer> compressionTable = new HashMap<>();

        for (Question q : questions) {
            offset = q.appendToWireWithCompression(wire, offset, compressionTable);
        }
        partial.lastGoodOffset = offset;

        for (RRSet rrs : shuffleRRSets(answer.values())) {
            rrs.fixTTL();
            rrs.shuffle();
            for (ResourceRecord rr : rrs) {
                offset = rr.appendToWireWithCompression(wire, offset, compressionTable);
            }
            if (offset < udpPayloadSize) {
                partial.lastGoodOffset = offset;
                partial.anscount += rrs.size();
            } else return offset;
        }
        if (!omitAuthority) {
            for (RRSet rrs : shuffleRRSets(authority.values())) {
                rrs.fixTTL();
                rrs.shuffle();
                for (ResourceRecord rr : rrs) {
                    offset = rr.appendToWireWithCompression(wire, offset, compressionTable);
                }
                if (offset < udpPayloadSize) {
                    partial.lastGoodOffset = offset;
                    partial.authcount += rrs.size();
                } else return offset;
            }
        }

        if (ednsOptRecord != null) {
            offset = ednsOptRecord.appendToWireWithCompression(wire, offset, compressionTable);
        }
        if (offset < udpPayloadSize) {
            partial.lastGoodOffset = offset;
            partial.addcount++;
        } else return offset;

        for (RRSet rrs : shuffleRRSets(additionalRRSets)) {
            rrs.fixTTL();
            rrs.shuffle();
            for (ResourceRecord rr : rrs) {
                offset = rr.appendToWireWithCompression(wire, offset, compressionTable);
            }
            if (offset < udpPayloadSize) {
                partial.lastGoodOffset = offset;
                partial.addcount += rrs.size();
            } else return offset;
        }
        return offset;
    }

    private Collection<RRSet> shuffleRRSets(Collection<RRSet> rrSets) {
        List<RRSet> res = new ArrayList<>();
        List<RRSet> toShuffle = new ArrayList<>();
        for (RRSet rrs : rrSets) {
            if (rrs.getType() == ResourceRecord.TYPE_CNAME || rrs.getType() == ResourceRecord.TYPE_SOA) {
                res.add(rrs);
            } else toShuffle.add(rrs);
        }
        Collections.shuffle(toShuffle);
        res.addAll(toShuffle);
        return res;
    }

    private byte[] truncationResponse() throws IOException {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        int offset = 0;
        wire.write((id >> 8) & 0xFF);
        wire.write(id & 0xFF);
        int flags = 0;
        if (isQueryResponse) flags = flags | 0x80;
        flags = flags | ((opcode & 0x0F) << 3);
        if (authAnswer) flags = flags | 0x04;
        if (true) flags = flags | 0x02; // truncated
        if (recursionDesired) flags = flags | 0x01;
        wire.write(flags);
        flags = 0;
        if (recursionAvailable) flags = flags | 0x80;
        flags = flags | (responseCode & 0x0F);
        wire.write(flags);
        int qcount = questions.length;
        int anscount = 0;
        int authcount = 0;
        int addcount = 0;
        if (ednsOptRecord != null) addcount += 1;
        wire.write((qcount >> 8) & 0xFF);
        wire.write(qcount & 0xFF);
        wire.write((anscount >> 8) & 0xFF);
        wire.write(anscount & 0xFF);
        wire.write((authcount >> 8) & 0xFF);
        wire.write(authcount & 0xFF);
        wire.write((addcount >> 8) & 0xFF);
        wire.write(addcount & 0xFF);
        offset += 12;

        Map<DomainName, Integer> compressionTable = new HashMap<>();
        for (Question q : questions) {
            offset = q.appendToWireWithCompression(wire, offset, compressionTable);
        }
        if (ednsOptRecord != null) {
            offset = ednsOptRecord.appendToWireWithCompression(wire, offset, compressionTable);
        }

        return wire.toByteArray();
    }

    /** the authority section can have referral, or the NS/SOA records for the nameserver's zone */
    private boolean authorityIsOmittable() {
        // there are answers...
        if (answer.size() == 0) return false;
        // if they are all CNAME, might still be a referral/negative...
        for (Question q : answer.keySet()) {
            if (q.getType() != ResourceRecord.TYPE_CNAME) return true;
        }
        // unless we asked for CNAME.
        for (Question q : questions) {
            if (q.getType() == ResourceRecord.TYPE_CNAME || q.getType() == QTYPE_ANY) return true;
        }
        return false;
    }

    private static final char HEX_VALUES[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    public static void debugPrint(byte b) {
        if (b >= ' ' && b <= '~') {
            System.err.print(' ');
            System.err.print((char) b);
        } else {
            System.err.print(HEX_VALUES[(b >>> 4) & 0x0F]);
            System.err.print(HEX_VALUES[b & 0x0F]);
        }
    }

    public static void debugPrint(byte[] bytes) {
        int i = 0;
        outerloop: while (true) {
            for (int j = 0; j < 30; j++) {
                debugPrint(bytes[i++]);
                if (i >= bytes.length) break outerloop;
            }
            System.err.println();
        }
        System.err.println();
    }

    public void debugPrint() {
        try {
            Message.debugPrint(getDatagram(65535));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

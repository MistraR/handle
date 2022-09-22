/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.handle.hdllib.HandleException;

public class NameServer {
    private final List<Storage> storageList;
    private final NameResolver resolver;
    private Cache cache;
    /** Used for root referrals---only sending one, which is questionable, but client won't use it anyway */
    public static final ResourceRecord A_ROOT_REFERRAL;
    static {
        try {
            A_ROOT_REFERRAL = new ResourceRecord(DomainName.ROOT, ResourceRecord.TYPE_NS, ResourceRecord.CLASS_IN, 0, "A.ROOT-SERVERS.NET.");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public NameServer(List<Storage> storageList, NameResolver resolver) {
        this.storageList = storageList;
        this.resolver = resolver;
    }

    public void shutdown() {
        this.cache.shutdown();
    }

    public void setCache(int prefetcherThreads, int size) {
        if (size <= 0) return;
        this.cache = new Cache(query -> realRespondToQuery(query), prefetcherThreads, size);
    }

    private Storage getZoneStorageFor(DomainName name) {
        int length = -1;
        Storage res = null;
        for (Storage s : storageList) {
            DomainName zone = s.getZone();
            if (zone.length() <= length) continue;
            if (name.descendsFrom(zone)) {
                length = zone.length();
                res = s;
            }
        }
        return res;
    }

    /** return a response, and also set the UDP payload size for this query */
    public Message respondToBytes(byte[] queryBytes, boolean allowRecursion, int[] udpPayloadArr) {
        Message query = new Message();
        try {
            query.parseWire(queryBytes);
        } catch (ParseException e) {
            e.printStackTrace();
            Message response = Message.initialResponse(query, allowRecursion, false);
            response.setResponseCode(Message.RC_FORMAT_ERROR);
            return response;
        }

        if (udpPayloadArr != null) udpPayloadArr[0] = query.getUDPPayloadSize();
        return respondToQuery(query, allowRecursion);
    }

    public Message refusalResponse(byte[] queryBytes) {
        Message query = new Message();
        try {
            query.parseWire(queryBytes);
        } catch (ParseException e) {
            e.printStackTrace();
            Message response = Message.initialResponse(query, false, false);
            if (response == null) return response;
            response.setResponseCode(Message.RC_FORMAT_ERROR);
            return response;
        }

        Message response = Message.initialResponse(query, false, false);
        if (response == null) return response;
        response.setResponseCode(Message.RC_REFUSED);
        return response;
    }

    public Message respondToQuery(Message query, boolean allowRecursion) {
        if (allowRecursion && query.getRecursionDesired() && resolver != null) return resolver.respondToQuery(query);

        if (cache != null) {
            Message response = cache.get(query);
            if (response != null) return response;
        }

        return realRespondToQuery(query);
    }

    Message realRespondToQuery(Message query) {
        Message response = Message.initialResponse(query, false, false);
        if (response == null || response.getExtendedResponseCode() != Message.RC_OK) return response;

        Storage storage = null;
        try {
            storage = accumulateAnswers(response, query.getQuestion().getName(), query.getQuestion().getType(), false);
        } catch (HandleException e) {
            e.printStackTrace();
            response.setResponseCode(Message.RC_SERVER_ERROR);
            return response;
        }

        boolean authorityIsReferralOrSOA = storage == null;

        if (!authorityIsReferralOrSOA && storage != null && !response.answer.keySet().contains(new Question(storage.getZone(), ResourceRecord.TYPE_NS, ResourceRecord.CLASS_IN))) {
            try {
                List<ResourceRecord> nsRecords = storage.getNSRecords(storage.getZone());
                if (nsRecords != null) {
                    for (ResourceRecord rr : nsRecords) {
                        response.authority.add(rr);
                    }
                }
            } catch (HandleException e) {
                e.printStackTrace();
            }
        }
        additionalSectionProcessing(response, authorityIsReferralOrSOA);
        if (cache != null) cache.put(query, response);
        return response;
    }

    /** Returns null, if no authoritative nameserver records should be added; or the storage from which to add the records. */
    private Storage accumulateAnswers(Message response, DomainName name, int type, boolean followingCNAME) throws HandleException {
        Storage storage = getZoneStorageFor(name);
        if (storage == null) {
            response.authority.add(A_ROOT_REFERRAL);
            return null;
        }
        DomainName zone = storage.getZone();
        ResourceRecord soaRecord = storage.getSOARecord(zone);
        if (soaRecord == null) {
            // not authoritative for this zone
            List<ResourceRecord> zoneCutRecords = storage.getNSRecords(zone);
            if (zoneCutRecords != null && zoneCutRecords.size() > 0) {
                for (ResourceRecord rr : zoneCutRecords) {
                    response.authority.add(rr);
                }
            } else response.authority.add(A_ROOT_REFERRAL);
            if (!followingCNAME) response.authAnswer = false;
            return null;
        }

        response.authAnswer = true;

        // walk down, looking for a zone cut
        DomainName match = zone;
        DomainName longestExistingAncestor = zone;
        while (match.length() < name.length()) {
            match = match.ancestorChildOf(name);
            List<ResourceRecord> zoneCutRecords = storage.getNSRecords(match); // skip at zone top
            if (zoneCutRecords != null) {
                longestExistingAncestor = match;
                if (zoneCutRecords.size() > 0) {
                    // zone cut; it's a referral
                    for (ResourceRecord rr : zoneCutRecords) {
                        response.authority.add(rr);
                    }
                    if (!followingCNAME) response.authAnswer = false;
                    return null;
                }
            }
        }

        List<ResourceRecord> records = storage.getRecords(name, type);
        if (records == null) {
            // walk up, looking for a wildcard
            DomainName wildcard = name;
            while (wildcard.length() > longestExistingAncestor.length()) {
                wildcard = wildcard.asteriskLabelSibling();

                // NS records at a wildcard?  Treat as a referral, even though undefined in RFC4592
                List<ResourceRecord> zoneCutRecords = storage.getWildcardNSRecords(wildcard, name);
                if (zoneCutRecords != null) {
                    if (zoneCutRecords.size() > 0) {
                        // zone cut; it's a referral
                        for (ResourceRecord rr : zoneCutRecords) {
                            response.authority.add(rr);
                        }
                        if (!followingCNAME) response.authAnswer = false;
                        return null;
                    }

                    records = storage.getWildcardRecords(wildcard, name, type);
                    if (records != null) break;
                }

                wildcard = wildcard.parent();
            }

            if (records == null) {
                // no wildcard either; it's NXDOMAIN
                // if we're following cnames, don't give the nxdomain.  This is a special case from RFC1034 4.3.2; in spite of RFC2308.  Too bad!
                if (!followingCNAME) {
                    response.setResponseCode(Message.RC_NAME_ERROR);
                    response.authority.add(soaRecord);
                }
                return null;
            }
        }

        if (records.size() == 0) {
            // no data

            response.authority.add(soaRecord);
            return null;
        }

        ResourceRecord cname = null;
        boolean cnamePossible = type != ResourceRecord.TYPE_CNAME && type != Message.QTYPE_ANY;
        for (ResourceRecord rr : records) {
            response.answer.add(rr);
            if (cnamePossible && rr.getType() == ResourceRecord.TYPE_CNAME) {
                if (cname != null) {
                    cnamePossible = false;
                    cname = null;
                } else {
                    cname = rr;
                }
            }
        }

        if (cname != null) {
            DomainName nextName = cname.nameFromData();
            if (nextName != null) {
                // check and see if this is a loop
                boolean loop = false;
                for (Question q : response.answer.keySet()) {
                    if (q.getName().equals(nextName)) {
                        loop = true;
                        break;
                    }
                }
                if (!loop) {
                    if (accumulateAnswers(response, nextName, type, true) == null) storage = null;
                }
            }
        }

        return storage;
    }

    private void additionalSectionProcessing(Message message, boolean authorityIsReferralOrSOA) {
        Set<Question> questions = new HashSet<>();
        message.additionalFromAnswerOrReferral = new HashSet<>();
        for (RRSet rrs : message.answer.values()) {
            for (ResourceRecord rr : rrs) {
                for (Question q : rr.additionalSectionProcessing()) {
                    questions.add(q);
                    message.additionalFromAnswerOrReferral.add(q);
                }
            }
        }
        for (RRSet rrs : message.authority.values()) {
            for (ResourceRecord rr : rrs) {
                for (Question q : rr.additionalSectionProcessing()) {
                    questions.add(q);
                    if (authorityIsReferralOrSOA) message.additionalFromAnswerOrReferral.add(q);
                }
            }
        }

        for (Question q : message.answer.keySet()) {
            questions.remove(q);
        }
        for (Question q : message.authority.keySet()) {
            questions.remove(q);
        }

        for (Question q : questions) {
            try {
                Storage storage = getZoneStorageFor(q.getName());
                if (storage == null) continue;
                List<ResourceRecord> rrs = storage.getRecords(q.getName(), q.getType(), false);
                if (rrs != null) {
                    for (ResourceRecord rr : rrs) {
                        message.additional.add(rr);
                    }
                }
            } catch (HandleException e) {
                e.printStackTrace();
            }
        }
    }

}

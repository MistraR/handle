/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.ResolutionResponse;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.hdllib.Util;
import net.handle.server.AbstractServer;
import net.handle.server.dns.DnsConfiguration;

public class Storage {

    private final DomainName zone;
    private final String handlePrefix;
    private final AbstractServer server;
    private final DnsConfiguration dnsConfig;

    public Storage(DomainName zone, String handlePrefix, AbstractServer server, DnsConfiguration dnsConfig) {
        this.zone = zone;
        this.handlePrefix = handlePrefix;
        this.server = server;
        this.dnsConfig = dnsConfig;
    }

    private static Map<Integer, byte[]> dnsTypeToHandleTypeMap = new HashMap<>();
    private static Map<String, Integer> handleTypeToDNSTypeMap = new HashMap<>();
    static {
        Field[] allFields = ResourceRecord.class.getFields();
        for (Field field : allFields) {
            String name = field.getName();
            try {
                if (name.startsWith("TYPE_")) {
                    int type = field.getInt(null);
                    dnsTypeToHandleTypeMap.put(type, Util.encodeString("DNS." + name.substring(5)));
                    handleTypeToDNSTypeMap.put("DNS." + name.substring(5), type);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final byte[] SYMBOLIC_CNAME = Util.encodeString("DNS.CNAME");
    private static final byte[] NUMERIC_CNAME = Util.encodeString("DNS.TYPE5");

    private static final byte[][] HDL_DNS_TYPES_ANY = new byte[][] { Util.encodeString("DNS.") };
    private static final byte[][] HDL_DNS_TYPES_CNAME = new byte[][] { SYMBOLIC_CNAME, NUMERIC_CNAME };

    private static byte[] handleTypeSymbolic(int type) {
        return dnsTypeToHandleTypeMap.get(type);
    }

    private static byte[] handleTypeNumeric(int type) {
        return Util.encodeString("DNS.TYPE" + type);
    }

    public static byte[][] dnsTypeToHandleTypes(int type, boolean includeCNAME) {
        if (type == Message.QTYPE_ANY) return HDL_DNS_TYPES_ANY;
        if (type == ResourceRecord.TYPE_CNAME) return HDL_DNS_TYPES_CNAME;

        if (type == Message.QTYPE_MAILA) {
            if (includeCNAME) return new byte[][] { handleTypeSymbolic(ResourceRecord.TYPE_MD), handleTypeNumeric(ResourceRecord.TYPE_MD), handleTypeSymbolic(ResourceRecord.TYPE_MF), handleTypeNumeric(ResourceRecord.TYPE_MF),
                    SYMBOLIC_CNAME, NUMERIC_CNAME };
            else return new byte[][] { handleTypeSymbolic(ResourceRecord.TYPE_MD), handleTypeNumeric(ResourceRecord.TYPE_MD), handleTypeSymbolic(ResourceRecord.TYPE_MF), handleTypeNumeric(ResourceRecord.TYPE_MF) };
        } else if (type == Message.QTYPE_MAILB) {
            if (includeCNAME) return new byte[][] { handleTypeSymbolic(ResourceRecord.TYPE_MB), handleTypeNumeric(ResourceRecord.TYPE_MB), handleTypeSymbolic(ResourceRecord.TYPE_MG), handleTypeNumeric(ResourceRecord.TYPE_MG),
                    handleTypeSymbolic(ResourceRecord.TYPE_MR), handleTypeNumeric(ResourceRecord.TYPE_MR), SYMBOLIC_CNAME, NUMERIC_CNAME };
            else return new byte[][] { handleTypeSymbolic(ResourceRecord.TYPE_MB), handleTypeNumeric(ResourceRecord.TYPE_MB), handleTypeSymbolic(ResourceRecord.TYPE_MG), handleTypeNumeric(ResourceRecord.TYPE_MG),
                    handleTypeSymbolic(ResourceRecord.TYPE_MR), handleTypeNumeric(ResourceRecord.TYPE_MR) };
        }

        byte[] symbolic = handleTypeSymbolic(type);
        if (symbolic == null) {
            if (includeCNAME) return new byte[][] { handleTypeNumeric(type), SYMBOLIC_CNAME, NUMERIC_CNAME };
            else return new byte[][] { handleTypeNumeric(type) };
        } else {
            if (includeCNAME) return new byte[][] { symbolic, handleTypeNumeric(type), SYMBOLIC_CNAME, NUMERIC_CNAME };
            else return new byte[][] { symbolic, handleTypeNumeric(type) };
        }
    }

    public static ResourceRecord handleValueToResourceRecord(DomainName name, HandleValue value) {
        String typeString = value.getTypeAsString();
        Integer type = handleTypeToDNSTypeMap.get(typeString);
        if (type == null) {
            try {
                if (typeString.startsWith("DNS.TYPE")) type = Integer.parseInt(typeString.substring(8));
                else return null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        int ttl = value.getTTL();
        if (value.getTTLType() == HandleValue.TTL_TYPE_ABSOLUTE) {
            ttl = ttl - (int) (System.currentTimeMillis() / 1000);
        }
        if (ttl < 0) ttl = 0;
        try {
            return new ResourceRecord(name, type, ResourceRecord.CLASS_IN, ttl, value.getDataAsString());
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    public DomainName getZone() {
        return zone;
    }

    /** This provides a simple way to call into the server's processRequest and use the response */
    private class SimpleResponseMessageCallback implements ResponseMessageCallback {
        public AbstractResponse response = null;

        @Override
        public void handleResponse(@SuppressWarnings("hiding") AbstractResponse response) /*throws HandleException*/ {
            this.response = response;
        }
    }

    List<ResourceRecord> getRecords(DomainName name, int type, boolean includeCNAME) throws HandleException {
        byte[] handle;
        try {
            handle = name.toHandle(handlePrefix, zone.length());
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
        byte[][] handleTypes = dnsTypeToHandleTypes(type, includeCNAME);

        ResolutionRequest req = new ResolutionRequest(handle, handleTypes, null, null);
        if (dnsConfig.isCertify()) req.certify = true;
        SimpleResponseMessageCallback callback = new SimpleResponseMessageCallback();
        server.processRequest(req, callback);
        if (callback.response instanceof ResolutionResponse) {
            HandleValue[] values = ((ResolutionResponse) callback.response).getHandleValues();
            ArrayList<ResourceRecord> res = new ArrayList<>();
            for (HandleValue value : values) {
                ResourceRecord rr = handleValueToResourceRecord(name, value);
                if (rr != null) res.add(rr);
            }
            return res;
        } else {
            if (callback.response == null) throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected null response");
            if (callback.response.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) return null;
            if (callback.response.responseCode == AbstractMessage.RC_VALUES_NOT_FOUND) return Collections.emptyList();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response: " + callback.response);
        }
    }

    List<ResourceRecord> getWildcardRecords(DomainName wildcard, DomainName name, int type, boolean includeCNAME) throws HandleException {
        List<ResourceRecord> rrs = getRecords(wildcard, type, includeCNAME);
        if (rrs == null) return null;
        ArrayList<ResourceRecord> newRRs = new ArrayList<>(rrs.size());
        for (ResourceRecord rr : rrs) {
            ResourceRecord newRR = new ResourceRecord(name, rr.getTTL(), rr);
            newRRs.add(newRR);
        }
        return newRRs;
    }

    List<ResourceRecord> getRecords(DomainName name, int type) throws HandleException {
        return getRecords(name, type, true);
    }

    List<ResourceRecord> getNSRecords(DomainName name) throws HandleException {
        return getRecords(name, ResourceRecord.TYPE_NS, false);
    }

    List<ResourceRecord> getWildcardNSRecords(DomainName wildcard, DomainName name) throws HandleException {
        return getWildcardRecords(wildcard, name, ResourceRecord.TYPE_NS, false);
    }

    List<ResourceRecord> getWildcardRecords(DomainName wildcard, DomainName name, int type) throws HandleException {
        return getWildcardRecords(wildcard, name, type, true);
    }

    ResourceRecord getSOARecord(DomainName name) throws HandleException {
        List<ResourceRecord> rrs = getRecords(name, ResourceRecord.TYPE_SOA, false);
        if (rrs == null || rrs.size() != 1) return null;
        return rrs.get(0);
    }
}

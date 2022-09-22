/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.cnri.util.StreamTable;
import net.handle.dnslib.DomainName;
import net.handle.dnslib.NameResolver;
import net.handle.dnslib.NameServer;
import net.handle.dnslib.ParseException;
import net.handle.dnslib.Storage;
import net.handle.dnslib.StubResolver;
import net.handle.server.AbstractServer;

public class DnsConfiguration {

    private final Map<String, String> dnsHandleMap;
    private List<InetAddress> dns_forward;
    private boolean recursive = false;
    private InetAddressRange[] allow_recursion;
    private InetAddressRange[] allow_query;
    private int dnsCacheSize = 10000; // currently used for internal cache
    private int dnsCachePrefetcherThreads = 10;
    private boolean certify = false;

    public DnsConfiguration(AbstractServer server, StreamTable config) throws UnknownHostException {
        if (config == null) config = new StreamTable();
        recursive = config.getBoolean("recursion");

        this.certify = config.getBoolean("certify");

        Object allow_recursion_raw = config.get("allow_recursion");
        if (allow_recursion_raw instanceof String) allow_recursion = new InetAddressRange[] { new InetAddressRange((String) allow_recursion_raw) };
        else if (allow_recursion_raw != null) {
            List<?> allow_recursion_list = (List<?>) allow_recursion_raw;
            allow_recursion = new InetAddressRange[allow_recursion_list.size()];
            for (int i = 0; i < allow_recursion.length; i++) {
                allow_recursion[i] = new InetAddressRange((String) allow_recursion_list.get(i));
            }
        }

        Object allow_query_raw = config.get("allow_query");
        if (allow_query_raw instanceof String) allow_query = new InetAddressRange[] { new InetAddressRange((String) allow_query_raw) };
        else if (allow_query_raw != null) {
            List<?> allow_query_list = (List<?>) allow_query_raw;
            allow_query = new InetAddressRange[allow_query_list.size()];
            for (int i = 0; i < allow_query.length; i++) {
                allow_query[i] = new InetAddressRange((String) allow_query_list.get(i));
            }
        }

        String cacheString = (String) config.get("dns_cache_size");
        if (cacheString != null) dnsCacheSize = Integer.parseInt(cacheString);

        String prefetcherString = (String) config.get("dns_cache_prefetcher_threads");
        if (prefetcherString != null) dnsCachePrefetcherThreads = Integer.parseInt(prefetcherString);

        Object dns_forward_raw = config.get("dns_forward");
        if (dns_forward_raw instanceof String) dns_forward = Collections.singletonList(InetAddress.getByName((String) dns_forward_raw));
        else if (dns_forward_raw != null) {
            @SuppressWarnings("unchecked")
            List<String> dns_forward_strings = (List<String>) dns_forward_raw;
            dns_forward = new ArrayList<>();
            for (String s : dns_forward_strings) {
                dns_forward.add(InetAddress.getByName(s));
            }
        }
        if (dns_forward == null) {
            if (recursive == true || allow_recursion != null) {
                System.err.println("Warning: DNS recursion support requested, but no dns_forward server configured");
                recursive = false;
                allow_recursion = null;
            }
        } else {
            _resolver = new StubResolver(dns_forward, false);
            _resolver.setCache(dnsCachePrefetcherThreads, dnsCacheSize);
        }
        @SuppressWarnings("unchecked")
        Map<String, String> castMap = (Map<String,String>) config.get("dns_handle_map");
        dnsHandleMap = castMap;
        if (dnsHandleMap != null) {
            _storageList = new ArrayList<>(dnsHandleMap.size());
            for (Map.Entry<String, String> entry : dnsHandleMap.entrySet()) {
                try {
                    _storageList.add(new Storage(DomainName.ofString(entry.getKey()), entry.getValue(), server, this));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            _nameServer = new NameServer(storageList(), _resolver);
            _nameServer.setCache(dnsCachePrefetcherThreads, dnsCacheSize);
        }
    }

    public boolean isCertify() {
        return certify;
    }

    private class InetAddressRange {
        byte[] address;
        int prefix;

        InetAddressRange(String s) throws UnknownHostException {
            int n = s.indexOf('/');
            if (n >= 0) {
                String s1 = s.substring(0, n).trim();
                address = InetAddress.getByName(s1).getAddress();
                prefix = Integer.parseInt(s.substring(n + 1).trim());
            } else {
                String s1 = s.trim();
                address = InetAddress.getByName(s1).getAddress();
                prefix = 8 * address.length;
            }
        }

        boolean matches(InetAddress addr) {
            byte[] addrBytes = addr.getAddress();
            if (addrBytes.length != address.length) return false;
            @SuppressWarnings("hiding")
            int prefix = this.prefix;
            for (int i = 0; i < address.length; i++) {
                if (prefix <= 0) return true;
                int mask = 0xFF;
                if (prefix < 8) mask = mask << (8 - prefix);
                if ((addrBytes[i] & mask) != (address[i] & mask)) return false;
                prefix -= 8;
            }
            return true;
        }
    }

    /**
     * Returns whether or not to fall back on DNS
     * when unable to resolve via handles
     */
    public boolean getRecursive(InetAddress addr) {
        if (allow_recursion == null || addr == null) return recursive;
        for (InetAddressRange element : allow_recursion) {
            if (element.matches(addr)) return true;
        }
        return false;
    }

    /**
     * Returns whether or not to fall back on DNS
     * when unable to resolve via handles
     */
    public boolean getAllowQuery(InetAddress addr) {
        if (allow_query == null) return true;
        if (addr == null) return false;
        for (InetAddressRange element : allow_query) {
            if (element.matches(addr)) return true;
        }
        return false;
    }

    private List<Storage> _storageList;

    public List<Storage> storageList() {
        return _storageList;
    }

    private NameServer _nameServer;

    public NameServer getNameServer() {
        return _nameServer;
    }

    private NameResolver _resolver;

    public NameResolver getNameResolver() {
        return _resolver;
    }

    /**
     * Returns the dns handle prefix
     */
    public String getDnsHandlePrefix(String suffix) {
        if (dnsHandleMap == null) return null;
        return dnsHandleMap.get(suffix.toLowerCase());
    }
}

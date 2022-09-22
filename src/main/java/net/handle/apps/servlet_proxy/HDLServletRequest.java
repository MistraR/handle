/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import net.cnri.simplexml.XParser;
import net.cnri.simplexml.XTag;
import net.cnri.util.StringUtils;
import net.handle.apps.servlet_proxy.handlers.Location;
import net.handle.hdllib.*;
import net.handle.util.LRUCacheTable;

import java.io.StringReader;
import java.net.IDN;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.*;

/** HDLServletRequest contains the handle, parameters, and other information
 * associated with a single handle resolution request. */
public class HDLServletRequest {

    private static LRUCacheTable<String, HandleLocationInfo> locationsCache = new LRUCacheTable<>(4096);

    private static HashMap<String, String> SPECIFIC_BROWSER_CONTENT_TYPES = new HashMap<>();
    static {
        SPECIFIC_BROWSER_CONTENT_TYPES.put("text/html", "");
        SPECIFIC_BROWSER_CONTENT_TYPES.put("text/xhtml", "");
        SPECIFIC_BROWSER_CONTENT_TYPES.put("application/xhtml+xml", "");
        SPECIFIC_BROWSER_CONTENT_TYPES.put("application/pdf", "");
        SPECIFIC_BROWSER_CONTENT_TYPES.put("application/vnd.wap.xhtml+xml", "");
        SPECIFIC_BROWSER_CONTENT_TYPES.put("text/vnd.wap.wml", "");
    }

    // comparator used to sort the Accept header entries by quality
    private static Comparator<AcceptEntry> acceptEntrySorter = (o1, o2) -> {
        float result = o1.quality - o2.quality;
        if (result < 0f) return 1;
        if (result > 0f) return -1;
        return 0;
    };

    public HDLProxy servlet;
    public HttpServletRequest req;
    private final InetAddress remoteInetAddress;
    private final String remoteAddr;
    public HttpServletResponse response;
    public String hdl = null;
    public HttpParams params = new HttpParams();
    public AuthenticationInfo authenticationInfo;

    private final Map<String, HandleLocationInfo> thisRequestLocationsCache = new HashMap<>();

    public boolean api = false;
    public boolean oldApi = false;

    public ResolutionRequest resRequest = null;
    public AbstractResponse resResponse = null;
    public long resolutionTime;
    public HandleException exception;

    private long expiration = Long.MAX_VALUE;

    private RequestProcessor resolver = null;

    private final long intime;

    public enum ResponseType {
        SEE_OTHER(303, "See Other"),
        MOVED_TEMPORARILY(307, "Moved Temporarily"),
        OLD_MOVED_TEMPORARILY(302, "Moved Temporarily"),
        MOVED_PERMANENTLY(301, "Moved Permanently"),
        NEW_PERMANENT_REDIRECT(308, "Permanent Redirect");

        public static final ResponseType DEFAULT_RESPONSE_TYPE = ResponseType.OLD_MOVED_TEMPORARILY;

        int responseCode;
        String message;

        ResponseType(int responseCode, String message) {
            this.responseCode = responseCode;
            this.message = message;
        }

        /**
         * Return the appropriate response type for the given string, which is expected
         * to appear with attribute "http" in the list of 10320/loc locations.
         */
        public static ResponseType typeForString(String typeStr) {
            if (typeStr == null) return DEFAULT_RESPONSE_TYPE;
            if (typeStr.equals("")) return DEFAULT_RESPONSE_TYPE;
            if (typeStr.equals("307")) return MOVED_TEMPORARILY;
            if (typeStr.equals("301")) return MOVED_PERMANENTLY;
            if (typeStr.equals("308")) return NEW_PERMANENT_REDIRECT;
            if (typeStr.equals("303")) return SEE_OTHER;
            if (typeStr.equals("302")) return OLD_MOVED_TEMPORARILY;
            return DEFAULT_RESPONSE_TYPE;
        }

    }

    public HDLServletRequest(HDLProxy proxy, HttpServletRequest req, HttpServletResponse response, RequestProcessor resolver) {
        //this(servlet, req, response, req.getRequestURI(), resolver);
        intime = System.currentTimeMillis();
        this.resolver = resolver;
        this.servlet = proxy;
        this.req = req;
        this.remoteInetAddress = servlet.getRemoteInetAddress(req);
        this.remoteAddr = servlet.getRemoteAddr(req);
        this.response = response;
        String requestURI = req.getRequestURI();
        if (req.getQueryString() == null || req.getQueryString().length() == 0) {
            int n = requestURI.indexOf('#');
            if (n >= 0) requestURI = requestURI.substring(0, n);
        }
        requestURI = StringUtils.decodeURLIgnorePlus(requestURI);
        String contextPath = StringUtils.decodeURLIgnorePlus(req.getContextPath());
        int indexOfContextPath = requestURI.indexOf(contextPath);
        String pathInfo = indexOfContextPath < 0 ? requestURI : requestURI.substring(indexOfContextPath + contextPath.length());
        this.hdl = pathInfo;
        if (this.hdl == null) this.hdl = "";
        while (this.hdl.startsWith("/")) this.hdl = this.hdl.substring(1).trim();

        if (req.getMethod().equalsIgnoreCase("POST")) {
            for (Enumeration<String> e = req.getParameterNames(); e.hasMoreElements();) {
                String key = e.nextElement();
                params.addParameters(key, req.getParameterValues(key));
            }
            String hdlStr = req.getParameter("hdl");
            if (hdlStr != null) this.hdl = hdlStr.trim();
        } else if (req.getMethod().equalsIgnoreCase("GET") || req.getMethod().equalsIgnoreCase("HEAD")) {
            parseGetParams(req.getRequestURI(), req.getQueryString());
        }

        // replace any action=metadata parameters with locatt=role:metadata
        boolean setMetadataParam = false;
        String actionParam = params.getParameter(HDLProxy.ACTION_PARAM);
        if (actionParam != null && actionParam.equalsIgnoreCase(HDLProxy.ACTION_METADATA)) {
            params.replaceParameterValue(HDLProxy.ACTION_PARAM, HDLProxy.ACTION_REDIRECT);
            params.addParameter("locatt", "role:metadata");
            setMetadataParam = true;
        }

        // lift query parameters to locatt parameters as configured (use for ap)
        List<String> locattParams = proxy.locattShortcutParameters.get(req.getServerName().toLowerCase());
        if (locattParams == null) locattParams = proxy.locattShortcutParameters.get("default");
        for (String locattParam : locattParams) {
            String value = params.getParameter(locattParam);
            if (value != null) {
                params.addParameter("locatt", locattParam + ":" + value);
            }
        }

        processAcceptHeader(setMetadataParam);
        processAcceptLanguageHeader();
    }

    private void processAcceptHeader(boolean setMetadataParam) {
        // parse the Accept: header to see if they're looking specifically for metadata
        List<AcceptEntry> accepts = parseAcceptHeader("accept");

        if (acceptsIsJustStarStar(accepts)) {
            String userAgent = req.getHeader("User-Agent");
            if (userAgent == null) {
                if (!setMetadataParam) params.addParameter("locatt", "http_role:no_conneg");
                return;
            }
            userAgent = userAgent.trim().toLowerCase();
            if (userAgent.startsWith("mozilla/") || userAgent.startsWith("opera/")) {
                if (!setMetadataParam) params.addParameter("locatt", "http_role:browser");
                params.addParameter("locatt", "ctype:text/html");
                params.addParameter("locatt", "media-type:text/html");
                return;
            } else {
                if (!setMetadataParam) params.addParameter("locatt", "http_role:no_conneg");
                return;
            }
        }

        // if html has the highest quality score, then this is a browser request and shouldn't do conneg
        float htmlScore = 0f;
        float maxScore = 0f;
        for (AcceptEntry acceptEntry : accepts) {
            float thisScore = acceptEntry.quality;
            if (thisScore > maxScore) maxScore = thisScore;
            if (thisScore > htmlScore && AcceptEntry.acceptsBrowserContent(acceptEntry.value)) htmlScore = thisScore;
        }

        // if the 'html score' is not one of the highest-quality content types, do conneg
        // and add locatt requests for specific content types
        if (htmlScore < maxScore) {
            Collections.sort(accepts, acceptEntrySorter);
            // System.err.println("accept header requests non-browser content:\n  "+accepts);
            for (AcceptEntry acceptEntry : accepts) {
                if (AcceptEntry.hasWildcard(acceptEntry.value)) break;
                params.addParameter("locatt", "ctype:" + acceptEntry.value);
                params.addParameter("locatt", "media-type:" + acceptEntry.value);
            }
            if (!setMetadataParam) params.addParameter("locatt", "http_role:conneg");
        } else {
            if (!setMetadataParam) params.addParameter("locatt", "http_role:browser");
            if (textHtmlAcceptable(accepts)) {
                params.addParameter("locatt", "ctype:text/html");
                params.addParameter("locatt", "media-type:text/html");
            }
        }
    }

    private boolean textHtmlAcceptable(List<AcceptEntry> accepts) {
        for (AcceptEntry acceptEntry : accepts) {
            if ("text/html".equals(acceptEntry.value)) return true;
            if ("text/*".equals(acceptEntry.value)) return true;
            if ("*/*".equals(acceptEntry.value)) return true;
        }
        return false;
    }

    private boolean acceptsIsJustStarStar(List<AcceptEntry> accepts) {
        for (AcceptEntry acceptEntry : accepts) {
            if (!"*/*".equals(acceptEntry.value)) {
                return false;
            }
        }
        return true;
    }

    private void processAcceptLanguageHeader() {
        List<AcceptEntry> accepts = parseAcceptHeader("accept-language");
        Collections.sort(accepts, acceptEntrySorter);
        for (AcceptEntry acceptEntry : accepts) {
            params.addParameter("locatt", "language:" + acceptEntry.value);
        }
    }

    private List<AcceptEntry> parseAcceptHeader(String header) {
        List<AcceptEntry> accepts = new ArrayList<>();
        List<String> acceptsHeaders = getRequestHeader(header);
        for (String field : acceptsHeaders) { // iterate over multiple accepts headers
            if (field == null) continue;
            String[] entries = field.split(",");
            for (String headerValue : entries) { // iterate over the acceptable types
                AcceptEntry acceptEntry = new AcceptEntry(headerValue.split(";"));
                accepts.add(acceptEntry);
            }
        }
        return accepts;
    }

    private void parseGetParams(String URI, String query) {
        if (query == null || query.length() == 0) {
            int n = URI.indexOf('#');
            if (n >= 0) params.addParameter("urlappend", StringUtils.decodeURLIgnorePlus(URI.substring(n)));
        } else {
            int n = query.indexOf('#');
            if (n >= 0) {
                params.addParameter("urlappend", StringUtils.decodeURLIgnorePlus(query.substring(n)));
                query = query.substring(0, n);
            }
            StringTokenizer st = new StringTokenizer(query, "&;");
            while (st.hasMoreTokens()) {
                String s = st.nextToken();
                String key, val;
                int pos = s.indexOf('=');
                if (pos >= 0) {
                    key = s.substring(0, pos);
                    val = s.substring(pos + 1);
                } else {
                    key = s;
                    val = "";
                }
                params.addParameter(StringUtils.decodeURLIgnorePlus(key), StringUtils.decodeURLIgnorePlus(val));
                // if ("id".equals(key)) hdl = val;
            }
        }
    }

    /** Resolve the handle for this request based on the query parameters.  A side
     * effect of this method is that the resRequest and resResponse members are set
     * to non-null values (well, resResponse may be null if there was an exception
     * during resolution.
     */
    public synchronized void resolveHandle() throws HandleException {

        byte types[][] = getRequestedTypes();
        int indices[] = null;
        try {
            indices = getRequestedIndices();
        } catch (Exception e) {
        } // we used to throw an exception here, now just ignore badly formatted indices

        resolveHandle(types, indices, authenticationInfo == null, params.getParameter("auth") != null, params.getParameter("cert") != null);

        if (resResponse != null && resResponse.responseCode == AbstractMessage.RC_INVALID_ADMIN && !resRequest.ignoreRestrictedValues) {
            resolveHandle(types, indices, true, resRequest.authoritative, resRequest.certify);
        }
    }

    /** send or re-send a resolution request for the requested handle with the given parameters */
    public synchronized void resolveHandle(byte types[][], int indices[], boolean ignoreRestrictedValues, boolean authoritative, boolean certified) throws HandleException {
        resRequest = new ResolutionRequest(Util.encodeString(hdl), types, indices, authenticationInfo);
        resRequest.ignoreRestrictedValues = ignoreRestrictedValues;
        resRequest.authoritative = authoritative;
        resRequest.certify = certified;
        resolutionTime = System.currentTimeMillis();
        resResponse = resolver.processRequest(resRequest, getRemoteInetAddress());
    }

    // Stores information about location types from a handle or handles.
    // The expiration information is only used when considering a single handle.
    public static class HandleLocationInfo {
        private long expirationDate = System.currentTimeMillis() + 60000 * 60; // default cache timeout: one hour
        public List<XTag> loc;
        public List<XTag> links;
        public List<String> headers;

        boolean isExpired() {
            return expirationDate <= System.currentTimeMillis();
        }

        public static boolean mergeIntoHandleLocationInfo(HDLServletRequest.HandleLocationInfo res, HDLServletRequest.HandleLocationInfo locInfo) {
            if (locInfo == null) return false;
            boolean found = false;
            if (locInfo.loc != null) {
                if (res.loc == null) res.loc = new ArrayList<>();
                res.loc.addAll(locInfo.loc);
                found = true;
            }
            if (locInfo.links != null) {
                if (res.links == null) res.links = new ArrayList<>();
                res.links.addAll(locInfo.links);
                found = true;
            }
            if (locInfo.headers != null) {
                if (res.headers == null) res.headers = new ArrayList<>();
                res.headers.addAll(locInfo.headers);
                found = true;
            }
            return found;
        }
    }

    private static XParser xmlParser = new XParser();

    public HandleLocationInfo resolveLocationsViaNamespace(List<String> handles) {
        if (handles == null || handles.isEmpty()) return null;
        HandleLocationInfo res = new HandleLocationInfo();
        boolean found = false;
        for (String handle : handles) {
            HandleLocationInfo locInfo = resolveLocationsViaNamespace(handle);
            boolean thisFound = HandleLocationInfo.mergeIntoHandleLocationInfo(res, locInfo);
            found = found || thisFound;
        }
        if (!found) return null;
        return res;
    }

    public HandleLocationInfo resolveLocationsViaNamespace(String handle) {
        boolean auth = params.getParameter("auth") != null;
        boolean cert = params.getParameter("cert") != null;
        try {
            ResolutionRequest hdlReq = new ResolutionRequest(Util.encodeString(handle), Location.NAMESPACE_TYPES, null, null);
            hdlReq.authoritative = auth;
            hdlReq.certify = cert;
            long nsResolutionTime = System.currentTimeMillis();
            AbstractResponse hdlResp = resolver.processRequest(hdlReq, null);
            if (hdlResp instanceof ResolutionResponse) {
                HandleValue[] values = ((ResolutionResponse) hdlResp).getHandleValues();
                for (HandleValue value : values) {
                    if (value.hasType(Common.NAMESPACE_INFO_TYPE)) {
                        modifyExpiration(nsResolutionTime, value);
                    }
                }
                NamespaceInfo nsInfo = Util.getNamespaceFromValues(handle, values);
                if (nsInfo == null) return null;
                List<String> nsHandles = nsInfo.getLocationTemplateHandles();
                if (nsHandles != null) return resolveLocationsForMultipleHandles(nsHandles);
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error resolving namespace for " + handle);
            return null;
        }
    }

    /**
     * Find and return all XML location structures in the given handles,
     * caching the parsed locations so as to avoid re-parsing the handle values
     * (which may also be cached at the handle resolution level).
     */
    public HandleLocationInfo resolveLocationsForMultipleHandles(List<String> handles) {
        if (handles == null || handles.isEmpty()) return null;
        HandleLocationInfo res = new HandleLocationInfo();
        boolean found = false;
        for (String handle : handles) {
            HandleLocationInfo locInfo = resolveLocations(handle);
            boolean thisFound = HandleLocationInfo.mergeIntoHandleLocationInfo(res, locInfo);
            found = found || thisFound;
        }
        if (!found) return null;
        return res;
    }

    private HandleLocationInfo resolveLocations(String handle) {
        boolean auth = params.getParameter("auth") != null;
        boolean cert = params.getParameter("cert") != null;
        HandleLocationInfo cachedVal = thisRequestLocationsCache.get(handle);
        if (cachedVal != null) return cachedVal;
        if (!auth && !cert) cachedVal = locationsCache.get(handle);
        if (cachedVal != null && !cachedVal.isExpired()) {
            if (cachedVal.expirationDate < expiration) expiration = cachedVal.expirationDate;
            thisRequestLocationsCache.put(handle, cachedVal);
            return cachedVal;
        }

        try {
            ResolutionRequest hdlReq = new ResolutionRequest(Util.encodeString(handle), Location.ALL_LOCATION_TYPES, null, null);
            hdlReq.authoritative = auth;
            hdlReq.certify = cert;
            long locResolutionTime = System.currentTimeMillis();
            // Since caller is null, this will not be logged.
            AbstractResponse hdlResp = resolver.processRequest(hdlReq, null);
            if (hdlResp instanceof ResolutionResponse) {
                HandleLocationInfo res = new HandleLocationInfo();
                boolean found = false;
                for (HandleValue value : ((ResolutionResponse) hdlResp).getHandleValues()) {
                    if (value.hasType(Location.LOCATION_TYPE)) {
                        String locationsStr = Util.decodeString(value.getData());
                        XTag locXML = xmlParser.parse(new StringReader(locationsStr), false);
                        if (locXML != null) {
                            modifyExpiration(locResolutionTime, value);
                            res.expirationDate = Math.min(res.expirationDate, expirationDateFor(locResolutionTime, value));
                            if (res.loc == null) res.loc = new ArrayList<>();
                            res.loc.add(locXML);
                            found = true;
                        }
                    } else if (value.hasType(Location.LINKS_TYPE)) {
                        String locationsStr = Util.decodeString(value.getData());
                        XTag locXML = xmlParser.parse(new StringReader(locationsStr), false);
                        if (locXML != null) {
                            modifyExpiration(locResolutionTime, value);
                            res.expirationDate = Math.min(res.expirationDate, expirationDateFor(locResolutionTime, value));
                            if (res.links == null) res.links = new ArrayList<>();
                            res.links.add(locXML);
                            found = true;
                        }
                    } else if (value.hasType(Location.HEADERS_TYPE)) {
                        String headersStr = Util.decodeString(value.getData());
                        if (headersStr != null) {
                            modifyExpiration(locResolutionTime, value);
                            res.expirationDate = Math.min(res.expirationDate, expirationDateFor(locResolutionTime, value));
                            if (res.headers == null) res.headers = new ArrayList<>();
                            res.headers.add(headersStr);
                            found = true;
                        }
                    }
                }
                if (found) {
                    thisRequestLocationsCache.put(handle, res);
                    locationsCache.put(handle, res);
                    return res;
                }
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error resolving locations for " + handle);
            return null;
        }

    }

    /** Remove any prefixes such as hdl: doi: info:doi/ etc. */
    public void normalizeHandle() {
        String lowerHdl = hdl.toLowerCase();
        if (lowerHdl.startsWith("api/handles/")) {
            api = true;
            hdl = hdl.substring(12);
            lowerHdl = lowerHdl.substring(12);
        } else if (lowerHdl.startsWith("api/")) {
            api = true;
            oldApi = true;
            hdl = hdl.substring(4);
            lowerHdl = lowerHdl.substring(4);
        }
        if (lowerHdl.startsWith("info:doi/") || lowerHdl.startsWith("info:hdl/")) {
            hdl = hdl.substring(9);
        } else if (lowerHdl.startsWith("hdl:") || lowerHdl.startsWith("doi:")) {
            hdl = hdl.substring(4);
        } else if (lowerHdl.startsWith("urn:hdl:") || lowerHdl.startsWith("urn:doi:") || lowerHdl.startsWith("urn:eidr:")) {
            int colon = hdl.indexOf(':', hdl.indexOf(':') + 1);
            hdl = hdl.substring(colon + 1);
            // First colon in a handle URN will be treated as a slash
            colon = hdl.indexOf(':');
            int slash = hdl.indexOf('/');
            if (colon >= 0 && (slash < 0 || slash > colon)) {
                hdl = hdl.substring(0, colon) + "/" + hdl.substring(colon + 1);
            }
        }
    }

    /** Return the URL from which the request was linked */
    public String getReferer() {
        String ref = req.getHeader("Referer");
        return ref == null ? "" : ref;
    }

    public List<String> getRequestHeader(String headerName) {
        List<String> values = new ArrayList<>();

        Enumeration<?> hvals = req.getHeaders(headerName.toString());
        while (hvals.hasMoreElements()) {
            String hval = String.valueOf(hvals.nextElement());
            values.add(hval);
        }

        return values;
    }

    public long getResponseTime() {
        return System.currentTimeMillis() - intime;
    }

    private int[] getRequestedIndices() {
        // parse requested types and indices from http request
        int indices[] = null;
        String s[] = params.getParameterValues("index");
        if (s != null) {
            indices = new int[s.length];
            for (int i = 0; i < s.length; i++) indices[i] = Integer.parseInt(s[i]);
        }
        return indices;
    }

    private byte[][] getRequestedTypes() {
        // parse requested types and indices from http request
        byte types[][] = null;
        String s[] = params.getParameterValues("type");
        if (s != null) {
            types = new byte[s.length][];
            for (int i = 0; i < s.length; i++) types[i] = Util.encodeString(s[i]);
        }
        return types;
    }

    public String getURLForHandle(String handle) {
        return getURLForHandle(handle, "");
    }

    public String getURLForHandle(String handle, String query) {
        if (query == null) query = "";
        String baseURL = servlet.getHandleLinkPrefix(req);
        if (baseURL.endsWith("/") || baseURL.endsWith(":")) return baseURL + StringUtils.encodeURLPath(handle) + query;
        else return baseURL + "/" + StringUtils.encodeURLPath(handle) + query;
    }

    /**
     * Encodes an IRI as a URI.  Finds the domain name of an http:, https:, or ftp: URI and IDNA-encodes it (Nameprep/Punycode).
     * This is required to have the browser find the location.
     * Officially that step is optional and in fact we'll skip it if there's any problem (including if the IDNA jarfile is missing).
     * Then percent-encodes everything else.
     */
    public static String encodeIRIToURI(String location) {
        String lower = location.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://")) {
            int startOfHost = location.indexOf(':') + 3;
            boolean potentialIDN = false;
            int endOfHost = location.length();
            int colon = endOfHost;
            boolean seenAt = false, seenColon = false;
            for (int i = startOfHost; i < endOfHost; i++) {
                char ch = location.charAt(i);
                if (ch >= 128 && !seenColon) potentialIDN = true;
                else if (!seenAt && ch == '@') {
                    seenAt = true;
                    seenColon = false;
                    startOfHost = i + 1;
                    potentialIDN = false;
                } else if (ch == '/' || ch == '?' || ch == '#') {
                    endOfHost = i;
                    break;
                } else if (!seenColon && ch == ':') {
                    seenColon = true;
                    colon = i;
                    if (seenAt) break;
                }
            }
            if (seenColon) endOfHost = colon;

            if (potentialIDN) {
                String host = location.substring(startOfHost, endOfHost);
                try {
                    String encodedHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
                    StringBuilder sb = new StringBuilder(location);
                    sb.replace(startOfHost, endOfHost, encodedHost);
                    return StringUtils.encodeURL(sb.toString());
                } catch (Exception e) {
                    // fall-through
                }
            }
        }

        return StringUtils.encodeURL(location);
    }

    /**
     * Send an HTTP redirect to the given location.  The kind of redirect depends
     * upon the given redirectType.
     */
    public void sendHTTPRedirect(ResponseType redirectType, String location) {
        if (req.getProtocol().startsWith("HTTP/1.0") && redirectType != ResponseType.MOVED_PERMANENTLY) {
            redirectType = ResponseType.OLD_MOVED_TEMPORARILY;
        }
        if (params.getParameter("redirectionHeaders") == null) {
            response.setStatus(redirectType.responseCode);
        }
        response.setHeader("Location", encodeIRIToURI(location.trim()));
        if (getExpiration() < Long.MAX_VALUE) {
            response.setDateHeader("Expires", getExpiration());
        }
    }

    /** Set the expiration date sooner based on a value from the resRequest/resResponse fields */
    public void modifyExpiration(HandleValue val) {
        modifyExpiration(resolutionTime, val);
    }

    /** Set the expiration date sooner based on a value which may come from a different resolution */
    public void modifyExpiration(long aResolutionTime, HandleValue val) {
        long newExpiration = expirationDateFor(aResolutionTime, val);
        if (newExpiration < expiration) {
            expiration = newExpiration;
        }
    }

    public long expirationDateFor(long aResolutionTime, HandleValue val) {
        long newExpiration;
        if (val.getTTLType() == HandleValue.TTL_TYPE_ABSOLUTE) {
            newExpiration = 1000L * val.getTTL();
        } else {
            newExpiration = aResolutionTime + 1000L * val.getTTL();
        }
        newExpiration = Math.min(newExpiration, aResolutionTime + 1000L * HandleValue.MAX_RECOGNIZED_TTL);
        return newExpiration;
    }

    public long getExpiration() {
        return expiration;
    }

    public static class AcceptEntry {
        public String value;
        public float quality = 1f;

        AcceptEntry(String acceptFields[]) {
            this.value = acceptFields == null || acceptFields.length <= 0 ? "*/*" : acceptFields[0];
            if (this.value != null) this.value = this.value.trim().toLowerCase();
            if (acceptFields != null && acceptFields.length > 1) {
                // only look for "q" fields
                for (int i = 1; i < acceptFields.length; i++) {
                    String field = acceptFields[i].trim();
                    if (field.startsWith("q=")) {
                        try {
                            quality = Float.parseFloat(field.substring(2));
                        } catch (Exception nfe) {
                        }
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "{" + value + "; q=" + quality + "} (normal=" + acceptsBrowserContent(value) + ")";
        }

        public static boolean acceptsBrowserContent(String contentType) {
            if (contentType == null) return true;
            if (contentType.indexOf('*') >= 0) return true;
            if (SPECIFIC_BROWSER_CONTENT_TYPES.containsKey(contentType)) return true;
            if (contentType.startsWith("image/")) return true;
            if (contentType.startsWith("audio/")) return true;
            if (contentType.startsWith("video/")) return true;
            return false;
        }

        public static boolean hasWildcard(String contentType) {
            return contentType == null || contentType.indexOf('*') >= 0;
        }

    }

    public InetAddress getRemoteInetAddress() {
        return remoteInetAddress;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public String getUriPathAndParams() {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.encodeURLPath(hdl));
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : params.getMap().entrySet()) {
            String key = entry.getKey();
            for (String val : entry.getValue()) {
                if (first) sb.append("?");
                else sb.append("&");
                first = false;
                sb.append(StringUtils.encodeURLComponentMinimal(key));
                if (!val.isEmpty()) {
                    sb.append("=");
                    sb.append(StringUtils.encodeURLComponentMinimal(val));
                }
            }
        }
        return sb.toString();
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy.handlers;

import net.handle.apps.servlet_proxy.*;
import net.handle.apps.servlet_proxy.HDLServletRequest.HandleLocationInfo;
import net.handle.apps.servlet_proxy.HDLServletRequest.ResponseType;
import net.handle.hdllib.*;
import net.cnri.simplexml.*;
import net.cnri.util.StringUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;

import javax.servlet.http.HttpServletResponse;

public class Location implements TypeHandler {
    public static final String LOCATIONS_TAG_NAME = "locations";
    public static final String CHOOSEBY_COUNTRY = "country";
    public static final String CHOOSEBY_WEIGHTED = "weighted";
    public static final String CHOOSEBY_LINKPARAM = "locatt";
    public static final String CHOOSEBY_ADDRESS = "address";
    public static final String CHOOSEBY_SCORE = "score";
    public static final String DEFAULT_CHOOSEBY[] = { "locatt", "address", "country", "score", "weighted" };
    public static final String CHOOSEBY_OLD_ATTRIBUTE = "chooseby";
    public static final String CHOOSEBY_ATTRIBUTE = "choose_by";
    public static final String SUPPRESS_LEGACY = "no_legacy";
    public static final String SUPPRESS_NAMESPACE_LOCS = "no_nslocs";
    public static final String ACCEPT_HEADER_STRING = "Accept";
    // Attribute of <locations> tag, adds a handle to be included as namespace locations
    public static final String NSHDL_ATTRIBUTE = "nshdl";
    public static final String HDL_TEMPLATE_STRING = "{hdl}";
    public static final String HDL_PATH_TEMPLATE_STRING = "{hdl:path}";
    public static final String LOC_ATTRIBUTE = "href";
    public static final String LOC_TEMPLATE_ATTRIBUTE = "href_template";
    public static final String LOC_TAG_NAME = "location";
    public static final String OLD_LOC_TEMPLATE_TAG_NAME = "loc_template";
    public static final String REDIRECT_TYPE_ATT = "http_sc";
    public static final String WEIGHT_ATTRIBUTE = "weight";
    public static final String LOC_ATT_HTTP_ROLE = "http_role";
    public static final String LOC_ATT_VALUE_CONNEG = "conneg";
    public static final String ADDRESSES_ATT = "addresses";

    public static final byte LOCATION_TYPE[] = Util.encodeString("10320/loc");
    public static final byte LINKS_TYPE[] = Util.encodeString("10320/links");
    public static final byte HEADERS_TYPE[] = Util.encodeString("10320/headers");
    public static final byte[][] ALL_LOCATION_TYPES = { LOCATION_TYPE, LINKS_TYPE, HEADERS_TYPE };
    public static final byte[][] NAMESPACE_TYPES = { Common.NAMESPACE_INFO_TYPE };
    public static final byte URL_TYPE[] = Util.encodeString("URL");

    private final XParser parser = new XParser();

    private final Object lookupInitLock = new Object();
    private boolean lookupServiceInitialized = false;
    private boolean lookupServiceUnavailable;
    private Object geoIP = null;
    private final Random random = new Random();
    private final boolean debug = false;

    /** Return the country code that should be used to */
    private String getCountryCode(HDLServletRequest req) {
        String countryCode = req.params.getParameter("country");
        if (countryCode != null) return countryCode;

        if (lookupServiceUnavailable) return null;

        String ipAddress = req.params.getParameter("ip");
        if (ipAddress == null) {
            ipAddress = req.getRemoteAddr();
        }

        if (!lookupServiceInitialized) { // first 'if' is non-synchronized for speed
            synchronized (lookupInitLock) {
                if (!lookupServiceInitialized) { // this 'if' is synchronized for safety
                    try {
                        String dbFile = req.servlet.getInitParameter("geoip_data_file");
                        if (dbFile == null) {
                            dbFile = req.servlet.getServletContext().getRealPath("/WEB-INF/GeoIP.dat");
                        }
                        System.err.println("Creating GeoIP LookupService using " + dbFile);
                        Class<?> klass = Class.forName("com.maxmind.geoip.LookupService");
                        Constructor<?> constructor = klass.getConstructor(String.class, Integer.TYPE);
                        geoIP = constructor.newInstance(dbFile, Integer.valueOf(3));
                        //            geoIP = new LookupService(dbFile,
                        //                                      LookupService.GEOIP_MEMORY_CACHE |
                        //                                      LookupService.GEOIP_CHECK_CACHE);
                    } catch (Exception e) {
                        System.err.println("Unable to initialize country lookup service: " + e);
                        e.printStackTrace(System.err);
                        lookupServiceUnavailable = true;
                        return null;
                    }
                    lookupServiceInitialized = true;
                }
            }
        }

        if (geoIP != null) {
            try {
                Method method = geoIP.getClass().getMethod("getCountry", String.class);
                Object country = method.invoke(geoIP, ipAddress);
                //System.err.println("Got country: "+country.getCode()+" for IP "+ipAddress);
                method = country.getClass().getMethod("getCode");
                return (String) method.invoke(country);
            } catch (Exception e) {
                System.err.println("Error resolving country for request: " + req);
            }
        }
        return null;
    }

    /**
     * Return true if the handle values contain a 10320/loc value or a value with
     * a sub-type of 10320/loc.
     */
    @Override
    public boolean canRedirect(HandleValue values[]) {
        return hasLocationOrUrlType(values);
    }

    /** Return true if the handle values contain either a URL or 10320/loc value */
    @Override
    public boolean canShowLocations(HandleValue values[]) {
        return hasLocationOrUrlType(values);
    }

    private boolean hasLocationOrUrlType(HandleValue[] values) {
        if (values == null) return false;
        for (int i = values.length - 1; i >= 0; i--) {
            if (values[i].hasType(LOCATION_TYPE) || values[i].hasType(URL_TYPE)) return true;
        }
        return false;
    }

    /**
     * Return true iff this TypeHandler can format the data from the given
     * HandleValue for a human client.
     */
    @Override
    public boolean canFormat(HandleValue value) {
        return false;
        //return value!=null && value.hasType(LOCATION_TYPE);
    }

    @Override
    public String toHTML(String handle, HandleValue value) {
        // a nicer display would be as a list of links
        return "<pre>" + StringUtils.cgiEscape(value.getDataAsString()) + "<pre>";
    }

    /** Gather all of the known locations and put them into an XML structure */
    @Override
    public XTag doShowLocations(HDLServletRequest req, HandleValue values[]) throws Exception {
        LocContext locContext = determineLocContext(req, values);
        if (locContext == null) return null;
        if (locContext.isLegacyOnly()) {
            return legacyOnlyUrlTypesDoShowLocations(locContext);
        }
        String name = LOCATIONS_TAG_NAME;
        if (locContext.locXMLs != null && !locContext.locXMLs.isEmpty()) {
            name = locContext.locXMLs.get(0).getName();
        }
        XTag locs = new XTag(name);
        if (locContext.locations != null) {
            for (XTag loc : locContext.locations) {
                locs.addSubTag(processTemplate(loc, req.hdl));
            }
        }
        return locs;
    }

    /**
     * Take any URL handle values from the given list and put them into an array
     * of XTags that describe a set of locations.
     * If there are URL types and URL.foo types, give them different weights.  If only URL.foo types, give them the higher default weight.
     */
    private static final void extractLegacyURLs(HDLServletRequest req, HandleValue values[], List<XTag> urlLocs, double defaultWeight, double defaultSubTypeWeight) {
        boolean hasJustUrl = false;
        for (int i = 0; values != null && i < values.length; i++) {
            if (Util.equalsCI(values[i].getType(), Common.STD_TYPE_URL)) {
                hasJustUrl = true;
                break;
            }
        }
        if (!hasJustUrl) defaultSubTypeWeight = defaultWeight;
        for (int i = 0; values != null && i < values.length; i++) {
            if (!values[i].hasType(Common.STD_TYPE_URL)) continue;
            req.modifyExpiration(values[i]);
            double weight = defaultSubTypeWeight;
            if (hasJustUrl && Util.equalsCI(values[i].getType(), Common.STD_TYPE_URL)) weight = defaultWeight;
            addLegacyLocationToList(values[i], weight, urlLocs);
        }
    }

    private static void addLegacyLocationToList(HandleValue val, double weight, List<XTag> urlLocs) {
        XTag urltag = new XTag(LOC_TAG_NAME);
        urltag.setAttribute(LOC_ATTRIBUTE, val.getDataAsString());
        urltag.setAttribute(WEIGHT_ATTRIBUTE, String.valueOf(weight));
        urltag.setAttribute("mode", "legacy");
        urlLocs.add(urltag);
    }

    /**
     * Find any namespace locations based on the handle resolution and possibly any
     * overriding namespace identifier in the locations XML.
     */
    private HandleLocationInfo resolveNamespaceLocations(HDLServletRequest req, List<XTag> locXMLs, List<String> overridePrefixes) {
        if (overridePrefixes != null && !overridePrefixes.isEmpty()) {
            return req.resolveLocationsViaNamespace(overridePrefixes);
        }
        NamespaceInfo nsInfo = req.resRequest.getNamespace();
        List<String> nsHandles = null;
        if (nsInfo != null) nsHandles = nsInfo.getLocationTemplateHandles();
        if (nsHandles == null && locXMLs != null) {
            for (XTag locXML : locXMLs) {
                String nsHandle = locXML.getAttribute(NSHDL_ATTRIBUTE, null);
                if (nsHandle != null) {
                    if (nsHandles == null) nsHandles = new ArrayList<>();
                    nsHandles.add(nsHandle);
                }
            }
        }
        if (nsHandles == null) return null;
        return req.resolveLocationsForMultipleHandles(nsHandles);
    }

    private void addLocsSubTagsToList(List<XTag> locXMLs, List<XTag> locations) {
        if (locXMLs == null) return;
        for (XTag locXML : locXMLs) {
            for (int i = 0; locXML != null && i < locXML.getSubTagCount(); i++) {
                XTag subtag = locXML.getSubTag(i);
                if (!subtag.getName().equalsIgnoreCase(LOC_TAG_NAME) && !subtag.getName().equalsIgnoreCase(OLD_LOC_TEMPLATE_TAG_NAME)) continue;
                locations.add(subtag);
            }
        }
    }

    private LocContext determineLocContext(HDLServletRequest req, HandleValue values[]) throws Exception {
        if (values == null) return null;

        List<XTag> locXMLs = new ArrayList<>();
        List<XTag> linksFromLocs = new ArrayList<>();
        List<XTag> links = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        boolean hasLocationTypeValue = false;

        for (HandleValue value : values) {
            if (value.hasType(LOCATION_TYPE)) {
                hasLocationTypeValue = true;
                req.modifyExpiration(value);
                String locationsStr = Util.decodeString(value.getData());
                XTag locXML = parser.parse(new StringReader(locationsStr), false);
                if (locXML != null) {
                    locXMLs.add(locXML);
                    // locations also provide link headers
                    linksFromLocs.add(locXML);
                }
            } else if (value.hasType(LINKS_TYPE)) {
                hasLocationTypeValue = true;
                req.modifyExpiration(value);
                String linksStr = Util.decodeString(value.getData());
                XTag linksXML = parser.parse(new StringReader(linksStr), false);
                if (linksXML != null) {
                    links.add(linksXML);
                }
            } else if (value.hasType(HEADERS_TYPE)) {
                hasLocationTypeValue = true;
                req.modifyExpiration(value);
                String headersStr = Util.decodeString(value.getData());
                headers.add(headersStr);
            }
        }

        Settings settings = new Settings();
        settings.adjust(locXMLs);
        List<XTag> locations = new ArrayList<>();
        addLocsSubTagsToList(locXMLs, locations);
        processIncludes(req, locXMLs, locations, linksFromLocs, links, headers, settings, 0);
        boolean hasLocalHeaders = !links.isEmpty() || !headers.isEmpty();

        // add any namespace URLs to the list of locations
        List<XTag> nsLocXMLs = null;
        List<XTag> nsLocs = new ArrayList<>();
        if (settings.suppressNamespaceLocs == null || !settings.suppressNamespaceLocs) {
            HandleLocationInfo locInfo = resolveNamespaceLocations(req, locXMLs, settings.overridePrefixes);
            if (locInfo != null) {
                nsLocXMLs = locInfo.loc;
                addLocsSubTagsToList(locInfo.loc, nsLocs);
                settings.adjust(locInfo.loc);
                List<XTag> nsLinksFromLocs = new ArrayList<>();
                List<XTag> nsLinks = new ArrayList<>();
                List<String> nsHeaders = new ArrayList<>();
                processIncludes(req, nsLocXMLs, nsLocs, nsLinksFromLocs, nsLinks, nsHeaders, settings, 0);
                boolean suppress = false;
                if (settings.suppressNamespaceHeaders != null && settings.suppressNamespaceHeaders) {
                    suppress = true;
                }
                if (!suppress && hasLocalHeaders && settings.suppressNamespaceHeadersIfLocalHeaders != null && settings.suppressNamespaceHeadersIfLocalHeaders) {
                    suppress = true;
                }
                if (!suppress) {
                    if (locInfo.loc != null) linksFromLocs.addAll(locInfo.loc);
                    if (locInfo.links != null) links.addAll(locInfo.links);
                    if (locInfo.headers != null) headers.addAll(locInfo.headers);
                    linksFromLocs.addAll(nsLinksFromLocs);
                    links.addAll(nsLinks);
                    headers.addAll(nsHeaders);
                }
            }
        }
        // locations also provide link headers
        links.addAll(linksFromLocs);
        if (hasLocationTypeValue || !nsLocs.isEmpty()) {
            // add legacy URL handle values to the list of locations
            if (settings.suppressLegacy == null || !settings.suppressLegacy) {
                if (settings.urlWeight == null) {
                    if (locXMLs.isEmpty()) settings.urlWeight = 1.0;
                    else settings.urlWeight = 0.0;
                }
                if (settings.urlSubtypeWeight == null) {
                    settings.urlSubtypeWeight = 0.0;
                }
                extractLegacyURLs(req, values, locations, settings.urlWeight, settings.urlSubtypeWeight);
            }
            locations.addAll(nsLocs);
            return new LocContext(req, locXMLs, nsLocXMLs, links, headers, locations, null);
        } else {
            // legacy-only special code
            return new LocContext(req, locXMLs, nsLocXMLs, links, headers, null, values);
        }
    }

    // various settings defined in 10320/loc attributes
    private static class Settings {
        Boolean suppressLegacy = null;
        Boolean suppressNamespaceLocs = null;
        Double urlWeight = null;
        Double urlSubtypeWeight = null;
        Boolean suppressNamespaceHeaders = null;
        Boolean suppressNamespaceHeadersIfLocalHeaders = null;
        List<String> overridePrefixes = new ArrayList<>();

        void adjust(XTag locXML) {
            suppressNamespaceLocs = setFromFirstBoolAttribute(suppressNamespaceLocs, locXML, SUPPRESS_NAMESPACE_LOCS);
            suppressLegacy = setFromFirstBoolAttribute(suppressLegacy, locXML, SUPPRESS_LEGACY);
            suppressNamespaceHeaders = setFromFirstBoolAttribute(suppressNamespaceHeaders, locXML, "suppressNsHeaders");
            suppressNamespaceHeadersIfLocalHeaders = setFromFirstBoolAttribute(suppressNamespaceHeadersIfLocalHeaders, locXML, "suppressNsHeadersIfLocalHeaders");
            urlWeight = setFromFirstDoubleAttribute(urlWeight, locXML, "urlWeight");
            urlSubtypeWeight = setFromFirstDoubleAttribute(urlSubtypeWeight, locXML, "urlSubtypeWeight");
            String overridePrefix = locXML.getAttribute("overridePrefix");
            if (overridePrefix != null) overridePrefixes.add(overridePrefix);
        }

        void adjust(List<XTag> locXMLs) {
            if (locXMLs == null) return;
            for (XTag locXML : locXMLs) {
                adjust(locXML);
            }
        }
    }

    private static Boolean setFromFirstBoolAttribute(Boolean priorValue, XTag locXML, String key) {
        if (priorValue != null) return priorValue;
        String att = locXML.getAttribute(key);
        if (att == null) return null;
        return locXML.getBoolAttribute(key, false);
    }

    private static Double setFromFirstDoubleAttribute(Double priorValue, XTag locXML, String key) {
        if (priorValue != null) return priorValue;
        String att = locXML.getAttribute(key);
        if (att == null) return null;
        return locXML.getDoubleAttribute(key, 0);
    }

    private void processIncludes(HDLServletRequest req, List<XTag> locXMLs, List<XTag> locations, List<XTag> linksFromLocs, List<XTag> links, List<String> headers, Settings settings, int depth) {
        if (locXMLs == null) return;
        if (depth > 20) {
            System.err.println("Recursive 10320/loc include too deep");
            return;
        }
        HandleLocationInfo combinedLocInfo = new HandleLocationInfo();
        boolean found = false;
        for (XTag locXML : locXMLs) {
            List<String> includeNamespace = getAttributeAndSubtags(locXML, "includeNamespace");
            if (includeNamespace != null && !includeNamespace.isEmpty()) {
                HandleLocationInfo locInfo = req.resolveLocationsViaNamespace(includeNamespace);
                boolean thisFound = HandleLocationInfo.mergeIntoHandleLocationInfo(combinedLocInfo, locInfo);
                found = found || thisFound;
            }
            List<String> include = getAttributeAndSubtags(locXML, "include");
            if (include != null && !include.isEmpty()) {
                HandleLocationInfo locInfo = req.resolveLocationsForMultipleHandles(include);
                boolean thisFound = HandleLocationInfo.mergeIntoHandleLocationInfo(combinedLocInfo, locInfo);
                found = found || thisFound;
            }
        }
        if (found) {
            addLocsSubTagsToList(combinedLocInfo.loc, locations);
            settings.adjust(combinedLocInfo.loc);
            processIncludes(req, combinedLocInfo.loc, locations, linksFromLocs, links, headers, settings, depth + 1);
            // always add the headers here, and suppress afterward if necessary
            if (combinedLocInfo.loc != null) linksFromLocs.addAll(combinedLocInfo.loc);
            if (combinedLocInfo.links != null) links.addAll(combinedLocInfo.links);
            if (combinedLocInfo.headers != null) headers.addAll(combinedLocInfo.headers);
        }
    }

    private List<String> getAttributeAndSubtags(XTag locXML, String name) {
        List<String> res = new ArrayList<>();
        String att = locXML.getAttribute(name);
        if (att != null) res.add(att);
        String[] subTags = locXML.getStrListSubTag(name);
        if (subTags != null) {
            res.addAll(Arrays.asList(subTags));
        }
        return res;
    }

    @Override
    public boolean doRedirect(HDLServletRequest req, HandleValue values[]) throws Exception {
        LocContext locContext = determineLocContext(req, values);
        if (locContext == null) return false;
        if (locContext.isLegacyOnly()) {
            return legacyOnlyUrlTypesDoRedirect(locContext);
        }
        if (chooseLocationFromValue(locContext)) {
            return true;
        }
        if (debug) System.err.println("Didn't find any acceptable redirects...");
        return false;
    }

    private boolean legacyOnlyUrlTypesDoRedirect(LocContext locContext) throws IOException {
        HDLServletRequest req = locContext.req;
        HandleValue[] values = locContext.legacyValues;

        // use legacy proxy behavior
        String redirectURL = null;

        // first check for base URL values
        for (HandleValue val : values) {
            if (val == null) continue;
            byte[] valType = val.getType();
            if (valType == null) continue;
            if (Util.equalsCI(valType, Common.STD_TYPE_URL) || Util.equalsCI(valType, Url.zeroDotTypeUrl)) {
                req.modifyExpiration(val);
                redirectURL = val.getDataAsString();
                break;
            }
        }

        // if no simple URL value exists, look for URL sub-types
        if (redirectURL == null) {
            for (HandleValue val : values) {
                if (val == null) continue;
                if (val.hasType(Common.STD_TYPE_URL) || val.hasType(Url.zeroDotTypeUrl)) {
                    req.modifyExpiration(val);
                    redirectURL = val.getDataAsString();
                    break;
                }
            }
        }

        if (redirectURL == null) {
            // no value was found with type URL or any subtypes
            return false;
        }

        String urlSuffix = req.params.getParameter("urlappend");
        if (urlSuffix == null) urlSuffix = "";
        // already decoded
        //        else urlSuffix = StringUtils.decodeURLIgnorePlus(urlSuffix);

        // send a redirect to the URL, with any suffix provided by the user
        try {
            sendHttpRedirectResponse(locContext, null, redirectURL + urlSuffix);
            return true;
        } catch (Exception e) {
            if (e.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")) throw (IOException) e;
            if (e.getClass().getName().equals("org.eclipse.jetty.io.EofException")) throw e;
            System.err.println("Error in legacy case of Location.doRedirect for " + req.hdl + ": " + e);
        }
        return false;
    }

    private XTag legacyOnlyUrlTypesDoShowLocations(LocContext locContext) {
        List<XTag> locations = new ArrayList<>();
        HDLServletRequest req = locContext.req;
        HandleValue[] values = locContext.legacyValues;
        // first check for base URL values
        boolean found = false;
        for (HandleValue val : values) {
            if (val == null) continue;
            byte[] valType = val.getType();
            if (valType == null) continue;
            if (Util.equalsCI(valType, Common.STD_TYPE_URL) || Util.equalsCI(valType, Url.zeroDotTypeUrl)) {
                double weight = found ? 0 : 1;
                found = true;
                addLegacyLocationToList(val, weight, locations);
            }
        }
        if (!found) {
            for (HandleValue val : values) {
                if (val == null) continue;
                if (val.hasType(Common.STD_TYPE_URL) || val.hasType(Url.zeroDotTypeUrl)) {
                    double weight = found ? 0 : 1;
                    found = true;
                    addLegacyLocationToList(val, weight, locations);
                }
            }
        }
        String name = LOCATIONS_TAG_NAME;
        XTag locs = new XTag(name);
        for (XTag loc : locations) {
            locs.addSubTag(processTemplate(loc, req.hdl));
        }
        return locs;
    }

    private static boolean someLocationDoesConneg(List<XTag> locations) {
        for (XTag loc : locations) {
            if (loc.getAttribute("http_role", "").contains("conneg")) {
                return true;
            }
        }
        return false;
    }

    private static void addVaryAccept(HttpServletResponse resp) {
        resp.addHeader("Vary", "Accept");
    }

    private boolean chooseLocationFromValue(LocContext locContext) throws Exception {
        HDLServletRequest req = locContext.req;
        List<XTag> locations = locContext.locations;
        if (debug) System.err.println("hdl:" + req.hdl + " locations: " + locations);
        if (locations.isEmpty()) return false;
        String chooseByStr = locContext.getAttribute(null, CHOOSEBY_ATTRIBUTE, null);
        if (chooseByStr == null) {
            chooseByStr = locContext.getAttribute(null, CHOOSEBY_OLD_ATTRIBUTE, null);
        }
        String chooseBy[] = chooseByStr == null ? DEFAULT_CHOOSEBY : chooseByStr.split(",");

        if (someLocationDoesConneg(locations)) addVaryAccept(req.response);

        // find appropriate locations according to the chooseby attribute
        for (String element : chooseBy) {
            if (locations.size() == 1) {
                // only one location left, redirect to it
                return sendRedirect(locContext, locations.get(0));
            }

            String fieldType = element.trim().toLowerCase();
            if (fieldType.length() <= 0) continue; // skip blank values

            if (fieldType.equals(CHOOSEBY_COUNTRY)) {
                // narrow down the list of choices based on the country in which the
                // caller is located
                List<XTag> countryLocs = filterBasedOnCountry(locations, req);
                // if there is at least one country match, use it/them
                // if there are no country matches, don't filter by country at all
                if (countryLocs.size() > 0) {
                    locations = countryLocs;
                }
            } else if (fieldType.equals(CHOOSEBY_ADDRESS)) {
                String ipAddress = req.getRemoteAddr();
                if (ipAddress == null) continue;
                List<XTag> newLocs = filterBasedOnAddress(locations, ipAddress);
                if (!newLocs.isEmpty()) locations = newLocs;
            } else if (fieldType.equals(CHOOSEBY_LINKPARAM)) {
                // the preferred location(s) are indicated by a parameter in the URI
                // try each locatt parameter in order to select the locations
                String linkParams[] = req.params.getParameterValues("locatt");
                if (linkParams == null || linkParams.length <= 0) continue;
                for (String linkParam : linkParams) {
                    if (linkParam != null) {
                        List<XTag> newlocs = filterBasedOnLinkParam(locations, linkParam);
                        // if there is at least one attribute match, use it/them
                        // if there are no matches, use the whole set
                        if (newlocs.size() > 0) {
                            locations = newlocs;
                        }
                    }
                }
            } else if (fieldType.equals(CHOOSEBY_SCORE)) {
                List<XTag> newLocs = filterBasedOnScore(locations);
                locations = newLocs;
            } else if (fieldType.equals(CHOOSEBY_WEIGHTED)) {
                return sendWeightedRedirect(locContext, locations);
            }
        }
        return sendWeightedRedirect(locContext, locations);
    }

    private List<XTag> filterBasedOnCountry(List<XTag> locations, HDLServletRequest req) {
        String myCountry = null;
        List<XTag> countryLocs = new ArrayList<>();
        for (int v = 0; v < locations.size(); v++) {
            XTag loctag = locations.get(v);
            String locCountry = loctag.getStrAttribute("country", null);
            if (locCountry != null && myCountry == null) {
                // lazy lookup of country from IP address
                myCountry = getCountryCode(req);
                if (myCountry == null) {
                    System.err.println("Couldn't determine country from address: " + req.getRemoteAddr());
                    break;
                }
            }
            if (locCountry != null && myCountry != null && myCountry.equalsIgnoreCase(locCountry.trim())) {
                // the country is specified and matches
                countryLocs.add(loctag);
            } else if (locCountry != null) {
                // the country is specified, but doesn't match.  Remove it from
                // consideration for resolution
            }
        }
        return countryLocs;
    }

    private List<XTag> filterBasedOnAddress(List<XTag> locations, String ipAddress) throws UnknownHostException {
        BigInteger target = null;
        List<XTag> newLocs = new ArrayList<>();
        for (XTag locTag : locations) {
            String addresses = locTag.getStrAttribute(ADDRESSES_ATT, null);
            if (addresses == null) continue;
            if (target == null) target = CIDRUtils.asBigInteger(ipAddress);
            String[] addressesList = addresses.split(",");
            for (String address : addressesList) {
                address = address.trim();
                CIDRUtils range = new CIDRUtils(address);
                if (range.isInRange(target)) {
                    newLocs.add(locTag);
                }
            }
        }
        return newLocs;
    }

    private List<XTag> filterBasedOnLinkParam(List<XTag> locations, String linkParam) {
        int colIdx = linkParam.indexOf(':');
        String attName = colIdx < 0 ? linkParam.trim() : linkParam.substring(0, colIdx).trim();
        String attVal = colIdx < 0 ? null : linkParam.substring(colIdx + 1);
        ArrayList<XTag> newlocs = new ArrayList<>();
        for (int v = 0; v < locations.size(); v++) {
            XTag loctag = locations.get(v);
            String att = loctag.getAttribute(attName, null);
            if (att != null) {
                if (attVal == null || attVal.equals(att)) {
                    newlocs.add(loctag);
                }
            }
        }
        return newlocs;
    }

    private List<XTag> filterBasedOnScore(List<XTag> locations) {
        double maxScore = 0;
        List<XTag> newLocs = new ArrayList<>();
        for (XTag locTag : locations) {
            double score = locTag.getDoubleAttribute("score", 0d);
            if (score > maxScore) {
                maxScore = score;
                newLocs.clear();
            }
            if (score == maxScore) {
                newLocs.add(locTag);
            }
        }
        return newLocs;
    }

    private boolean sendWeightedRedirect(LocContext locContext, List<XTag> locations) throws IOException {
        if (debug) System.err.println("Sending weighted redirect of " + locations);
        if (locations.size() <= 0) {
            // no available locations!
            return false;
        }

        // calculate the sum of the non-negative location weights
        double totalWeight = 0f;
        for (int i = locations.size() - 1; i >= 0; i--) {
            double weight = locations.get(i).getDoubleAttribute(WEIGHT_ATTRIBUTE, 1f);
            totalWeight += Math.max(0f, weight);
        }

        int randIdx = 0;
        if (totalWeight > 0) {
            double randVal = random.nextDouble() * totalWeight;
            totalWeight = 0f;
            XTag loc = null;
            for (int i = locations.size() - 1; i >= 0; i--) {
                loc = locations.get(i);
                totalWeight += Math.max(0f, loc.getDoubleAttribute(WEIGHT_ATTRIBUTE, 1f));
                if (totalWeight >= randVal) {
                    return sendRedirect(locContext, loc);
                }
            }
            if (loc != null) { // shouldn't happen... return the last location
                return sendRedirect(locContext, loc);
            } else {
                // there were no locations!  can't get here
                return false;
            }
        } else {
            // the items all have zero (or less) weight... rare but might happen
            randIdx = (int) Math.round(Math.abs(random.nextDouble()) * locations.size());
            randIdx = Math.max(0, Math.min(locations.size() - 1, randIdx));
            return sendRedirect(locContext, locations.get(randIdx));
        }
    }

    /**
     * Return an XTag with any template href values converted to regular hrefs.
     * If there are any templates, then the returned XTag may be different than
     * the one passed to this method.  For efficiency reasons this will return the
     * exact same XTag if no templates needed to be processed.
     */
    private XTag processTemplate(XTag locInfo, String handle) {
        if (locInfo.getStrAttribute(LOC_ATTRIBUTE, null) == null) {
            String locTemplate = locInfo.getStrAttribute(LOC_TEMPLATE_ATTRIBUTE, null);
            if (locTemplate != null) {
                String loc = processTemplate(locTemplate, handle);
                locInfo = locInfo.shallowCloneTag();
                locInfo.setAttribute(LOC_ATTRIBUTE, loc);
                return locInfo;
            }
        }
        return locInfo;
    }

    private String processTemplate(String locTemplate, String handle) {
        String res = locTemplate.replace(HDL_TEMPLATE_STRING, StringUtils.encodeURLComponent(handle));
        res = res.replace(HDL_PATH_TEMPLATE_STRING, StringUtils.encodeURLPath(handle));
        return res;
    }

    private boolean sendRedirect(LocContext locContext, XTag locInfo) throws IOException {
        HDLServletRequest req = locContext.req;
        try {
            if (debug) System.err.println("Redirecting to: " + locInfo);

            locInfo = processTemplate(locInfo, req.hdl);

            String url = locInfo.getStrAttribute(LOC_ATTRIBUTE, null);
            if (url == null) {
                System.err.println("Error: no URL in location: " + locInfo);
                return false;
            }

            String urlSuffix = req.params.getParameter("urlappend");
            if (urlSuffix != null) url += urlSuffix;

            sendHttpRedirectResponse(locContext, locInfo, url);
            return true;
        } catch (Exception e) {
            if (e.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")) throw (IOException) e;
            if (e.getClass().getName().equals("org.eclipse.jetty.io.EofException")) throw e;
            System.err.println("Error in Location.sendRedirect for " + req + ": " + e);
        }
        return false;
    }

    private void sendHttpRedirectResponse(LocContext locContext, XTag locInfo, String url) throws UnsupportedEncodingException, IOException {
        HDLServletRequest req = locContext.req;
        // don't use sendRedirect(), because it tries to be smart and
        // occasionally mangles the uri(e.g. on mailto's)
        // response.sendRedirect(url+suffix);
        String redirectType = locContext.getAttribute(locInfo, REDIRECT_TYPE_ATT, null);
        req.sendHTTPRedirect(ResponseType.typeForString(redirectType), url);
        // print out terse page to avoid tomcat's redirect message for
        // things like mailto where a separate viewer is spawned
        writeHttpHeaders(req.response, locContext.headers, req.hdl);
        writeLinks(req.response, locContext.links, req.hdl);
        req.response.setContentType("text/html; charset=utf-8");
        String escapedURL = StringUtils.cgiEscape(url);
        OutputStreamWriter out = new OutputStreamWriter(req.response.getOutputStream(), "UTF-8");
        out.write("<html><head><title>Handle Redirect</title>");
        writeHtmlHeaders(out, locContext.headers, req.hdl);
        out.write("</head>\n<body><a href=\"" + escapedURL + "\">");
        out.write(escapedURL + "</a></body></html>");
        out.close();
    }

    private void writeHttpHeaders(HttpServletResponse response, List<String> headers, String handle) {
        if (headers == null) return;
        for (String headerStrings : headers) {
            for (String header : headerStrings.split("\n")) {
                header = header.trim();
                if (header.isEmpty()) continue;
                if (header.startsWith("<")) continue;
                int colon = header.indexOf(':');
                if (colon < 0) continue;
                String name = header.substring(0, colon).trim();
                String value = header.substring(colon + 1).trim();
                value = processTemplate(value, handle);
                response.addHeader(name, value);
            }
        }
    }

    private void writeLinks(HttpServletResponse response, List<XTag> links, String handle) {
        if (links == null) return;
        for (XTag linksTag : links) {
            Map<String, String> anchors = new HashMap<>();
            List<XTag> locSubtags = new ArrayList<>();
            for (int i = 0; i < linksTag.getSubTagCount(); i++) {
                XTag subtag = linksTag.getSubTag(i);
                if (!subtag.getName().equalsIgnoreCase(LOC_TAG_NAME) && !subtag.getName().equalsIgnoreCase(OLD_LOC_TEMPLATE_TAG_NAME)) continue;
                String rel = subtag.getAttribute("rel");
                if (rel == null) continue;
                subtag = processTemplate(subtag, handle);
                String href = subtag.getAttribute("href");
                if (href == null) continue;
                String id = subtag.getAttribute("id");
                if (id != null) {
                    anchors.put(id, href);
                }
                locSubtags.add(subtag);
            }
            for (XTag subtag : locSubtags) {
                String anchor = subtag.getAttribute("anchor");
                if (anchor != null) continue;
                String anchorId = subtag.getAttribute("anchor-id");
                if (anchorId == null) continue;
                anchor = anchors.get(anchorId);
                if (anchor == null) continue;
                subtag.setAttribute("anchor", anchor);
            }
            for (XTag subtag : locSubtags) {
                String value = getLinkValue(subtag);
                response.addHeader("Link", value);
            }
        }
    }

    private String getLinkValue(XTag locTag) {
        String href = locTag.getAttribute("href");
        String rel = locTag.getAttribute("rel");
        String anchor = locTag.getAttribute("anchor");
        String mediaType = locTag.getAttribute("media-type");
        String targetType = locTag.getAttribute("target-type");
        String title = locTag.getAttribute("title");
        String media = locTag.getAttribute("media");
        String hreflang = locTag.getAttribute("hreflang");
        String titlelang = locTag.getAttribute("titlelang");
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        sb.append(StringUtils.encodeURL(href));
        sb.append(">");
        if (anchor != null) {
            sb.append("; anchor=\"");
            sb.append(StringUtils.encodeURL(anchor));
            sb.append("\"");
        }
        if (rel != null) {
            sb.append("; rel=\"");
            sb.append(rel);
            sb.append("\"");
        }
        if (mediaType != null) {
            sb.append("; type=\"");
            sb.append(mediaType);
            sb.append("\"");
        }
        if (targetType != null) {
            sb.append("; target-type=\"");
            sb.append(targetType);
            sb.append("\"");
        }
        if (media != null) {
            sb.append("; media=\"");
            sb.append(media);
            sb.append("\"");
        }
        if (hreflang != null) {
            sb.append("; hreflang=\"");
            sb.append(hreflang);
            sb.append("\"");
        }
        if (title != null) {
            if (titlelang == null && isPrintableAscii(title)) {
                sb.append("; title=\"");
                sb.append(title.replace("\\", "\\\\").replace("\"", "\\\""));
                sb.append("\"");
            } else {
                sb.append("; title*=UTF-8'");
                if (titlelang != null) sb.append(titlelang);
                sb.append("'");
                sb.append(StringUtils.encodeURLComponent(title));
            }
        }
        return sb.toString();
    }

    private static boolean isPrintableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 32 || ch >= 127) return false;
        }
        return true;
    }

    private void writeHtmlHeaders(Writer out, List<String> headers, String handle) throws IOException {
        if (headers == null) return;
        boolean first = true;
        for (String headerStrings : headers) {
            for (String header : headerStrings.split("\n")) {
                if (header.trim().startsWith("<")) {
                    if (first) {
                        out.write("\n");
                        first = false;
                    }
                    out.write(processTemplate(header, handle));
                    out.write("\n");
                }
            }
        }
    }

    private static class LocContext {
        final HDLServletRequest req;
        final List<XTag> locXMLs;
        final List<XTag> links;
        final List<String> headers;
        final List<XTag> nsLocXMLs;
        final List<XTag> locations;
        final HandleValue[] legacyValues; // only populated when there are only legacy values

        LocContext(HDLServletRequest req, List<XTag> locXMLs, List<XTag> nsLocXMLs, List<XTag> links, List<String> headers, List<XTag> locations, HandleValue[] legacyValues) {
            this.locXMLs = locXMLs;
            this.nsLocXMLs = nsLocXMLs;
            this.req = req;
            this.links = links;
            this.headers = headers;
            this.locations = locations;
            this.legacyValues = legacyValues;
        }

        public boolean isLegacyOnly() {
            return legacyValues != null;
        }

        public String getAttribute(XTag loc, String attributeName, String defaultValue) {
            String val = null;
            // return the location-specific attribute, if any
            if (loc != null) {
                val = loc.getAttribute(attributeName);
                if (val != null) return val;
            }

            // return the location-set-specific attribute, if any
            if (locXMLs != null) {
                for (XTag parentInfoTag : locXMLs) {
                    val = parentInfoTag.getAttribute(attributeName);
                    if (val != null) return val;
                }
            }

            // look in namespace-level locs
            if (nsLocXMLs != null) {
                for (XTag parentLocs : nsLocXMLs) {
                    val = parentLocs.getAttribute(attributeName);
                    if (val != null) return val;
                }
            }

            return defaultValue;
        }
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.auth;

import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import net.cnri.util.StringUtils;

public class HandleAuthorizationHeader {

    private static final String token = "[-A-Za-z0-9!#$%&'*+.`^_|~]++";
    private static final String quotedString = "\"[^\\\\\"]*+(?>\\\\.[^\\\\\"]*+)*+\"";
    private static final Pattern handleAuthorizationHeaderPattern = Pattern.compile("\\s*+Handle\\s++(.*+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern handleAuthorizationHeaderParamPattern = Pattern.compile("\\s*+(" + token + ")\\s*+=\\s*+(" + token + "|" + quotedString + ")\\s*+((?>,\\s*+)*+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private String version;

    private String clientCert;
    private String renegotiate;

    private String sessionId;
    private String id;
    private String cnonce;
    private String type;
    private String alg;
    private String signature;

    // for PBMAC
    private String salt;
    private String iterations;
    private String length;

    public HandleAuthorizationHeader() {
    }

    public static HandleAuthorizationHeader fromHeader(String header) {
        if (header == null) return null;
        Matcher m = handleAuthorizationHeaderPattern.matcher(header);
        if (!m.matches()) return null;
        boolean comma = true;
        if (m.group(1).isEmpty()) return null;
        m = handleAuthorizationHeaderParamPattern.matcher(m.group(1));
        HandleAuthorizationHeader res = new HandleAuthorizationHeader();
        boolean done = false;
        while (!done && m.find()) {
            if (!comma) return null;
            String key = m.group(1);
            String value = unquote(m.group(2));
            if (isPercentEncoded(key)) {
                value = StringUtils.decodeURLIgnorePlus(value);
            }
            res.put(key, value);
            comma = m.group(3) != null && !m.group(3).isEmpty();
            done = m.end() == m.regionEnd();
        }
        if (!done) return null;
        return res;
    }

    private static boolean isPercentEncoded(String key) {
        return key.equalsIgnoreCase("id");
    }

    public static HandleAuthorizationHeader fromHeaderAndParameters(String header, HttpServletRequest req) {
        HandleAuthorizationHeader res = HandleAuthorizationHeader.fromHeader(header);
        if (res == null) res = new HandleAuthorizationHeader();
        Enumeration<String> names = req.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = req.getParameter(name);
            if (value == "") value = "true";
            res.putIfAbsent(name, value);
        }
        return res;
    }

    static String unquote(String s) {
        if (!(s.startsWith("\"") && s.endsWith("\""))) return s;
        s = s.substring(1, s.length() - 1);
        s = s.replaceAll("\\\\(.)", "$1");
        return s;
    }

    public boolean isAuthenticating() {
        return id != null || type != null || alg != null || signature != null;
    }

    public Boolean getClientCertAsBooleanObject() {
        if (clientCert == null) return null;
        return Boolean.parseBoolean(clientCert);
    }

    public boolean isRequestingForceRenegotiate() {
        return Boolean.parseBoolean(renegotiate);
    }

    public boolean requiresSession() {
        return sessionId != null || isAuthenticating() || isRequestingServerSignature();
    }

    public boolean isIncompleteAuthentication() {
        return id == null || cnonce == null || type == null || alg == null || signature == null;
    }

    public boolean isRequestingServerSignature() {
        return cnonce != null && id == null;
    }

    public String getVersion() {
        return version;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getId() {
        return id;
    }

    public String getCnonce() {
        return cnonce;
    }

    public String getType() {
        return type;
    }

    public String getAlg() {
        return alg;
    }

    public String getSignature() {
        return signature;
    }

    public String getSalt() {
        return salt;
    }

    public String getIterations() {
        return iterations;
    }

    public String getLength() {
        return length;
    }

    public String getClientCert() {
        return clientCert;
    }

    public String getRenegotiate() {
        return renegotiate;
    }

    public void put(String s, String v) {
        if (s.equalsIgnoreCase("version")) version = v;
        else if (s.equalsIgnoreCase("sessionId")) sessionId = v;
        else if (s.equalsIgnoreCase("id")) id = v;
        else if (s.equalsIgnoreCase("cnonce")) cnonce = v;
        else if (s.equalsIgnoreCase("type")) type = v;
        else if (s.equalsIgnoreCase("alg")) alg = v;
        else if (s.equalsIgnoreCase("signature")) signature = v;
        else if (s.equalsIgnoreCase("salt")) salt = v;
        else if (s.equalsIgnoreCase("iterations")) iterations = v;
        else if (s.equalsIgnoreCase("length")) length = v;
        else if (s.equalsIgnoreCase("clientCert")) clientCert = v;
        else if (s.equalsIgnoreCase("renegotiate")) renegotiate = v;
    }

    public void putIfAbsent(String s, String v) {
        if (s.equalsIgnoreCase("version") && version == null) version = v;
        else if (s.equalsIgnoreCase("sessionId") && sessionId == null) sessionId = v;
        else if (s.equalsIgnoreCase("id") && id == null) id = v;
        else if (s.equalsIgnoreCase("cnonce") && cnonce == null) cnonce = v;
        else if (s.equalsIgnoreCase("type") && type == null) type = v;
        else if (s.equalsIgnoreCase("alg") && alg == null) alg = v;
        else if (s.equalsIgnoreCase("signature") && signature == null) signature = v;
        else if (s.equalsIgnoreCase("salt") && salt == null) salt = v;
        else if (s.equalsIgnoreCase("iterations") && iterations == null) iterations = v;
        else if (s.equalsIgnoreCase("length") && length == null) length = v;
        else if (s.equalsIgnoreCase("clientCert") && clientCert == null) clientCert = v;
        else if (s.equalsIgnoreCase("renegotiate") && renegotiate == null) renegotiate = v;
    }

    @Override
    public String toString() {
        String res = "HandleAuthorizationHeader [version=" + version + ", clientCert=" + clientCert + ", renegotiate=" + renegotiate + ", sessionId=" + sessionId + ", id=" + id + ", cnonce=" + cnonce + ", type=" + type + ", alg=" + alg
            + ", signature=" + signature;
        if (salt != null) {
            res += ", salt=" + salt + ", iterations=" + iterations + ", length=" + length;
        }
        return res + "]";
    }
}

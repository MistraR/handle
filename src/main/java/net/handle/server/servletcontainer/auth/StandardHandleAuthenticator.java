/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.auth;

import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;

import net.cnri.util.StringUtils;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AbstractResponseAndIndex;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.ChallengeAnswerRequest;
import net.handle.hdllib.ChallengeResponse;
import net.handle.hdllib.Common;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.GenericRequest;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.ResolutionResponse;
import net.handle.hdllib.SecretKeyAuthenticationInfo;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;
import net.handle.hdllib.VerifyAuthRequest;
import net.handle.hdllib.VerifyAuthResponse;
import net.handle.server.servletcontainer.HandleServerInterface;
import net.handle.server.servletcontainer.support.PreAuthenticatedAuthenticationInfo;
import net.handle.util.X509HSTrustManager;

public class StandardHandleAuthenticator {
    private final HttpServletRequest request;
    private final HttpSession session;
    private final HandleAuthenticationStatus handleAuthStatus;
    private final AuthenticationResponse authResp;
    private final String authHeader;
    private final HandleAuthorizationHeader parsedHandleAuthHeader;

    public StandardHandleAuthenticator(HttpServletRequest request, HttpSession session, HandleAuthenticationStatus handleAuthStatus, AuthenticationResponse authResp) {
        this.request = request;
        this.session = session;
        this.handleAuthStatus = handleAuthStatus;
        this.authResp = authResp;
        this.parsedHandleAuthHeader = (HandleAuthorizationHeader) request.getAttribute(HandleAuthorizationHeader.class.getName());
        this.authHeader = request.getHeader("Authorization");
    }

    public void authenticate() {
        if (badSessionInHandleAuthorizationHeader()) {
            authResp.setAuthenticating(true);
            authResp.setAuthenticated(false);
            return;
        }
        AuthenticationInfoWithId authInfo = null;
        boolean saveAuth = true;
        if (isAuthenticatingViaHeaderOrHandleAuthHeaderEntity()) {
            authResp.setAuthenticating(true);
            authInfo = checkSeenBefore();
            if (authInfo != null) saveAuth = false;
            if (authInfo == null) authInfo = authenticateViaHandleAuthorizationHeader();
            if (authInfo == null) authInfo = authenticateViaBasic();
        } else {
            authInfo = authenticateViaSession();
            if (authInfo != null) saveAuth = false;
            if (authInfo == null) authInfo = authenticateViaClientSideCert();
        }
        if (authInfo != null) {
            authResp.setAuthenticated(true);
            authResp.setId(authInfo.getId());
            if (session != null && saveAuth) {
                HandleAuthenticationStatus newStatus = new HandleAuthenticationStatus(handleAuthStatus.getSessionId(), handleAuthStatus.getNonce(), handleAuthStatus.getCnonce(), handleAuthStatus.getServerSignature(), authHeader, authInfo.getAuthInfo(), authInfo.getId());
                session.setAttribute(HandleAuthenticationStatus.class.getName(), newStatus);
            }
            request.setAttribute(AuthenticationInfo.class.getName(), authInfo.getAuthInfo());
        }
    }

    private boolean badSessionInHandleAuthorizationHeader() {
        return parsedHandleAuthHeader != null && parsedHandleAuthHeader.getSessionId() != null && (session == null || !session.getId().equals(parsedHandleAuthHeader.getSessionId()));
    }

    private boolean isAuthenticatingViaHeaderOrHandleAuthHeaderEntity() {
        if (authHeader != null && authHeader.trim().startsWith("Basic")) return true;
        if (parsedHandleAuthHeader != null) return parsedHandleAuthHeader.isAuthenticating();
        return (authHeader != null);
    }

    private AuthenticationInfoWithId authenticateViaHandleAuthorizationHeader() {
        if (parsedHandleAuthHeader == null || !parsedHandleAuthHeader.isAuthenticating()) return null;
        if (session == null) return null;
        AuthenticationInfoWithId authInfo = authenticateViaHandleAuthorizationHeaderWhichIsAuthenticatingInSession();
        return authInfo;
    }

    private AuthenticationInfoWithId authenticateViaHandleAuthorizationHeaderWhichIsAuthenticatingInSession() {
        if (parsedHandleAuthHeader.getVersion() != null && !"0".equals(parsedHandleAuthHeader.getVersion())) {
            authResp.addError("Unknown version in Authorization: Handle");
            return null;
        }
        if (parsedHandleAuthHeader.isIncompleteAuthentication()) {
            authResp.addError("Missing fields in authentication data");
            return null;
        }
        try {
            byte[] nonce = handleAuthStatus.getNonce();
            byte[] cnonce = Base64.decodeBase64(parsedHandleAuthHeader.getCnonce());
            byte[] signature = Base64.decodeBase64(parsedHandleAuthHeader.getSignature());
            String alg = parsedHandleAuthHeader.getAlg();
            ValueReference val = extractHandleValueReferenceFromString(parsedHandleAuthHeader.getId());
            String authType = parsedHandleAuthHeader.getType();
            byte[] algBytes = Util.encodeString(alg);
            if (Util.equalsIgnoreCaseAndPunctuation(algBytes, Common.HASH_ALG_PBKDF2_HMAC_SHA1) || Util.equalsIgnoreCaseAndPunctuation(algBytes, Common.HASH_ALG_PBKDF2_HMAC_SHA1_ALTERNATE)) {
                String salt = parsedHandleAuthHeader.getSalt();
                String iterationsString = parsedHandleAuthHeader.getIterations();
                String keyLengthString = parsedHandleAuthHeader.getLength();
                if (salt != null && iterationsString != null && keyLengthString != null) {
                    signature = Util.constructPbkdf2Encoding(Base64.decodeBase64(salt), Integer.parseInt(iterationsString), Integer.parseInt(keyLengthString), signature);
                }
            }
            byte[] signedResponse = constructSignedResponse(authType, alg, signature);
            if (signedResponse == null) {
                authResp.addError("Error parsing Authorization: Handle");
                return null;
            }
            int index = verifyIdentityAndGetIndex(val, authType, nonce, cnonce, signedResponse);
            if (index >= 0) return new AuthenticationInfoWithId(parsedHandleAuthHeader.getId(), new PreAuthenticatedAuthenticationInfo(val.handle, index));
            return null;
        } catch (Exception e) {
            authResp.addError("Exception (" + e.getClass().getName() + ") parsing Authorization: Handle");
            return null;
        }
    }

    public static byte[] constructSignedResponse(String authType, String alg, byte[] signature) {
        byte[] algBytes;
        byte algCode;
        byte[] algEncoded = Util.encodeString(alg);
        if (alg.equalsIgnoreCase("MD5")) {
            algBytes = Common.HASH_ALG_MD5;
            algCode = Common.HASH_CODE_MD5;
        } else if (alg.equalsIgnoreCase("SHA") || alg.equalsIgnoreCase("SHA1") || alg.equalsIgnoreCase("SHA-1")) {
            algBytes = Common.HASH_ALG_SHA1;
            algCode = Common.HASH_CODE_SHA1;
        } else if (alg.equalsIgnoreCase("SHA-256") || alg.equalsIgnoreCase("SHA256") || alg.equalsIgnoreCase("SHA-2") || alg.equalsIgnoreCase("SHA2")) {
            algBytes = Common.HASH_ALG_SHA256;
            algCode = Common.HASH_CODE_SHA256;
        } else if (Util.equalsIgnoreCaseAndPunctuation(Common.HASH_ALG_HMAC_SHA1, algEncoded)) {
            algBytes = Common.HASH_ALG_HMAC_SHA1;
            algCode = Common.HASH_CODE_HMAC_SHA1;
        } else if (Util.equalsIgnoreCaseAndPunctuation(Common.HASH_ALG_HMAC_SHA256, algEncoded)) {
            algBytes = Common.HASH_ALG_HMAC_SHA256;
            algCode = Common.HASH_CODE_HMAC_SHA256;
        } else if (Util.equalsIgnoreCaseAndPunctuation(algEncoded, Common.HASH_ALG_PBKDF2_HMAC_SHA1) || Util.equalsIgnoreCaseAndPunctuation(algEncoded, Common.HASH_ALG_PBKDF2_HMAC_SHA1_ALTERNATE)) {
            algBytes = Common.HASH_ALG_PBKDF2_HMAC_SHA1;
            algCode = Common.HASH_CODE_PBKDF2_HMAC_SHA1;
        } else {
            return null;
        }
        if ("HS_SECKEY".equals(authType)) {
            byte authResponse[] = new byte[signature.length + 1];
            authResponse[0] = algCode;
            System.arraycopy(signature, 0, authResponse, 1, signature.length);
            return authResponse;
        } else if ("HS_PUBKEY".equals(authType)) {
            int offset = 0;
            byte authResponse[] = new byte[signature.length + algBytes.length + 2 * Encoder.INT_SIZE];
            offset += Encoder.writeByteArray(authResponse, offset, algBytes);
            offset += Encoder.writeByteArray(authResponse, offset, signature);
            return authResponse;
        } else return null;
    }

    private AuthenticationInfoWithId authenticateViaBasic() {
        if (authHeader == null) return null;
        String[] parts = authHeader.trim().split("\\s++");
        if (parts.length != 2) return null;
        if (!parts[0].equalsIgnoreCase("Basic")) return null;
        byte[] bytes = Base64.decodeBase64(parts[1]);
        int colon = Util.indexOf(bytes, (byte) ':');
        if (colon < 0) return null;
        byte[] usernameEncoded = Util.substring(bytes, 0, colon);
        byte[] password = Util.substring(bytes, colon + 1);
        ValueReference val = extractHandleValueReferenceFromUrlEncodedBytes(usernameEncoded);
        if (checkSecretKey(val, password)) return new AuthenticationInfoWithId(new PreAuthenticatedAuthenticationInfo(val.handle, val.index));
        return null;
    }

    private AuthenticationInfoWithId checkSeenBefore() {
        if (session == null || authHeader == null) return null;
        String oldAuthHeader = handleAuthStatus.getAuthorizationHeader();
        if (authHeader.equals(oldAuthHeader)) {
            return handleAuthStatus.getAuthInfoWithId();
        }
        return null;
    }

    private boolean checkSecretKey(ValueReference val, byte[] password) {
        SecretKeyAuthenticationInfo authInfo = new SecretKeyAuthenticationInfo(val.handle, val.index, password);
        ServletContext servletContext = request.getServletContext();
        HandleServerInterface handleServer = (net.handle.server.servletcontainer.HandleServerInterface) servletContext.getAttribute("net.handle.server.HandleServer");
        if (handleServer == null) {
            HandleResolver handleResolver = getHandleResolver(servletContext);
            return checkSecretKeyViaResolver(handleResolver, authInfo);
        } else {
            return checkSecretKeyViaServer(handleServer, authInfo);
        }
    }

    // return -1 for false, otherwise the actual authenticating index
    private int verifyIdentityAndGetIndex(ValueReference val, String authType, byte[] nonce, byte[] cnonce, byte[] signature) {
        ServletContext servletContext = request.getServletContext();
        HandleServerInterface handleServer = (net.handle.server.servletcontainer.HandleServerInterface) servletContext.getAttribute("net.handle.server.HandleServer");
        if (handleServer == null) {
            HandleResolver handleResolver = getHandleResolver(servletContext);
            return verifyIdentityViaResolver(handleResolver, val, authType, nonce, cnonce, signature);
        } else {
            return verifyIdentityViaServer(handleServer, val, authType, nonce, cnonce, signature);
        }
    }

    private HandleResolver getHandleResolverEvenIfInServer(ServletContext servletContext) {
        HandleServerInterface handleServer = (net.handle.server.servletcontainer.HandleServerInterface) servletContext.getAttribute("net.handle.server.HandleServer");
        if (handleServer != null) return handleServer.getResolver();
        return getHandleResolver(servletContext);
    }

    // only specify HandleResolver once
    private HandleResolver getHandleResolver(ServletContext servletContext) {
        HandleResolver handleResolver = (HandleResolver) servletContext.getAttribute(HandleResolver.class.getName());
        if (handleResolver == null) {
            synchronized (servletContext) {
                handleResolver = (HandleResolver) servletContext.getAttribute(HandleResolver.class.getName());
                if (handleResolver == null) {
                    handleResolver = new HandleResolver();
                    servletContext.setAttribute(HandleResolver.class.getName(), handleResolver);
                }
            }
        }
        return handleResolver;
    }

    private boolean checkSecretKeyViaServer(HandleServerInterface handleServer, SecretKeyAuthenticationInfo authInfo) {
        try {
            String uri = request.getRequestURI();
            if (request.getQueryString() != null) uri = uri + "?" + request.getQueryString();
            AbstractRequest origReq = new GenericRequest(Util.encodeString(uri), AbstractMessage.OC_GET_SITE_INFO, null);
            ChallengeResponse cRes = new ChallengeResponse(origReq, true);
            byte[] signature = authInfo.authenticate(cRes, origReq);
            ChallengeAnswerRequest crReq = new ChallengeAnswerRequest(authInfo.getAuthType(), authInfo.getUserIdHandle(), authInfo.getUserIdIndex(), signature, authInfo);
            AbstractResponse resp = handleServer.verifyIdentity(cRes, crReq, origReq);
            if (resp == null) return true;
            authResp.addError("Identity not verified");
            return false;
        } catch (HandleException e) {
            authResp.addError("Exception (" + e.getClass().getName() + ") verifying identity");
            return false;
        }
    }

    // return -1 for false, otherwise the actual authenticating index
    private int verifyIdentityViaServer(HandleServerInterface handleServer, ValueReference val, String authType, byte[] nonce, byte[] cnonce, byte[] signature) {
        try {
            String uri = request.getRequestURI();
            if (request.getQueryString() != null) uri = uri + "?" + request.getQueryString();
            AbstractRequest origReq = new GenericRequest(Util.encodeString(uri), AbstractMessage.OC_GET_SITE_INFO, null);
            ChallengeResponse cRes = new ChallengeResponse(AbstractMessage.OC_GET_SITE_INFO, nonce);
            cRes.majorProtocolVersion = Common.MAJOR_VERSION;
            cRes.minorProtocolVersion = Common.MINOR_VERSION;
            cRes.rdHashType = hashTypeForCnonce(cnonce);
            cRes.requestDigest = cnonce;
            ChallengeAnswerRequest crReq = new ChallengeAnswerRequest(Util.encodeString(authType), val.handle, val.index, signature, null);
            AbstractResponseAndIndex resp = handleServer.verifyIdentityAndGetIndex(cRes, crReq, origReq);
            if (resp.getResponse() instanceof ChallengeResponse) {
                authResp.addError("Identity not verified; verifying server may only support older-format SHA-1 MAC");
                return -1;
            } else if (resp.getResponse() != null) {
                authResp.addError("Identity not verified");
                return -1;
            }
            if (resp.getIndex() == 0) return val.index;
            else return resp.getIndex();
        } catch (HandleException e) {
            authResp.addError("Exception (" + e.getClass().getName() + ") verifying identity");
            return -1;
        }
    }

    public static byte hashTypeForCnonce(@SuppressWarnings("unused") byte[] cnonce) {
        // the old format also allows variable length, so we re-use it here
        return Common.HASH_CODE_MD5_OLD_FORMAT;
        //        return cnonce.length == 32 ? Common.HASH_CODE_SHA256 : (cnonce.length == 20 ? Common.HASH_CODE_SHA1 : Common.HASH_CODE_MD5);
    }

    private boolean checkSecretKeyViaResolver(HandleResolver handleResolver, SecretKeyAuthenticationInfo authInfo) {
        try {
            String uri = request.getRequestURI();
            if (request.getQueryString() != null) uri = uri + "?" + request.getQueryString();
            AbstractRequest origReq = new GenericRequest(Util.encodeString(uri), AbstractMessage.OC_GET_SITE_INFO, null);
            ChallengeResponse challengeResp = new ChallengeResponse(origReq, true);
            byte[] signature = authInfo.authenticate(challengeResp, origReq);
            VerifyAuthRequest verifyAuthReq = new VerifyAuthRequest(authInfo.getUserIdHandle(), challengeResp.nonce, challengeResp.requestDigest, challengeResp.rdHashType, signature, authInfo.getUserIdIndex(), null);
            verifyAuthReq.certify = true;
            AbstractResponse response = handleResolver.processRequest(verifyAuthReq);
            if (response instanceof VerifyAuthResponse) {
                if (((VerifyAuthResponse) response).isValid) return true;
                authResp.addError("Identity not verified");
                return false;
            } else {
                authResp.addError("Identity not verified");
                return false;
            }
        } catch (HandleException e) {
            authResp.addError("Exception (" + e.getClass().getName() + ") verifying identity");
            return false;
        }
    }

    // return -1 for false, otherwise the actual authenticating index
    private int verifyIdentityViaResolver(HandleResolver handleResolver, ValueReference val, String authType, byte[] nonce, byte[] cnonce, byte[] signature) {
        try {
            if ("HS_SECKEY".equals(authType)) {
                if (verifySecretKeyIdentityViaResolver(handleResolver, val, nonce, cnonce, signature)) {
                    return -1;
                } else {
                    return val.index;
                }
            } else if ("HS_PUBKEY".equals(authType)) {
                return verifyPublicKeyIdentityViaResolver(handleResolver, val, nonce, cnonce, signature);
            } else {
                authResp.addError("Unknown authType " + authType);
                return -1;
            }
        } catch (Exception e) {
            authResp.addError("Exception (" + e.getClass().getName() + ") verifying identity");
            return -1;
        }
    }

    // return -1 for false, otherwise the actual authenticating index
    private int verifyPublicKeyIdentityViaResolver(HandleResolver handleResolver, ValueReference val, byte[] nonce, byte[] cnonce, byte[] signature) throws Exception {
        ResolutionRequest req = new ResolutionRequest(val.handle, val.index > 0 ? null : Common.PUBLIC_KEY_TYPES, val.index > 0 ? new int[] { val.index } : null, null);
        req.certify = true;
        AbstractResponse response = handleResolver.processRequest(req);
        if (!(response instanceof ResolutionResponse)) return -1;
        ResolutionResponse rresponse = (ResolutionResponse) response;
        HandleValue values[] = rresponse.getHandleValues();
        if (values == null || values.length < 1) return -1;

        int offset = 0;
        byte hashAlgId[] = Encoder.readByteArray(signature, offset);
        offset += Encoder.INT_SIZE + hashAlgId.length;
        byte sigBytes[] = Encoder.readByteArray(signature, offset);
        offset += Encoder.INT_SIZE + sigBytes.length;

        Arrays.sort(values, HandleValue.INDEX_COMPARATOR);
        for (HandleValue value : values) {
            try {
                PublicKey pubKey = Util.getPublicKeyFromBytes(value.getData(), 0);
                Signature sig = Signature.getInstance(Util.getSigIdFromHashAlgId(hashAlgId, pubKey.getAlgorithm()));
                sig.initVerify(pubKey);
                sig.update(nonce);
                sig.update(cnonce);
                if (sig.verify(sigBytes)) return value.getIndex();
            } catch (Exception e) {
                // not verified
            }
        }
        authResp.addError("Identity not verified, signature failed");
        return -1;
    }

    private int getIndexMatchingPublicKey(HandleResolver handleResolver, byte[] handle, PublicKey publicKey) {
        try {
            ResolutionRequest req = new ResolutionRequest(handle, Common.PUBLIC_KEY_TYPES, null, null);
            req.certify = true;
            AbstractResponse response = handleResolver.processRequest(req);
            if (!(response instanceof ResolutionResponse)) return 0;
            ResolutionResponse rresponse = (ResolutionResponse) response;
            HandleValue values[] = rresponse.getHandleValues();
            if (values == null || values.length < 1) return 0;
            Arrays.sort(values, HandleValue.INDEX_COMPARATOR);
            for (HandleValue value : values) {
                PublicKey handlePubKey = Util.getPublicKeyFromBytes(value.getData(), 0);
                if (publicKey.equals(handlePubKey) || Util.equals(publicKey.getEncoded(), handlePubKey.getEncoded())) return value.getIndex();
            }
        } catch (Exception e) {
            // something went wrong; fall back to 0
        }
        return 0;
    }

    private boolean verifySecretKeyIdentityViaResolver(HandleResolver handleResolver, ValueReference val, byte[] nonce, byte[] cnonce, byte[] signature) throws HandleException {
        VerifyAuthRequest verifyAuthReq = new VerifyAuthRequest(val.handle, nonce, cnonce, hashTypeForCnonce(cnonce), signature, val.index, null);
        verifyAuthReq.certify = true;
        AbstractResponse response = handleResolver.processRequest(verifyAuthReq);
        if (response instanceof VerifyAuthResponse) {
            if (((VerifyAuthResponse) response).isValid) return true;
            authResp.addError("Identity not verified");
            return false;
        } else {
            authResp.addError("Identity not verified");
            return false;
        }
    }

    private ValueReference extractHandleValueReferenceFromUrlEncodedBytes(byte[] usernameEncoded) {
        return extractHandleValueReferenceFromUrlEncodedString(Util.decodeString(usernameEncoded));
    }

    private ValueReference extractHandleValueReferenceFromUrlEncodedString(String usernameEncoded) {
        String username = StringUtils.decodeURLIgnorePlus(usernameEncoded);
        return extractHandleValueReferenceFromString(username);
    }

    private ValueReference extractHandleValueReferenceFromString(String username) {
        int colon = username.indexOf(':');
        if (colon < 0) return new ValueReference(Util.encodeString(username), 0);
        String maybeIndex = username.substring(0, colon);
        if (isDigits(maybeIndex)) {
            String handle = username.substring(colon + 1);
            return new ValueReference(Util.encodeString(handle), Integer.parseInt(maybeIndex));
        }
        return new ValueReference(Util.encodeString(username), 0);
    }

    private AuthenticationInfoWithId authenticateViaClientSideCert() {
        X509Certificate cert = extractCertificate(request);
        if (cert == null) return null;
        ValueReference id = X509HSTrustManager.parseIdentity(cert);
        if (id == null) {
            authResp.addError("Unable to parse identity from client-side certificate");
            return null;
        }
        int index = id.index;
        if (index == 0) index = getIndexMatchingPublicKey(getHandleResolverEvenIfInServer(request.getServletContext()), id.handle, cert.getPublicKey());
        return new AuthenticationInfoWithId(new PreAuthenticatedAuthenticationInfo(id.handle, index));
    }

    public static X509Certificate extractCertificate(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
        if (null != certs && certs.length > 0) {
            return certs[0];
        }
        return null;
    }

    private AuthenticationInfoWithId authenticateViaSession() {
        if (session == null) return null;
        return handleAuthStatus.getAuthInfoWithId();
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }
}

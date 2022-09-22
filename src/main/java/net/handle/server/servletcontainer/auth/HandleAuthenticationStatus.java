/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.auth;

import java.security.PrivateKey;
import java.security.Signature;

import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.ChallengeResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.Util;
import net.handle.server.servletcontainer.HandleServerInterface;

public class HandleAuthenticationStatus {
    private final String sessionId;
    private final byte[] nonce;

    private final byte[] cnonce;
    private final byte[] serverSignature;
    private final String authorizationHeader;

    private final AuthenticationInfo authInfo;
    private final String id;

    public HandleAuthenticationStatus(String sessionId, byte[] nonce, byte[] cnonce, byte[] serverSignature, String authorizationHeader, AuthenticationInfo authInfo, String id) {
        this.sessionId = sessionId;
        this.nonce = nonce;
        this.cnonce = cnonce;
        this.serverSignature = serverSignature;
        this.authorizationHeader = authorizationHeader;
        this.authInfo = authInfo;
        this.id = id;
    }

    public static byte[] generateNonce() {
        return ChallengeResponse.generateNonce();
    }

    public String getSessionId() {
        return sessionId;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public byte[] getCnonce() {
        return cnonce;
    }

    public byte[] getServerSignature() {
        return serverSignature;
    }

    public String getAuthorizationHeader() {
        return authorizationHeader;
    }

    public AuthenticationInfo getAuthInfo() {
        return authInfo;
    }

    public String getId() {
        return id;
    }

    public AuthenticationInfoWithId getAuthInfoWithId() {
        if (authInfo == null) return null;
        return new AuthenticationInfoWithId(id, authInfo);
    }

    public static String getServerAlg(HandleServerInterface handleServer) throws HandleException {
        return Util.decodeString(Util.getHashAlgIdFromSigId(Util.getDefaultSigId(handleServer.getPrivateKey().getAlgorithm())));
    }

    private byte[] buildServerSignature(HandleServerInterface handleServer, byte[] cnonceParam) throws HandleException {
        PrivateKey privateKey = handleServer.getPrivateKey();
        try {
            Signature signer = Signature.getInstance(Util.getDefaultSigId(privateKey.getAlgorithm()));
            signer.initSign(privateKey);
            signer.update(this.nonce);
            signer.update(cnonceParam);
            byte[] signatureBytes = signer.sign();
            return signatureBytes;
        } catch (Exception e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unable to sign challenge", e);
        }
    }

    public static HandleAuthenticationStatus fromSession(HttpSession session, boolean create) {
        if (session == null) return null;
        HandleAuthenticationStatus res = (HandleAuthenticationStatus) session.getAttribute(HandleAuthenticationStatus.class.getName());
        if (res != null) return res;
        if (!create) return null;
        res = new HandleAuthenticationStatus(session.getId(), generateNonce(), null, null, null, null, null);
        session.setAttribute(HandleAuthenticationStatus.class.getName(), res);
        return res;
    }

    // modify authResp, also cache serverSignature to session; return possibly changed HandleAuthenticationStatus
    public static HandleAuthenticationStatus processServerSignature(HandleAuthenticationStatus status, HandleServerInterface handleServer, HttpSession session, HandleAuthorizationHeader handleAuthHeader, AuthenticationResponse authResp) {
        if (handleAuthHeader != null && handleAuthHeader.isRequestingServerSignature() && handleServer != null) {
            authResp.setSessionId(status.getSessionId());
            authResp.setNonce(status.getNonce());
            try {
                byte[] cnonce = Base64.decodeBase64(handleAuthHeader.getCnonce());
                byte[] serverSignature;
                if (status.getServerSignature() != null && Util.equals(cnonce, status.getCnonce())) {
                    serverSignature = status.getServerSignature();
                } else {
                    serverSignature = status.buildServerSignature(handleServer, cnonce);
                    status = new HandleAuthenticationStatus(status.getSessionId(), status.getNonce(), cnonce, serverSignature, status.getAuthorizationHeader(), status.getAuthInfo(), status.getId());
                    session.setAttribute(HandleAuthenticationStatus.class.getName(), status);
                }
                authResp.setServerSignature(serverSignature);
                authResp.setServerAlg(getServerAlg(handleServer));
            } catch (HandleException e) {
                authResp.getErrors().add(e.toString());
            }
        }
        return status;
    }

}

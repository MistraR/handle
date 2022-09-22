/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.controller;

import net.handle.hdllib.*;
import java.security.*;
import java.security.interfaces.*;
import java.util.*;

public final class AuthenticationUtil {
    private final HandleResolver resolver;
    private Integer index;

    public AuthenticationUtil(HandleResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * For a public key authentication with index 0, returns a real working index
     */
    public Integer getIndex() {
        return index;
    }

    public boolean checkAuthentication(AuthenticationInfo authInfo) throws Exception {
        ResolutionRequest request = new ResolutionRequest(Common.BLANK_HANDLE, null, null, null);
        ChallengeResponse challengeResp = new ChallengeResponse(request, true);

        byte authBytes[] = authInfo.authenticate(challengeResp, request);
        if (Util.equals(authInfo.getAuthType(), Common.SECRET_KEY_TYPE)) {
            // Secret key authentication
            return verifySecretKeyAuth(authInfo, challengeResp, authBytes);

        } else if (Util.equals(authInfo.getAuthType(), Common.PUBLIC_KEY_TYPE)) {
            // Public key authentication
            return verifyPubKeyAuth(authInfo, challengeResp, authBytes);

        } else {
            // Unknown authentication type
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Unknown authentication type: " + Util.decodeString(authInfo.getAuthType()));
        }
    }

    /**
     * Verify that the given secret key-based ChallengeResponse was actually
     * 'signed' by the given AuthenticationInfo object.
     */
    public boolean verifySecretKeyAuth(AuthenticationInfo authInfo, ChallengeResponse challengeResp, byte[] authBytes) throws HandleException {
        VerifyAuthRequest verifyAuthReq = new VerifyAuthRequest(authInfo.getUserIdHandle(), challengeResp.nonce, challengeResp.requestDigest, challengeResp.rdHashType, authBytes, authInfo.getUserIdIndex(), null);
        verifyAuthReq.certify = true;

        AbstractResponse response = resolver.processRequest(verifyAuthReq);

        // make sure we got a VerifyAuthResponse
        if (response instanceof VerifyAuthResponse) {
            return ((VerifyAuthResponse) response).isValid;
        } else {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Unable to verify authentication\n" + response);
        }
    }

    /**
     * Verify that the given public key-based ChallengeResponse was actually
     * signed by the given AuthenticationInfo object's private key.
     */
    public boolean verifyPubKeyAuth(AuthenticationInfo authInfo, ChallengeResponse challengeResp, byte[] authBytes) throws Exception {
        // first retrieve the public key (checking server signatures)
        int userIDIndex = authInfo.getUserIdIndex();
        ResolutionRequest request = new ResolutionRequest(authInfo.getUserIdHandle(), userIDIndex > 0 ? null : Common.PUBLIC_KEY_TYPES, userIDIndex > 0 ? new int[] { userIDIndex } : null, null);
        request.certify = true;
        AbstractResponse response = resolver.processRequest(request);

        // make sure we got a ResolutionResponse
        if (!(response instanceof ResolutionResponse)) throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Unable to verify authentication\n" + response);

        Map<Integer, PublicKey> pubKeys = new HashMap<>();
        // make sure we got the handle values
        HandleValue values[] = ((ResolutionResponse) response).getHandleValues();
        for (int i = 0; values != null && i < values.length; i++) {
            HandleValue value = values[i];
            if (value == null) continue;
            if (userIDIndex <= 0 || userIDIndex == value.getIndex()) {
                if (value.hasType(Common.STD_TYPE_HSPUBKEY)) {
                    pubKeys.put(Integer.valueOf(value.getIndex()), Util.getPublicKeyFromBytes(value.getData(), 0));
                }
            }
        }

        if (pubKeys.size() <= 0) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "No public key found for the given authentication " + authInfo);
        }

        // get the algorithm used to sign
        int offset = 0;
        byte hashAlgId[] = Encoder.readByteArray(authBytes, offset);
        offset += Encoder.INT_SIZE + hashAlgId.length;

        // get the actual bytes of the signature
        byte sigBytes[] = Encoder.readByteArray(authBytes, offset);
        offset += Encoder.INT_SIZE + sigBytes.length;

        for (Map.Entry<Integer, PublicKey> entry : pubKeys.entrySet()) {
            @SuppressWarnings("hiding")
            int index = entry.getKey().intValue();
            PublicKey pubkey = entry.getValue();
            if (pubkey instanceof DSAPublicKey) {
                if (verifyDSAPublicKey(hashAlgId, pubkey, challengeResp, sigBytes)) {
                    this.index = index;
                    return true;
                }
            } else if (pubkey instanceof RSAPublicKey) {
                if (verifyRSAPublicKeyImpl(hashAlgId, pubkey, challengeResp, sigBytes)) {
                    this.index = index;
                    return true;
                }
            }
        }
        return false;
        //
        //    if(pubKey instanceof DSAPublicKey) {
        //      return verifyDSAPublicKey(hashAlgId, pubKey, challengeResp, sigBytes);
        //    } else if(pubKey instanceof RSAPublicKey) {
        //      return verifyRSAPublicKeyImpl(hashAlgId, pubKey, challengeResp, sigBytes);
        //    } else {
        //      throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE,
        //                                "Unrecognized key type: "+pubKey);
        //    }
    }

    /**
     * Verify that the given ChallengeResponse was signed by the given DSA PublicKey.
     */
    public boolean verifyDSAPublicKey(byte[] hashAlgId, PublicKey pubKey, ChallengeResponse challengeResp, byte[] sigBytes) throws Exception {
        // load the signature
        try {
            String sigId = Util.getSigIdFromHashAlgId(hashAlgId, pubKey.getAlgorithm());
            Signature sig = Signature.getInstance(sigId);
            sig.initVerify(pubKey);

            // verify the signature
            sig.update(challengeResp.nonce);
            sig.update(challengeResp.requestDigest);
            return sig.verify(sigBytes);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * Verify that the given ChallengeResponse was signed by the given RSA PublicKey.
     */
    public boolean verifyRSAPublicKeyImpl(byte[] hashAlgId, PublicKey pubKey, ChallengeResponse challengeResp, byte[] sigBytes) throws Exception {
        // load the signature
        String sigId = Util.getSigIdFromHashAlgId(hashAlgId, pubKey.getAlgorithm());
        Signature sig = Signature.getInstance(sigId);
        sig.initVerify(pubKey);

        // verify the signature
        sig.update(challengeResp.nonce);
        sig.update(challengeResp.requestDigest);
        return sig.verify(sigBytes);
    }
}

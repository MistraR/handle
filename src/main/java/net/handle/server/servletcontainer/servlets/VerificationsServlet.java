/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;

import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;
import net.handle.hdllib.VerifyAuthRequest;
import net.handle.server.servletcontainer.auth.StandardHandleAuthenticator;

public class VerificationsServlet extends BaseHandleRequestProcessingServlet {
    {
        allowString = "GET, HEAD, TRACE, OPTIONS";
    }

    @Override
    protected void doGet(HttpServletRequest servletReq, HttpServletResponse servletResp) throws ServletException, IOException {
        String handle = getPath(servletReq);
        VerifyAuthRequest req;
        AbstractResponse resp;
        if (handle.isEmpty()) {
            req = null;
            resp = errorResponseFromException(new Exception("Handle not specified in verification request"));
        } else {
            try {
                req = getVerificationRequest(handle, servletReq);
                resp = processRequest(servletReq, req);
            } catch (Exception e) {
                req = null;
                resp = errorResponseFromException(e);
            }
        }
        processResponse(servletReq, servletResp, req, resp);
    }

    private static VerifyAuthRequest getVerificationRequest(String handle, HttpServletRequest servletReq) throws Exception {
        ValueReference handleAndIndex = getValueReferenceFromIndexColonHandle(handle);
        int[] indexes = getIndexes(servletReq);
        if (indexes == null || indexes.length == 0) {
            if (handleAndIndex.index == -1) {
                throw new Exception("No index provided");
            }
        } else if (indexes.length > 1 || handleAndIndex.index >= 0) {
            throw new Exception("Too many indexes specified in verification request");
        } else {
            handleAndIndex.index = indexes[0];
        }
        String nonce = servletReq.getParameter("nonce");
        String cnonce = servletReq.getParameter("cnonce");
        String alg = servletReq.getParameter("alg");
        String signature = servletReq.getParameter("signature");
        if (nonce == null || cnonce == null || alg == null || signature == null) {
            throw new Exception("Missing parameters in verification request");
        }
        byte[] nonceBytes = Base64.decodeBase64(nonce);
        byte[] cnonceBytes = Base64.decodeBase64(cnonce);
        byte[] decodedSignatureBytes = Base64.decodeBase64(signature);
        byte[] algBytes = Util.encodeString(alg);
        if (Util.equalsIgnoreCaseAndPunctuation(algBytes, Common.HASH_ALG_PBKDF2_HMAC_SHA1) || Util.equalsIgnoreCaseAndPunctuation(algBytes, Common.HASH_ALG_PBKDF2_HMAC_SHA1_ALTERNATE)) {
            String salt = servletReq.getParameter("salt");
            String iterationsString = servletReq.getParameter("iterations");
            String keyLengthString = servletReq.getParameter("length");
            if (salt != null && iterationsString != null && keyLengthString != null) {
                decodedSignatureBytes = Util.constructPbkdf2Encoding(Base64.decodeBase64(salt), Integer.parseInt(iterationsString), Integer.parseInt(keyLengthString), decodedSignatureBytes);
            }
        }
        byte[] sigBytes = StandardHandleAuthenticator.constructSignedResponse("HS_SECKEY", alg, decodedSignatureBytes);
        if (sigBytes == null) {
            throw new Exception("Unknown algorithm");
        }
        AuthenticationInfo authInfo = getAuthenticationInfo(servletReq);
        VerifyAuthRequest verifyAuthReq = new VerifyAuthRequest(handleAndIndex.handle, nonceBytes, cnonceBytes, StandardHandleAuthenticator.hashTypeForCnonce(cnonceBytes), sigBytes, handleAndIndex.index, authInfo);
        return verifyAuthReq;
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    private static ValueReference getValueReferenceFromIndexColonHandle(String indexColonHandle) {
        if (indexColonHandle == null) return null;
        int colon = indexColonHandle.indexOf(':');
        if (colon < 0) return new ValueReference(Util.encodeString(indexColonHandle), -1);
        String maybeIndex = indexColonHandle.substring(0, colon);
        if (isDigits(maybeIndex)) {
            String handle = indexColonHandle.substring(colon + 1);
            return new ValueReference(Util.encodeString(handle), Integer.parseInt(maybeIndex));
        }
        return new ValueReference(Util.encodeString(indexColonHandle), -1);
    }
}

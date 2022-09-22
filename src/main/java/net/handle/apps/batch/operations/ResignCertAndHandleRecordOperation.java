/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.operations;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;
import net.handle.hdllib.trust.HandleClaimsSet;
import net.handle.hdllib.trust.HandleSigner;
import net.handle.hdllib.trust.HandleVerifier;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;
import net.handle.hdllib.trust.TrustException;
import net.handle.apps.batch.HandleRecordOperationInterface;

public class ResignCertAndHandleRecordOperation implements HandleRecordOperationInterface {
    private static final JsonWebSignatureFactory factory = JsonWebSignatureFactory.getInstance();
    private static final HandleVerifier verifier = new HandleVerifier();
    private static final HandleSigner signer = new HandleSigner();
    private PrivateKey issPrivateKey;
    private final ValueReference issIdentity;
    private final String firstLinkInChain;

    private String baseUri;
    private String username;
    private String password;
    private String privateKeyId;
    private String privateKeyPassphrase;

    private boolean isRemote = false;

    private final ValueReference oldSignerId;

    private boolean dryRun;

    public ResignCertAndHandleRecordOperation(ValueReference oldSignerId, PrivateKey issPrivateKey, ValueReference issIdentity, String firstLinkInChain) {
        this.oldSignerId = oldSignerId;
        this.issPrivateKey = issPrivateKey;
        this.issIdentity = issIdentity;
        this.firstLinkInChain = firstLinkInChain;
    }

    public ResignCertAndHandleRecordOperation(ValueReference oldSignerId, ValueReference issIdentity, String firstLinkInChain, String baseUri, String username, String password, String privateKeyId, String privateKeyPassphrase) {
        this.oldSignerId = oldSignerId;
        this.issIdentity = issIdentity;
        this.firstLinkInChain = firstLinkInChain;

        this.baseUri = baseUri;
        this.username = username;
        this.password = password;
        this.privateKeyId = privateKeyId;
        this.privateKeyPassphrase = privateKeyPassphrase;
        isRemote = true;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    @Override
    public void process(String handle, HandleValue[] values, HandleResolver resolver, AuthenticationInfo authInfo, SiteInfo site) throws HandleException {
        //resign the certs
        List<String> chain = null;
        boolean foundCert = false;
        boolean foundSig = false;
        try {
            for (HandleValue value : values) {
                if (value.hasType(Common.HS_CERT_TYPE)) {
                    String data = value.getDataAsString();
                    JsonWebSignature jws = factory.deserialize(data);
                    HandleClaimsSet claims = verifier.getHandleClaimsSet(jws);
                    if (oldSignerId.toString().equals(claims.iss)) {
                        foundCert = true;
                        claims.iss = issIdentity.toString();
                        if (claims.chain != null) {
                            if (firstLinkInChain == null) {
                                claims.chain = null;
                            } else {
                                claims.chain = new ArrayList<>(claims.chain);
                                claims.chain.set(0, firstLinkInChain);
                            }
                        }
                        claims.iat = System.currentTimeMillis() / 1000;
                        claims.nbf = claims.iat - 10 * 60;
                        claims.exp = claims.iat + (366L * 24L * 60L * 60L * 2L);
                        JsonWebSignature newJws;
                        if (isRemote) {
                            newJws = signer.signClaimsRemotely(claims, baseUri, username, password, privateKeyId, privateKeyPassphrase);
                        } else {
                            newJws = signer.signClaims(claims, issPrivateKey);
                        }
                        value.setData(Util.encodeString(newJws.serialize()));
                        System.out.println("Resigned HS_CERT for " + handle);
                    }
                } else if (value.hasType(Common.HS_SIGNATURE_TYPE)) {
                    String data = value.getDataAsString();
                    JsonWebSignature jws = factory.deserialize(data);
                    HandleClaimsSet claims = verifier.getHandleClaimsSet(jws);
                    if (oldSignerId.toString().equals(claims.iss)) {
                        foundSig = true;
                        if (claims.chain != null) {
                            if (firstLinkInChain == null) {
                                chain = null;
                            } else {
                                chain = new ArrayList<>(claims.chain);
                                chain.set(0, firstLinkInChain);
                            }
                        }
                        System.out.println("Resigned HS_SIGNATURE for " + handle);
                    }
                }
            }
        } catch (TrustException e) {
            throw new HandleException(HandleException.SECURITY_ALERT, e);
        }

        if (!foundSig) {
            if (foundCert) throw new HandleException(HandleException.SECURITY_ALERT, "Found HS_CERT but not HS_SIGNATURE, manual resigning required");
            return;
        }

        if (dryRun) return;

        JoseSignHandleRecordOperation resignHandleOp;
        if (isRemote) {
            resignHandleOp = new JoseSignHandleRecordOperation(issIdentity, chain, baseUri, username, password, privateKeyId, privateKeyPassphrase);
        } else {
            resignHandleOp = new JoseSignHandleRecordOperation(issPrivateKey, issIdentity, chain);
        }
        resignHandleOp.process(handle, values, resolver, authInfo, site);
    }

}

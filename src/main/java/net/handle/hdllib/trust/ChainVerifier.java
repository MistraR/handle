/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.hdllib.trust;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;

public class ChainVerifier {
    private static final String TRUST_ROOT_HANDLE = "0.0/0.0";
    private final HandleVerifier handleVerifier = new HandleVerifier();

    private final List<PublicKey> rootKeys;
    private AbstractRequiredSignerStore requiredSigners;

    public ChainVerifier(List<PublicKey> rootKeys) {
        this.rootKeys = rootKeys;
    }

    public ChainVerifier(List<PublicKey> rootKeys, AbstractRequiredSignerStore requiredSigners) {
        this.rootKeys = rootKeys;
        this.requiredSigners = requiredSigners;
    }

    public ChainVerificationReport verifyValues(String handle, List<HandleValue> values, List<IssuedSignature> issuedSignatures) {
        ChainVerificationReport report = new ChainVerificationReport();
        ValuesSignatureVerificationReport valuesReport = handleVerifier.verifyValues(handle, values, issuedSignatures.get(0).jws, issuedSignatures.get(0).issuerPublicKey);
        report.valuesReport = valuesReport;
        setChainReportValues(report, handle, issuedSignatures);
        return report;
    }

    public ChainVerificationReport verifyChain(List<IssuedSignature> issuedSignatures) {
        ChainVerificationReport report = new ChainVerificationReport();
        String handle = null;
        setChainReportValues(report, handle, issuedSignatures);
        return report;
    }

    private void setChainReportValues(ChainVerificationReport report, String handle, List<IssuedSignature> issuedSignatures) {
        List<IssuedSignatureVerificationReport> reports = checkIssuedSignatures(handle, issuedSignatures);
        report.issuedSignatureVerificationReports = reports;
        if (requiredSigners != null && handle != null) {
            List<JsonWebSignature> relevantRequiredSigners = requiredSigners.getRequiredSignersAuthorizedOver(handle);
            if (relevantRequiredSigners != null && !relevantRequiredSigners.isEmpty()) {
                report.chainNeedsRequiredSigner = true;
                report.chainGoodUpToRequiredSigner = areIssuedSignaturesTrustAndAuthorizedUpToRequiredSigner(relevantRequiredSigners, issuedSignatures, reports);
            }
        }
        JsonWebSignature rootSig = issuedSignatures.get(issuedSignatures.size() - 1).jws;
        HandleClaimsSet rootClaims = handleVerifier.getHandleClaimsSet(rootSig);
        if (rootClaims == null) return;
        if (isRoot(rootClaims.sub, rootClaims.publicKey)) report.rootIsTrusted = true;
    }

    private boolean areIssuedSignaturesTrustAndAuthorizedUpToRequiredSigner(List<JsonWebSignature> relevantRequiredSigners, List<IssuedSignature> issuedSignatures, List<IssuedSignatureVerificationReport> reports) {
        // go up the chain
        for (int i = 0; i < issuedSignatures.size(); i++) {
            IssuedSignature sig = issuedSignatures.get(i);
            IssuedSignatureVerificationReport sigReport = reports.get(i);
            HandleClaimsSet chainClaims = handleVerifier.getHandleClaimsSet(sig.jws);
            for (JsonWebSignature requiredSigner : relevantRequiredSigners) {
                HandleClaimsSet requiredSignerClaims = handleVerifier.getHandleClaimsSet(requiredSigner);
                // if you see an entity named in a relevant local cert, you are done
                if (Util.equalsPrefixCI(requiredSignerClaims.sub, chainClaims.iss) && requiredSignerClaims.publicKey.equals(sig.issuerPublicKey)) {
                    return true;
                }
            }
            // if not at a local cert entity, but permission not granted, the chain is bad, even if it reaches a local cert entity higher up
            if (!sigReport.canTrustAndAuthorized()) {
                return false;
            }
        }
        // never saw a local cert entity
        return false;
    }

    private List<IssuedSignatureVerificationReport> checkIssuedSignatures(String handle, List<IssuedSignature> issuedSignatures) {
        List<IssuedSignatureVerificationReport> result = new ArrayList<>();
        for (IssuedSignature issuedSignature : issuedSignatures) {
            IssuedSignatureVerificationReport report = handleVerifier.verifyIssuedSignature(handle, issuedSignature);
            result.add(report);
        }
        return result;
    }

    private boolean isRoot(String subject, PublicKey publicKey) {
        if (rootKeys == null) {
            System.err.println("Error missing root keys.");
        }
        return Util.equalsPrefixCI(TRUST_ROOT_HANDLE, ValueReference.fromString(subject).getHandleAsString()) && rootKeys.contains(publicKey);
    }

}

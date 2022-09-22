/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.auth;

import java.util.ArrayList;
import java.util.List;

public class AuthenticationResponse {
    private boolean isAuthenticating;
    private String sessionId;
    private byte[] nonce;
    private String serverAlg;
    private byte[] serverSignature;
    private String id;
    private boolean authenticated;
    private List<String> errors = new ArrayList<>();

    public AuthenticationResponse() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isAuthenticating() {
        return isAuthenticating;
    }

    public void setAuthenticating(boolean isAuthenticating) {
        this.isAuthenticating = isAuthenticating;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public String getServerAlg() {
        return serverAlg;
    }

    public void setServerAlg(String serverAlg) {
        this.serverAlg = serverAlg;
    }

    public byte[] getServerSignature() {
        return serverSignature;
    }

    public void setServerSignature(byte[] serverSignature) {
        this.serverSignature = serverSignature;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addError(String error) {
        this.errors.add(error);
    }

}

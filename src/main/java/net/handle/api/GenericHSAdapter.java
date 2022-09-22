/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.api;

import java.security.PrivateKey;
import net.handle.hdllib.*;

/** Implementation of HSAdapter that uses the handle protocol
 * to resolve, create, modify and delete handles
 */
class GenericHSAdapter implements HSAdapter {

    private String adminHandle = null;

    private Resolver resolver = null;

    private ClientSessionTracker sessionTracker = null;

    private AuthenticationInfo authenticationInfo = null;

    private boolean isAuthenticated = false;

    /** Constructor of HSAdapter with no administrative privileges.
     */
    GenericHSAdapter() {
        resolver = new Resolver();
        String traceFlag = System.getProperty("handletrace", "no");
        resolver.getResolver().traceMessages = traceFlag.equals("on") || traceFlag.startsWith("t") || traceFlag.startsWith("y");
        useSessions();
    }

    /** Constructor for HSAdapter with administrative privileges based on the private key
     *         provided.
     *
     * @param adminHandle
     *            The administrative Handle of the user.
     * @param keyIndex
     *            The index at which the public key is present in the
     *            administrative Handle.
     * @param privateKey
     *            The byte array of the private key that matches the public key.
     * @param cipher
     *            The byte array of the cipher used to encrypt the keys. Use
     *            null for unencrypted keys.
     * @throws HandleException
     *             Thrown when the authentication information is invalid.
     */
    GenericHSAdapter(final String adminHandle, final int keyIndex, final byte[] privateKey, final byte[] cipher) throws HandleException {
        this();
        this.adminHandle = adminHandle;
        try {
            // decrypt the private key using the cipher
            byte[] buffer = Util.decrypt(privateKey, cipher);
            PrivateKey key = Util.getPrivateKeyFromBytes(buffer, 0);
            authenticationInfo = new PublicKeyAuthenticationInfo(Util.encodeString(adminHandle), keyIndex, key);

            if (!resolver.checkAuthentication(authenticationInfo)) {
                throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Invalid credentials");
            }
        } catch (Exception ex) {
            if (ex instanceof HandleException) throw (HandleException) ex;
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        isAuthenticated = true;
        useSessions();
    }

    /** Constructor for HSAdapter with administrative privileges based on the secret key
     *         provided.
     *
     * @param adminHandle
     *            The administrative Handle of the user.
     * @param keyIndex
     *            The index at which the public key is present in the
     *            administrative Handle.
     * @param secretKey
     *            The byte array of the secret key.
     * @throws HandleException
     *             Thrown when the authentication information is invalid.
     */
    GenericHSAdapter(final String adminHandle, final int keyIndex, final byte[] secretKey) throws HandleException {
        this();
        this.adminHandle = adminHandle;
        try {
            authenticationInfo = new SecretKeyAuthenticationInfo(Util.encodeString(adminHandle), keyIndex, secretKey);

            if (!resolver.checkAuthentication(authenticationInfo)) {
                throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Invalid credentials");
            }
        } catch (Exception ex) {
            if (ex instanceof HandleException) throw (HandleException) ex;
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        isAuthenticated = true;
        useSessions();
    }

    /**
     * Add handle values to the given handle
     */
    @Override
    public void addHandleValues(final String handle, final HandleValue[] values) throws HandleException {
        if (!isAuthenticated) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        if (values == null || values.length <= 0) return;
        HandleException exception = null;
        try {
            AbstractRequest request = new AddValueRequest(Util.encodeString(handle), values, authenticationInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error adding handle records");
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Build a HandleValue of type HS_ADMIN that refers to the given administrator
     * with the default permissions.
     */
    @Override
    public HandleValue createAdminValue(final String adminHandleForValue, final int keyIndex, int index) throws HandleException {
        AdminRecord adminRecord = new AdminRecord(Util.encodeString(adminHandleForValue), keyIndex, true, true, true, true, true, true, true, true, true, true, true, true);
        return new HandleValue(index, Common.ADMIN_TYPE, Encoder.encodeAdminRecord(adminRecord), HandleValue.TTL_TYPE_RELATIVE, 86400, 0, null, true, true, true, false);
    }

    /**
     * Create the given handle, initially containing the given handle values
     */
    @Override
    public void createHandle(final String handle, final HandleValue[] values) throws HandleException {
        if (!isAuthenticated) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        if (values == null || values.length <= 0) {
            throw new HandleException(HandleException.INVALID_VALUE, "Handle values not found");
        }
        HandleException exception = null;
        try {
            AbstractRequest request = new CreateHandleRequest(Util.encodeString(handle), values, authenticationInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error creating handle");
            try {
                exception.initCause(e);
            } catch (Throwable e2) {
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Build a HandleValue with the given index, type and data fields with
     * all other fields containing the default values.
     */
    @Override
    public HandleValue createHandleValue(final int index, final String type, final String data) throws HandleException {
        return new HandleValue(index, Util.encodeString(type), Util.encodeString(data), HandleValue.TTL_TYPE_RELATIVE, 86400, 0, null, true, true, true, false);
    }

    /**
     * Delete the given handle
     */
    @Override
    public void deleteHandle(final String handle) throws HandleException {
        if (!isAuthenticated) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        HandleException exception = null;
        try {
            AbstractRequest request = new DeleteHandleRequest(Util.encodeString(handle), authenticationInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error deleting handle");
            try {
                exception.initCause(e);
            } catch (Throwable e2) {
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Remove the given handle values, based on their indexes, from the given handle
     */
    @Override
    public void deleteHandleValues(final String handle, HandleValue[] values) throws HandleException {
        if (!isAuthenticated) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        if (values == null || values.length <= 0) {
            throw new HandleException(HandleException.INVALID_VALUE, "No values given in deleteHandleValues");
        }
        HandleException exception = null;
        try {
            int[] indexes = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                indexes[i] = values[i].getIndex();
            }
            AbstractRequest request = new RemoveValueRequest(Util.encodeString(handle), indexes, authenticationInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error deleting handle records");
            try {
                exception.initCause(e);
            } catch (Throwable e2) {
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    public HandleValue[] resolveHandle(final String handle, String[] types, int[] indexes) throws HandleException {
        return resolveHandle(handle, types, indexes, false);
    }

    /**
     * Retrieve the values that are associated with the given handle and that
     * either have one of the given types or one of the given indexes.
     */
    @Override
    public HandleValue[] resolveHandle(final String handle, String[] types, int[] indexes, boolean auth) throws HandleException {
        byte[][] filters = null;
        HandleException exception = null;
        if (types == null) types = new String[0];
        if (indexes == null) indexes = new int[0];
        // convert the filters
        if (types.length > 0) {
            filters = new byte[types.length][];
            for (int i = 0; i < types.length; i++) {
                filters[i] = Util.encodeString(types[i]);
            }
        }
        try {
            AbstractRequest request = new ResolutionRequest(Util.encodeString(handle), filters, indexes, authenticationInfo);
            if (authenticationInfo != null && isAuthenticated) {
                request.ignoreRestrictedValues = false;
            }
            request.authoritative = auth;
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                return ((ResolutionResponse) response).getHandleValues();
            } else {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error resolving handle");
            try {
                exception.initCause(e);
            } catch (Throwable e2) {
            }
        }
        throw exception;
    }

    /*****************************************************************
     * Set how long to wait for responses to TCP and HTTP requests.
     *****************************************************************/
    @Override
    public void setTcpTimeout(int newTcpTimeout) {
        this.resolver.getResolver().setTcpTimeout(newTcpTimeout);
    }

    /*****************************************************************
     * Get how long to wait for responses to TCP requests.
     *****************************************************************/
    @Override
    public int getTcpTimeout() {
        return this.resolver.getResolver().getTcpTimeout();
    }

    /**
     * Adds and prioritizes the UDP for communicating with the Handle server.
     */
    @Override
    public void setUseUDP(boolean useUDP) {
        if (useUDP) {
            resolver.getResolver().setPreferredProtocols(new int[] { Interface.SP_HDL_UDP, Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP });
        } else {
            resolver.getResolver().setPreferredProtocols(new int[] { Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP });
        }
    }

    /**
     * Update the given values in the handle server, replacing any values that
     * have the same index.
     */
    @Override
    public void updateHandleValues(String handle, HandleValue[] values) throws HandleException {
        if (!isAuthenticated) {
            throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        }
        if (values == null || values.length <= 0) return;
        HandleException exception = null;
        try {
            AbstractRequest request = new ModifyValueRequest(Util.encodeString(handle), values, authenticationInfo);
            AbstractResponse response = resolver.getResolver().processRequest(request);
            if (!(response.responseCode == AbstractMessage.RC_SUCCESS)) {
                exception = errorToException(response, handle);
            }
        } catch (Throwable e) {
            exception = new HandleException(HandleException.INTERNAL_ERROR, "Error updating handle records");
            try {
                exception.initCause(e);
            } catch (Throwable e2) {
            }
        }
        if (exception != null) throw exception;
    }

    private void useSessions() {
        if (!isAuthenticated) return;
        sessionTracker = new ClientSessionTracker();
        try {
            sessionTracker.setSessionSetupInfo(new SessionSetupInfo());
        } catch (Exception e) {
            System.err.println("**** Sessions not activated for " + adminHandle);
        }
        resolver.getResolver().setSessionTracker(sessionTracker);
    }

    /**
     * Process most of the response error codes. The successfull code has to be
     * handles by the method and there needs to be a catchall exception
     *
     * @param response    The response to process.
     * @param handle      The Handle for which the command was issued.
     */
    private static HandleException errorToException(AbstractResponse response, String handle) {
        if (response.responseCode == AbstractMessage.RC_HANDLE_ALREADY_EXISTS) {
            return new HandleException(HandleException.HANDLE_ALREADY_EXISTS, "Handle already exists");
        } else if (response.responseCode == AbstractMessage.RC_HANDLE_NOT_FOUND) {
            return new HandleException(HandleException.HANDLE_DOES_NOT_EXIST, "Handle not found");
        } else if (response.responseCode == AbstractMessage.RC_VALUE_ALREADY_EXISTS) {
            return new HandleException(HandleException.INVALID_VALUE, "Handle value already exists");
        } else if (response.responseCode == AbstractMessage.RC_INVALID_VALUE) {
            return new HandleException(HandleException.INVALID_VALUE, "Invalid handle value");
        } else if (response.responseCode == AbstractMessage.RC_INVALID_HANDLE) {
            return new HandleException(HandleException.INTERNAL_ERROR, "Invalid handle value");
        } else if (response.responseCode >= AbstractMessage.RC_INVALID_ADMIN) {
            return new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        } else if (response.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED) {
            return new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Error authenticating handle administrator");
        } else if (response.responseCode == AbstractMessage.RC_INSUFFICIENT_PERMISSIONS) {
            return new HandleException(HandleException.SECURITY_ALERT, "Insufficient handle permissions");
        } else if (response.responseCode == AbstractMessage.RC_VALUES_NOT_FOUND) {
            return new HandleException(HandleException.INVALID_VALUE, "Handle values not found");
        } else {
            return new HandleException(HandleException.SERVER_ERROR, "Unknown handle error");
        }
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.handle.hdllib.*;

import java.net.*;
import java.security.cert.X509Certificate;
import java.io.*;

import javax.net.ssl.SSLHandshakeException;

/**********************************************************************
 * An HdlTcpRequestHandler object will handle requests submitted using
 * the TCP handle protocol.  The request will be processed using the
 * server object and a response will be returned using the TCP handle
 * protocol.
 **********************************************************************/
public class HdlTcpRequestHandler implements Runnable, ResponseMessageCallback {
    private static final int DEFAULT_MAX_MESSAGE_LENGTH = 1024;

    private Socket socket = null;
    private final AbstractServer server;
    private final Main main;

    private boolean logAccesses = false;
    private final MessageEnvelope envelope = new MessageEnvelope();
    private final byte envelopeBuf[] = new byte[Common.MESSAGE_ENVELOPE_SIZE];
    private byte messageBuf[] = new byte[DEFAULT_MAX_MESSAGE_LENGTH];

    public static final String ACCESS_TYPE = "TCP:HDL";
    public static final byte MSG_INVALID_MSG_SIZE[] = Util.encodeString("Invalid message length");
    public static final byte MSG_READ_TIMED_OUT[] = Util.encodeString("Read timed out");

    private long recvTime = 0; // time current request was received
    private AbstractRequest currentRequest;
    private final HdlTcpInterface interfc;

    public HdlTcpRequestHandler(Main main, HdlTcpInterface ifc, boolean logAccesses, Socket socket, long recvTime) {
        this.main = main;
        this.interfc = ifc;
        this.server = main.getServer();
        this.logAccesses = logAccesses;
        this.recvTime = recvTime;
        this.socket = socket;
    }

    @Override
    public void run() {
        InputStream in = null;
        try {
            in = socket.getInputStream();
            int r, n = 0;
            // receive and parse the message envelope
            while (n < Common.MESSAGE_ENVELOPE_SIZE && (r = in.read(envelopeBuf, n, Common.MESSAGE_ENVELOPE_SIZE - n)) > 0) {
                n += r;
            }
            Encoder.decodeEnvelope(envelopeBuf, envelope);
            if (envelope.messageLength > Common.MAX_MESSAGE_LENGTH || envelope.messageLength < 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }
            if (messageBuf.length < envelope.messageLength) { // increase the messageBuf size if necessary
                messageBuf = new byte[envelope.messageLength];
            }
            // receive the rest of the message
            r = n = 0;
            while (n < envelope.messageLength && (r = in.read(messageBuf, n, envelope.messageLength - n)) > 0) {
                n += r;
            }
            if (n < envelope.messageLength) { // we didn't receive the whole message...
                String errMsg = "Expecting " + envelope.messageLength + " bytes, " + "only received " + n;
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(errMsg)));
                return;
            }

            if (envelope.encrypted) { //decrypt incoming request if it says so ..
                if (envelope.sessionId > 0) {
                    ServerSideSessionInfo sssinfo = null;
                    if (server instanceof HandleServer) {
                        sssinfo = ((HandleServer) server).getSession(envelope.sessionId);
                        if (sssinfo != null) {
                            try {
                                messageBuf = sssinfo.decryptBuffer(messageBuf, 0, envelope.messageLength);
                                envelope.encrypted = false;
                                envelope.messageLength = messageBuf.length;
                            } catch (Exception e) {
                                main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Exception decrypting request: " + e);
                                e.printStackTrace();
                                System.err.println("Exception decrypting request with session key: " + e.getMessage());
                                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Exception decrypting request with session key " + e)));
                                return;
                            }
                        } else {
                            // sssinfo == null, maybe time out!
                            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Session information not available or time out. Unable to decrypt request message");
                            System.err.println("Session information not available or time out. Unable to decrypt request message.");
                            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_TIMEOUT, Util.encodeString("Session information not available or time out. Unable to decrypt request message.")));
                            return;
                        }
                    } else {
                        // serverSessionMan == null
                        main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Session manager not available. Unable to decrypt request message.");
                        System.err.println("Session manager not available. Request message not decrypted.");
                        handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Session manager not available. Unable to decrypt request message.")));
                        return;
                    }
                } else {
                    main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, "Invalid session id. Request message not decrypted.");
                    System.err.println("Invalid session id. Request message not decrypted.");
                    handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Invalid session id. Unable to decrypt request message.")));
                    return;
                }
            }

            if (envelope.messageLength < 24) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            int opCode = Encoder.readOpCode(messageBuf, 0);
            if (opCode == 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString("Unknown opCode in message: " + opCode)));
                return;
            }

            currentRequest = (AbstractRequest) Encoder.decodeMessage(messageBuf, 0, envelope);
            String errMsg = interfc.canProcessMsg(currentRequest);
            if (errMsg != null) {
                main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, errMsg);
                handleResponse(new ErrorResponse(currentRequest.opCode, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(errMsg)));
                return;
            }
            server.processRequest(currentRequest, this);
        } catch (SocketTimeoutException e) {
            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_READ_TIMED_OUT));
        } catch (Throwable e) {
            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_ERROR, Util.encodeString("Server error processing request, see server logs")));
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception processing request: " + e);
            e.printStackTrace();
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable e) { }
            }
            if (socket != null) {
                try { socket.close(); } catch (Exception e){ }
                socket = null;
            }
        }
    }

    /****************************************************************************
     * Handle (log) any messages that are reported by the upstream message
     * provider.
     ****************************************************************************/
    public void handleResponseError(String error) {
        main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Server error: " + error);
    }

    /****************************************************************************
     * Encode and send the response
     ****************************************************************************/
    @Override
    @SuppressWarnings("resource") // we may keep the socket (and thus the output stream) open
    public void handleResponse(AbstractResponse response) {
        OutputStream out = null;
        boolean keepSocketOpen = response.continuous;
        boolean errorWriting = false;
        try {
            byte msg[] = response.getEncodedMessage();

            // when to encrypt? right before sending it out! after the credential portion is formed!
            // encrypt response here if the request asks for encryption
            // and set the flag in envelop if successfull
            boolean encrypted = false;
            if (response.sessionId > 0 && (response.encrypt || response.shouldEncrypt())) {
                ServerSideSessionInfo sssinfo = null;
                if (server instanceof HandleServer) {
                    sssinfo = ((HandleServer) server).getSession(response.sessionId);
                    if (sssinfo != null) {
                        try {
                            msg = sssinfo.encryptBuffer(msg, 0, msg.length);
                            encrypted = true;
                        } catch (Exception e) {
                            main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Exception encrypting response: " + e);
                            System.err.println("Exception encrypting message with session key: " + e.getMessage());
                            encrypted = false;
                        }
                    } // sssinfo != null
                } else {
                    // serverSessionMan == null
                    main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, "Session manager not available. Message not encrypted.");
                    System.err.println("Session manager not available. Message not encrypted.");
                    encrypted = false;
                }
            }

            // set the envelop flag for encryption
            envelope.encrypted = encrypted;
            envelope.messageLength = msg.length; //get the length after encryption
            envelope.messageId = 0;
            envelope.requestId = response.requestId;
            envelope.sessionId = response.sessionId;
            envelope.protocolMajorVersion = response.majorProtocolVersion;
            envelope.protocolMinorVersion = response.minorProtocolVersion;
            envelope.suggestMajorProtocolVersion = response.suggestMajorProtocolVersion;
            envelope.suggestMinorProtocolVersion = response.suggestMinorProtocolVersion;
            Encoder.encodeEnvelope(envelope, envelopeBuf);

            try {
                out = socket.getOutputStream();
                out.write(Util.concat(envelopeBuf, msg));
                out.flush();
            } catch (Exception e) {
                errorWriting = true;
                throw e;
            }

            long respTime = System.currentTimeMillis() - recvTime;
            if (logAccesses) {
                if (currentRequest != null) {
                    main.logAccess(ACCESS_TYPE + "(" + currentRequest.suggestMajorProtocolVersion + "." + currentRequest.suggestMinorProtocolVersion + ")", socket.getInetAddress(), currentRequest.opCode, response.responseCode,
                        Util.getAccessLogString(currentRequest, response), respTime);
                }
            }
            if (response.streaming) { // if the response is "streamable," send the streamed part...
                streamResponse(response);
            }
        } catch (Exception e) {
            String clientString = "";
            try {
                clientString = " to " + Util.rfcIpRepr(socket.getInetAddress());
            } catch (Exception ex) {
                // ignore
            }
            if (errorWriting && keepSocketOpen) {
                keepSocketOpen = false;
                throw new RuntimeException(new HandleException(HandleException.INTERNAL_ERROR, "Error writing continuous handle response" + clientString, e));
            }
            if (response.streaming && e instanceof SSLHandshakeException) {
                // no stack trace; may happen due to Java 7 Diffie-Hellman padding bug
                main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Exception sending response" + clientString + " (if occasional handshake failure, safe to ignore): " + e);
            } else if (response.streaming && e.getCause() instanceof SSLHandshakeException) {
                // no stack trace; may happen due to Java 7 Diffie-Hellman padding bug
                main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Exception sending response" + clientString + " (if occasional handshake failure, safe to ignore): " + e.getCause());
            } else {
                main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Exception sending response" + clientString + ": " + e);
                if (e instanceof java.net.SocketTimeoutException || e.getCause() instanceof java.net.SocketTimeoutException) {
                    // no stack trace
                } else {
                    e.printStackTrace(System.err);
                }
            }
        } finally {
            if (out != null && !keepSocketOpen) {
                try {
                    out.close();
                } catch (Exception e) {
                    main.logError(ServerLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Exception sending response: " + e);
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    @SuppressWarnings("resource") // we don't close here, only flush
    private void streamResponse(AbstractResponse response) throws HandleException, IOException {
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        SignedOutputStream sout;
        if (server instanceof HandleServer && response.hasEqualOrGreaterVersion(2, 8)) {
            X509Certificate certificate = ((HandleServer) server).getHdlTcpCertificate();
            sout = new SignedOutputStream(certificate, server.getPrivateKey(), out, socket);
        } else {
            sout = new SignedOutputStream(server.getPrivateKey(), out);
        }
        response.streamResponse(sout);
        sout.flush();
    }
}

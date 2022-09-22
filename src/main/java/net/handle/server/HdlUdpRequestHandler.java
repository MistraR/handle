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

/**********************************************************************
 * An HdlUdpRequestHandler object will handle requests submitted using
 * the UDP handle protocol.  The request will be processed using the
 * server object and a response will be returned using the UDP handle
 * protocol.
 **********************************************************************/

public class HdlUdpRequestHandler implements Runnable, ResponseMessageCallback {
    private final DatagramPacket packet;
    private final DatagramSocket dsocket;
    private final AbstractServer server;
    private final Main main;
    private final HdlUdpInterface listener;

    private boolean logAccesses = false;
    private final MessageEnvelope envelope = new MessageEnvelope();

    private AbstractRequest currentRequest;
    private final long recvTime;

    public static final String ACCESS_TYPE = "UDP:HDL";
    public static final byte MSG_INVALID_MSG_SIZE[] = Util.encodeString("Invalid message length");

    public HdlUdpRequestHandler(Main main, DatagramSocket dsock, HdlUdpInterface listener, boolean logAccesses, DatagramPacket packet, long recvTime) {
        this.main = main;
        this.server = main.getServer();
        this.dsocket = dsock;
        this.logAccesses = logAccesses;
        this.listener = listener;
        this.packet = packet;
        this.recvTime = recvTime;
    }

    @Override
    public void run() {
        byte pkt[] = null;
        int offset = Common.MESSAGE_ENVELOPE_SIZE;
        try {
            pkt = packet.getData();
            Encoder.decodeEnvelope(pkt, envelope);
            if (envelope.messageLength > Common.MAX_MESSAGE_LENGTH || envelope.messageLength < 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            if (envelope.truncated) {
                // this is one packet in a multi-packet request.
                // we should give the packet to the listener object, who will
                // return a complete HdlUdpPendingRequest object if we are to handle
                // the complete request.  If null is returned, then the request
                // will be given to another handler so we don't have to worry about it.
                HdlUdpPendingRequest req = listener.addMultiPacketListener(envelope, packet, packet.getAddress());
                if (req == null) return;
                pkt = req.getMessage();
                offset = 0;
            }

            //decrypt incoming request if it says so
            if (envelope.encrypted) {
                if (envelope.sessionId > 0) {
                    ServerSideSessionInfo sssinfo = null;
                    if (server instanceof HandleServer) {
                        sssinfo = ((HandleServer) server).getSession(envelope.sessionId);
                        if (sssinfo != null) {
                            try {
                                pkt = sssinfo.decryptBuffer(pkt, offset, envelope.messageLength);
                                envelope.encrypted = false;
                                envelope.messageLength = pkt.length;
                                offset = 0;
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

            int opCode = Encoder.readOpCode(pkt, offset);
            if (opCode == 0) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString("Unknown opCode in message: " + opCode)));
                return;
            }

            currentRequest = (AbstractRequest) Encoder.decodeMessage(pkt, offset, envelope);
            String errMsg = listener.canProcessMsg(currentRequest);
            if (errMsg != null) {
                main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, errMsg);
                handleResponse(new ErrorResponse(currentRequest.opCode, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(errMsg)));
                return;
            }
            server.processRequest(currentRequest, this);
        } catch (Throwable e) {
            handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_ERROR, Util.encodeString("Server error processing request, see server logs")));
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception processing request: " + e);
            e.printStackTrace(System.err);
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
    public void handleResponse(AbstractResponse response) {
        try {
            byte msg[] = response.getEncodedMessage();
            //when to encrypt? right before sending it out! after the credential portion is formed!
            //encrypt response here if the request asks for encryption
            //and set the flag in envelop if successfull
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

            //set the envelop flag for encryption
            envelope.encrypted = encrypted;
            envelope.messageLength = msg.length; //get the length after encryption
            envelope.messageId = 0;
            envelope.requestId = response.requestId;
            envelope.sessionId = response.sessionId;
            envelope.protocolMajorVersion = response.majorProtocolVersion;
            envelope.protocolMinorVersion = response.minorProtocolVersion;
            envelope.suggestMajorProtocolVersion = response.suggestMajorProtocolVersion;
            envelope.suggestMinorProtocolVersion = response.suggestMinorProtocolVersion;
            if (msg.length > Common.MAX_UDP_DATA_SIZE) {
                // split the response into multiple pieces and send it
                int bytesRemaining = msg.length;
                while (bytesRemaining > 0) {
                    byte buf[];
                    if (bytesRemaining <= Common.MAX_UDP_DATA_SIZE) {
                        buf = new byte[bytesRemaining + Common.MESSAGE_ENVELOPE_SIZE];
                        System.arraycopy(msg, msg.length - bytesRemaining, buf, Common.MESSAGE_ENVELOPE_SIZE, bytesRemaining);
                    } else {
                        buf = new byte[Common.MAX_UDP_DATA_SIZE + Common.MESSAGE_ENVELOPE_SIZE];
                        System.arraycopy(msg, msg.length - bytesRemaining, buf, Common.MESSAGE_ENVELOPE_SIZE, Common.MAX_UDP_DATA_SIZE);
                    }
                    Encoder.encodeEnvelope(envelope, buf);
                    dsocket.send(new DatagramPacket(buf, buf.length, packet.getAddress(), packet.getPort()));
                    bytesRemaining -= Common.MAX_UDP_DATA_SIZE;
                    envelope.messageId++;
                }
            } else {
                // all of the response fits in one packet, so let's send it..
                byte buf[] = new byte[msg.length + Common.MESSAGE_ENVELOPE_SIZE];
                Encoder.encodeEnvelope(envelope, buf);
                System.arraycopy(msg, 0, buf, Common.MESSAGE_ENVELOPE_SIZE, msg.length);
                dsocket.send(new DatagramPacket(buf, buf.length, packet.getAddress(), packet.getPort()));
            }
        } catch (Exception e) {
            String clientString = "";
            try {
                clientString = " to " + Util.rfcIpRepr(dsocket.getInetAddress());
            } catch (Exception ex) {
                // ignore
            }
            main.logError(ServerLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception sending response" + clientString + ": " + e);
            e.printStackTrace(System.err);
        }
        if (logAccesses) {
            if (currentRequest != null) {
                long time = System.currentTimeMillis() - recvTime;
                main.logAccess(ACCESS_TYPE + "(" + currentRequest.suggestMajorProtocolVersion + "." + currentRequest.suggestMinorProtocolVersion + ")", packet.getAddress(), currentRequest.opCode,
                    (response != null ? response.responseCode : AbstractMessage.RC_ERROR), Util.getAccessLogString(currentRequest, response), time);
            }
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer.servlets;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.cnri.util.ServletUtil;
import net.handle.apps.servlet_proxy.HDLProxy;
import net.handle.apps.servlet_proxy.RotatingAccessLog;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.Common;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.ErrorResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.Interface;
import net.handle.hdllib.MessageEnvelope;
import net.handle.hdllib.RequestProcessor;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.hdllib.ServerInfo;
import net.handle.hdllib.SessionInfo;
import net.handle.hdllib.SignedOutputStream;
import net.handle.hdllib.Util;
import net.handle.server.servletcontainer.HandleServerInterface;

public class NativeServlet extends HttpServlet {
    public static final byte[] MSG_INVALID_MSG_SIZE = Util.encodeString("Invalid message length");
    public static final byte[] MSG_INVALID_REQUEST = Util.encodeString("Invalid request");

    HandleServerInterface handleServer;
    RequestProcessor requestHandler;
    boolean processQueriesHttp;
    boolean processAdminRequestsHttp;
    boolean processQueriesHttps;
    boolean processAdminRequestsHttps;

    @Override
    public void init() throws ServletException {
        handleServer = (net.handle.server.servletcontainer.HandleServerInterface) getServletContext().getAttribute("net.handle.server.HandleServer");
        if (handleServer == null) {
            requestHandler = (HandleResolver) getServletContext().getAttribute(HandleResolver.class.getName());
            if (requestHandler == null) requestHandler = new HandleResolver();
            processQueriesHttp = true;
            processAdminRequestsHttp = false;
            processQueriesHttps = true;
            processAdminRequestsHttps = false;
        } else {
            requestHandler = handleServer;
            setCanProcessInServer();
        }
    }

    private void logError(int level, String logString) {
        if (handleServer == null) {
            HDLProxy hdlProxy = (HDLProxy) getServletContext().getAttribute(HDLProxy.class.getName());
            if (hdlProxy == null) {
                System.err.println(logString);
            } else {
                hdlProxy.logError(level, logString);
            }
        } else {
            handleServer.logError(level, logString);
        }
    }

    private void setCanProcessInServer() {
        ServerInfo svrInfo = handleServer.getServerInfo();
        for (Interface intf : svrInfo.interfaces) {
            if (intf == null) continue;
            if (intf.protocol == Interface.SP_HDL_HTTP) {
                if (intf.type == Interface.ST_ADMIN_AND_QUERY || intf.type == Interface.ST_ADMIN) {
                    processAdminRequestsHttp = true;
                    processAdminRequestsHttps = true;
                }
                if (intf.type == Interface.ST_ADMIN_AND_QUERY || intf.type == Interface.ST_QUERY) {
                    processQueriesHttp = true;
                    processQueriesHttps = true;
                }
            } else if (intf.protocol == Interface.SP_HDL_HTTPS) {
                if (intf.type == Interface.ST_ADMIN_AND_QUERY || intf.type == Interface.ST_ADMIN) {
                    processAdminRequestsHttps = true;
                }
                if (intf.type == Interface.ST_ADMIN_AND_QUERY || intf.type == Interface.ST_QUERY) {
                    processQueriesHttps = true;
                }
            }
        }
    }

    String canProcessMsg(HttpServletRequest servletReq, AbstractRequest req) {
        boolean processQueries = servletReq.isSecure() ? processQueriesHttps : processQueriesHttp;
        boolean processAdminRequests = servletReq.isSecure() ? processAdminRequestsHttps : processAdminRequestsHttp;
        return Interface.canProcessMsg(req, processQueries, processAdminRequests);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(HttpServletResponse.SC_FOUND);
        resp.setHeader("Location", getServletContext().getContextPath() + "/api/handles" + ServletUtil.pathExcluding(req.getRequestURI(), getServletContext().getContextPath()));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        new Handler(req, resp).handle();
    }

    class Handler implements ResponseMessageCallback {
        private final HttpServletRequest servletReq;
        private final HttpServletResponse servletResp;
        private AbstractRequest currentHdlRequest;
        private final long recvTime = System.currentTimeMillis();

        public Handler(HttpServletRequest req, HttpServletResponse resp) {
            this.servletReq = req;
            this.servletResp = resp;
        }

        void handle() throws IOException {
            InputStream in = new BufferedInputStream(servletReq.getInputStream());

            int r, n = 0;
            byte envelopeBuf[] = new byte[Common.MESSAGE_ENVELOPE_SIZE];
            MessageEnvelope envelope = new MessageEnvelope();

            // ignore extra newline char, if necessary
            r = in.read();
            if (r != '\n' && r != '\r') {
                envelopeBuf[0] = (byte) r;
                n++;
            }

            // receive and parse the message envelope
            while (n < Common.MESSAGE_ENVELOPE_SIZE && (r = in.read(envelopeBuf, n, Common.MESSAGE_ENVELOPE_SIZE - n)) > 0) {
                n += r;
            }

            if (n < Common.MESSAGE_ENVELOPE_SIZE) {
                // not all of the envelope was received...
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            try {
                Encoder.decodeEnvelope(envelopeBuf, envelope);
            } catch (HandleException e) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            byte messageBuf[] = new byte[envelope.messageLength];

            // receive the rest of the message
            r = n = 0;
            while (n < envelope.messageLength && (r = in.read(messageBuf, n, envelope.messageLength - n)) > 0) {
                n += r;
            }

            if (n < envelope.messageLength) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString("Expecting " + envelope.messageLength + " bytes, " + "only received " + n)));
                return;
            }

            //decrypt incoming request if it says so
            if (envelope.encrypted) {
                messageBuf = decryptMessageReturnNullIfError(envelope, messageBuf);
                if (messageBuf == null) return;
            }

            if (envelope.messageLength < 24) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, MSG_INVALID_MSG_SIZE));
                return;
            }

            try {
                currentHdlRequest = (AbstractRequest) Encoder.decodeMessage(messageBuf, 0, envelope);
            } catch (Exception e) {
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(e.toString())));
                return;
            }

            String errMsg = canProcessMsg(servletReq, currentHdlRequest);
            if (errMsg != null) {
                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD, errMsg);
                handleErrorResponse(currentHdlRequest, AbstractMessage.RC_PROTOCOL_ERROR, Util.encodeString(errMsg));
                return;

            }

            try {
                requestHandler.processRequest(currentHdlRequest, getRemoteInetAddress(servletReq), this);
            } catch (HandleException e) {
                handleErrorResponse(currentHdlRequest, AbstractMessage.RC_ERROR, Util.encodeString("Server error processing request, see server logs"));
                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD, String.valueOf(this.getClass()) + ": Exception processing request: " + e);
            }
        }

        private byte[] decryptMessageReturnNullIfError(MessageEnvelope envelope, byte[] messageBuf) {
            if (envelope.sessionId <= 0) {
                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD, "Invalid session id. Request message not decrypted.");
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Invalid session id. Unable to decrypt request message.")));
                return null;
            }
            //            if(!(handleServer instanceof HandleServerInterface)) {
            //                // serverSessionMan == null
            //                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD,
            //                    "Session manager not available. Request message not decrypted.");
            //                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED,
            //                    AbstractMessage.RC_SESSION_FAILED,
            //                    Util.encodeString("Session manager not available. Unable to decrypt request message.")));
            //
            //                return null;
            //            }
            //
            SessionInfo sssinfo = handleServer.getSession(envelope.sessionId);
            if (sssinfo == null) {
                // sssinfo == null or request id < 0
                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD, "Session information not available. Request message not decrypted.");
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Session information not available. Unable to decrypt request message.")));
                return null;
            }
            try {
                messageBuf = sssinfo.decryptBuffer(messageBuf, 0, envelope.messageLength);
                envelope.encrypted = false;
                envelope.messageLength = messageBuf.length;
                return messageBuf;
            } catch (Exception e) {
                logError(RotatingAccessLog.ERRLOG_LEVEL_REALBAD, "Exception decrypting request: " + e);
                handleResponse(new ErrorResponse(AbstractMessage.OC_RESERVED, AbstractMessage.RC_SESSION_FAILED, Util.encodeString("Exception decrypting request with session key " + e)));
                return null;
            }
        }

        private void handleErrorResponse(AbstractRequest req, int responseCode, byte[] message) {
            ErrorResponse resp;
            try {
                resp = new ErrorResponse(req, responseCode, message);
            } catch (HandleException e) {
                resp = new ErrorResponse(req.opCode, responseCode, message);
                resp.requestId = req.requestId;
                resp.sessionId = req.sessionId;
                resp.takeValuesFrom(req);
                resp.setSupportedProtocolVersion();
            }
            if (handleServer != null) {
                try {
                    handleServer.sendResponse(this, resp);
                    return;
                } catch (HandleException e) {
                    // ignore
                }
            }
            handleResponse(resp);
        }

        @Override
        public void handleResponse(AbstractResponse response) {
            SignedOutputStream sout = null;
            OutputStream out = null;
            try {
                byte msg[] = response.getEncodedMessage();

                //when to encrypt? right before sending it out! after the credential
                //portion is formed!  encrypt response here if the request asks for
                //encryption and set the flag in envelop if successful
                boolean encrypted = false;
                if (response.sessionId > 0 && (response.encrypt || (!servletReq.isSecure() && response.shouldEncrypt()))) {
                    //                    if (handleServer instanceof HandleServerInterface) {
                    SessionInfo sssinfo = handleServer.getSession(response.sessionId);
                    if (sssinfo != null) {

                        try {
                            msg = sssinfo.encryptBuffer(msg, 0, msg.length);
                            encrypted = true;
                        } catch (Exception e) {
                            logError(RotatingAccessLog.ERRLOG_LEVEL_NORMAL, "Exception encrypting response: " + e);
                            encrypted = false;
                        }
                    } // sssinfo != null
                      //                    } else {
                      //                        // serverSessionMan == null
                      //                        logError(RotatingAccessLog.ERRLOG_LEVEL_NORMAL,
                      //                            "Session manager not available. Message not encrypted.");
                      //                        encrypted = false;
                      //                    }
                }

                // set the envelop flag for encryption
                MessageEnvelope envelope = new MessageEnvelope();
                envelope.encrypted = encrypted;
                envelope.messageLength = msg.length; //use the length after encryption
                envelope.messageId = 0;
                envelope.requestId = response.requestId;
                envelope.sessionId = response.sessionId;
                envelope.protocolMajorVersion = response.majorProtocolVersion;
                envelope.protocolMinorVersion = response.minorProtocolVersion;
                envelope.suggestMajorProtocolVersion = response.suggestMajorProtocolVersion;
                envelope.suggestMinorProtocolVersion = response.suggestMinorProtocolVersion;

                byte envelopeBuf[] = new byte[Common.MESSAGE_ENVELOPE_SIZE];
                Encoder.encodeEnvelope(envelope, envelopeBuf);

                servletResp.setContentType(Common.HDL_MIME_TYPE);
                if (!response.streaming) servletResp.setContentLength(envelopeBuf.length + msg.length);

                out = new BufferedOutputStream(servletResp.getOutputStream());
                out.write(envelopeBuf);
                out.write(msg);
                out.flush();

                logAccess(response);

                // if the response is "streamable," send the streamed part...
                if (response.streaming) {
                    if (servletReq.isSecure() && !"DSA".equals(handleServer.getPublicKey().getAlgorithm())) {
                        sout = new SignedOutputStream(out);
                    } else {
                        sout = new SignedOutputStream(handleServer.getPrivateKey(), out);
                    }
                    response.streamResponse(sout);
                    out.flush();
                }
            } catch (Exception e) {
                logError(RotatingAccessLog.ERRLOG_LEVEL_NORMAL, String.valueOf(this.getClass()) + ": Exception sending response: " + e);
                e.printStackTrace(System.err);
            } finally {
                if (sout != null) {
                    try { sout.flush(); } catch (Exception e) {}
                    try { sout.close(); } catch (Exception e) {}
                }
                if (out != null) {
                    try { out.flush(); } catch (Exception e) {}
                    try { out.close(); } catch (Exception e) {}
                }
            }
        }

        private void logAccess(AbstractResponse response) {
            if (currentHdlRequest == null) return;
            String accessString = "HTTP:HDL" + "(" + currentHdlRequest.suggestMajorProtocolVersion + "." + currentHdlRequest.suggestMinorProtocolVersion + ")";
            if (handleServer != null) {
                if (handleServer.logHttpAccesses()) {
                    long respTime = System.currentTimeMillis() - recvTime;
                    handleServer.logAccess(accessString, getRemoteInetAddress(servletReq), currentHdlRequest.opCode, (response != null ? response.responseCode : AbstractMessage.RC_ERROR), Util.getAccessLogString(currentHdlRequest, response),
                        respTime);
                }
            } else {
                HDLProxy hdlProxy = (HDLProxy) servletReq.getServletContext().getAttribute(HDLProxy.class.getName());
                if (hdlProxy == null) return;
                long responseTime = System.currentTimeMillis() - recvTime;
                String referer = servletReq.getHeader("Referer");
                if (referer == null) referer = "";
                String userAgent = servletReq.getHeader("user-agent");
                hdlProxy.logAccess(accessString, response.opCode, response.responseCode, Util.decodeString(currentHdlRequest.handle), hdlProxy.getRemoteAddr(servletReq), referer, userAgent, responseTime, null, null);
            }
        }

    }

    private static InetAddress getRemoteInetAddress(HttpServletRequest servletReq) {
        try {
            HDLProxy hdlProxy = (HDLProxy) servletReq.getServletContext().getAttribute(HDLProxy.class.getName());
            if (hdlProxy == null) return InetAddress.getByName(servletReq.getRemoteAddr());
            return hdlProxy.getRemoteInetAddress(servletReq);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}

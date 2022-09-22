/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.servletcontainer;

import java.io.IOException;
import java.net.InetAddress;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import net.cnri.util.StreamTable;
import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AbstractResponseAndIndex;
import net.handle.hdllib.ChallengeAnswerRequest;
import net.handle.hdllib.ChallengeResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.ReplicationDaemonInterface;
import net.handle.hdllib.RequestProcessor;
import net.handle.hdllib.ResponseMessageCallback;
import net.handle.hdllib.ServerInfo;
import net.handle.hdllib.SessionInfo;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.TransactionValidator;
import net.handle.server.replication.NotifierInterface;

public interface HandleServerInterface extends RequestProcessor {
    PublicKey getPublicKey();

    X509Certificate getCertificate();

    PrivateKey getPrivateKey();

    boolean isCaseSensitive();

    void dumpHandles() throws HandleException, IOException;

    SiteInfo getSiteInfo();

    int getServerNum();

    ServerInfo getServerInfo();

    HandleResolver getResolver();

    HandleStorage getStorage();

    byte[][] storageGetRawHandleValues(byte[] handle, int[] indexList, byte[][] typeList) throws HandleException;

    AbstractResponse errorIfNotHaveHandle(AbstractRequest req) throws HandleException;

    ReplicationDaemonInterface getReplicationDaemon();

    void registerInternalTransactionValidator(TransactionValidator internalTransactionValidator);

    void registerReplicationTransactionValidator(TransactionValidator replicationTransactionValidator);

    void registerReplicationErrorNotifier(NotifierInterface notifier);

    byte[][] getRawHandleValuesWithTemplate(byte inHandle[], int indexList[], byte typeList[][], short recursionCount) throws HandleException;

    void disable();

    void enable();

    void processRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException;

    void processPreAuthenticatedRequest(AbstractRequest req, ResponseMessageCallback callback) throws HandleException;

    void setReplicationPriority(int i);

    SessionInfo getSession(int sessionId);

    void shutdown();

    void logError(int level, String message);

    StreamTable getConfig();

    java.io.File getConfigDir();

    boolean logHttpAccesses();

    void logAccess(String accesssType, InetAddress addr, int opCode, int rsCode, String message, long time);

    void sendResponse(ResponseMessageCallback callback, AbstractResponse response) throws HandleException;

    AbstractResponse verifyIdentity(ChallengeResponse cRes, ChallengeAnswerRequest crReq, AbstractRequest origReq) throws HandleException;

    AbstractResponseAndIndex verifyIdentityAndGetIndex(ChallengeResponse cRes, ChallengeAnswerRequest crReq, AbstractRequest origReq) throws HandleException;
}

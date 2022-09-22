/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.handle.hdllib.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.security.*;

public class SessionManager {

    private final ConcurrentMap<Integer, ServerSideSessionInfo> sessionInfoDict = new ConcurrentHashMap<>(10);
    
    private boolean keepRunning = true;

    private static Random sessionRandom;
    private static String sessionRandomLock = "sessionRandomLock";

    public static final int SESSION_NOT_AVAILABLE = -100;
    public static final int SESSION_MAXNUM_PER_SERVER = 10;

    public SessionManager() {
    }

    public ServerSideSessionInfo getSession(int sessionId) //SERVER side only
    {
        //server side dict is a Hashtable;
        //the keys are session ids, values are ServerSideSessionInfo.
        //it could be an anonymous session record
        ServerSideSessionInfo ssinfo = sessionInfoDict.get(Integer.valueOf(sessionId));
        if (ssinfo != null) {
            ssinfo.touch();
            return ssinfo;
        }

        return null;
    }

    /** to remove a session info entry from session manager.
     */
    public void removeSession(int sessionId) {
        sessionInfoDict.remove(Integer.valueOf(sessionId));
    }

    /*
     replace the old session info with the new session info*/
    public boolean replaceServerSideSessionInfo(int sessionId, ServerSideSessionInfo newInfo) {
        if (newInfo == null) {
            System.err.println("The new session info is null. No info is replaced.");
            return true;
        }
        sessionInfoDict.put(Integer.valueOf(sessionId), newInfo);
        newInfo.touch();
        return true;
    }

    public boolean addSession(ServerSideSessionInfo sessionInfo) {
        if (sessionInfo == null) return true;
        //server side manager adds a session info
        ServerSideSessionInfo ssinfo = sessionInfo;
        sessionInfoDict.put(Integer.valueOf(ssinfo.sessionId), ssinfo);
        sessionInfo.touch();
        return true;
    }

    public void checkTimeoutSession() {
        // start thread to purge timed-out ServerSideSessionInfo, i.g. Session objects.
        // thus the session will not hang on for days...
        CheckSessionTimeOut cst = new CheckSessionTimeOut();
        cst.setDaemon(true);
        cst.setPriority(Thread.MIN_PRIORITY);
        cst.start();
    }

    public Vector<ServerSideSessionInfo> getSessions() {
        return new Vector<>(sessionInfoDict.values());
    }

    public Vector<ServerSideSessionInfo> getSessions(AuthenticationInfo info) {
        if (info != null) return getSessions(info.getUserIdHandle(), info.getUserIdIndex());
        else return null;
    }

    public Vector<ServerSideSessionInfo> getSessions(byte identityHandle[], int identityIndex) {
        //return an enumeration of ServersideServerSideSessionInfo
        Vector<ServerSideSessionInfo> vecSessions = new Vector<>();
        Iterator<ServerSideSessionInfo> enumSession = sessionInfoDict.values().iterator();
        while (enumSession.hasNext()) {
            ServerSideSessionInfo sssinfo = enumSession.next();
            if (Util.equals(sssinfo.identityKeyHandle, identityHandle) && sssinfo.identityKeyIndex == identityIndex) {
                vecSessions.addElement(sssinfo);
            }
        }
        return vecSessions;
    }

    public Enumeration<Integer> getAllKeys() {
        return Collections.enumeration(sessionInfoDict.keySet());
    }

    public synchronized void shutdown() {
        if (!keepRunning) return;
        //to inform the checkSessionTimeOut thread to end
        keepRunning = false;
    }

    /* why we want to put the session time out checking in a seperate thread,
     *  we may want to check session time out on client side.
     */
    private class CheckSessionTimeOut extends Thread {

        @Override
        public void run() {
            while (keepRunning) {
                try {
                    Thread.sleep(60000); //at least 1 minute
                    //server side session manager is checking on session time out

                    Iterator<ServerSideSessionInfo> iter = sessionInfoDict.values().iterator();
                    while (iter.hasNext()) {
                        //can not use gerServerSideSessionInfo method here, that will touch
                        //the "lastTransaction" field non-intentionally
                        ServerSideSessionInfo ssinfo = iter.next();
                        if (ssinfo != null && ssinfo.hasExpired()) {
                            iter.remove();
                        }
                    }

                } //try ...
                catch (Throwable e) {
                    System.err.println("Error purging timeout session objects: " + e);
                }
            }
        }
    }

    /** generate random session key **/
    public static final byte[] getGeneratedSecretKey() {
        // generate a nonce to make this a unique non-repeatable challenge
        byte[] sessionKey = new byte[Common.SESSION_KEY_SIZE];
        getSessionRandom().nextBytes(sessionKey);

        return sessionKey;
    }

    public static final void initializeSessionKeyRandom() {
        initializeSessionKeyRandom(null);
    }

    public static final void initializeSessionKeyRandom(byte seed[]) {
        if (sessionRandom == null) {
            synchronized (sessionRandomLock) {
                if (sessionRandom == null) {
                    if (seed == null) {
                        sessionRandom = new SecureRandom();
                        sessionRandom.setSeed(System.nanoTime());
                    } else sessionRandom = new SecureRandom(seed);
                    sessionRandom.nextInt();
                }
            }
        }
    }

    private static Random getSessionRandom() {
        if (sessionRandom == null) initializeSessionKeyRandom();
        return sessionRandom;
    }

}

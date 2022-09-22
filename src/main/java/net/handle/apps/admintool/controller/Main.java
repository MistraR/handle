/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.controller;

import net.handle.apps.admintool.view.*;
import net.handle.hdllib.*;
import net.cnri.util.*;
import java.io.*;
import java.security.PublicKey;

public class Main {
    private static final String VERSION_STRING = "2.0";
    private Resolver highLevelResolver = null;
    private HandleResolver resolver = null;
    private AdminToolUI ui = null;
    private final File configDir;
    private final File configFile;

    private final StreamTable preferences = new StreamTable();

    public Main() {
        System.setProperty("com.apple.macos.useScreenMenuBar", "true");
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        highLevelResolver = new Resolver();
        resolver = highLevelResolver.getResolver();
        resolver.traceMessages = true;
        resolver.setCache(new MemCache());

        configDir = highLevelResolver.getConfigFile().getParentFile();
        configFile = new File(configDir, "admin_tool.dict");
        try {
            if (configFile.exists()) {
                preferences.readFromFile(configFile);
            }
        } catch (Throwable t) {
            System.err.println("Error reading preferences file: " + t);
        }

    }

    public String getVersion() {
        return VERSION_STRING;
    }

    public HandleResolver getResolver() {
        return resolver;
    }

    public String getResolverConfigDir() {
        if (resolver.getConfiguration() instanceof FilesystemConfiguration) {
            return ((FilesystemConfiguration) resolver.getConfiguration()).getConfigDir().getAbsolutePath();
        } else {
            return null;
        }
    }

    public void setResolverConfigDir(String newDir) {
        resolver.setConfiguration(new FilesystemConfiguration(new File(newDir)));
        clearHandleCache();
    }

    /** Sends the given request to the handle server and returns the response. */
    public AbstractResponse sendMessage(AbstractRequest req) throws HandleException {
        req.certify = true;
        long start = System.currentTimeMillis();
        AbstractResponse response;
        SiteInfo site = ui.getSpecificSite();
        if (ui.getSpecificSiteDoNotRefer()) req.doNotRefer = true;
        if (site == null) {
            response = resolver.processRequest(req);
        } else {
            response = resolver.sendRequestToSite(req, site);
        }
        System.err.println("message processed in " + (System.currentTimeMillis() - start) + " ms");
        return response;
    }

    public void clearHandleCache() {
        this.resolver.setCache(new MemCache());
        this.resolver.setCertifiedCache(new MemCache());
    }

    /** Get the preferences table */
    public StreamTable prefs() {
        return preferences;
    }

    /** Save the preferences table */
    public void savePreferences() throws Exception {
        preferences.writeToFile(configFile);
    }

    synchronized final void go() {
        if (ui == null) ui = new AdminToolUI(this);
        ui.go();
    }

    public static void main(String argv[]) {
        Main m = new Main();
        m.go();
    }

    public boolean retrieveHandlesSinceTime(AuthenticationInfo auth, SiteInfo replicationSite, int serverNum, long lastTxnID, long lastDate, TransactionCallback callback) throws HandleException {
        RetrieveTxnRequest req = new RetrieveTxnRequest(lastTxnID, lastDate, SiteInfo.HASH_TYPE_BY_ALL, 1, // one server number... ie send all transactions
            0, // this is 'server 0'
            auth);
        req.encrypt = false;
        req.certify = true;

        AbstractResponse res = resolver.sendRequestToServer(req, replicationSite, replicationSite.servers[serverNum]);

        if (res.responseCode == AbstractMessage.RC_SUCCESS) {
            // decode the public key to authenticate the stream
            PublicKey pubKey = null;
            try {
                pubKey = replicationSite.servers[serverNum].getPublicKey();
            } catch (Exception e) {
                throw new HandleException(HandleException.UNABLE_TO_AUTHENTICATE, "Unable to extract public key for replication source: " + e);
            }

            int status = ((RetrieveTxnResponse) res).processStreamedPart(callback, pubKey);

            if (status == RetrieveTxnResponse.NEED_TO_REDUMP) {
                return false;
            } else if (status == RetrieveTxnResponse.SENDING_TRANSACTIONS) {
                return true;
            } else {
                throw new HandleException(HandleException.SERVER_ERROR, "Unknown status code from server during replication: " + status);
            }

        } else {
            throw new HandleException(HandleException.SERVER_ERROR, "Unexpected response to replication request: " + res);
        }

        // this doesn't handle wrap-around serial numbers - maybe it should.
        // but the numbers can go so large it shouldn't matter.
        // The date/time/seconds could be used.

    }

}

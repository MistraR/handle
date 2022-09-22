/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.Common;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ServerInfo;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;
import net.handle.server.HandleServer;

public class HandleBasedReplicationSourceSiteCollection implements ReplicationSourceSiteCollection {

    static final String[] SITE_INFO_TYPES = new String[] { Util.decodeString(Common.SITE_INFO_TYPE), Util.decodeString(Common.SITE_INFO_6_TYPE) };

    final HandleResolver resolver; // so you can refresh
    final HandleServer server; // so you can tell it about your own priority and name
    final ServerInfo serverInfo; // so you can recognize yourself
    final int singleSiteIndex;
    final boolean isPullFromSingleSite;
    final String handle;

    String ownName;

    List<ReplicationSourceSiteInfo> replicationSourceSites = new ArrayList<>();

    public HandleBasedReplicationSourceSiteCollection(String handle, HandleResolver resolver, ServerInfo serverInfo, HandleServer server) {
        this.isPullFromSingleSite = false;
        this.singleSiteIndex = -1;
        this.handle = handle;
        this.resolver = resolver;
        this.serverInfo = serverInfo;
        this.server = server;
    }

    public HandleBasedReplicationSourceSiteCollection(int singleSiteIndex, String handle, HandleResolver resolver, ServerInfo serverInfo, HandleServer server) {
        this.singleSiteIndex = singleSiteIndex;
        this.isPullFromSingleSite = true;
        this.handle = handle;
        this.resolver = resolver;
        this.serverInfo = serverInfo;
        this.server = server;
    }

    @Override
    public String getOwnName() {
        return ownName;
    }

    @Override
    public List<ReplicationSourceSiteInfo> getReplicationSourceSites() {
        return replicationSourceSites;
    }

    @Override
    @SuppressWarnings("hiding")
    public void refresh() throws HandleException {
        // System.err.println("refreshing site info from handle "+handle);
        int[] indexes = null;
        String[] types = SITE_INFO_TYPES;
        if (isPullFromSingleSite) {
            indexes = new int[] { singleSiteIndex };
            types = null;
        }
        HandleValue[] values = resolver.resolveHandle(handle, types, indexes);
        List<ReplicationSourceSiteInfo> newReplicationSourceSites = new ArrayList<>(replicationSourceSites.size());
        for (int i = 0; i < values.length; i++) {
            if (isPullFromSingleSite) {
                if (values[i].getIndex() != singleSiteIndex) continue;
            }
            boolean isNew = true;
            int index = values[i].getIndex();
            String name = index + ":" + handle;
            SiteInfo newSite = Encoder.decodeSiteInfoRecord(values[i].getData(), 0);
            if (!newSite.isPrimary) {
                // if it's a secondary, we only consider it if we're pulling from a single site; otherwise continue
                if (!isPullFromSingleSite) {
                    continue;
                }
            }
            for (ReplicationSourceSiteInfo replInfo : replicationSourceSites) {
                if (replInfo.getName().equals(name)) {
                    replInfo.setSite(newSite);
                    newReplicationSourceSites.add(replInfo);
                    isNew = false;
                    break;
                }
            }
            if (isNew) {
                ReplicationSourceSiteInfo replicationSourceSite = new ReplicationSourceSiteInfo(newSite, name);

                // let's make sure we don't try to replicate ourself
                boolean match = false;
                outerloop: for (int server = 0; server < newSite.servers.length; server++) {
                    ServerInfo serverInfo = newSite.servers[server];
                    if (Util.equals(serverInfo.ipAddress, this.serverInfo.ipAddress)) {
                        for (int a = 0; a < serverInfo.interfaces.length; a++) {
                            for (int b = 0; b < this.serverInfo.interfaces.length; b++) {
                                if (serverInfo.interfaces[a].port == this.serverInfo.interfaces[b].port) {
                                    this.server.setReplicationPriority(index);
                                    ownName = name;
                                    this.server.setReplicationSiteName(ownName);
                                    match = true;
                                    break outerloop;
                                }
                            }
                        }
                    }

                }
                if (!match) {
                    newReplicationSourceSites.add(replicationSourceSite);
                }
            }
        }
        replicationSourceSites = newReplicationSourceSites;
    }

}

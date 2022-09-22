/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import net.cnri.io.AtomicFile;
import net.handle.apps.simple.SiteInfoConverter;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;

public class FileBasedReplicationSourceSiteCollection implements ReplicationSourceSiteCollection {
    private ReplicationSourceSiteInfo replicationSourceSiteInfo;
    private final File replicationSiteInfoFile;
    private long timestamp;

    public FileBasedReplicationSourceSiteCollection(File replicationSiteInfoFile) {
        this.replicationSiteInfoFile = replicationSiteInfoFile;
    }

    @Override
    public List<ReplicationSourceSiteInfo> getReplicationSourceSites() {
        return Collections.singletonList(replicationSourceSiteInfo);
    }

    @Override
    public String getOwnName() {
        return null;
    }

    @Override
    public void refresh() throws IOException, HandleException {
        if (replicationSourceSiteInfo == null || replicationSiteInfoFile.lastModified() > timestamp) {
            this.timestamp = replicationSiteInfoFile.lastModified();

            byte siteBuf[] = new AtomicFile(replicationSiteInfoFile).readFully();
            SiteInfo newSite = new SiteInfo();
            if (Util.looksLikeBinary(siteBuf)) {
                Encoder.decodeSiteInfoRecord(siteBuf, 0, newSite);
            } else {
                newSite = SiteInfoConverter.convertToSiteInfo(new String(siteBuf, "UTF-8"));
            }
            replicationSourceSiteInfo = new ReplicationSourceSiteInfo(newSite, ReplicationDaemon.REPLICATION_SOURCES);
        }
    }

}

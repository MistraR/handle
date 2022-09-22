/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import net.handle.hdllib.ReplicationStateInfo;
import net.handle.hdllib.SiteInfo;

public class RedumpErrorMessage {
    public String type = "RedumpErrorMessage";
    public SiteInfo receivingSiteInfo;
    public int receivingServerNumber;
    public SiteInfo sourceSiteInfo;
    public String sourceSiteName;
    public int sourceServerNumber;
    public ReplicationStateInfo replicationStateInfo;
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import net.handle.hdllib.HandleException;

public interface NotifierInterface {
    public void sendNotification(String message) throws HandleException;

    public void sendNotification(String message, String type) throws HandleException;
}

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

/*************************************************************************
 * This class is used by the HdlUdpRequestHandler and HdlUdpInterface
 * classes to identify and collect the packets for multi-packet requests.
 *************************************************************************/
class HdlUdpPendingRequest {
    String idString;
    boolean gotPacket[] = null;
    byte message[];

    /***********************************************************************
     * Get a String value that uniquely identifies this request.  Currently,
     * this is based on the requestor's IP address and the ID of the request.
     ***********************************************************************/
    static final String getRequestId(InetAddress addr, int requestId) {
        return String.valueOf(requestId) + '@' + Util.rfcIpRepr(addr);
    }

    HdlUdpPendingRequest(String idString, MessageEnvelope firstEnv, DatagramPacket firstPkt) {
        this.idString = idString;

        // calculate the number of packets for this message...
        int numPackets = firstEnv.messageLength / Common.MAX_UDP_DATA_SIZE;
        if ((firstEnv.messageLength % Common.MAX_UDP_DATA_SIZE) != 0) numPackets++;

        gotPacket = new boolean[numPackets];
        addPacket(firstEnv, firstPkt);
    }

    /***************************************************************
     * Check to see if all of the packets have been received for
     * this message so far.
     ***************************************************************/
    boolean isComplete() {
        for (int i = 0; i < gotPacket.length; i++) {
            if (!gotPacket[i]) return false;
        }
        return true;
    }

    /****************************************************************
     * Add the given packet to this request.  This is called when
     * a packet is received and it has been identified as belonging
     * to this request.
     ****************************************************************/
    void addPacket(MessageEnvelope env, DatagramPacket pkt) {
        gotPacket[env.messageId] = true;

        if (message == null) message = new byte[env.messageLength];

        // copy the data into the correct location in the message
        System.arraycopy(pkt.getData(), Common.MESSAGE_ENVELOPE_SIZE, message, env.messageId * Common.MAX_UDP_DATA_SIZE, pkt.getLength() - Common.MESSAGE_ENVELOPE_SIZE);
    }

    /***************************************************************
     * Get the combined message (excluding envelopes).
     ***************************************************************/
    byte[] getMessage() {
        return message;
    }

}

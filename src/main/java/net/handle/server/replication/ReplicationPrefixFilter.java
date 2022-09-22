/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import java.util.List;

import net.handle.hdllib.Common;
import net.handle.hdllib.Transaction;
import net.handle.hdllib.Util;

public class ReplicationPrefixFilter {

    private final List<String> acceptPrefixes;

    public ReplicationPrefixFilter(List<String> acceptPrefixes) {
        for (int i = 0; i < acceptPrefixes.size(); i++) {
            acceptPrefixes.set(i, Util.decodeString(Util.upperCase(Util.encodeString(acceptPrefixes.get(i)))));
        }
        this.acceptPrefixes = acceptPrefixes;
    }

    public boolean acceptHandle(byte[] handle) {
        String prefix = getHandlePrefix(handle);
        return acceptPrefixes.contains(prefix);
    }

    public boolean acceptNA(byte[] authHandle) {
        byte[] naBytes;
        if (Util.startsWith(authHandle, Common.NA_HANDLE_PREFIX)) {
            naBytes = Util.getSuffixPart(authHandle);
        } else {
            naBytes = authHandle; // allows for the case where "0.NA/" is omitted from the NA
        }
        byte[] upperCaseNaBytes = Util.upperCase(naBytes);
        String naString = Util.decodeString(upperCaseNaBytes);
        return acceptPrefixes.contains(naString);
    }

    public boolean acceptTransaction(Transaction txn) {
        if (txn.action == Transaction.ACTION_HOME_NA || txn.action == Transaction.ACTION_UNHOME_NA) {
            return acceptNA(txn.handle);
        } else {
            return acceptHandle(txn.handle);
        }
    }

    private String getHandlePrefix(byte[] handle) {
        byte[] prefixBytes = Util.getPrefixPart(handle);
        byte[] upperCasePrefixBytes = Util.upperCase(prefixBytes);
        String prefixString = Util.decodeString(upperCasePrefixBytes);
        return prefixString;
    }
}

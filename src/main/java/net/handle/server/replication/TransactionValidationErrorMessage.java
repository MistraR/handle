/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server.replication;

import com.google.gson.JsonObject;

import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Transaction;

public class TransactionValidationErrorMessage {

    public String type = "TransactionValidationErrorMessage";
    public HandleValue[] handleValues;
    public Transaction txn;
    public SiteInfo receivingSiteInfo;
    public int receivingServerNumber;
    public SiteInfo sourceSiteInfo;
    public int sourceServerNumber;
    public String message;
    public JsonObject report;

    public TransactionValidationErrorMessage(HandleValue[] handleValues, Transaction txn, SiteInfo receivingSiteInfo, int receivingServerNumber, SiteInfo sourceSiteInfo, int sourceServerNumber, String message, JsonObject report) {
        this.handleValues = handleValues;
        this.txn = txn;
        this.receivingSiteInfo = receivingSiteInfo;
        this.receivingServerNumber = receivingServerNumber;
        this.sourceSiteInfo = sourceSiteInfo;
        this.sourceServerNumber = sourceServerNumber;
        this.message = message;
        this.report = report;
    }
}

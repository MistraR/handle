/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.hdllib;

public class ErrorResponse extends AbstractResponse {

    public byte message[];

    public ErrorResponse(byte message[]) {
        this.message = message;
    }

    public ErrorResponse(int opCode, int responseCode, byte message[]) {
        super(opCode, responseCode);
        this.message = message;
    }

    public ErrorResponse(AbstractRequest req, int errorCode, byte message[]) throws HandleException {
        super(req, errorCode);
        this.message = message;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Error(");
        sb.append(responseCode);
        sb.append("): ");
        sb.append(AbstractMessage.getResponseCodeMessage(responseCode));
        if (message != null && message.length > 0) {
            sb.append(": ");
            sb.append(Util.decodeString(message));
        }
        return sb.toString();
    }
}

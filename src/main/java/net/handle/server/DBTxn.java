/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.handle.hdllib.*;
import java.io.*;

/***********************************************************************
 * Class responsible for holding a single database transaction.
 ***********************************************************************/

public final class DBTxn {
    private final byte action;
    private final byte key[];
    private final byte val[];
    private final long date;

    private static final byte NEWLINE = (byte) 'n';

    public DBTxn(byte action, byte key[], byte val[], long date) {
        this.action = action;
        this.key = key;
        this.val = val;
        this.date = date;
    }

    public final long getDate() {
        return date;
    }

    public final byte getAction() {
        return action;
    }

    public final byte[] getKey() {
        return key;
    }

    public final byte[] getValue() {
        return val;
    }

    public final byte[] getLogBytes() {
        byte logBytes[] = new byte[1 + 1 + Encoder.INT_SIZE + key.length + Encoder.INT_SIZE + val.length + 8];
        int offset = 0;
        logBytes[offset++] = NEWLINE;
        logBytes[offset++] = action;
        offset += Encoder.writeLong(logBytes, offset, date);
        offset += Encoder.writeByteArray(logBytes, offset, key);
        offset += Encoder.writeByteArray(logBytes, offset, val);
        return logBytes;
    }

    private static final byte buf[] = new byte[Encoder.LONG_SIZE];

    public static synchronized DBTxn readTxn(InputStream in) throws IOException {
        int ib, r, n;
        n = 0;
        while ((ib = in.read()) == NEWLINE) {
        }
        if (ib < 0) return null;

        byte action = (byte) ib;

        while ((r = in.read(buf, n, Encoder.LONG_SIZE - n)) > 0 && n < Encoder.LONG_SIZE)
            n += r;
        long date = Encoder.readLong(buf, 0);

        //read the size of key
        n = 0;
        while ((r = in.read(buf, n, Encoder.INT_SIZE - n)) > 0 && n < Encoder.INT_SIZE)
            n += r;

        //read the content of key
        byte key[] = new byte[Encoder.readInt(buf, 0)];
        n = 0;
        while ((r = in.read(key, n, key.length - n)) > 0 && n < key.length)
            n += r;

        //read the size of val
        n = 0;
        while ((r = in.read(buf, n, Encoder.INT_SIZE - n)) > 0 && n < Encoder.INT_SIZE)
            n += r;

        //read the content of val
        byte val[] = new byte[Encoder.readInt(buf, 0)];
        n = 0;
        while ((r = in.read(val, n, val.length - n)) > 0 && n < val.length)
            n += r;

        return new DBTxn(action, key, val, date);
    }
}

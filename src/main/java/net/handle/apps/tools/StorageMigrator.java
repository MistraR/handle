/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ScanCallback;

public class StorageMigrator {

    private final HandleStorage source;
    private final HandleStorage destination;

    public StorageMigrator(HandleStorage source, HandleStorage destination) {
        this.source = source;
        this.destination = destination;
    }

    public void migrate() throws HandleException {
        destination.deleteAllRecords();
        source.scanNAs(new MigrateRecordCallBack(source, destination, true));
        source.scanHandles(new MigrateRecordCallBack(source, destination, false));
    }

    static class MigrateRecordCallBack implements ScanCallback {

        private final HandleStorage source;
        private final HandleStorage destination;
        private final boolean isNas;

        MigrateRecordCallBack(HandleStorage source, HandleStorage destination, boolean isNas) {
            this.source = source;
            this.destination = destination;
            this.isNas = isNas;
        }

        @Override
        public void scanHandle(byte[] handle) throws HandleException {
            if (isNas) {
                destination.setHaveNA(handle, true);
            } else {
                byte[][] rawValues = source.getRawHandleValues(handle, null, null);
                HandleValue[] values = handleValuesFromRawValues(rawValues);
                destination.createHandle(handle, values);
            }
            System.out.print(".");
        }

        private static HandleValue[] handleValuesFromRawValues(byte[][] values) throws HandleException {
            HandleValue retValues[] = new HandleValue[values.length];
            for (int i = 0; i < retValues.length; i++) {
                retValues[i] = new HandleValue();
                Encoder.decodeHandleValue(values[i], 0, retValues[i]);
            }
            return retValues;
        }
    }
}

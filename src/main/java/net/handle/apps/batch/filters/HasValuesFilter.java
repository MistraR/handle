/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch.filters;

import java.util.List;

import net.handle.hdllib.HandleValue;
import net.handle.apps.batch.HandleRecordFilter;

public class HasValuesFilter implements HandleRecordFilter {

    List<TypeAndValue> typeAndValueList;

    public HasValuesFilter(List<TypeAndValue> typeAndValueList) {
        this.typeAndValueList = typeAndValueList;
    }

    @Override
    public boolean accept(HandleValue[] values) {
        for (HandleValue value : values) {
            if (matchesOne(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesOne(HandleValue value) {
        for (TypeAndValue typeAndValue : typeAndValueList) {
            String type = value.getTypeAsString();
            String data = value.getDataAsString();
            if (typeAndValue.type.equals(type)) {
                if (typeAndValue.value.equals(data)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static class TypeAndValue {

        public String type;
        public String value;

        public TypeAndValue(String type, String value) {
            this.type = type;
            this.value = value;
        }
    }

}

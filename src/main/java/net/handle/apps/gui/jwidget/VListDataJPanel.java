/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.util.*;

@SuppressWarnings("rawtypes")
public class VListDataJPanel extends GenDataJPanel {
    protected ValueRefListJPanel vlist;

    /**
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, only view data
     **/
    public VListDataJPanel(boolean moreFlag, boolean editFlag) {
        this(moreFlag, editFlag, 1);
    }

    public VListDataJPanel(boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, index);
        vlist = new ValueRefListJPanel(editFlag);
        panel.add(vlist, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, true));

        handlevalue.setType(Common.STD_TYPE_HSVALLIST);
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY) {
            return;
        }

        vlist.clearAll();
        try {
            ValueReference[] ref = Encoder.decodeValueReferenceList(data, 0);
            if (ref != null) for (int i = 0; i < ref.length; i++)
                vlist.appendItem(ref[i]);
        } catch (HandleException e) {
            getToolkit().beep();
        }

    }

    @Override
    public byte[] getValueData() {
        Vector v = vlist.getItems();
        if (v == null) return null;
        ValueReference[] ref = new ValueReference[v.size()];
        for (int i = 0; i < v.size(); i++)
            ref[i] = (ValueReference) v.elementAt(i);
        return (Encoder.encodeValueReferenceList(ref));

    }
}

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

import javax.swing.*;

public class TextDataJPanel extends GenDataJPanel {
    protected JTextField textField;

    /**
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, only view
     **/
    public TextDataJPanel(byte[] type, boolean moreFlag, boolean editFlag) {
        this(type, moreFlag, editFlag, 0);
    }

    public TextDataJPanel(byte[] type, boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, index);
        textField = new JTextField("", 30);
        String labelStr = " " + Util.decodeString(type) + ": ";

        if (Util.equals(type, Common.STD_TYPE_URL)) textField = new JTextField("http://", 30);

        textField.setEditable(editFlag);

        panel.add(new JLabel(labelStr, SwingConstants.RIGHT), AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, true, true));
        panel.add(textField, AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, true, false));

        handlevalue.setType(type);
        if (handlevalue.hasType(Common.STD_TYPE_HSSECKEY)) handlevalue.setAnyoneCanRead(false);
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY || data == null) {
            System.err.println("warning message: Handle value data is empty");
            return;
        }

        String str;
        if (Util.looksLikeBinary(data)) str = Util.decodeHexString(data, false);
        else str = Util.decodeString(data);

        textField.setText(str);
        textField.setScrollOffset(0);
    }

    @Override
    public byte[] getValueData() {
        try {
            String str = textField.getText().trim();
            return (Util.encodeString(str));
        } catch (Exception e) {
            System.err.println("warning message: Exception at getValueData");
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

}

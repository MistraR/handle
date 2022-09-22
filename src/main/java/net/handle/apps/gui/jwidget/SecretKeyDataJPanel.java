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

/**
 * SecretKeyDataJPanel
 *
 */
public class SecretKeyDataJPanel extends GenDataJPanel {
    protected JTextField secretKeyField;
    protected JCheckBox hashedPassBox;

    /* -------------------------------------------------------------------------------------------------------------------
     * Constructor
     *
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, only view
     *
     */
    public SecretKeyDataJPanel(byte[] type, boolean moreFlag, boolean editFlag) {
        this(type, moreFlag, editFlag, 0);
    }

    /* -------------------------------------------------------------------------------------------------------------------
     * Constructor
     */
    public SecretKeyDataJPanel(byte[] type, boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, index);
        secretKeyField = new JTextField("", 30);
        String labelStr = " " + Util.decodeString(type) + ": ";
        hashedPassBox = new JCheckBox("Use SHA-1 hash of password", false);

        secretKeyField.setEditable(editFlag);

        panel.add(new JLabel(labelStr, SwingConstants.RIGHT), AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, true, true));
        panel.add(secretKeyField, AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, true, false));
        panel.add(hashedPassBox, AwtUtil.getConstraints(1, 2, 1, 0, 1, 1, true, true));

        handlevalue.setType(type);
        if (handlevalue.hasType(Common.STD_TYPE_HSSECKEY)) handlevalue.setAnyoneCanRead(false);
    }

    /* -------------------------------------------------------------------------------------------------------------------
     * Set handle value data
     */
    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY || data == null) {
            System.err.println("warning message: Handle value data is empty");
            return;
        }

        String str;
        if (Util.looksLikeBinary(data)) str = Util.decodeHexString(data, false);
        else str = Util.decodeString(data);

        secretKeyField.setText(str);
        secretKeyField.setScrollOffset(0);
    }

    /* -------------------------------------------------------------------------------------------------------------------
     * Get the secret key handle value
     */
    @Override
    public byte[] getValueData() {
        try {
            return Encoder.encodeSecretKey(Util.encodeString(secretKeyField.getText().trim()), hashedPassBox.isSelected());
        } catch (Exception e) {
            System.err.println("warning message: Exception at getValueData");
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

}//end class SecretKeyDataJPanel

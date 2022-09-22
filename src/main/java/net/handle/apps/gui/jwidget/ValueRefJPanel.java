/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import java.awt.*;
import javax.swing.*;

/**************************************************************************\
 * value reference panel
 **************************************************************************/
public class ValueRefJPanel extends JPanel {
    protected JTextField handleIdNameField;
    protected JTextField handleIdIndField;
    protected boolean editFlag;

    /**
     *@param editFlag if not true, all field not editable
     **/

    public ValueRefJPanel(String label1, String tip1, String label2, String tip2, boolean editFlag) {
        this(label1, tip1, label2, tip2, editFlag, "");
    }

    public ValueRefJPanel(String label1, String tip1, String label2, String tip2, boolean editFlag, String indStr) {
        super(new GridBagLayout());
        this.editFlag = editFlag;

        handleIdNameField = new JTextField("", 20);
        handleIdNameField.setToolTipText(tip1);
        handleIdNameField.setEditable(editFlag);

        handleIdIndField = new JTextField(indStr, 5);
        handleIdIndField.setToolTipText(tip2);
        handleIdIndField.setEditable(editFlag);

        int x = 0, y = 0;
        add(new JLabel(label1, SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(handleIdNameField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(new JLabel(label2, SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(handleIdIndField, AwtUtil.getConstraints(x + 1, y, 1, 1, 1, 1, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, false, false));

    }

    public ValueRefJPanel(boolean editFlag) {
        this(" Handle Id Name: ", null, " Handle Id Index: ", null, editFlag);
    }

    public String getHandleIdName() {
        return handleIdNameField.getText().trim();
    }

    public int getHandleIdIndex() {
        try {
            return Integer.parseInt(handleIdIndField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public String getHandleIdIndexStr() {
        return handleIdIndField.getText().trim();
    }

    public void setHandleIdName(String name) {
        handleIdNameField.setText(name);
    }

    public void setHandleIdIndex(int index) {
        handleIdIndField.setText(Integer.toString(index));
    }

    public void setHandleIdIndexStr(String index) {
        handleIdIndField.setText(index);
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import net.handle.awt.*;
import javax.swing.*;
import java.awt.*;

/**
 * This class defines an interface that is used to edit
 */
public class ValueReferenceEditor extends JPanel {
    private JTextField hdlField;
    private JTextField idxField;

    public ValueReferenceEditor() {
        this(null);
    }

    public ValueReferenceEditor(ValueReference valRef) {
        super(new GridBagLayout());

        add(new JLabel("Handle: ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, true, false));
        add(hdlField = new JTextField("", 20), AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, true, false));
        add(new JLabel("Value Index: ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, true, false));
        add(idxField = new JTextField("", 20), AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, true, false));

        setValue(valRef);
    }

    public void setValue(ValueReference valRef) {
        if (valRef == null) {
            hdlField.setText("");
            idxField.setText("1");
        } else {
            byte bytes[] = valRef.handle;
            if (bytes == null) {
                hdlField.setText("");
            } else {
                try {
                    hdlField.setText(Util.decodeString(bytes));
                } catch (Exception e) {
                    hdlField.setText("");
                }
            }
            idxField.setText(String.valueOf(valRef.index));
        }
    }

    /**
     * If the currently entered values are valid, return a ValueReference object.
     * If the currently entered values are not valid then this displays an error
     * message and returns null.
     */
    public ValueReference getValue() {
        String hdl = hdlField.getText().trim();
        if (hdl.length() <= 0) {
            JOptionPane.showMessageDialog(this, "Warning: empty handle invalid.\n");
            return null;
        }
        String idxStr = idxField.getText().trim();
        int idx = -1;
        try {
            idx = Integer.parseInt(idxStr);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Warning: invalid index value: '" + idxStr + "'.\n  Indexes must be a positive integer.");
            return null;
        }
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "Warning: invalid index value: '" + idxStr + "'.\n  Indexes must be a positive integer.");
            return null;
        }
        return new ValueReference(Util.encodeString(hdl), idx);
    }

}

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

public class AttributeInfoEditor extends JPanel {
    private final JTextField keyField;
    private final JTextField valField;

    public AttributeInfoEditor(AdminToolUI ui) {
        super(new GridBagLayout());
        keyField = new JTextField("", 15);
        valField = new JTextField("", 30);

        int y = 0;
        add(new JLabel(ui.getStr("attribute_name") + ":", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        add(keyField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        add(new JLabel(ui.getStr("attribute_value") + ":", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        add(valField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
    }

    public void loadAttribute(Attribute attribute) {
        if (attribute == null) {
            keyField.setText("");
            valField.setText("");
        } else {
            keyField.setText(attribute.name == null ? "" : Util.decodeString(attribute.name));
            valField.setText(attribute.value == null ? "" : Util.decodeString(attribute.value));
        }
    }

    public void saveAttribute(Attribute attribute) {
        attribute.name = Util.encodeString(keyField.getText());
        attribute.value = Util.encodeString(valField.getText());
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;

import net.handle.awt.*;
import net.handle.hdllib.*;

import java.awt.*;
import javax.swing.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class InterfaceJPanel extends JPanel {
    protected JTextField portField;
    protected JComboBox typeList;
    protected JComboBox protocolList;

    protected Interface interf;

    public InterfaceJPanel(boolean editFlag) {
        super(new GridBagLayout());

        portField = new JTextField("", 5);
        portField.setEnabled(editFlag);

        protocolList = new JComboBox();
        protocolList.setToolTipText("Choose interface protocol");
        protocolList.setEditable(false);
        protocolList.setEnabled(editFlag);
        for (int i = 0; i < CommonDef.INTERFACE_PROTOCOL_STR.length; i++)
            protocolList.addItem(CommonDef.INTERFACE_PROTOCOL_STR[i]);

        typeList = new JComboBox();
        typeList.setToolTipText("Choose interface type");
        typeList.setEditable(false);
        typeList.setEnabled(editFlag);
        for (int i = 0; i < CommonDef.INTERFACE_ADMIN_STR.length; i++)
            typeList.addItem(CommonDef.INTERFACE_ADMIN_STR[i]);

        int x = 0;
        int y = 0;
        Insets insets = new Insets(5, 5, 5, 5);

        add(new JLabel(" Bind port: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, true));
        add(portField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, insets, GridBagConstraints.WEST, false, true));

        add(new JLabel("Protocol: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, insets, true, true));
        add(protocolList, AwtUtil.getConstraints(x + 1, y, 1, 1, 1, 1, insets, GridBagConstraints.WEST, false, true));

        add(new JLabel("Admin type: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x + 2, y, 1, 1, 1, 1, true, true));
        add(typeList, AwtUtil.getConstraints(x + 3, y++, 1, 1, 1, 1, insets, GridBagConstraints.WEST, false, true));
        interf = new Interface();
    }

    public void setInterface(Interface interf) {
        if (interf == null) return;
        try {
            protocolList.setSelectedIndex(interf.protocol);
            typeList.setSelectedIndex(interf.type);
            portField.setText(String.valueOf(interf.port));
        } catch (Exception e) {
            System.err.println("Interface: " + interf);
            e.printStackTrace(System.err);
        }
    }

    public void setPortEditable(boolean flag) {
        portField.setEditable(flag);
    }

    public Interface getInterface() {
        try {
            int ind = protocolList.getSelectedIndex();
            interf.protocol = CommonDef.INTERFACE_PROTOCOL_TYPE[ind];
            ind = typeList.getSelectedIndex();
            interf.type = CommonDef.INTERFACE_ADMIN_TYPE[ind];
            interf.port = Integer.parseInt(portField.getText().trim());
            return interf;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }
}

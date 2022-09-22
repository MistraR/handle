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
import java.awt.event.*;

public class SecretKeyValueEditor extends JPanel implements ActionListener, HandleValueEditor {
    private JLabel inputLabel;
    private final JTextField inputField;
    private final JCheckBox hashedPasswordBox;
    private final JButton editButton;

    public SecretKeyValueEditor() {
        super(new GridBagLayout());

        inputField = new JTextField();
        hashedPasswordBox = new JCheckBox("Use SHA-1 hash of password");
        editButton = new JButton("Change Password");

        add(inputLabel = new JLabel("Password: ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));
        add(inputField, AwtUtil.getConstraints(1, 0, 1, 0, 2, 1, new Insets(4, 4, 4, 4), true, false));
        add(hashedPasswordBox, AwtUtil.getConstraints(0, 1, 0, 0, 2, 1, new Insets(4, 4, 4, 4), GridBagConstraints.WEST, false, false));
        add(editButton, AwtUtil.getConstraints(2, 1, 0, 0, 1, 1, new Insets(4, 4, 4, 4), false, false));
        add(Box.createHorizontalStrut(40), AwtUtil.getConstraints(1, 2, 1, 1, 1, 1, false, false));
        editButton.addActionListener(this);
        //editButton.setVisible(false);
    }

    @Override
    public boolean saveValueData(HandleValue value) {
        try {
            byte seckey[] = Encoder.encodeSecretKey(Util.encodeString(inputField.getText()), hashedPasswordBox.isSelected());
            value.setData(seckey);
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error encoding secret key: " + e);
            return false;
        }
    }

    @Override
    public void loadValueData(HandleValue value) {
        inputField.setText(Util.decodeString(value.getData()));
        inputField.setEditable(false);
        inputField.setEnabled(false);
        hashedPasswordBox.setVisible(false);
        hashedPasswordBox.setSelected(false);
        editButton.setVisible(true);
        inputLabel.setText("Password: ");
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == editButton) {
            inputField.setEnabled(true);
            inputField.setEditable(true);
            hashedPasswordBox.setVisible(true);
            hashedPasswordBox.setSelected(false);
            editButton.setVisible(false);
            inputField.setText("");
            inputLabel.setText("New Password: ");
        }
    }

}

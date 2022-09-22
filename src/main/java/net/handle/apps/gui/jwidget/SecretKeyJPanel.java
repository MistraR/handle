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

import javax.swing.*;
import java.awt.*;

public class SecretKeyJPanel extends JPanel {
    private final JTextField userIdHandleField;
    private final JTextField userIdIndexField;
    private final JPasswordField passwordField;
    private final JCheckBox hashedPassBox;

    public SecretKeyJPanel() {

        super(new GridBagLayout());
        String s = HDLToolConfig.table.getStr("SecIndex", "300");
        userIdIndexField = new JTextField("300", 5);
        userIdIndexField.setToolTipText("Input handle value index");
        s = HDLToolConfig.table.getStr("SecHandle", "");
        userIdHandleField = new JTextField(s, 25);
        userIdHandleField.setScrollOffset(0);
        userIdHandleField.setToolTipText("Input handle name");

        passwordField = new JPasswordField("", 30);
        passwordField.setScrollOffset(0);
        passwordField.setToolTipText("Input the secret key");
        hashedPassBox = new JCheckBox("Use SHA-1 hash of password", HDLToolConfig.table.getBoolean("ShadowPass", false));

        int x = 0;
        int y = 0;
        add(new JLabel(" ID Handle: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(userIdHandleField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(new JLabel(" ID Index: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(userIdIndexField, AwtUtil.getConstraints(x + 1, y++, 0, 1, 1, 1, new Insets(1, 1, 1, 30), GridBagConstraints.WEST, false, false));
        add(new JLabel(" Secret Key: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(passwordField, AwtUtil.getConstraints(x + 1, y++, 0, 0, 2, 1, true, true));

        add(hashedPassBox, AwtUtil.getConstraints(x + 1, y, 0, 0, 1, 1, true, true));
    }

    public void setUserIdHandle(String newHandle) {
        userIdHandleField.setText(newHandle);
    }

    public void setUserIdIndex(int newIndex) {
        userIdIndexField.setText(String.valueOf(newIndex));
    }

    public String getUserIdHandle() {
        return userIdHandleField.getText().trim();
    }

    public int getUserIdIndex() {
        try {
            return Integer.parseInt(userIdIndexField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public char[] getSecretKey() {
        return passwordField.getPassword();
    }

    public String getSecretKeyStr() {
        return (new String(passwordField.getPassword()));
    }

    public boolean isHashedPasswordEnabled() {
        return hashedPassBox.isSelected();
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        Container c = f.getContentPane();
        c.add(new SecretKeyJPanel());
        f.setSize(500, 500);
        f.pack();
        f.setVisible(true);
    }
}

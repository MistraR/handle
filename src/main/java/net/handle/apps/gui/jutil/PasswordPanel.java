/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import net.handle.awt.*;
import java.awt.*;
import javax.swing.*;

public class PasswordPanel {

    public static boolean show(String[] strs) {
        return show(strs, true);
    }

    public static boolean show(String[] strs, boolean confirmNewPasswd) {
        String passPhrase1 = null;
        String passPhrase2 = null;
        JPasswordField passPhraseField1 = null;
        JPasswordField passPhraseField2 = null;

        passPhraseField1 = new JPasswordField("", 20);
        if (confirmNewPasswd) {
            passPhraseField2 = new JPasswordField("", 20);
        }

        JPanel p = new JPanel(new GridBagLayout());
        int y = 0;
        p.add(new JLabel(" Enter secret passphrase: "), AwtUtil.getConstraints(0, y++, 0, 0, 1, 1, true, true));
        p.add(passPhraseField1, AwtUtil.getConstraints(0, y++, 0, 0, 1, 1, true, true));
        if (confirmNewPasswd) {
            p.add(new JLabel(" Re-enter secret passphrase: "), AwtUtil.getConstraints(0, y++, 0, 0, 1, 1, true, true));
            p.add(passPhraseField2, AwtUtil.getConstraints(0, y++, 0, 0, 1, 1, true, true));
        }

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Enter passphrase: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
            strs[0] = null;
            return false;
        }

        passPhrase1 = new String(passPhraseField1.getPassword());

        if (confirmNewPasswd && passPhraseField2 != null) {
            passPhrase2 = new String(passPhraseField2.getPassword());
            if (!passPhrase2.equals(passPhrase1)) {
                JOptionPane.showMessageDialog(null, "The two passphrases that you entered do not match.\n" + "The key generation process has been aborted.", "Error:", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
        strs[0] = passPhrase1;
        return true;
    }
}

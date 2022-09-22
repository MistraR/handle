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
import java.security.*;
import java.io.*;

public class PublicKeyJPanel extends JPanel {
    private final JTextField userIdHandleField;
    private final JTextField userIdIndexField;
    private PrivateKey privateKey = null;
    private final BrowsePanel browser;

    /**
     * using private key to authenticate the public key
     */

    public PublicKeyJPanel() {
        super(new GridBagLayout());
        String s;
        s = HDLToolConfig.table.getStr("PubHandle", "");
        userIdHandleField = new JTextField(s, 25);
        userIdHandleField.setScrollOffset(0);
        userIdHandleField.setToolTipText("Input handle name");
        s = HDLToolConfig.table.getStr("PubIndex", "300");
        userIdIndexField = new JTextField(s, 5);
        userIdIndexField.setToolTipText("Input handle value index");
        s = HDLToolConfig.table.getStr("PrivKey", "");
        browser = new BrowsePanel("Private Key File: ", new File(s), "", null, false);
        int x = 0;
        int y = 0;
        add(new JLabel(" ID Handle: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(userIdHandleField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(new JLabel(" ID Index: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(userIdIndexField, AwtUtil.getConstraints(x + 1, y++, 0, 1, 1, 1, new Insets(1, 1, 1, 30), GridBagConstraints.WEST, false, false));
        add(browser, AwtUtil.getConstraints(x, y, 0, 1, 3, 1, true, true));
    }

    public String getPrivKeyPath() {
        return browser.getPath();
    }

    public PrivateKey getPrivKey() {

        File[] files = new File[1];
        InputStream in = null;

        if (!browser.getReadFile(files)) return null;

        try {
            File pkFile = files[0];
            if (!pkFile.isFile()) return null;
            byte rawKey[] = new byte[(int) pkFile.length()];
            in = new FileInputStream(pkFile);
            int n = 0;
            int r = 0;
            while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0) {
                n += r;
            }
            in.close();

            byte secretKey[] = null;
            if (Util.requiresSecretKey(rawKey)) {
                String[] passPhrase = new String[1];
                if (!PasswordPanel.show(passPhrase, false)) return null;
                secretKey = Util.encodeString(passPhrase[0]);
            }
            byte keyBytes[] = Util.decrypt(rawKey, secretKey);

            try {
                privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
            } catch (Exception e) {
                if (secretKey != null) {
                    try {
                        in.close();
                    } catch (Exception e1) {
                        return null;
                    }
                    JOptionPane.showMessageDialog(null, "There was an error decrypting your private key.\n" + "Are you sure that you entered the correct passphrase?\n" + "Please try again.", "Warning: ", JOptionPane.WARNING_MESSAGE);
                    return null;
                } else {
                    throw e;
                }
            }

            return privateKey;
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e1) {
                    return null;
                }
                JOptionPane.showMessageDialog(null, "Error loading private key: " + e, "Warning: ", JOptionPane.WARNING_MESSAGE);
            }
            return null;
        }
    }

    public int getUserIdIndex() {
        return Integer.parseInt(userIdIndexField.getText());
    }

    public String getUserIdHandle() {
        return userIdHandleField.getText().trim();
    }

    public void setUserIdIndex(int index) {
        userIdIndexField.setText(Integer.toString(index));
    }

    public void setUserIdHandle(String handle) {
        userIdHandleField.setText(handle);
    }
}

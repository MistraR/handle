/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import net.handle.awt.*;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.security.*;
import java.io.*;

/**
 * Window that provides an interface for a user to enter their authentication
 * information.  The given roleID provides a key to the type of authentication
 * that is being performed, so that the appropriate default login and key file
 * are used.
 */
public class AuthWindow extends JDialog implements ActionListener {
    private static final String SETTINGS_FILE = ".handle_authentication";

    private final JComboBox<String> authTypeChoice;
    private final JTextField idField;
    private final JTextField indexField;
    private final JLabel privKeyLabel;
    private final JPasswordField secretKeyField;
    private final JCheckBox hashedPassBox;
    private final JButton okButton;
    private final JButton cancelButton;
    private final JButton loadKeyButton;
    private String authRole = "handle";

    private File privKeyFile = null;
    private AuthenticationInfo auth = null;
    private boolean wasCanceled = true;
    private StreamTable storedSettings = null;

    public AuthWindow(Frame owner) throws HeadlessException {
        this(owner, "default");
    }

    /**
     * Constructor for a window object that provides an interface for a user
     * to enter their authentication information.  The given roleID provides
     * a key to the type of authentication that is being performed, so that
     * the appropriate default login and key file are used.
     */
    public AuthWindow(Frame owner, String roleID) throws HeadlessException {
        super(owner, "Handle Authentication", true);

        if (roleID != null) authRole = roleID;

        storedSettings = new StreamTable();

        try {
            storedSettings.readFromFile(System.getProperty("user.home", "") + File.separator + SETTINGS_FILE);
        } catch (Exception e) {
        }

        String privKeyFileStr = storedSettings.getStr("keyfile_" + authRole, null);
        if (privKeyFileStr != null) privKeyFile = new File(privKeyFileStr);

        authTypeChoice = new JComboBox<>(new String[] { "Public/Private Key", "Password" });
        idField = new JTextField(storedSettings.getStr("id_" + authRole, ""), 30);
        indexField = new JTextField(storedSettings.getStr("idx_" + authRole, "300"), 4);
        secretKeyField = new JPasswordField("", 15);
        hashedPassBox = new JCheckBox("Use SHA-1 hash of password", storedSettings.getBoolean("shadow_" + authRole, false));
        okButton = new JButton("OK");
        cancelButton = new JButton("Cancel");
        loadKeyButton = new JButton("Select Key File...");
        privKeyLabel = new JLabel("no key loaded");
        privKeyLabel.setForeground(Color.gray);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        int y = 0;
        p.add(new JLabel("Your ID:", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(4, 4, 4, 0), false, false));

        JPanel tmp = new JPanel(new GridBagLayout());
        tmp.add(idField, AwtUtil.getConstraints(0, 0, 1, 0, 2, 1, new Insets(0, 10, 0, 0), true, false));
        tmp.add(new JLabel("Key Index:", SwingConstants.RIGHT), AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(0, 10, 0, 0), false, false));
        tmp.add(indexField, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, new Insets(0, 4, 0, 0), true, false));
        p.add(tmp, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(4, 4, 4, 0), true, false));

        p.add(new JLabel("Key Type:", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(4, 4, 4, 0), true, false));
        p.add(authTypeChoice, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(4, 10, 4, 0), true, false));

        p.add(new JLabel("Your Key:", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(4, 4, 4, 0), false, false));
        p.add(privKeyLabel, AwtUtil.getConstraints(1, y, 1, 0, 1, 1, new Insets(4, 10, 4, 4), true, false));
        p.add(loadKeyButton, AwtUtil.getConstraints(2, y, 0, 0, 1, 1, new Insets(4, 0, 4, 4), true, false));
        p.add(secretKeyField, AwtUtil.getConstraints(1, y, 1, 0, 1, 1, new Insets(4, 10, 4, 4), true, false));
        p.add(hashedPassBox, AwtUtil.getConstraints(2, y++, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));

        JPanel bp = new JPanel(new GridBagLayout());
        bp.setBorder(new javax.swing.border.EmptyBorder(10, 0, 0, 0));
        bp.add(new JLabel(" "), AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, new Insets(0, 0, 0, 20), true, false));
        bp.add(cancelButton, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(0, 0, 0, 20), false, false));
        bp.add(okButton, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, false, false));
        p.add(bp, AwtUtil.getConstraints(0, y++, 1, 0, 3, 1, true, true));

        okButton.addActionListener(this);
        cancelButton.addActionListener(this);
        loadKeyButton.addActionListener(this);
        authTypeChoice.addActionListener(this);

        getContentPane().add(p);
        getRootPane().setDefaultButton(okButton);

        if (storedSettings.getStr("authtype_" + authRole, "priv").equals("priv")) authTypeChoice.setSelectedIndex(0);
        else authTypeChoice.setSelectedIndex(1);

        authTypeSelected();
        updateKeyLabel();
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);
    }

    private void authTypeSelected() {
        int authTypeIdx = authTypeChoice.getSelectedIndex();
        boolean privKeyMode = authTypeIdx == 0;
        privKeyLabel.setVisible(privKeyMode);
        loadKeyButton.setVisible(privKeyMode);
        secretKeyField.setVisible(!privKeyMode);
        hashedPassBox.setVisible(!privKeyMode);
        pack();
        setSize(getPreferredSize());
    }

    private void updateKeyLabel() {
        if (privKeyFile != null) {
            privKeyLabel.setText(privKeyFile.getName());
        } else {
            privKeyLabel.setText("no key loaded - press the \"Load\" button");
        }
    }

    private void chooseKey() {
        JFileChooser chooser = new JFileChooser();
        String filename = storedSettings.getStr("key_directory", null);
        if (filename != null) {
            chooser.setCurrentDirectory(new File(filename));
        }
        chooser.setDialogTitle("Select your private key file");
        int returnVal = chooser.showOpenDialog(null);
        if (returnVal != JFileChooser.APPROVE_OPTION) {
            updateKeyLabel();
            return;
        }

        File f = chooser.getSelectedFile();
        storedSettings.put("key_directory", chooser.getCurrentDirectory().getPath());
        if (f != null && (privKeyFile == null || !f.equals(privKeyFile))) {
            // the user selected a different file
            privKeyFile = f;
        }
        updateKeyLabel();
        return;
    }

    private static Window getWindowForComponent(Component parentComponent) {
        if (parentComponent == null) return JOptionPane.getRootFrame();
        if (parentComponent instanceof Frame || parentComponent instanceof Dialog) return (Window) parentComponent;
        return getWindowForComponent(parentComponent.getParent());
    }

    private PrivateKey loadPrivateKeyFromFile() {
        if (privKeyFile != null) {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte buf[] = new byte[4096];
                FileInputStream fin = new FileInputStream(privKeyFile);
                try {
                    int r = 0;
                    while ((r = fin.read(buf)) >= 0)
                        bout.write(buf, 0, r);
                } finally {
                    fin.close();
                }
                buf = bout.toByteArray();
                byte passphrase[] = null;
                if (Util.requiresSecretKey(buf)) {
                    // ask the user for their secret key...
                    final PassphrasePanel pp = new PassphrasePanel();
                    final JOptionPane pane = new JOptionPane(pp, JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

                    // I am inlining here the code for JOptionPane.showConfirmDialog, but setting the focus on the passphrase.
                    final JDialog dialog;
                    Window window = getWindowForComponent(this);
                    if (window instanceof Frame) {
                        dialog = new JDialog((Frame) window, "Enter Passphrase", true);
                    } else {
                        dialog = new JDialog((Dialog) window, "Enter Passphrase", true);
                    }
                    dialog.setComponentOrientation(this.getComponentOrientation());
                    Container contentPane = dialog.getContentPane();

                    contentPane.setLayout(new BorderLayout());
                    contentPane.add(pane, BorderLayout.CENTER);
                    dialog.setResizable(false);
                    if (JDialog.isDefaultLookAndFeelDecorated()) {
                        boolean supportsWindowDecorations = UIManager.getLookAndFeel().getSupportsWindowDecorations();
                        if (supportsWindowDecorations) {
                            dialog.setUndecorated(true);
                            getRootPane().setWindowDecorationStyle(JRootPane.QUESTION_DIALOG);
                        }
                    }
                    dialog.pack();
                    dialog.setLocationRelativeTo(this);
                    WindowAdapter adapter = new WindowAdapter() {
                        private boolean gotFocus = false;

                        @Override
                        public void windowClosing(WindowEvent we) {
                            pane.setValue(null);
                        }

                        @Override
                        public void windowGainedFocus(WindowEvent we) {
                            // Once window gets focus, set initial focus
                            if (!gotFocus) {
                                pp.focus();
                                gotFocus = true;
                            }
                        }
                    };
                    dialog.addWindowListener(adapter);
                    dialog.addWindowFocusListener(adapter);
                    dialog.addComponentListener(new ComponentAdapter() {
                        @Override
                        public void componentShown(ComponentEvent ce) {
                            // reset value to ensure closing works properly
                            pane.setValue(JOptionPane.UNINITIALIZED_VALUE);
                        }
                    });
                    pane.addPropertyChangeListener(new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent event) {
                            // Let the defaultCloseOperation handle the closing
                            // if the user closed the window without selecting a button
                            // (newValue = null in that case).  Otherwise, close the dialog.
                            if (dialog.isVisible() && event.getSource() == pane && (event.getPropertyName().equals(JOptionPane.VALUE_PROPERTY)) && event.getNewValue() != null && event.getNewValue() != JOptionPane.UNINITIALIZED_VALUE) {
                                dialog.setVisible(false);
                            }
                        }
                    });

                    dialog.setVisible(true);
                    dialog.dispose();

                    int result;
                    Object selectedValue = pane.getValue();
                    if (selectedValue instanceof Integer) result = ((Integer) selectedValue).intValue();
                    else result = JOptionPane.CLOSED_OPTION;
                    if (result == JOptionPane.OK_OPTION) {
                        passphrase = new String(pp.getPassphrase()).getBytes("UTF8");
                    } else {
                        return null;
                    }
                }

                buf = Util.decrypt(buf, passphrase);

                return Util.getPrivateKeyFromBytes(buf, 0);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error: " + e, "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace(System.err);
            }
        } else {
            // minor fix sghosh
            // prevents run-time crash
            JOptionPane.showMessageDialog(this, "Error: Please specify a private key file", "Error", JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

    private boolean checkInputs() {
        auth = null;

        int idIndex;
        try {
            if (indexField.getText().trim().isEmpty()) idIndex = 0;
            else idIndex = Integer.parseInt(indexField.getText().trim());
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(this, "Invalid index for key: " + indexField.getText(), "Invalid index value", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String id = idField.getText().trim();
        if (id == null || id.trim().length() <= 0) {
            JOptionPane.showMessageDialog(this, "Please enter your identifier", "No identifier specified", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        int authTypeIdx = authTypeChoice.getSelectedIndex();
        if (authTypeIdx == 0) {
            PrivateKey privKey = loadPrivateKeyFromFile();
            if (privKey == null) {
                return false;
            }
            auth = new PublicKeyAuthenticationInfo(Util.encodeString(id), idIndex, privKey);
        } else {
            byte secKey[] = Util.encodeString(new String(secretKeyField.getPassword()));
            try {
                auth = new SecretKeyAuthenticationInfo(Util.encodeString(id), idIndex, secKey, hashedPassBox.isSelected());
            } catch (Exception e) {
                e.printStackTrace(System.err);
                JOptionPane.showMessageDialog(this, "Error calculating or encoding password", "Error encoding password", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }

        return true;
    }

    private void storeValues() {
        storedSettings.put("id_" + authRole, idField.getText());
        storedSettings.put("idx_" + authRole, indexField.getText());
        storedSettings.put("authtype_" + authRole, authTypeChoice.getSelectedIndex() == 0 ? "priv" : "sec");
        try {
            storedSettings.put("keyfile_" + authRole, privKeyFile.getCanonicalPath());
        } catch (Exception e) {
        }
        try {
            storedSettings.writeToFile(System.getProperty("user.home", "") + File.separator + SETTINGS_FILE);
        } catch (Exception e) {
        }
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    public AuthenticationInfo getAuthentication() {
        if (wasCanceled) return null;
        return auth;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == okButton) {
            if (checkInputs()) {
                wasCanceled = false;
                setVisible(false);
                storeValues();
            }
        } else if (src == cancelButton) {
            wasCanceled = true;
            setVisible(false);
        } else if (src == loadKeyButton) {
            chooseKey();
        } else if (src == authTypeChoice) {
            authTypeSelected();
        }
    }

    private class PassphrasePanel extends JPanel {
        private final JPasswordField passField;

        PassphrasePanel() {
            setLayout(new GridBagLayout());
            setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
            passField = new JPasswordField("", 30);

            add(new JLabel("Enter the passphrase to decrypt your private key"), AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, false, false));
            add(passField, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, false, false));
        }

        char[] getPassphrase() {
            return passField.getPassword();
        }

        void focus() {
            passField.requestFocusInWindow();
        }
    }

}

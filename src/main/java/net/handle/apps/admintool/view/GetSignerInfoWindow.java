/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import net.cnri.guiutil.*;
import net.cnri.util.StreamTable;
import net.handle.awt.*;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.text.JTextComponent;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.ImageObserver;
import java.security.*;
import java.io.*;

/**
 * Window that provides an interface for a user to enter their private key and identifier, usually in order to sign something.
 */
public class GetSignerInfoWindow extends JDialog implements ActionListener {
    private static final String SETTINGS_FILE = ".handle_siginfo";

    private final JPanel localPanel;
    private final JRadioButton localChoice;
    private final JTextField indexField;
    private final JTextField idField;
    private final JLabel privKeyLabel;
    private final JButton okButton;
    private final JButton cancelButton;
    private final JButton loadKeyButton;
    private String authRole = "handle";
    private File privKeyFile = null;

    private final JPanel remotePanel;
    private final JRadioButton remoteChoice;
    private final JTextField issuerField;
    private final JTextField baseUri;
    private final JTextField username;
    private final JPasswordField password;
    private final JTextField privateKeyId;
    private final JPasswordField privateKeyPassphrase;

    private SignerInfo sigInfo = null;
    private boolean wasCanceled = true;
    private StreamTable storedSettings = null;

    public GetSignerInfoWindow(Frame owner) throws HeadlessException {
        this(owner, "default");
    }

    /**
     * Constructor for a window object that provides an interface for a user to enter their identity and private key. The given roleID provides a key
     * to the type of identification that is being performed, so that the appropriate default ID and file are used.
     */
    public GetSignerInfoWindow(Frame owner, String roleID) throws HeadlessException {
        super(owner, "Signing Key", true);

        this.setResizable(false);

        if (roleID != null) {
            authRole = roleID;
        }

        storedSettings = new StreamTable();

        try {
            storedSettings.readFromFile(System.getProperty("user.home", "") + File.separator + SETTINGS_FILE);
        } catch (Exception e) {
        }

        String privKeyFileStr = storedSettings.getStr("keyfile_" + authRole, null);
        if (privKeyFileStr != null) {
            privKeyFile = new File(privKeyFileStr);
        }

        localChoice = new JRadioButton("Sign using local key");
        idField = new JTextField(storedSettings.getStr("id_" + authRole, ""), 30);
        indexField = new JTextField(storedSettings.getStr("idx_" + authRole, ""), 4);
        okButton = new JButton("OK");
        cancelButton = new JButton("Cancel");
        loadKeyButton = new JButton("Select Key File...");
        privKeyLabel = new JLabel("no key loaded");
        privKeyLabel.setForeground(Color.gray);

        localPanel = new JPanel(new GridBagLayout());
        localPanel.setBorder(new CompoundBorder(new EmptyBorder(15, 15, 15, 15), new CompoundBorder(new EtchedBorder(), new EmptyBorder(10, 10, 10, 10))));
        int y = 0;
        Box radioBox = new Box(BoxLayout.X_AXIS);
        radioBox.add(localChoice);
        localPanel.add(radioBox, GridC.getc(0, y++).colspan(3).wx(1).fillx().insets(0, 0, 0, 0));

        localPanel.add(new JLabel("Your ID:"), GridC.getc(0, y).label());
        //        localPanel.add(idField, GridC.getc(1, y++).field().colspan(2));
        JPanel tmp = new JPanel(new GridBagLayout());
        tmp.add(idField, AwtUtil.getConstraints(0, 0, 1, 0, 2, 1, new Insets(0, 10, 0, 0), true, false));
        tmp.add(new JLabel("Key Index:", SwingConstants.RIGHT), AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(0, 10, 0, 0), false, false));
        tmp.add(indexField, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, new Insets(0, 4, 0, 0), true, false));
        localPanel.add(tmp, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(4, 4, 4, 0), true, false));

        localPanel.add(new JLabel("Your Key:"), GridC.getc(0, y).label());
        localPanel.add(privKeyLabel, GridC.getc(1, y).field());
        localPanel.add(loadKeyButton, GridC.getc(2, y++).field().wx(0));

        okButton.addActionListener(this);
        cancelButton.addActionListener(this);
        loadKeyButton.addActionListener(this);

        getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
        getContentPane().add(localPanel);

        remoteChoice = new JRadioButton("Sign using remote service");
        issuerField = new JTextField(storedSettings.getStr("issuer", ""), 30);
        baseUri = new JTextField(storedSettings.getStr("baseUri", ""), 30);
        username = new JTextField(storedSettings.getStr("username", ""), 30);
        password = new JPasswordField(30);
        privateKeyId = new JTextField(storedSettings.getStr("privateKeyId", ""), 30);
        privateKeyPassphrase = new JPasswordField(30);

        remotePanel = new JPanel();
        remotePanel.setBorder(new CompoundBorder(new EmptyBorder(0, 15, 15, 15), new CompoundBorder(new EtchedBorder(), new EmptyBorder(10, 10, 10, 10))));
        remotePanel.setLayout(new BoxLayout(remotePanel, BoxLayout.Y_AXIS));

        addLeftJustifiedComponent(remoteChoice);
        addLeftLabeledComponent(baseUri, "Service URI");
        addLeftLabeledComponent(issuerField, "Issuer");
        addLeftLabeledComponent(username, "Username");
        addLeftLabeledComponent(password, "Password");
        addLeftLabeledComponent(privateKeyId, "Key ID");
        addLeftLabeledComponent(privateKeyPassphrase, "Key Passphrase");
        getContentPane().add(remotePanel);

        JPanel bp = new JPanel(new GridBagLayout());
        bp.add(Box.createHorizontalStrut(100), GridC.getc(0, 0).wx(1));
        bp.add(cancelButton, GridC.getc(1, 0));
        bp.add(okButton, GridC.getc(2, 0));
        getContentPane().add(bp);

        getRootPane().setDefaultButton(okButton);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(localChoice);
        buttonGroup.add(remoteChoice);
        localChoice.addActionListener(this);
        remoteChoice.addActionListener(this);
        if (storedSettings.getBoolean("isLocalChoice", true)) {
            localChoice.setSelected(true);
            localChoice.requestFocusInWindow();
        } else {
            remoteChoice.setSelected(true);
            remoteChoice.requestFocusInWindow();
        }
        enableDisable();

        updateKeyLabel();

        pack();
        setSize(getPreferredSize());

        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);
    }

    private void addLeftJustifiedComponent(Component component) {
        Box box = new Box(BoxLayout.X_AXIS);
        box.add(component);
        box.add(Box.createHorizontalGlue());
        remotePanel.add(box);
    }

    private void addLeftLabeledComponent(Component component, String label) {
        Box box = new Box(BoxLayout.X_AXIS);
        JLabel labelComponent = new JLabel(label);
        labelComponent.setPreferredSize(new Dimension(120, 1));
        box.add(labelComponent);
        box.add(component);
        remotePanel.add(box);
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

    private PrivateKey loadPrivateKeyFromFile() {
        if (privKeyFile != null) {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte buf[] = new byte[4096];
                FileInputStream fin = new FileInputStream(privKeyFile);
                try {
                    int r = 0;
                    while ((r = fin.read(buf)) >= 0) {
                        bout.write(buf, 0, r);
                    }
                } finally {
                    fin.close();
                }
                buf = bout.toByteArray();
                byte passphrase[] = null;
                if (Util.requiresSecretKey(buf)) {
                    // ask the user for their secret key...
                    PassphrasePanel pp = new PassphrasePanel();
                    int result = JOptionPane.showConfirmDialog(this, pp, "Enter Passphrase", JOptionPane.OK_CANCEL_OPTION);
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
            JOptionPane.showMessageDialog(this, "Error: Please specify a private key file", "Error", ImageObserver.ERROR);
        }
        return null;
    }

    private boolean checkInputs() {
        sigInfo = null;

        if (localChoice.isSelected()) {
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

            PrivateKey privKey = loadPrivateKeyFromFile();
            if (privKey == null) {
                return false;
            }

            sigInfo = new SignerInfo(new PublicKeyAuthenticationInfo(Util.encodeString(id), idIndex, privKey));
        } else {
            String baseUriString = baseUri.getText().trim();
            String issuerString = issuerField.getText().trim();
            String usernameString = username.getText().trim();
            String passwordString = new String(password.getPassword());
            String privateKeyIdString = privateKeyId.getText().trim();
            String privateKeyPassPhraseString = new String(privateKeyPassphrase.getPassword());

            if (issuerString.length() <= 0) {
                JOptionPane.showMessageDialog(this, "Please enter the issuer id", "No issuer specified", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (baseUriString.length() == 0) {
                JOptionPane.showMessageDialog(this, "Please enter the URL of the signing server", "No server specified", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (usernameString.length() == 0) {
                JOptionPane.showMessageDialog(this, "Please enter a username to access server", "No username specified", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (passwordString.length() == 0) {
                JOptionPane.showMessageDialog(this, "Please enter a password to access server", "No password specified", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            if (privateKeyIdString.length() == 0) {
                JOptionPane.showMessageDialog(this, "Please enter the id of the private key", "No private key id specified", JOptionPane.ERROR_MESSAGE);
                return false;
            }
            RemoteSignerInfo remoteSignerInfo = new RemoteSignerInfo(issuerString, baseUriString, usernameString, passwordString, privateKeyIdString, privateKeyPassPhraseString);
            sigInfo = new SignerInfo(remoteSignerInfo);
        }
        return true;
    }

    private void storeValues() {
        storedSettings.put("isLocalChoice", localChoice.isSelected());
        storedSettings.put("id_" + authRole, idField.getText());
        storedSettings.put("idx_" + authRole, indexField.getText());
        try {
            storedSettings.put("keyfile_" + authRole, privKeyFile.getCanonicalPath());
        } catch (Exception e) {
        }

        storedSettings.put("issuer", issuerField.getText());
        storedSettings.put("baseUri", baseUri.getText());
        storedSettings.put("username", username.getText());
        storedSettings.put("privateKeyId", privateKeyId.getText());

        try {
            storedSettings.writeToFile(System.getProperty("user.home", "") + File.separator + SETTINGS_FILE);
        } catch (Exception e) {
        }
    }

    public boolean wasCanceled() {
        return wasCanceled;
    }

    public SignerInfo getSignatureInfo() {
        if (wasCanceled) {
            return null;
        }
        return sigInfo;
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
        } else if (src == localChoice || src == remoteChoice) {
            enableDisable();
        }
    }

    private void enableDisable() {
        if (localChoice.isSelected()) {
            setComponentEnabled(false, remotePanel);
            setComponentEnabled(true, localPanel);
        } else {
            setComponentEnabled(false, localPanel);
            setComponentEnabled(true, remotePanel);
        }
    }

    private void setComponentEnabled(boolean enabled, Component component) {
        if (component instanceof JTextComponent || component instanceof JComboBox || component instanceof JCheckBox || component instanceof JButton || component instanceof JLabel) {
            component.setEnabled(enabled);
        }
        if (component instanceof Container) {
            Component[] components = ((Container) component).getComponents();
            if (components != null && components.length > 0) {
                for (Component heldComponent : components) {
                    setComponentEnabled(enabled, heldComponent);
                }
            }
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
    }

}

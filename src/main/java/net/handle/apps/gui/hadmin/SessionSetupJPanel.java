/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jwidget.*;
import net.handle.apps.gui.jutil.*;
import net.handle.security.*;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.security.*;
import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SessionSetupJPanel extends JDialog implements ActionListener {
    protected BrowsePanel browserPubkey;
    protected BrowsePanel browserCliPrivkey;
    protected BrowsePanel browserHdlPrivkey;

    protected JTextField keyrefHandleField;
    protected JTextField keyrefIndexField;
    protected JCheckBox sessionEncrypted;
    protected JCheckBox sessionAuthenticated;
    protected JTextField sessionTimeout;
    protected JButton genRSAKeyButton;
    protected JFrame parent;
    protected JLabel timeoutL1;
    protected JLabel timeoutL2;

    String privKeyFile;
    String pubKeyFile;

    protected JComboBox modeBox;

    JPanel modeOptionsPanel, hdlCipherPanel, emptyPanel;
    JPanel clientCipherPanel;

    static final String MODE_NONE = "Disabled";
    static final String MODE_DH = "Diffie-Hellman";
    static final String MODE_CLIENT = "Client Cipher";
    static final String MODE_SERVER = "Server Cipher";
    static final String MODE_HDL = "Cipher Reference";

    SessionSetupInfo oldInfo;
    HandleTool hdlTool;

    //since this window can not work with WorkWindow to display "Help" and
    //"Close" button add a "Help" button here.  this JPanle will be added to a
    //modal dialog, so no "Close" button is needed
    protected MyButton helpButton = new MyButton("Help", "Help for session setup");

    public SessionSetupJPanel(HandleTool parent, SessionSetupInfo info) {

        super(parent, "Session Setup", false);
        hdlTool = parent;

        JPanel panel = new JPanel(new GridBagLayout());
        this.parent = (parent == null ? new JFrame() : parent);

        oldInfo = info;

        JButton okButton = new JButton(new AbstractAction("Ok") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    SessionSetupInfo ss = getSetupInfo();
                    hdlTool.resolver.setSessionTracker(new ClientSessionTracker(ss));
                    dispose();
                } catch (Exception ee) {
                    String msg = ee.getMessage();
                    if (msg == null || msg.length() == 0) msg = ee.toString();
                    ee.printStackTrace();
                    JOptionPane.showMessageDialog(hdlTool, msg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JButton cancelButton = new JButton(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        helpButton.addActionListener(this);
        JPanel btnPanel = new JPanel();
        btnPanel.add(okButton);
        btnPanel.add(cancelButton);
        btnPanel.add(helpButton);

        //create the "generateRSA key" button
        genRSAKeyButton = new JButton("Generate Key Pair");
        genRSAKeyButton.addActionListener(this);

        //create the public key browser panel
        browserPubkey = new BrowsePanel("Public Key File: ", (File) null, "", null, false);
        browserHdlPrivkey = new BrowsePanel("Private Key File: ", (File) null, "", null, false);

        // create the public key ref controls
        hdlCipherPanel = new JPanel(new GridBagLayout());
        hdlCipherPanel.setBorder(new EmptyBorder(6, 2, 5, 5));
        keyrefHandleField = new JTextField("", 20);
        keyrefHandleField.setScrollOffset(0);
        keyrefHandleField.setToolTipText("Input public key ref handle");
        keyrefIndexField = new JTextField("300", 3);
        keyrefIndexField.setToolTipText("Input public key ref handle index");
        GridBagConstraints c = AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, false);
        hdlCipherPanel.add(new JLabel("Public Key Reference Handle:"), c);
        c.gridx++;
        hdlCipherPanel.add(keyrefHandleField, c);
        c.gridx = 0;
        c.gridy++;
        hdlCipherPanel.add(new JLabel("Public Key Reference Handle Index:"), c);
        c.gridx++;
        hdlCipherPanel.add(keyrefIndexField, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        hdlCipherPanel.add(browserHdlPrivkey, c);

        clientCipherPanel = new JPanel(new GridBagLayout());
        clientCipherPanel.setBorder(new EmptyBorder(6, 2, 5, 5));

        //create the private exchange key brower panel
        // int h = (int)browserPrivkey.getPreferredSize().getHeight();
        // browserPrivkey.setPreferredSize(new Dimension(450, h));
        browserCliPrivkey = new BrowsePanel("Private Key File: ", (File) null, "", null, false);

        browserPubkey.setLayout(new FlowLayout(FlowLayout.LEFT));
        browserCliPrivkey.setLayout(new FlowLayout(FlowLayout.LEFT));
        clientCipherPanel.add(browserPubkey, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, false));
        clientCipherPanel.add(browserCliPrivkey, AwtUtil.getConstraints(0, 2, 1, 1, 1, 1, true, false));
        clientCipherPanel.add(genRSAKeyButton, AwtUtil.getConstraints(0, 3, 1, 1, 1, 1, new Insets(0, 60, 0, 60), true, false));

        //create attributes frame, titled "session options"
        JPanel optionsP = new JPanel(new GridBagLayout());
        optionsP.setBorder(new CompoundBorder(new TitledBorder(new EtchedBorder(), "Session Options"), new EmptyBorder(10, 10, 10, 10)));

        // String modes[] = {MODE_NONE, MODE_CLIENT, MODE_SERVER, MODE_HDL, MODE_DH};
        String modes[] = { MODE_NONE, MODE_DH };
        modeBox = new JComboBox(modes);
        // make sure diffie-hellman is supported
        try {
            HdlSecurityProvider.getInstance().generateDHKeyPair(0);
        } catch (NoSuchAlgorithmException e) {
            modeBox.removeItem(MODE_DH);
        } catch (Exception e) {
        }

        modeBox.addActionListener(this);

        sessionEncrypted = new JCheckBox("Encrypted");
        sessionAuthenticated = new JCheckBox("Certified");

        sessionTimeout = new JTextField("86400", 5); //(default in seconds is 24 hours)
        timeoutL1 = new JLabel("Max Lifetime: ");
        timeoutL2 = new JLabel(" Seconds");
        JPanel timeoutP = new JPanel(new FlowLayout(FlowLayout.LEFT));
        timeoutP.add(sessionEncrypted);
        timeoutP.add(sessionAuthenticated);
        timeoutP.add(new JSeparator(SwingConstants.HORIZONTAL));
        timeoutP.add(timeoutL1);
        timeoutP.add(sessionTimeout);
        timeoutP.add(timeoutL2);
        Border b = timeoutL1.getBorder();
        timeoutL1.setBorder(new CompoundBorder(new EmptyBorder(0, 20, 0, 0), b));

        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(new JLabel("Session Mode: "));
        p.add(modeBox);
        optionsP.add(p, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, false));
        // p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // optionsP.add(p, AwtUtil.getConstraints(0,1,1,1,1,1, true, false));

        optionsP.add(timeoutP, AwtUtil.getConstraints(0, 2, 1, 1, 1, 1, true, false));

        optionsP.add(new JSeparator(SwingConstants.VERTICAL), AwtUtil.getConstraints(0, 3, 1, 1, 2, 1, true, true));

        optionsP.add(modeOptionsPanel = new JPanel(), AwtUtil.getConstraints(0, 4, 1, 1, 2, 1, true, true));
        Dimension d = new Dimension(500, 140);
        emptyPanel = new JPanel();
        modeOptionsPanel.setPreferredSize(d);
        clientCipherPanel.setPreferredSize(d);
        hdlCipherPanel.setMaximumSize(d);
        emptyPanel.setPreferredSize(d);

        getContentPane().add(panel);
        panel.add(optionsP, AwtUtil.getConstraints(0, 0, 1, 1, 2, 1, true, true));
        panel.add(btnPanel, AwtUtil.getConstraints(0, 1, 1, 0.1, 1, 1, true, true));

        //initiate the panel with parameter info!!
        if (info == null) {
            modeBox.setSelectedIndex(0);
            sessionTimeout.setEnabled(false);
            sessionEncrypted.setEnabled(false);
            timeoutL1.setEnabled(false);
            timeoutL2.setEnabled(false);
            sessionAuthenticated.setEnabled(false);
            sessionTimeout.setEditable(false);
        } else {
            if (info.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_SERVER) {
                modeBox.setSelectedItem(MODE_SERVER);
            } else if (info.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_CLIENT) {
                modeBox.setSelectedItem(MODE_CLIENT);
            } else if (info.keyExchangeMode == Common.KEY_EXCHANGE_CIPHER_HDL) {
                modeBox.setSelectedItem(MODE_HDL);
            } else if (info.keyExchangeMode == Common.KEY_EXCHANGE_NONE) {
                modeBox.setSelectedItem(MODE_NONE);
            } else if (info.keyExchangeMode == Common.KEY_EXCHANGE_DH) {
                modeBox.setSelectedItem(MODE_DH);
            }
            browserCliPrivkey.setPath(privKeyFile);
            browserHdlPrivkey.setPath(privKeyFile);
            browserPubkey.setPath(pubKeyFile);
            sessionEncrypted.setSelected(info.encrypted);
            sessionAuthenticated.setSelected(info.authenticated);
            sessionTimeout.setText(Integer.toString(info.timeout));
            if (info.exchangeKeyHandle != null) {
                keyrefHandleField.setText(Util.decodeString(info.exchangeKeyHandle));
                keyrefIndexField.setText(Integer.toString(info.exchangeKeyIndex));
            }
        }

        pack();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == modeBox) {
            modeBoxSelected();
        } else if (src == genRSAKeyButton) {
            generateKey();

        } else if (src == helpButton) {
            HelpPanel.show(parent, CommonDef.HELP_DIR, CommonDef.HELP_SESSION_SETUP);
        }
    }

    private void modeBoxSelected() {
        modeOptionsPanel.removeAll();
        modeOptionsPanel.add(emptyPanel);
        modeOptionsPanel.revalidate();
        modeOptionsPanel.repaint();
        modeOptionsPanel.removeAll();

        String mode = (String) modeBox.getSelectedItem();
        sessionEncrypted.setEnabled(mode != MODE_NONE);
        sessionAuthenticated.setEnabled(mode != MODE_NONE);
        sessionTimeout.setEditable(mode != MODE_NONE);
        sessionTimeout.setEnabled(mode != MODE_NONE);
        timeoutL1.setEnabled(mode != MODE_NONE);
        timeoutL2.setEnabled(mode != MODE_NONE);

        if (mode == MODE_CLIENT) {
            modeOptionsPanel.add(clientCipherPanel);
        } else if (mode == MODE_HDL) {
            modeOptionsPanel.add(hdlCipherPanel);
        }
        modeOptionsPanel.revalidate();
        modeOptionsPanel.repaint();
    }

    public byte[] getPubKeyBytes() {
        File[] files = new File[1];
        if (!browserPubkey.getReadFile(files)) return null;

        pubKeyFile = files[0].getPath();
        return Util.getBytesFromFile(files[0]);
    }

    public PrivateKey getExchangePrivateKey() {
        //to save the private key file name to prevent popping up more than one
        //time for pass phrase of the private key (when user has input right
        //once)
        BrowsePanel browserPrivkey;
        if (modeBox.getSelectedItem() == MODE_HDL) {
            browserPrivkey = browserHdlPrivkey;
        } else { //  Common.KEY_EXCHANGE_CIPHER_CLIENT)
            browserPrivkey = browserCliPrivkey;
        }
        PrivateKey privateKey;
        File[] files = new File[1];
        if (!browserPrivkey.getReadFile(files)) return null;

        privKeyFile = files[0].getPath();

        byte[] rawKey = Util.getBytesFromFile(files[0]);
        byte secretKey[] = null;
        try {
            while (true) {
                if (Util.requiresSecretKey(rawKey)) {
                    String[] passPhrase = new String[1];
                    if (PasswordPanel.show(passPhrase, false)) {
                        secretKey = Util.encodeString(passPhrase[0]);
                    } else continue;
                }

                byte keyBytes[] = Util.decrypt(rawKey, secretKey);
                try {
                    privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
                    return privateKey;
                }

                catch (Exception e) {
                    if (secretKey != null) {
                        JOptionPane.showMessageDialog(null, "There was an error decrypting your private key.\n" + "Are you sure that you entered the correct passphrase?\n" + "Please try again.", "Warning", JOptionPane.WARNING_MESSAGE);
                        //return null;
                    } else {
                        return null;
                    }
                }

            }

        } catch (Throwable e) {
            System.err.println("Can't decrypt the private key file." + e);
            return null;
        }
    }

    private void generateKey() {
        GenerateKeyJPanel keyPanel = new GenerateKeyJPanel(new File(""));

        JOptionPane.showOptionDialog(this, keyPanel, "Generate Key Pair: ", JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] { "Close" }, null);

        browserPubkey.setPath(keyPanel.getPubkeyFile());
        browserCliPrivkey.setPath(keyPanel.getPrivkeyFile());
        browserHdlPrivkey.setPath(keyPanel.getPrivkeyFile());
    }

    public SessionSetupInfo getSetupInfo() throws Exception {
        String mode = (String) modeBox.getSelectedItem();
        final SessionSetupInfo sess = new SessionSetupInfo(0);

        try {
            sess.timeout = Integer.parseInt(sessionTimeout.getText());
        } catch (NumberFormatException e) {
            throw new Exception("Invalid session lifetime value.");
        }

        if (mode == MODE_NONE) {
            return null;
        } else if (mode == MODE_HDL) {
            sess.keyExchangeMode = Common.KEY_EXCHANGE_CIPHER_HDL;
            sess.exchangeKeyHandle = Util.encodeString(keyrefHandleField.getText());
            try {
                sess.exchangeKeyIndex = Integer.parseInt(keyrefIndexField.getText());
            } catch (NumberFormatException e) {
                throw new Exception("Invalid handle index.");
            }
            sess.privateExchangeKey = getExchangePrivateKey();
            if (sess.privateExchangeKey == null) {
                throw new Exception("Invalid private key file or passphrase");
            }
        } else if (mode == MODE_DH) {
            sess.keyExchangeMode = Common.KEY_EXCHANGE_DH;
            if (oldInfo == null || oldInfo.publicExchangeKey == null || oldInfo.privateExchangeKey == null) {
                JOptionPane p = new JOptionPane("Generating Diffie-Hellman Keys", JOptionPane.CANCEL_OPTION);
                final JDialog d = p.createDialog(parent, "Wait");
                d.setModal(false);
                d.setVisible(true);
                new Thread() {
                    @Override
                    public void run() {
                        setPriority(MIN_PRIORITY);
                        try {
                            sess.initDHKeys();
                        } catch (Exception e) {
                        }
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                d.setVisible(false);
                            }
                        });
                    }
                }.run();
            }
        } else if (mode == MODE_CLIENT) {
            sess.keyExchangeMode = Common.KEY_EXCHANGE_CIPHER_CLIENT;
            sess.privateExchangeKey = getExchangePrivateKey();
            if (sess.privateExchangeKey == null) {
                throw new Exception("Invalid private key file or passphrase");
            }
            sess.publicExchangeKey = getPubKeyBytes();
        } else if (mode == MODE_SERVER) {
            sess.keyExchangeMode = Common.KEY_EXCHANGE_CIPHER_SERVER;
        }
        return sess;
    }
}

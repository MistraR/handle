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
import java.awt.event.*;
import java.io.*;
import java.security.*;
import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class GenerateKeyJPanel extends JPanel implements ActionListener {
    protected BrowsePanel privkeyPanel;
    protected BrowsePanel pubkeyPanel;
    protected JComboBox algField;
    protected JTextField lenField;
    protected ButtonGroup bgroup;
    protected MyRadioButton encryptButton;
    protected MyRadioButton nocryptButton;
    protected KeyPair currentKeys;
    protected MyButton genButton;
    protected MyButton cancelButton;
    protected Thread genThread = null;

    public GenerateKeyJPanel() {
        this(new File(""));
    }

    public GenerateKeyJPanel(File dir) {
        this(dir, CommonDef.FILE_PUBKEY, CommonDef.FILE_PRIVKEY);
    }

    public GenerateKeyJPanel(File dir, String pubKeyFile, String privKeyFile) {
        super();
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);
        setBorder(new EtchedBorder());

        privkeyPanel = new BrowsePanel("privkey file: ", dir, File.separator + privKeyFile, null, true);
        pubkeyPanel = new BrowsePanel("pubkey file: ", dir, File.separator + pubKeyFile, null, true);

        algField = new JComboBox();
        algField.setToolTipText("Choose Algorithm to generate key pair");
        algField.setEditable(false);
        algField.addItem("RSA");
        algField.addItem("DSA");
        //algField.addItem("other");
        algField.addActionListener(this);

        lenField = new JTextField("1024", 5);
        //lenField.setEditable(false);
        lenField.setToolTipText("Input the strength length of your key pair");

        JPanel p = new JPanel(gridbag);
        encryptButton = new MyRadioButton("Encrypt", "Encrypt the privkey file");
        nocryptButton = new MyRadioButton("Do not encrypt", "Do not encrypt privkey file");
        encryptButton.setSelected(true);
        bgroup = new ButtonGroup();
        bgroup.add(encryptButton);
        bgroup.add(nocryptButton);
        p.add(encryptButton, AwtUtil.getConstraints(0, 0, 1f, 1f, 1, 1, new Insets(2, 25, 1, 5), true, true));
        p.add(nocryptButton, AwtUtil.getConstraints(1, 0, 1f, 1f, 1, 1, new Insets(2, 5, 1, 25), true, true));

        JPanel p1 = new JPanel(gridbag);
        genButton = new MyButton("GenKeys", "click to begin the process");
        cancelButton = new MyButton("Cancel", "click to cancel the process");
        genButton.addActionListener(this);
        cancelButton.addActionListener(this);
        cancelButton.setEnabled(false);
        p1.add(genButton, AwtUtil.getConstraints(0, 0, 1f, 1f, 1, 1, new Insets(1, 15, 5, 15), true, true));
        p1.add(cancelButton, AwtUtil.getConstraints(1, 0, 1f, 1f, 1, 1, new Insets(1, 15, 5, 15), true, true));

        int x = 0;
        int y = 0;

        add(privkeyPanel, AwtUtil.getConstraints(x, y++, 1f, 1f, 5, 1, true, true));
        add(pubkeyPanel, AwtUtil.getConstraints(x, y++, 1f, 1f, 5, 1, true, true));

        add(new JLabel("Algorithm: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1f, 1f, 1, 1, true, true));
        add(algField, AwtUtil.getConstraints(x + 1, y, 1f, 1f, 1, 1, true, true));
        add(new JLabel("Strength: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x + 3, y, 1f, 1f, 1, 1, true, true));
        add(lenField, AwtUtil.getConstraints(x + 4, y++, 1f, 1f, 1, 1, true, true));

        add(p, AwtUtil.getConstraints(x + 1, y++, 1f, 1f, 3, 1, true, true));

        add(p1, AwtUtil.getConstraints(x + 1, y, 1f, 1f, 3, 1, true, true));

    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("GenKeys")) genKeys();
        else if (ae.getActionCommand().equals("Cancel")) cancelProcess();
        else if (ae.getSource() == algField) {
            algFieldSelected();
        }
    }

    public boolean isEncrypt() {
        if (bgroup.getSelection().getActionCommand().equals("Encrypt")) return true;
        else return false;
    }

    public String getAlg() {
        return (String) algField.getSelectedItem();
    }

    public boolean getWritePubkeyFile(File[] files) {
        return pubkeyPanel.getWriteFile(files);
    }

    public boolean getWritePrivkeyFile(File[] files) {
        return privkeyPanel.getWriteFile(files);
    }

    public String getPubkeyFile() {
        return pubkeyPanel.getPath();
    }

    public String getPrivkeyFile() {
        return privkeyPanel.getPath();
    }

    public int getStrength() {
        try {
            return Integer.parseInt(lenField.getText().trim());
        } catch (Exception e) {
            return 1024;
        }
    }

    public boolean getPassPhrase(String[] passPhrase) {
        while (true) {
            if (PasswordPanel.show(passPhrase)) break;
            if (passPhrase[0] == null) return false;

        }
        return true;
    }

    public PublicKey getCurrentPublicKey() {
        if (currentKeys == null) return null;
        return currentKeys.getPublic();
    }

    public PrivateKey getCurrentPrivateKey() {
        if (currentKeys == null) return null;
        return currentKeys.getPrivate();
    }

    //NOTE: gen Keys process is time consuming. Putting it into a separate thread is better
    // if not, it will block the main thread. After it is finished, put an event to show
    // it is finished.

    public void genKeys() {
        File[] files = new File[1];
        File privKeyFile, pubKeyFile;
        if (getWritePubkeyFile(files)) pubKeyFile = files[0];
        else return;

        if (getWritePrivkeyFile(files)) privKeyFile = files[0];
        else return;

        String[] password = new String[1];

        if (isEncrypt()) {
            if (!getPassPhrase(password)) return;
        }

        genButton.setEnabled(false);
        cancelButton.setEnabled(true);
        final String passPhrase = password[0];
        final boolean encrypt = isEncrypt();
        final File pubf = pubKeyFile;
        final File privf = privKeyFile;
        final String alg = getAlg();
        final int len = getStrength();

        Runnable r1 = new Runnable() {
            @Override
            public void run() {
                generateProcess(passPhrase, encrypt, pubf, privf, alg, len);
                Runnable r2 = new Runnable() {
                    @Override
                    public void run() {
                        genButton.setEnabled(true);
                        cancelButton.setEnabled(false);
                    }
                };
                SwingUtilities.invokeLater(r2);
            }
        };

        genThread = new Thread(r1);
        genThread.start();
    }

    protected void generateProcess(String passPhrase, boolean encryptFlag, File pubKeyFile, File privKeyFile, String alg, int strength) {
        System.err.println("generate key message: Generating key pair now, wait please...");
        FileOutputStream out = null;
        try {
            @SuppressWarnings("hiding")
            KeyPair currentKeys = null;

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg);
            kpg.initialize(strength);
            currentKeys = kpg.generateKeyPair();

            if (currentKeys == null) return;
            // save the public key to a file...
            System.err.println("generate key message: Save public key");
            PublicKey publicKey = currentKeys.getPublic();
            out = new FileOutputStream(pubKeyFile);
            out.write(Util.getBytesFromPublicKey(publicKey));
            out.close();

            //save the private key to a file ...
            PrivateKey privateKey = currentKeys.getPrivate();
            out = new FileOutputStream(privKeyFile);
            byte[] privKeyBytes = null;
            if (encryptFlag) {
                privKeyBytes = Util.encrypt(Util.getBytesFromPrivateKey(privateKey), Util.encodeString(passPhrase));
            } else {
                privKeyBytes = Util.encrypt(Util.getBytesFromPrivateKey(privateKey), null, Common.ENCRYPT_NONE);
            }
            out.write(privKeyBytes);
            out.close();

            System.err.println("generate key message: Finished generating keys.");
            JOptionPane.showMessageDialog(this, "Finished generating keys", " Message ", JOptionPane.INFORMATION_MESSAGE);
            return;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            if (out != null) try {
                out.close();
            } catch (Exception e1) {
            }
            JOptionPane.showMessageDialog(this, "Generating keys failed.", " Message ", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void algFieldSelected() {
        /* if (algSelected.equals("RSA"))
          lenField.setText("1024");  //use key size 1024 for RSA key
        else
         */
        lenField.setText("1024");
    }

    @SuppressWarnings("deprecation")
    protected void cancelProcess() {
        if (genThread == null) return;
        genThread.stop();

        JOptionPane.showMessageDialog(this, "Generating keys", " Cancel: ", JOptionPane.INFORMATION_MESSAGE);

        genButton.setEnabled(true);
        cancelButton.setEnabled(false);
    }

    /*
    public static boolean generateKeys(String passPhrase, boolean encryptFlag,
                       File pubKeyFile, File privKeyFile,
                       String alg, int strength){
     System.err.println("generate key message: Generating key pairs now, wait please...");
     FileOutputStream out= null;
     try{
    
         KeyPair keys = null;
         if (alg.equals("RSA"))
         {
           //use provider instead
            HdlSecurityProvider cryptoProvider = HdlSecurityProvider.getInstance();
           if (cryptoProvider==null) {
             throw new HandleException(HandleException.MISSING_CRYPTO_PROVIDER,
                                      "Encryption/Key generation engine missing");
           }
    
           keys = cryptoProvider.generateRSAKeyPair(strength);
         } else
         {
    
           KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg);
            kpg.initialize(strength);
           keys = kpg.generateKeyPair();
         }
    
         // save the public key to a file...
         System.err.println("generate key message: Save public key");
         PublicKey publicKey = keys.getPublic();
         out = new FileOutputStream(pubKeyFile);
         out.write(Util.getBytesFromPublicKey(publicKey));
         out.close();
    
         //save the private key to a file ...
         System.err.println("generate key message: Save public key");
         PrivateKey privateKey = keys.getPrivate();
         out = new FileOutputStream(privKeyFile);
         byte[]privKeyBytes = null;
         if(encryptFlag)
         privKeyBytes = Util.encrypt(Util.getBytesFromPrivateKey(privateKey),
                         Util.encodeString(passPhrase));
         else
         privKeyBytes = Util.getBytesFromPrivateKey(privateKey);
    
         out.write(privKeyBytes);
         out.close();
         System.err.println("generate key message: Finished generating keys.");
         return true;
     }catch(Exception e){
         e.printStackTrace(System.err);
         if(out!=null)try{out.close();}catch(Exception e1){return false;}
         return false;
     }
    }
     */
}

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
import javax.swing.*;
import java.security.*;
import java.io.*;

public class PubkeyDataJPanel extends GenDataJPanel {
    protected JTextArea keyField;
    protected BrowsePanel browser;
    protected MyButton genKeyButton;
    protected MyButton loadKeyButton;
    protected MyButton saveKeyButton;
    protected MyButton clearButton;
    protected PublicKey pubkey = null;

    /**
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, just for view
     **/
    public PubkeyDataJPanel(boolean moreFlag, boolean editFlag) {
        this(moreFlag, editFlag, 1);
    }

    public PubkeyDataJPanel(boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, index);

        genKeyButton = new MyButton("Generate Key Pair", "click to generate public key and private");
        genKeyButton.addActionListener(this);
        genKeyButton.setEnabled(editFlag);

        loadKeyButton = new MyButton("Load Key", "click to load keyfile");
        loadKeyButton.addActionListener(this);
        loadKeyButton.setEnabled(editFlag);

        saveKeyButton = new MyButton("Save Key", "Click to save the public key to a file");
        saveKeyButton.addActionListener(this);

        clearButton = new MyButton("Clear", "click to clear key field");
        clearButton.addActionListener(this);
        clearButton.setEnabled(editFlag);

        keyField = new JTextArea(4, 25);
        keyField.setEditable(false);
        JScrollPane sp = new JScrollPane(keyField);

        int x = 0;
        int y = 0;
        panel.add(new JLabel(" Public Key:", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        panel.add(sp, AwtUtil.getConstraints(x + 1, y++, 1, 1, 4, 1, new Insets(8, 8, 8, 10), true, false));

        panel.add(genKeyButton, AwtUtil.getConstraints(x + 1, y, 0, 1, 1, 1, new Insets(8, 8, 8, 5), false, false));
        panel.add(loadKeyButton, AwtUtil.getConstraints(x + 2, y, 0, 1, 1, 1, new Insets(8, 8, 8, 5), false, false));
        panel.add(saveKeyButton, AwtUtil.getConstraints(x + 3, y, 0, 1, 1, 1, new Insets(8, 8, 8, 5), false, false));
        panel.add(clearButton, AwtUtil.getConstraints(x + 4, y, 0, 1, 1, 1, new Insets(8, 8, 8, 5), false, false));

        handlevalue.setType(Common.STD_TYPE_HSPUBKEY);

    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getActionCommand().equals("Generate Key Pair")) generateKey();
        else if (e.getActionCommand().equals("More")) more();
        else if (e.getActionCommand().equals("Load Key")) loadkey();
        else if (e.getActionCommand().equals("Save Key")) savekey();
        else if (e.getActionCommand().equals("Clear")) clear();
        else System.err.println("Error Input");

    }

    @Override
    public byte[] getValueData() {
        if (pubkey == null) {
            System.err.println("Error: public key is null");
            return null;
        }
        try {
            byte[] buf = Util.getBytesFromPublicKey(pubkey);
            return buf;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY) {
            System.err.println("warning message: Handle value data is empty");
            return;
        }
        clear();
        keyField.setText(Util.decodeHexString(data, true));
        try {
            pubkey = Util.getPublicKeyFromBytes(data, 0);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void clear() {
        keyField.setText("");
        pubkey = null;
    }

    public void generateKey() {
        GenerateKeyJPanel keyPanel = new GenerateKeyJPanel(new File(""));

        JOptionPane.showOptionDialog(this, keyPanel, "Generate Key Pair: ", JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[] { "Close" }, null);
        pubkey = keyPanel.getCurrentPublicKey();
        if (pubkey == null) return;
        try {
            byte[] buffer = Util.getBytesFromPublicKey(pubkey);
            keyField.setText(Util.decodeHexString(buffer, true));
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void savekey() {
        if (pubkey == null) {
            JOptionPane.showMessageDialog(this, "There is no public key to save!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        int returnVal = chooser.showSaveDialog(this);
        File keyFile = null;
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            keyFile = chooser.getSelectedFile();
        }
        if (keyFile == null) return;

        try (OutputStream out = new FileOutputStream(keyFile)) {
            out.write(Util.getBytesFromPublicKey(pubkey));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Error: " + e, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void loadkey() {
        browser = new BrowsePanel(" Public Key: ", (File) null, File.separator + "pubkey.bin", null, false);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, browser, "Load Public Key: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        File[] files = new File[1];
        if (!browser.getReadFile(files)) {
            return;
        }

        try (InputStream in = new FileInputStream(files[0])) {
            byte[] rawKey = new byte[(int) files[0].length()];
            int n = 0;
            int r = 0;
            while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0)
                n += r;
            keyField.setText(Util.decodeHexString(rawKey, true));
            pubkey = Util.getPublicKeyFromBytes(rawKey, 0);
        } catch (Exception e) {
            e.printStackTrace(System.err);

        }
    }
}

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
//import java.text.*;
import java.io.*;

public class DefaultValueEditor extends JPanel implements ActionListener, HandleValueEditor {
    private final JComboBox<String> formatChoice;
    private final JTextArea inputField;
    private final JButton loadButton;
    private final JButton saveButton;

    public DefaultValueEditor() {
        super(new GridBagLayout());

        loadButton = new JButton("Load From File");
        saveButton = new JButton("Save To File");
        formatChoice = new JComboBox<>(new String[] { "Hex", "UTF8 Text" });
        inputField = new JTextArea(5, 20);

        add(formatChoice, AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        add(new JLabel(" "), AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, new Insets(4, 20, 4, 4), true, true));
        add(loadButton, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));
        add(saveButton, AwtUtil.getConstraints(3, 0, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));
        add(new JScrollPane(inputField), AwtUtil.getConstraints(0, 1, 1, 1, 4, 1, new Insets(4, 4, 4, 4), true, true));

        formatChoice.addActionListener(this);
        loadButton.addActionListener(this);
        saveButton.addActionListener(this);
    }

    @Override
    public boolean saveValueData(HandleValue value) {
        if (formatChoice.getSelectedIndex() == 0) { // hex format
            value.setData(Util.encodeHexString(inputField.getText()));
        } else {
            value.setData(Util.encodeString(inputField.getText()));
        }
        return true;
    }

    @Override
    public void loadValueData(HandleValue value) {
        setData(value == null ? null : value.getData());
    }

    private void setData(byte data[]) {
        if (data == null) {
            previousFormat = 1;
            formatChoice.setSelectedIndex(1);
            inputField.setText("");
        } else if (Util.looksLikeBinary(data)) {
            previousFormat = 0;
            formatChoice.setSelectedIndex(0);
            inputField.setText(Util.decodeHexString(data, true));
        } else { // looks like a text value
            previousFormat = 1;
            formatChoice.setSelectedIndex(1);
            inputField.setText(Util.decodeString(data));
        }
    }

    int previousFormat = 0;

    public void formatSelected() {
        int newFormat = formatChoice.getSelectedIndex();
        if (newFormat != previousFormat) {
            if (newFormat == 0) { // convert from text to hex
                byte data[] = Util.encodeString(inputField.getText());
                inputField.setText(Util.decodeHexString(data, true));
            } else { // convert from hex to text
                byte data[] = Util.encodeHexString(inputField.getText());
                String dataStr = Util.decodeString(data);

                // check if the text form will convert back to the same
                // binary value.  If not, do not allow editing in text mode
                if (!Util.equals(data, Util.encodeString(dataStr))) {
                    // not valid text... force the UI back to hex mode
                    getToolkit().beep();
                    formatChoice.setSelectedIndex(0);
                    return;
                }
                inputField.setText(dataStr);
            }
            previousFormat = newFormat;
        }
    }

    private void loadFromFile() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Load", FileDialog.LOAD);

        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte buf[] = new byte[1024];
            FileInputStream fin = new FileInputStream(new File(dirName, fileName));
            try {
                int r;
                while ((r = fin.read(buf)) >= 0)
                    bout.write(buf, 0, r);
            } finally {
                fin.close();
            }
            buf = bout.toByteArray();

            if (Util.looksLikeBinary(buf)) {
                previousFormat = 0;
                formatChoice.setSelectedIndex(0);
                inputField.setText(Util.decodeHexString(buf, true));
            } else { // looks like a text value
                previousFormat = 1;
                formatChoice.setSelectedIndex(1);
                inputField.setText(Util.decodeString(buf));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: loading file: " + e);
        }
    }

    private void saveToFile() {
        byte buf[] = null;
        if (formatChoice.getSelectedIndex() == 0) { // hex format
            buf = Util.encodeHexString(inputField.getText());
        } else {
            buf = Util.encodeString(inputField.getText());
        }

        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Save", FileDialog.SAVE);

        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        try {
            FileOutputStream fout = new FileOutputStream(new File(dirName, fileName));
            fout.write(buf);
            fout.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: loading file: " + e);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == formatChoice) {
            formatSelected();
        } else if (src == loadButton) {
            loadFromFile();
        } else if (src == saveButton) {
            saveToFile();
        }
    }

}

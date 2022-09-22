/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.handle.hdllib.*;
import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;

public class DefaultDataPanel extends Panel implements ItemListener {
    private final TextArea textArea;
    private final Choice formatChoice;
    private int lastFormat;

    public DefaultDataPanel() {
        setLayout(new BorderLayout());

        textArea = new TextArea(5, 50);
        formatChoice = new Choice();
        formatChoice.addItem("Hex");
        formatChoice.addItem("Text");
        formatChoice.addItem("Site Info");
        formatChoice.addItem("Admin Info");
        formatChoice.addItem("Reference List");

        Button loadButton = new Button("Load");
        Button saveButton = new Button("Save");

        Panel northPanel = new Panel();
        northPanel.add(formatChoice);
        northPanel.add(new Label("   "));
        northPanel.add(loadButton);
        northPanel.add(saveButton);

        add(northPanel, "North");
        add(textArea, "Center");

        lastFormat = 0;

        formatChoice.addItemListener(this);
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                loadFromFile();
            }
        });
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                saveToFile();
            }
        });
    }

    private void loadFromFile() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Select File to Load", FileDialog.LOAD);
        fwin.setVisible(true);

        String dir = fwin.getDirectory();
        String file = fwin.getFile();
        if (dir == null || file == null || file.trim().length() <= 0) return;
        java.io.InputStream in = null;
        try {
            java.io.File dataFile = new java.io.File(dir, file);
            byte data[] = new byte[(int) dataFile.length()];
            in = new java.io.FileInputStream(dataFile);
            int n = 0;
            int r = 0;
            while (n < data.length && (r = in.read(data, n, data.length - n)) > 0) {
                n += r;
            }
            selectFormat(0);
            lastFormat = 0;
            setHexValue(data);
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            GenericDialog.askQuestion("Error", "Error unable to load data: " + e, GenericDialog.QUESTION_OK, this);
        } finally {
            if (in != null) try {
                in.close();
            } catch (Exception e) {
            }
        }
    }

    private void saveToFile() {
        System.err.println("Saving to a file....");
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Select File to Save", FileDialog.SAVE);
        fwin.setVisible(true);

        String dir = fwin.getDirectory();
        String file = fwin.getFile();
        if (dir == null || file == null || file.trim().length() <= 0) return;
        java.io.OutputStream out = null;
        try {
            java.io.File dataFile = new java.io.File(dir, file);
            out = new java.io.FileOutputStream(dataFile);
            out.write(getValue());
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            GenericDialog.askQuestion("Error", "Error unable to save data: " + e, GenericDialog.QUESTION_OK, this);
        } finally {
            if (out != null) try {
                out.close();
            } catch (Exception e) {
            }
        }
    }

    public void setValue(byte value[]) {
        if (lastFormat == 0) {
            setHexValue(value);
        } else {
            setTextValue(value);
        }
    }

    public byte[] getValue() {
        if (lastFormat == 0) {
            return getHexValue();
        } else {
            return getTextValue();
        }
    }

    private void getSiteValue() {
        byte currentVal[] = getValue();
        SiteInfo site = new SiteInfo();
        if (currentVal.length > 0) {
            try {
                Encoder.decodeSiteInfoRecord(currentVal, 0, site);
            } catch (Throwable e) {
                System.err.println("Exception parsing current value: " + e);
                e.printStackTrace(System.err);
            }
        }
        SiteInfoPanel sitePanel = new SiteInfoPanel();
        sitePanel.setSiteInfo(site);
        if (GenericDialog.ANSWER_CANCEL != GenericDialog.showDialog("Edit Data", sitePanel, GenericDialog.QUESTION_OK_CANCEL, this)) {

            sitePanel.getSiteInfo(site);

            // set the value to the HEX representation of the site
            byte record[] = Encoder.encodeSiteInfoRecord(site);
            selectFormat(0);
            setValue(record);
        } else {
            selectFormat(lastFormat);
        }
    }

    private void getAdminValue() {
        byte currentVal[] = getValue();
        AdminRecord admin = new AdminRecord();

        if (currentVal.length > 0) {
            try {
                Encoder.decodeAdminRecord(currentVal, 0, admin);
            } catch (Throwable e) {
                System.err.println("Exception parsing current value: " + e);
                e.printStackTrace(System.err);
            }
        }
        AdminInfoPanel adminPanel = new AdminInfoPanel();
        adminPanel.setAdminInfo(admin);
        if (GenericDialog.ANSWER_CANCEL != GenericDialog.showDialog("Edit Admin Record", adminPanel, GenericDialog.QUESTION_OK_CANCEL, this)) {

            adminPanel.getAdminInfo(admin);

            // set the value to the HEX representation of the admin
            byte record[] = Encoder.encodeAdminRecord(admin);
            selectFormat(0);
            setValue(record);
        } else {
            selectFormat(lastFormat);
        }
    }

    private void getVListValue() {
        byte currentVal[] = getValue();
        ValueReference valuesInGroup[] = new ValueReference[0];
        if (currentVal.length > 0) {
            try {
                valuesInGroup = Encoder.decodeValueReferenceList(currentVal, 0);
            } catch (Throwable e) {
                System.err.println("Exception parsing current value: " + e);
                e.printStackTrace(System.err);
            }
        }
        ValueListInfoPanel valuesPanel = new ValueListInfoPanel();
        valuesPanel.setValueListInfo(valuesInGroup);
        if (GenericDialog.ANSWER_CANCEL != GenericDialog.showDialog("Edit Value List Record", valuesPanel, GenericDialog.QUESTION_OK_CANCEL, this)) {

            valuesInGroup = valuesPanel.getValueListInfo();

            // set the value to the HEX representation of the value list
            byte record[] = Encoder.encodeValueReferenceList(valuesInGroup);
            selectFormat(0);
            setValue(record);
        } else {
            selectFormat(lastFormat);
        }
    }

    private void convertTextToHex() {
        setHexValue(getTextValue());
    }

    private void convertHexToText() {
        setTextValue(getHexValue());
    }

    private byte[] getTextValue() {
        return Util.encodeString(textArea.getText());
    }

    private void setTextValue(byte b[]) {
        textArea.setText(Util.decodeString(b));
    }

    private byte[] getHexValue() {
        return Util.encodeHexString(textArea.getText());
    }

    private void setHexValue(byte b[]) {
        textArea.setText(Util.decodeHexString(b, true));
    }

    private void selectFormat(int newFormat) {
        formatChoice.select(newFormat);
    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        if (evt.getSource() == formatChoice) {
            int format = formatChoice.getSelectedIndex();
            if (format == lastFormat) return;
            if (format == 0) {
                convertTextToHex();
                lastFormat = format;
            } else if (format == 1) {
                convertHexToText();
                lastFormat = format;
            } else if (format == 2) {
                getSiteValue();
            } else if (format == 3) {
                getAdminValue();
            } else if (format == 4) {
                getVListValue();
            }
        }
    }
}

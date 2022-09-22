/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import net.handle.apps.simple.SiteInfoConverter;
import net.handle.awt.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import net.cnri.guiutil.*;
import java.io.*;

public class SiteInfoEditor extends JPanel implements ActionListener, HandleValueEditor {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;
    protected JButton loadButton;
    protected JButton saveButton;
    protected HDLAction testAction;

    protected JTextField dataVersionField;
    protected JTextField majorProtocolField;
    protected JTextField minorProtocolField;
    protected JTextField serialNumberField;
    protected JCheckBox isPrimaryCheckbox;
    protected JCheckBox multiplePrimaryCheckbox;
    protected JComboBox<String> hashOptionChoice;
    protected DefaultListModel<ServerInfo> serverListModel;
    protected JList<ServerInfo> serverList;
    protected JButton addServerButton;
    protected JButton removeServerButton;
    protected JButton editServerButton;
    protected DefaultListModel<Attribute> attributeListModel;
    protected JList<Attribute> attributeList;
    protected JButton addAttributeButton;
    protected JButton removeAttributeButton;
    protected JButton editAttributeButton;

    protected JButton loadFromNAButton;

    public SiteInfoEditor(AdminToolUI ui) {
        super(new GridBagLayout());
        this.ui = ui;

        loadButton = new JButton("Load From File");
        saveButton = new JButton("Save To File");
        testAction = new HDLAction(ui, "run_site_test", "run_site_test", this);

        dataVersionField = new JTextField("", 3);
        majorProtocolField = new JTextField("", 3);
        minorProtocolField = new JTextField("", 3);
        serialNumberField = new JTextField("", 3);
        isPrimaryCheckbox = new JCheckBox(ui.getStr("is_primary_site"));
        multiplePrimaryCheckbox = new JCheckBox(ui.getStr("is_multiple_primary_site"));
        hashOptionChoice = new JComboBox<>();
        hashOptionChoice.addItem(ui.getStr("hash_by_handle"));
        hashOptionChoice.addItem(ui.getStr("hash_by_prefix"));
        hashOptionChoice.addItem(ui.getStr("hash_by_localname"));
        serverListModel = new DefaultListModel<>();
        serverList = new JList<>(serverListModel);
        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addServerButton = new JButton(ui.getStr("add"));
        removeServerButton = new JButton(ui.getStr("remove"));
        editServerButton = new JButton(ui.getStr("modify"));
        attributeListModel = new DefaultListModel<>();
        attributeList = new JList<>(attributeListModel);
        attributeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addAttributeButton = new JButton(ui.getStr("add"));
        removeAttributeButton = new JButton(ui.getStr("remove"));
        editAttributeButton = new JButton(ui.getStr("modify"));

        loadFromNAButton = new JButton(ui.getStr("load_from_prefix"));

        int y = 0;
        //add(new JLabel(ui.getStr("data_version")+": ", JLabel.RIGHT),
        //    AwtUtil.getConstraints(0,y,0,0,1,1,new Insets(5,5,5,0),true,true));
        //add(dataVersionField,
        //    AwtUtil.getConstraints(1,y++,1,0,1,1,new Insets(5,5,5,5),true,true));

        JPanel fileP = new JPanel(new GridBagLayout());
        fileP.add(new JButton(testAction), GridC.getc(0, 0).wx(1).insets(5, 5, 5, 20).west());
        fileP.add(loadButton, GridC.getc(1, 0).wx(0).insets(5, 5, 5, 5));
        fileP.add(saveButton, GridC.getc(2, 0).wx(0).insets(5, 5, 5, 5));
        add(fileP, GridC.getc(0, y++).colspan(4).wx(1).fillboth());

        add(new JLabel(ui.getStr("protocol_version") + ": ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, true));
        add(majorProtocolField, AwtUtil.getConstraints(1, y, 1, 0, 1, 1, new Insets(5, 5, 5, 1), true, true));
        add(new JLabel("."), AwtUtil.getConstraints(2, y, 0, 0, 1, 1, true, true));
        add(minorProtocolField, AwtUtil.getConstraints(3, y++, 1, 0, 1, 1, new Insets(5, 1, 5, 5), true, true));

        add(new JLabel(ui.getStr("site_serial_number") + ": ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, true));
        add(serialNumberField, AwtUtil.getConstraints(1, y++, 1, 0, 3, 1, new Insets(5, 5, 5, 5), true, true));

        add(new JLabel(ui.getStr("hash_alg") + ": ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, true));
        add(hashOptionChoice, AwtUtil.getConstraints(1, y++, 1, 0, 3, 1, new Insets(5, 5, 5, 5), true, true));

        add(isPrimaryCheckbox, AwtUtil.getConstraints(1, y++, 1, 0, 3, 1, new Insets(5, 5, 5, 5), true, false));

        add(multiplePrimaryCheckbox, AwtUtil.getConstraints(1, y++, 1, 0, 3, 1, new Insets(5, 5, 5, 5), true, false));

        JPanel p;
        p = new JPanel(new GridBagLayout());
        p.add(new JLabel(ui.getStr("servers") + ":"), AwtUtil.getConstraints(0, 0, 0, 0, 2, 1, new Insets(5, 5, 5, 5), true, false));
        p.add(new JScrollPane(serverList), AwtUtil.getConstraints(0, 1, 1, 1, 1, 4, new Insets(0, 5, 5, 5), true, true));
        p.add(addServerButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, new Insets(0, 5, 5, 5), true, true));
        p.add(editServerButton, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(removeServerButton, AwtUtil.getConstraints(1, 3, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(Box.createVerticalStrut(0), AwtUtil.getConstraints(1, 4, 0, 1, 1, 1, true, true));
        add(p, AwtUtil.getConstraints(0, y++, 1, 1, 4, 1, true, false));

        p = new JPanel(new GridBagLayout());
        p.add(new JLabel(ui.getStr("attributes") + ":"), AwtUtil.getConstraints(0, 0, 0, 0, 2, 1, new Insets(5, 5, 5, 5), true, false));
        p.add(new JScrollPane(attributeList), AwtUtil.getConstraints(0, 1, 1, 1, 1, 4, new Insets(0, 5, 5, 5), true, true));
        p.add(addAttributeButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, new Insets(0, 5, 5, 5), true, true));
        p.add(editAttributeButton, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(removeAttributeButton, AwtUtil.getConstraints(1, 3, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(Box.createVerticalStrut(0), AwtUtil.getConstraints(1, 4, 0, 1, 1, 1, true, true));

        add(p, AwtUtil.getConstraints(0, y++, 1, 1, 4, 1, true, false));

        serverList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editServer();
                }
            }
        });
        attributeList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && editAttributeButton.isEnabled()) {
                    editAttribute();
                }
            }
        });

        loadButton.addActionListener(this);
        saveButton.addActionListener(this);
        addServerButton.addActionListener(this);
        removeServerButton.addActionListener(this);
        editServerButton.addActionListener(this);
        addAttributeButton.addActionListener(this);
        removeAttributeButton.addActionListener(this);
        editAttributeButton.addActionListener(this);
        loadFromNAButton.addActionListener(this);
    }

    private void editServer() {
        ServerInfo info = serverList.getSelectedValue();
        ServerInfoEditor sie = new ServerInfoEditor(ui);
        sie.loadServerInfo(info);
        if (!editServerButton.isEnabled()) sie.setComponentEnabled(false);
        while (true) {
            if (JOptionPane.showConfirmDialog(this, sie, ui.getStr("server_information"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) break;
            if (sie.saveServerInfo(info)) {
                serverList.repaint();
                break;
            }
        }
    }

    private void editAttribute() {
        Attribute info = attributeList.getSelectedValue();
        AttributeInfoEditor aie = new AttributeInfoEditor(ui);
        aie.loadAttribute(info);
        if (JOptionPane.showConfirmDialog(this, aie, ui.getStr("edit_attribute"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) return;
        aie.saveAttribute(info);
        attributeList.repaint();
    }

    /**
     * Saves the handle value data in the given HandleValue.  Returns false
     * if there was an error and the operation was canceled.
     */
    @Override
    public boolean saveValueData(HandleValue value) {
        SiteInfo siteinfo = new SiteInfo();
        siteinfo.dataFormatVersion = Common.SITE_RECORD_FORMAT_VERSION;
        try {
            siteinfo.serialNumber = Integer.parseInt(serialNumberField.getText().trim());
        } catch (Exception e) {
            showError(ui.getStr("invalid_serial_num") + ": " + serialNumberField.getText());
            return false;
        }
        try {
            siteinfo.majorProtocolVersion = Byte.parseByte(majorProtocolField.getText().trim());
        } catch (Exception e) {
            showError(ui.getStr("invalid_proto_version") + ": " + majorProtocolField.getText());
            return false;
        }
        try {
            siteinfo.minorProtocolVersion = Byte.parseByte(minorProtocolField.getText().trim());
        } catch (Exception e) {
            showError(ui.getStr("invalid_proto_version") + ": " + minorProtocolField.getText());
            return false;
        }
        siteinfo.isPrimary = isPrimaryCheckbox.isSelected();
        siteinfo.multiPrimary = multiplePrimaryCheckbox.isSelected();
        siteinfo.isRoot = false;
        switch (hashOptionChoice.getSelectedIndex()) {
        case 1:
            siteinfo.hashOption = SiteInfo.HASH_TYPE_BY_PREFIX;
            break;
        case 2:
            siteinfo.hashOption = SiteInfo.HASH_TYPE_BY_SUFFIX;
            break;
        case 0:
        default:
            siteinfo.hashOption = SiteInfo.HASH_TYPE_BY_ALL;
            break;
        }

        siteinfo.servers = new ServerInfo[serverListModel.getSize()];
        for (int i = 0; i < siteinfo.servers.length; i++) {
            siteinfo.servers[i] = serverListModel.getElementAt(i);
        }
        siteinfo.attributes = new Attribute[attributeListModel.getSize()];
        for (int i = 0; i < siteinfo.attributes.length; i++) {
            siteinfo.attributes[i] = attributeListModel.getElementAt(i);
        }

        value.setData(Encoder.encodeSiteInfoRecord(siteinfo));
        return true;
    }

    /**
     * Sets the handle value data from the given HandleValue.
     */
    @Override
    public void loadValueData(HandleValue value) {
        SiteInfo siteinfo = new SiteInfo();
        try {
            byte[] data = value.getData();
            if (Util.looksLikeBinary(data)) {
                Encoder.decodeSiteInfoRecord(data, 0, siteinfo);
            } else {
                SiteInfo converted = SiteInfoConverter.convertToSiteInfo(new String(data, "UTF-8"));
                if (converted != null) siteinfo = converted;
            }
        } catch (Exception e) {
            System.err.println("Unable to extract value reference list from " + value);
        }

        dataVersionField.setText(String.valueOf(siteinfo.dataFormatVersion));
        serialNumberField.setText(String.valueOf(siteinfo.serialNumber));
        majorProtocolField.setText(String.valueOf(siteinfo.majorProtocolVersion));
        minorProtocolField.setText(String.valueOf(siteinfo.minorProtocolVersion));
        isPrimaryCheckbox.setSelected(siteinfo.isPrimary);
        multiplePrimaryCheckbox.setSelected(siteinfo.multiPrimary);
        //isRootCheckbox.setSelected(siteinfo.isRoot);

        switch (siteinfo.hashOption) {
        case SiteInfo.HASH_TYPE_BY_PREFIX:
            hashOptionChoice.setSelectedIndex(1);
            break;
        case SiteInfo.HASH_TYPE_BY_SUFFIX:
            hashOptionChoice.setSelectedIndex(2);
            break;
        case SiteInfo.HASH_TYPE_BY_ALL:
        default:
            hashOptionChoice.setSelectedIndex(0);
            break;
        }

        serverListModel.removeAllElements();
        for (int i = 0; siteinfo.servers != null && i < siteinfo.servers.length; i++) {
            serverListModel.addElement(siteinfo.servers[i].cloneServerInfo());
        }

        attributeListModel.removeAllElements();
        for (int i = 0; siteinfo.attributes != null && i < siteinfo.attributes.length; i++) {
            attributeListModel.addElement(siteinfo.attributes[i].cloneAttribute());
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

            HandleValue value = new HandleValue();
            value.setData(buf);
            loadValueData(value);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: loading file: " + e);
        }
    }

    private void saveToFile() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Save", FileDialog.SAVE);
        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        HandleValue value = new HandleValue();
        if (!saveValueData(value)) return;

        try {
            FileOutputStream fout = new FileOutputStream(new File(dirName, fileName));
            fout.write(value.getData());
            fout.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: saving file: " + e);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (testAction.matchesCommand(evt)) {
            HandleValue siteValue = new HandleValue();
            if (!saveValueData(siteValue)) return;
            try {
                Window parent = SwingUtilities.getWindowAncestor(this);
                SiteTester tester;
                if (parent == null || parent instanceof Frame) {
                    tester = new SiteTester((Frame) parent, ui, siteValue);
                } else {
                    tester = new SiteTester((Dialog) parent, ui, siteValue);
                }
                tester.setLocationRelativeTo(this);
                tester.setVisible(true);
            } catch (Exception e) {
                showError("Error parsing site information: " + e);
                e.printStackTrace(System.err);
                return;
            }
        } else if (src == addServerButton) {
            ServerInfoEditor sie = new ServerInfoEditor(ui);
            while (true) {
                if (JOptionPane.showConfirmDialog(this, sie, ui.getStr("server_information"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) return;
                ServerInfo info = new ServerInfo();
                if (sie.saveServerInfo(info)) {
                    serverListModel.addElement(info);
                    break;
                }
            }
        } else if (src == removeServerButton) {
            int row = serverList.getSelectedIndex();
            if (row < 0) return;
            if (JOptionPane.showConfirmDialog(this, ui.getStr("confirm_rem_server"), ui.getStr("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                serverListModel.remove(row);
            }
        } else if (src == editServerButton) {
            editServer();
        } else if (src == addAttributeButton) {
            AttributeInfoEditor aie = new AttributeInfoEditor(ui);
            Attribute info = new Attribute();
            if (JOptionPane.showConfirmDialog(this, aie, ui.getStr("edit_attribute"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) return;

            aie.saveAttribute(info);
            attributeListModel.addElement(info);
        } else if (src == removeAttributeButton) {
            int row = attributeList.getSelectedIndex();
            if (row < 0) return;
            if (JOptionPane.showConfirmDialog(this, ui.getStr("confirm_rem_attribute"), ui.getStr("confirm"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                attributeListModel.remove(row);
            }
        } else if (src == editAttributeButton) {
            editAttribute();
        } else if (src == loadFromNAButton) {
            // not implemented...
        } else if (src == loadButton) {
            loadFromFile();
        } else if (src == saveButton) {
            saveToFile();
        }
    }

    private void showError(String errorMessage) {
        JOptionPane.showMessageDialog(null, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

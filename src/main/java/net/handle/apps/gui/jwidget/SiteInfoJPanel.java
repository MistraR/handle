/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;
import net.handle.apps.simple.SiteInfoConverter;

import net.handle.awt.*;
import net.handle.hdllib.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SiteInfoJPanel extends JPanel implements ActionListener {
    protected JTextField dataVersionField;
    protected JTextField majorProtocolField;
    protected JTextField minorProtocolField;
    protected JTextField serialNumberField;
    protected JCheckBox isPrimaryCheckbox;
    protected JCheckBox multiplePrimaryCheckbox;
    protected JComboBox hashOptionChoice;
    protected MyButton saveButton;
    protected MyButton loadButton;
    protected MyButton naButton;

    protected JPanel setPanel;
    protected JPanel savePanel;
    protected ServerList serverList;
    protected AttributeListJPanel attriList;
    protected SiteInfo siteInfo;
    protected boolean editFlag;

    public SiteInfoJPanel(boolean editFlag) {
        super();
        this.editFlag = editFlag;

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);
        EtchedBorder etchBorder = new EtchedBorder();

        dataVersionField = new JTextField("1", 5);
        dataVersionField.setEditable(false);

        majorProtocolField = new JTextField("5", 3);
        majorProtocolField.setToolTipText("Major Protocol type, default value is 5");
        majorProtocolField.setEditable(editFlag);

        minorProtocolField = new JTextField("0", 3);
        minorProtocolField.setToolTipText("Minor Protocol type, default value is 0");
        minorProtocolField.setEditable(editFlag);

        serialNumberField = new JTextField("1", 5);
        serialNumberField.setToolTipText("increased by 1, whenever change the site information");
        serialNumberField.setEditable(editFlag);

        isPrimaryCheckbox = new JCheckBox("Primary Site", false);
        isPrimaryCheckbox.setToolTipText("Check to select the site as primary site, otherwise replication site");
        isPrimaryCheckbox.setEnabled(editFlag);

        multiplePrimaryCheckbox = new JCheckBox("Multiple Primary Sites", false);
        multiplePrimaryCheckbox.setToolTipText("Check to indicate that multiple sites may support administrative requests.");
        multiplePrimaryCheckbox.setEnabled(editFlag);

        hashOptionChoice = new JComboBox();
        hashOptionChoice.setToolTipText(" Choose the function to hash handles");
        hashOptionChoice.setEditable(false);
        hashOptionChoice.setEnabled(editFlag);
        hashOptionChoice.addItem("By Prefix");
        hashOptionChoice.addItem("By Local Name");
        hashOptionChoice.addItem("By Entire Handle");
        hashOptionChoice.setSelectedIndex(SiteInfo.HASH_TYPE_BY_ALL);

        setPanel = new JPanel(gridbag);
        int x = 0;
        int y = 0;
        Insets insets = new Insets(2, 2, 2, 2);
        setPanel.add(new JLabel("Data Version: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        setPanel.add(dataVersionField, AwtUtil.getConstraints(x + 1, y++, 0f, 0f, 1, 1, insets, GridBagConstraints.WEST, false, true));
        setPanel.add(new JLabel("Protocol: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        setPanel.add(majorProtocolField, AwtUtil.getConstraints(x + 1, y, 1f, 0f, 1, 1, true, true));
        setPanel.add(minorProtocolField, AwtUtil.getConstraints(x + 2, y++, 1f, 0f, 1, 1, true, true));

        setPanel.add(new JLabel("Serial #: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        setPanel.add(serialNumberField, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, insets, GridBagConstraints.WEST, false, true));

        setPanel.add(isPrimaryCheckbox, AwtUtil.getConstraints(x + 1, y, 0f, 0f, 2, 1, true, true));
        setPanel.add(multiplePrimaryCheckbox, AwtUtil.getConstraints(x + 3, y++, 0f, 0f, 2, 1, true, true));

        setPanel.add(new JLabel("Hash: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        setPanel.add(hashOptionChoice, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        serverList = new ServerList();
        serverList.setBorder(new TitledBorder(etchBorder, " Servers: "));
        attriList = new AttributeListJPanel(editFlag);
        attriList.setBorder(new TitledBorder(etchBorder, " Attributes: "));

        saveButton = new MyButton("Save To File", "Save current data");
        loadButton = new MyButton("Load From File", "Load data from file");
        naButton = new MyButton("Load From NA", "Load data from NA handle");
        saveButton.addActionListener(this);
        loadButton.addActionListener(this);
        naButton.addActionListener(this);

        savePanel = new JPanel(gridbag);
        savePanel.add(saveButton, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, true));
        savePanel.add(naButton, AwtUtil.getConstraints(1, 0, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, true));
        savePanel.add(loadButton, AwtUtil.getConstraints(2, 0, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, true));

        siteInfo = new SiteInfo();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("Save To File")) saveToFile();
        else if (ae.getActionCommand().equals("Load From File")) loadFromFile();
        else if (ae.getActionCommand().equals("Load From NA")) loadFromNA();
    }

    public void setSiteInfo(SiteInfo site) {
        if (site == null) {
            System.err.println("error message: not siteinfo in the SiteInfoJPanel");
            return;
        }
        try {
            dataVersionField.setText(String.valueOf(site.dataFormatVersion));
            majorProtocolField.setText(String.valueOf(site.majorProtocolVersion));
            minorProtocolField.setText(String.valueOf(site.minorProtocolVersion));
            serialNumberField.setText(String.valueOf(site.serialNumber));
            isPrimaryCheckbox.setSelected(site.isPrimary);
            multiplePrimaryCheckbox.setSelected(site.multiPrimary);
            hashOptionChoice.setSelectedIndex(site.hashOption);
            serverList.clearAll();
            if (site.servers != null) {
                for (int i = 0; i < site.servers.length; i++)
                    serverList.appendItem(site.servers[i]);
            }
            attriList.clearAll();
            if (site.attributes != null) {
                for (int i = 0; i < site.attributes.length; i++)
                    attriList.appendItem(site.attributes[i]);
            }

        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public int getDataVersion() {
        try {
            return Integer.parseInt(dataVersionField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public byte getMajorProtocol() {
        try {
            return (byte) Integer.parseInt(majorProtocolField.getText().trim());
        } catch (Exception e) {
            return (byte) 0;
        }
    }

    public byte getMinorProtocol() {
        try {
            return (byte) Integer.parseInt(minorProtocolField.getText().trim());
        } catch (Exception e) {
            return (byte) 0;
        }
    }

    public int getSerialNumber() {
        try {
            return Integer.parseInt(serialNumberField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public SiteInfo getSiteInfo() {
        try {
            siteInfo.dataFormatVersion = getDataVersion();
            siteInfo.majorProtocolVersion = getMajorProtocol();
            siteInfo.minorProtocolVersion = getMinorProtocol();
            siteInfo.serialNumber = getSerialNumber();
            siteInfo.isPrimary = isPrimaryCheckbox.isSelected();
            siteInfo.multiPrimary = multiplePrimaryCheckbox.isSelected();
            siteInfo.hashOption = (byte) hashOptionChoice.getSelectedIndex();

            Vector v = serverList.getItems();
            if (v != null) {
                siteInfo.servers = new ServerInfo[v.size()];
                for (int i = 0; i < v.size(); i++)
                    siteInfo.servers[i] = (ServerInfo) v.elementAt(i);
            }
            v = attriList.getItems();
            if (v != null) {
                siteInfo.attributes = new Attribute[v.size()];
                for (int i = 0; i < v.size(); i++)
                    siteInfo.attributes[i] = (Attribute) v.elementAt(i);
            }
            return siteInfo;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    protected void saveToFile() {
        SiteInfo site = getSiteInfo();
        if (site == null) {
            System.err.println("No data to save");
            return;
        }

        BrowsePanel browser = new BrowsePanel("Save Path: ", (File) null, "", null, true);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, browser, "Save Site info :", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        File[] files = new File[1];
        FileOutputStream out = null;
        if (!browser.getWriteFile(files)) {
            System.err.println("warning message: Save path is not correct");
            return;
        }

        try {
            out = new FileOutputStream(files[0]);
            byte[] buffer = Encoder.encodeSiteInfoRecord(site);
            out.write(buffer);
            out.close();
            out = null;
        } catch (Exception e) {
            if (out != null) try {
                out.close();
            } catch (Exception e1) {
            }
            e.printStackTrace(System.err);
        }
    }

    protected void loadFromNA() {
        String na = JOptionPane.showInputDialog(this, "Load NA Site Info");
        if (na == null) return;
        try {
            String s[] = { "HS_SITE" };
            HandleValue v[] = (new HandleResolver()).resolveHandle(na, s, null);
            if (v == null || v.length == 0) {
                throw new Exception("HS_SITE not found.");
            }
            SiteInfo site = new SiteInfo();
            Encoder.decodeSiteInfoRecord(v[0].getData(), 0, site);
            setSiteInfo(site);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Can't load NA " + na + ": " + e, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected void loadFromFile() {
        BrowsePanel browser = new BrowsePanel("Open Path: ", (File) null, "", null, false);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, browser, "Load Site info :", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;

        File[] files = new File[1];
        if (!browser.getReadFile(files)) {
            System.err.println("warning message: Load path is not correct");
            return;
        }

        FileInputStream in = null;
        try {
            in = new FileInputStream(files[0]);
            byte[] buffer = new byte[(int) files[0].length()];
            int n = 0;
            int r = 0;
            while (n < buffer.length && ((r = in.read(buffer, n, buffer.length - n)) >= 0))
                n += r;
            in.close();
            SiteInfo site = new SiteInfo();
            if (Util.looksLikeBinary(buffer)) {
                Encoder.decodeSiteInfoRecord(buffer, 0, site);
            } else {
                site = SiteInfoConverter.convertToSiteInfo(new String(buffer, "UTF-8"));
            }
            setSiteInfo(site);
        } catch (Exception e) {
            if (in != null) try {
                in.close();
            } catch (Exception e1) {
            }
            e.printStackTrace(System.err);
        }
    }

    class ServerList extends DataListJPanel {
        ServerList() {
            super();
            int x = 0;
            int y = 0;
            GridBagLayout gridbag = new GridBagLayout();
            buttonPanel = new JPanel(gridbag);

            if (editFlag) {
                buttonPanel.add(addItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
                buttonPanel.add(editItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
                buttonPanel.add(remItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
                buttonPanel.add(clearButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            }
            buttonPanel.add(viewItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, editFlag));
            this.add(pane, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, true, true));
            this.add(buttonPanel, AwtUtil.getConstraints(2, 0, 1, 1, 1, 8, true, true));

        }

        @Override
        protected Object addData() {
            ServerInfoJPanel p = new ServerInfoJPanel(true);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Add Server: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            return p.getServerInfo();
        }

        @Override
        protected Object modifyData(int ind) {
            ServerInfo server = (ServerInfo) this.items.elementAt(ind);

            ServerInfoJPanel p = new ServerInfoJPanel(true);
            p.setServerInfo(server);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Modify Server: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            return p.getServerInfo();
        }

        @Override
        protected boolean removeData(int ind) {
            return true;
        }

        @Override
        protected void viewData(int ind) {
            ServerInfo server = (ServerInfo) this.items.elementAt(ind);
            ServerInfoJPanel p = new ServerInfoJPanel(false);
            p.setServerInfo(server);
            JOptionPane.showMessageDialog(this, p, "View Server: ", JOptionPane.PLAIN_MESSAGE);
        }
    }

}

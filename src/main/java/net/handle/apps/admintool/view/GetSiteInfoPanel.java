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
import net.handle.apps.simple.SiteInfoConverter;
import net.handle.awt.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

public class GetSiteInfoPanel extends JPanel implements ActionListener {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;

    private JRadioButton byPreviousChoice;
    private final JRadioButton byAddressChoice;
    private final JRadioButton bySiteChoice;
    private final JRadioButton byHandleChoice;
    private JRadioButton byNoneChoice;
    private final JTextField hostField;
    private final JTextField portField;
    private final JButton siteFileButton;
    private final JTextField siteFileField;
    private final JTextField handleField;
    private final JTextField indexField;
    private final JCheckBox usePrimaryCheckBox;

    private SiteInfo previousSite;

    private JLabel siteFileLabel;
    private JLabel hostLabel;
    private JLabel portLabel;
    private JLabel handleLabel;
    private JLabel indexLabel;
    private JLabel previousSiteLabel;

    private JCheckBox doNotReferCheckBox;

    public GetSiteInfoPanel(AdminToolUI ui, String description) {
        this(ui, description, false);
    }

    public GetSiteInfoPanel(AdminToolUI ui, String description, boolean forSpecificSite) {
        this.ui = ui;
        if (forSpecificSite) {
            previousSite = ui.getSpecificSite();
            if (previousSite != null) {
                byPreviousChoice = new JRadioButton(ui.getStr("use_previous_siteinfo") + ": ");
                previousSiteLabel = new JLabel(displaySiteInfo(previousSite));
            }
            byNoneChoice = new JRadioButton(ui.getStr("no_specific_site"));
        }

        byAddressChoice = new JRadioButton(ui.getStr("by_address"));
        hostField = new JTextField("127.0.0.1");
        portField = new JTextField("2641");

        bySiteChoice = new JRadioButton(ui.getStr("by_siteinfo"));
        siteFileButton = new JButton(ui.getStr("choose_file"));
        siteFileField = new JTextField("");

        byHandleChoice = new JRadioButton(ui.getStr("by_handle"));
        handleField = new JTextField("");
        indexField = new JTextField("");
        usePrimaryCheckBox = new JCheckBox(ui.getStr("use_primary_from_handle"), true);

        ButtonGroup byGroup = new ButtonGroup();
        byGroup.add(byAddressChoice);
        byGroup.add(bySiteChoice);
        byGroup.add(byHandleChoice);
        if (byPreviousChoice != null) byGroup.add(byPreviousChoice);
        if (byNoneChoice != null) byGroup.add(byNoneChoice);

        if (forSpecificSite) {
            doNotReferCheckBox = new JCheckBox(ui.getStr("do_not_refer"), ui.getSpecificSiteDoNotRefer());
        }

        super.setLayout(new GridBagLayout());
        JPanel p1;
        int y = 0;
        if (description != null) add(new JLabel("<html>" + description + "</html>"), GridC.getc(0, y++).wx(1).fillboth().insets(15, 15, 5, 15));

        if (byPreviousChoice != null) {
            p1 = new JPanel(new GridBagLayout());
            p1.setBorder(new EtchedBorder());
            p1.add(byPreviousChoice, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
            p1.add(previousSiteLabel, GridC.getc(0, 1).insets(5, 5, 5, 15).fillboth());
            add(p1, GridC.getc(0, y++).wx(1).insets(15, 15, 15, 15).fillboth());
        }

        p1 = new JPanel(new GridBagLayout());
        p1.setBorder(new EtchedBorder());
        p1.add(bySiteChoice, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
        p1.add(siteFileLabel = new JLabel(ui.getStr("siteinfo_file") + ":"), GridC.getc(0, 1).insets(5, 5, 5, 5).fillboth());
        p1.add(siteFileField, GridC.getc(1, 1).wx(1).insets(5, 5, 5, 5).fillboth());
        p1.add(Box.createHorizontalStrut(80), GridC.getc(1, 1).wx(1).insets(5, 5, 5, 5).fillboth());
        p1.add(siteFileButton, GridC.getc(2, 1).insets(5, 5, 5, 5).fillboth());
        add(p1, GridC.getc(0, y++).wx(1).insets(15, 15, 15, 15).fillboth());

        p1 = new JPanel(new GridBagLayout());
        p1.setBorder(new EtchedBorder());
        p1.add(byAddressChoice, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
        p1.add(hostLabel = new JLabel(ui.getStr("server_address") + ":"), GridC.getc(0, 1).insets(5, 5, 5, 5).fillboth());
        p1.add(hostField, GridC.getc(1, 1).wx(1).insets(5, 5, 5, 5).fillboth());
        p1.add(portLabel = new JLabel(ui.getStr("server_port") + ":"), GridC.getc(0, 2).insets(5, 5, 5, 5).fillboth());
        p1.add(portField, GridC.getc(1, 2).wx(1).insets(5, 5, 5, 5).fillboth());
        add(p1, GridC.getc(0, y++).wx(1).insets(15, 15, 15, 15).fillboth());

        p1 = new JPanel(new GridBagLayout());
        p1.setBorder(new EtchedBorder());
        p1.add(byHandleChoice, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
        p1.add(handleLabel = new JLabel(ui.getStr("handle") + ":"), GridC.getc(0, 1).insets(5, 5, 5, 5).fillboth());
        p1.add(handleField, GridC.getc(1, 1).wx(1).insets(5, 5, 5, 5).fillboth());
        p1.add(usePrimaryCheckBox, GridC.getc(0, 2).wx(1).colspan(3).insets(0, 0, 0, 0).fillboth());
        p1.add(indexLabel = new JLabel(ui.getStr("index") + ":"), GridC.getc(0, 3).insets(5, 5, 5, 5).fillboth());
        p1.add(indexField, GridC.getc(1, 3).wx(1).insets(5, 5, 5, 5).fillboth());
        add(p1, GridC.getc(0, y++).wx(1).insets(15, 15, 15, 15).fillboth());

        add(Box.createHorizontalStrut(12), GridC.getc(0, y++).wy(1));

        if (byNoneChoice != null) {
            p1 = new JPanel(new GridBagLayout());
            p1.setBorder(new EtchedBorder());
            p1.add(byNoneChoice, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
            add(p1, GridC.getc(0, y++).wx(1).insets(15, 15, 15, 15).fillboth());
        }

        if (doNotReferCheckBox != null) {
            p1 = new JPanel(new GridBagLayout());
            p1.add(doNotReferCheckBox, GridC.getc(0, 0).wx(1).colspan(3).insets(5, 5, 5, 5).west());
            add(p1, GridC.getc(0, y++).wx(1).insets(0, 15, 0, 15).fillboth());
        }

        byAddressChoice.addActionListener(this);
        bySiteChoice.addActionListener(this);
        siteFileButton.addActionListener(this);
        byHandleChoice.addActionListener(this);
        usePrimaryCheckBox.addActionListener(this);
        if (byPreviousChoice != null) byPreviousChoice.addActionListener(this);
        if (byNoneChoice != null) byNoneChoice.addActionListener(this);

        if (byPreviousChoice != null) byPreviousChoice.setSelected(true);
        else bySiteChoice.setSelected(true);
        enableAppropriateFields();
    }

    private String displaySiteInfo(SiteInfo site) {
        // Change servers[] into comma-and-space-separated string
        String servList = "";
        if (site.servers != null) {
            servList = servList + site.servers[0];
            for (int i = 1; i < site.servers.length; i++)
                servList += ",<br>" + site.servers[i];
        }

        return "<html>version: " + site.majorProtocolVersion + '.' + site.minorProtocolVersion + "; serial:" + site.serialNumber + "; primary:" + (site.isPrimary ? "y; " : "n; ") + "servers=[" + servList + "]</html>";
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == byPreviousChoice || src == byNoneChoice || src == byAddressChoice || src == bySiteChoice || src == byHandleChoice || src == usePrimaryCheckBox) {
            enableAppropriateFields();
        } else if (src == siteFileButton) {
            FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), ui.getStr("choose_file"), FileDialog.LOAD);
            String currentFile = siteFileField.getText();
            if (currentFile != null && currentFile.trim().length() > 0) {
                try {
                    File f = new File(currentFile);
                    fwin.setDirectory(f.getParent());
                    fwin.setFile(f.getName());
                } catch (Exception e) {
                }
            }
            fwin.setVisible(true);

            String fileStr = fwin.getFile();
            String dirStr = fwin.getDirectory();
            if (fileStr != null && dirStr != null) {
                siteFileField.setText(new File(dirStr, fileStr).getPath());
            }
        }
    }

    /** Load/retrieve/parse the siteinfo for the site where the home-prefix
     * message will be sent.  Displays an error and returns null if there
     * was a problem. */
    public SiteInfo getSiteInfo() {
        if (byPreviousChoice != null && byPreviousChoice.isSelected()) return previousSite;
        else if (byNoneChoice != null && byNoneChoice.isSelected()) return null;
        else if (byAddressChoice.isSelected()) {
            return getManualSiteInfo();
        } else if (byHandleChoice.isSelected()) {
            Exception error = null;
            try {
                HandleResolver resolver = ui.getMain().getResolver();
                boolean usePrimary = usePrimaryCheckBox.isSelected();
                SiteInfo[] sites;
                if (usePrimary) {
                    // check HS_SERV also
                    sites = resolver.findLocalSitesForNA(Util.encodeString(handleField.getText().trim()));
                } else {
                    int[] indexes = new int[] { Integer.parseInt(indexField.getText().trim()) };
                    HandleValue[] values = resolver.resolveHandle(handleField.getText().trim(), null, indexes);
                    sites = Util.getSitesAndAltSitesFromValues(values);
                }
                if (sites != null && sites.length > 0) {
                    if (usePrimary) {
                        for (SiteInfo site : sites) {
                            if (site.isPrimary) return site;
                        }
                    } else {
                        return sites[0];
                    }
                }
            } catch (Exception e) {
                error = e;
            }

            if (error != null) {
                JOptionPane.showMessageDialog(this, ui.getStr("error_retrieve_siteinfo") + "\n\n" + error, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, ui.getStr("error_retrieve_siteinfo"), ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
            }
            return null;
        } else if (bySiteChoice.isSelected()) {
            SiteInfo siteinfo = new SiteInfo();
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte buf[] = new byte[2048];
                FileInputStream fin = new FileInputStream(siteFileField.getText());
                try {
                    int r;
                    while ((r = fin.read(buf)) >= 0)
                        bout.write(buf, 0, r);
                } finally {
                    fin.close();
                }
                byte[] data = bout.toByteArray();
                if (Util.looksLikeBinary(data)) {
                    Encoder.decodeSiteInfoRecord(data, 0, siteinfo);
                } else {
                    siteinfo = SiteInfoConverter.convertToSiteInfo(new String(data, "UTF-8"));
                }
                return siteinfo;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, ui.getStr("error_load_siteinfo") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } else return null;
    }

    public boolean getDoNotRefer() {
        if (doNotReferCheckBox == null) return false;
        return doNotReferCheckBox.isSelected();
    }

    /** Retrieve the siteinfo that describes the site where the checkpoint message
     *  will be sent.  Displays an error and returns null if there was a problem. */
    public SiteInfo getManualSiteInfo() {
        return ui.getSiteInfoFromHost(hostField.getText().trim(), Integer.parseInt(portField.getText().trim()));
    }

    public void enableAppropriateFields() {
        boolean bySite = bySiteChoice.isSelected();
        boolean byAddress = byAddressChoice.isSelected();
        boolean byHandle = byHandleChoice.isSelected();
        boolean usePrimary = usePrimaryCheckBox.isSelected();
        hostLabel.setEnabled(byAddress);
        hostField.setEnabled(byAddress);
        portLabel.setEnabled(byAddress);
        portField.setEnabled(byAddress);

        siteFileLabel.setEnabled(bySite);
        siteFileButton.setEnabled(bySite);
        siteFileField.setEnabled(bySite);

        handleField.setEnabled(byHandle);
        handleLabel.setEnabled(byHandle);
        usePrimaryCheckBox.setEnabled(byHandle);
        indexField.setEnabled(byHandle && !usePrimary);
        indexLabel.setEnabled(byHandle && !usePrimary);
    }

}

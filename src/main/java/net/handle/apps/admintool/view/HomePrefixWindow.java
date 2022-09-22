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
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class HomePrefixWindow extends JFrame implements ActionListener {
    private final AdminToolUI ui;

    private boolean home;

    private final JRadioButton homeChoice;
    private final JRadioButton unhomeChoice;

    private final JTextField prefixField;

    private final GetSiteInfoPanel getSiteInfoPanel;

    private final JButton goButton;

    public HomePrefixWindow(AdminToolUI ui) {
        super(ui.getStr("home_unhome_prefix"));
        this.ui = ui;
        setJMenuBar(ui.getAppMenu());

        homeChoice = new JRadioButton(ui.getStr("home_prefix"));
        unhomeChoice = new JRadioButton(ui.getStr("unhome_prefix"));
        ButtonGroup huGroup = new ButtonGroup();
        huGroup.add(homeChoice);
        huGroup.add(unhomeChoice);

        prefixField = new JTextField("");

        goButton = new JButton(ui.getStr("do_it"));

        JPanel p = new JPanel(new GridBagLayout());
        JPanel p1;
        int y = 0;

        p.add(new JLabel("<html>" + ui.getStr("home_unhome_desc") + "</html>"), AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(15, 15, 5, 15), true, true));

        p1 = new JPanel(new GridBagLayout());
        p1.add(new JLabel(ui.getStr("prefix") + ": "), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), false, false));
        p1.add(prefixField, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        p.add(p1, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(5, 5, 0, 5), true, true));

        p1 = new JPanel(new GridBagLayout());
        p1.add(homeChoice, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), false, false));
        p1.add(unhomeChoice, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), false, false));
        p.add(p1, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(5, 5, 0, 5), true, true));

        getSiteInfoPanel = new GetSiteInfoPanel(ui, null);

        p.add(getSiteInfoPanel, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(15, 15, 15, 15), true, true));

        p1 = new JPanel(new GridBagLayout());
        p1.add(Box.createHorizontalStrut(250), AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
        p1.add(goButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, false, false));
        p.add(p1, AwtUtil.getConstraints(0, y++, 1, 1, 1, 1, new Insets(0, 15, 15, 15), true, true));

        getContentPane().add(p);

        goButton.addActionListener(this);

        homeChoice.addActionListener(this);
        unhomeChoice.addActionListener(this);

        homeChoice.setSelected(true);
        home = true;
        getRootPane().setDefaultButton(goButton);

        pack();
        setSize(new Dimension(400, getPreferredSize().height + 100));
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == goButton) {
            doit();
        } else if (src == homeChoice) {
            home = true;
        } else if (src == unhomeChoice) {
            home = false;
        }
    }

    public void doit() {
        String prefix = prefixField.getText().trim();
        byte naHandle[] = Util.upperCaseInPlace(Util.encodeString(prefix));
        if (!Util.hasSlash(naHandle)) naHandle = Util.convertSlashlessHandleToZeroNaHandle(naHandle);

        SiteInfo site = getSiteInfoPanel.getSiteInfo();
        if (site == null) return;

        try {
            if (!site.isPrimary) {
                throw new Exception("Given server is not a primary server.");
            }

            AuthenticationInfo authInfo = ui.getAuthentication(false);
            if (authInfo == null) return;

            GenericRequest homeNAReq = new GenericRequest(naHandle, home ? AbstractMessage.OC_HOME_NA : AbstractMessage.OC_UNHOME_NA, authInfo);

            homeNAReq.majorProtocolVersion = 2;
            homeNAReq.minorProtocolVersion = 0;
            homeNAReq.isAdminRequest = true;

            homeNAReq.certify = true;
            for (int i = 0; i < site.servers.length; i++) {
                AbstractResponse response = ui.getMain().getResolver().sendRequestToServer(homeNAReq, site, site.servers[i]);
                if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                    throw new Exception(String.valueOf(response));
                }
            }

            JOptionPane.showMessageDialog(this, ui.getStr("success_home_unhome"), ui.getStr("success_title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, ui.getStr("error_home_unhome") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}

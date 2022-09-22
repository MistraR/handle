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

public class CheckpointWindow extends JFrame implements ActionListener {
    private final AdminToolUI ui;

    private final GetSiteInfoPanel getSiteInfoPanel;

    private final JButton goButton;

    public CheckpointWindow(AdminToolUI ui) {
        super(ui.getStr("checkpoint_server"));
        this.ui = ui;
        setJMenuBar(ui.getAppMenu());

        goButton = new JButton(ui.getStr("do_it"));

        JPanel p = new JPanel(new GridBagLayout());
        int y = 0;

        getSiteInfoPanel = new GetSiteInfoPanel(ui, ui.getStr("checkpoint_desc"));
        p.add(getSiteInfoPanel, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(15, 15, 15, 15), true, true));

        JPanel p1 = new JPanel(new GridBagLayout());
        p1.add(Box.createHorizontalStrut(250), AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
        p1.add(goButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, false, false));
        p.add(p1, AwtUtil.getConstraints(0, y++, 1, 1, 1, 1, new Insets(0, 15, 15, 15), true, true));

        getContentPane().add(p);

        goButton.addActionListener(this);
        getRootPane().setDefaultButton(goButton);

        pack();
        setSize(new Dimension(400, getPreferredSize().height + 80));
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == goButton) {
            doit();
        }
    }

    public void doit() {
        SiteInfo site = getSiteInfoPanel.getSiteInfo();
        if (site == null) return;

        try {

            // ask which server we should be backed up, if not all?
            int serverToBackup = -1; // -1 means backup all servers
            if (site.servers.length > 1) {

                String choices[] = new String[site.servers.length + 1];
                choices[0] = ui.getStr("all_servers");
                for (int i = 0; i < site.servers.length; i++) {
                    choices[i + 1] = ui.getStr("server") + " " + (i + 1) + " (" + site.servers[i] + ")";
                }

                Object selected = JOptionPane.showInputDialog(this, ui.getStr("select_server_to_checkpoint"), ui.getStr("select_server_title"), JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
                if (selected == null) return;

                for (int i = 0; i < choices.length; i++) {
                    if (selected.equals(choices[i])) {
                        serverToBackup = i - 1;
                        break;
                    }
                }
            }

            AuthenticationInfo authInfo = ui.getAuthentication(false);
            if (authInfo == null) return;

            GenericRequest checkpointReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_BACKUP_SERVER, authInfo);

            //checkpointReq.majorProtocolVersion = 2;
            //checkpointReq.minorProtocolVersion = 0;
            checkpointReq.isAdminRequest = true;

            checkpointReq.certify = true;
            for (int i = 0; i < site.servers.length; i++) {
                if (serverToBackup >= 0 && serverToBackup != i) continue;

                checkpointReq.clearBuffers();
                AbstractResponse response = ui.getMain().getResolver().sendRequestToServer(checkpointReq, site, site.servers[i]);
                if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                    throw new Exception(String.valueOf(response));
                }
            }

            JOptionPane.showMessageDialog(this, ui.getStr("success_checkpoint"), ui.getStr("success_title"), JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, ui.getStr("error_checkpoint") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}

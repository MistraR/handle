/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jutil.*;
import net.handle.awt.*;
import net.handle.hdllib.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class BackupServerWindow extends JDialog implements ActionListener {
    private final JTextField addressField;
    private final JTextField portField;
    private final JButton okButton;
    private final JButton cancelButton;
    private final JComboBox protocolChoice;
    private final JButton helpButton;

    private final HandleTool tool;

    public BackupServerWindow(HandleTool tool) {
        super(tool, "Backup Server Window", false);
        this.tool = tool;

        addressField = new JTextField("Your server address");
        addressField.setScrollOffset(0);
        portField = new JTextField("2641", 6);

        protocolChoice = new JComboBox();
        protocolChoice.addItem("UDP");
        protocolChoice.addItem("TCP");
        protocolChoice.addItem("HTTP");
        protocolChoice.setSelectedIndex(1);
        okButton = new JButton("Ok");
        okButton.addActionListener(this);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);

        helpButton = new JButton("Help");
        helpButton.addActionListener(this);

        int x = 0;
        int y = 0;
        JPanel p = new JPanel(new GridBagLayout());
        p.add(helpButton, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, false, false));
        p.add(new JLabel("Primary server address: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(addressField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        p.add(new JLabel("Primary server port: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(portField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        p.add(new JLabel("Interface/Protocol: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(protocolChoice, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.add(okButton, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, false, false));
        buttonPanel.add(cancelButton, AwtUtil.getConstraints(1, 0, 1, 1, 1, 1, false, false));
        p.add(buttonPanel, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, true, true));

        getContentPane().add(p);

        pack();
        setSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, tool);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == okButton) {
            if (backupServer()) {
                setVisible(false);
            }
        } else if (src == cancelButton) {
            setVisible(false);
        } else if (src == helpButton) {
            HelpPanel.show(tool, CommonDef.HELP_DIR, CommonDef.HELP_BACKUP);
        }
    }

    @Override
    public void setVisible(boolean val) {
        super.setVisible(val);
    }

    /** Set backup request to server. If we can't, return false. */
    private boolean backupServer() {
        try {
            InetAddress svrAddr = InetAddress.getByName(addressField.getText().trim());
            int svrPort = Integer.parseInt(portField.getText().trim());

            // retrieve the site information...
            GenericRequest siReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);

            AbstractResponse response = null;
            switch (protocolChoice.getSelectedIndex()) {
            case 0:
                response = tool.resolver.sendHdlUdpRequest(siReq, svrAddr, svrPort);
                break;
            case 1:
                response = tool.resolver.sendHdlTcpRequest(siReq, svrAddr, svrPort);
                break;
            case 2:
                response = tool.resolver.sendHttpRequest(siReq, svrAddr, svrPort);
                break;
            default:
                throw new Exception("No protocol selected");
            }

            SiteInfo siteInfo = null;
            if (response != null && response.responseCode == AbstractMessage.RC_SUCCESS) {
                siteInfo = ((GetSiteInfoResponse) response).siteInfo;
            } else {
                throw new Exception("Unable to retrieve site information from server.");
            }

            if (!siteInfo.isPrimary) {
                throw new Exception("Given server is not a primary server.");
            }

            AuthenticationInfo authInfo = tool.getAuthentication();
            if (authInfo == null) return false;

            GenericRequest backupReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_BACKUP_SERVER, authInfo);

            //set request protocol version
            if ((siteInfo.majorProtocolVersion == 5 && siteInfo.minorProtocolVersion == 0) || (siteInfo.majorProtocolVersion < Common.MAJOR_VERSION)
                || (siteInfo.majorProtocolVersion == Common.MAJOR_VERSION && siteInfo.minorProtocolVersion < Common.MINOR_VERSION)) {
                //older version server
                backupReq.majorProtocolVersion = siteInfo.majorProtocolVersion;
                backupReq.minorProtocolVersion = siteInfo.minorProtocolVersion;
            } else {
                //current version server
                backupReq.majorProtocolVersion = Common.MAJOR_VERSION;
                backupReq.minorProtocolVersion = Common.MINOR_VERSION;
            }
            backupReq.certify = true;

            String respStr = null;
            response = null;
            for (int i = 0; i < siteInfo.servers.length; i++)
                if (siteInfo.servers[i].getInetAddress().equals(svrAddr)) {
                    for (int j = 0; j < siteInfo.servers[i].interfaces.length; j++) {
                        Interface interf = siteInfo.servers[i].interfaces[j];
                        if (interf.port == svrPort && interf.protocol == (byte) protocolChoice.getSelectedIndex()) {
                            response = tool.processRequest(this, backupReq, siteInfo.servers[i], "Sending server backup request...");
                            if (response == null) {
                                respStr = "There is no response for the server backup request";
                            } else if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                                JOptionPane.showMessageDialog(this, "Success, server is doing backup\n", "Info", JOptionPane.INFORMATION_MESSAGE);
                                return true;
                            } else if (response instanceof ErrorResponse) respStr = String.valueOf(response);
                            else respStr = response.getClass().getName() + "( " + String.valueOf(response.responseCode) + "): " + AbstractMessage.getResponseCodeMessage(response.responseCode);

                            JOptionPane.showMessageDialog(this, "Response: Server can not process the backup request\n" + respStr, "Error", JOptionPane.ERROR_MESSAGE);

                            return false;
                        }
                    }
                }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Catch exception at backup server:\n" + e.getMessage() + "\nTry again please", "Error", JOptionPane.ERROR_MESSAGE);
        }

        return false;
    }
}

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
public class UnhomeNAWindow extends JDialog implements ActionListener {

    private final JTextField naField;
    private final JTextField addressField;
    private final JTextField portField;
    private final JButton okButton;
    private final JButton cancelButton;
    private final JComboBox protocolChoice;
    private final JButton changeAuthButton;
    private final JButton helpButton;

    private final HandleTool tool;

    public UnhomeNAWindow(HandleTool tool) {
        super(tool, "Unhome Prefix", false);

        this.tool = tool;

        naField = new JTextField("0.NA/YOUR_PREFIX", 20);
        naField.setScrollOffset(0);
        addressField = new JTextField("Your server address");
        addressField.setScrollOffset(0);
        portField = new JTextField("2641", 6);
        okButton = new JButton("Ok");
        cancelButton = new JButton("Cancel");
        changeAuthButton = new JButton("Authentication");
        helpButton = new JButton("Help");

        protocolChoice = new JComboBox();

        protocolChoice.addItem("TCP");
        protocolChoice.addItem("UDP");
        protocolChoice.addItem("HTTP");

        int x = 0;
        int y = 0;

        JPanel p = new JPanel(new GridBagLayout());
        p.add(helpButton, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, false, false));
        p.add(changeAuthButton, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, false, false));
        p.add(new JLabel("Primary server address: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(addressField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        p.add(new JLabel("Primary server port: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(portField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        p.add(new JLabel("Prefix Handle: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(naField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        p.add(new JLabel("Interface/Protocol: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 1, 1, 1, true, false));
        p.add(protocolChoice, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        buttonPanel.add(okButton, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, false, false));
        buttonPanel.add(cancelButton, AwtUtil.getConstraints(1, 0, 1, 1, 1, 1, false, false));
        p.add(buttonPanel, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, true, true));

        getContentPane().add(p);

        changeAuthButton.addActionListener(this);
        okButton.addActionListener(this);
        cancelButton.addActionListener(this);
        helpButton.addActionListener(this);

        pack();
        setSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, tool);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == okButton) {
            if (unhomeNA()) {
                setVisible(false);
            }
        } else if (src == cancelButton) {
            setVisible(false);
        } else if (src == changeAuthButton) {
            tool.changeAuthentication();
        } else if (src == helpButton) {
            HelpPanel.show(tool, CommonDef.HELP_DIR, CommonDef.HELP_UNHOME_NA);
        }
    }

    @Override
    public void setVisible(boolean val) {
        super.setVisible(val);
    }

    /** Unhome the prefix at the specified server.  If we can't,
      return false. */
    private boolean unhomeNA() {
        try {
            InetAddress svrAddr = InetAddress.getByName(addressField.getText().trim());
            int svrPort = Integer.parseInt(portField.getText().trim());
            byte naHandle[] = Util.encodeString(naField.getText().trim());
            if (!Util.hasSlash(naHandle)) naHandle = Util.convertSlashlessHandleToZeroNaHandle(naHandle);

            // retrieve the site information...
            GenericRequest siReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
            //      siReq.certify = true;
            AbstractResponse response = null;
            try {
                //        tool.resolver.setCheckSignatures(false);
                switch (protocolChoice.getSelectedIndex()) {
                case 0:
                    response = tool.resolver.sendHdlTcpRequest(siReq, svrAddr, svrPort);
                    break;
                case 1:
                    response = tool.resolver.sendHdlUdpRequest(siReq, svrAddr, svrPort);
                    break;
                case 2:
                    response = tool.resolver.sendHttpRequest(siReq, svrAddr, svrPort);
                    break;
                default:
                    throw new Exception("No protocol selected");
                }
            } finally {
                tool.resolver.setCheckSignatures(true);
            }

            SiteInfo siteInfo = null;
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                siteInfo = ((GetSiteInfoResponse) response).siteInfo;
            } else {
                throw new Exception("Unable to retrieve site information from server");
            }

            if (!siteInfo.isPrimary) {
                throw new Exception("Given server is not a primary server.");
            }

            AuthenticationInfo authInfo = tool.getAuthentication();
            if (authInfo == null) return false;
            GenericRequest unhomeNAReq = new GenericRequest(naHandle, AbstractMessage.OC_UNHOME_NA, authInfo);
            unhomeNAReq.isAdminRequest = true;

            unhomeNAReq.certify = true;
            for (int i = 0; i < siteInfo.servers.length; i++) {
                response = tool.resolver.sendRequestToServer(unhomeNAReq, siteInfo, siteInfo.servers[i]);
                if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                    throw new Exception(String.valueOf(response));
                }
            }

            GenericDialog.askQuestion("Success", "The prefix was unhomed on all servers\n" + "within the given site.", GenericDialog.QUESTION_OK, this);
            return true;
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            GenericDialog.askQuestion("Error", "Error: " + e + "\nPlease try again\n" + sw.toString(), GenericDialog.QUESTION_OK, this);
        }

        return false;
    }

}

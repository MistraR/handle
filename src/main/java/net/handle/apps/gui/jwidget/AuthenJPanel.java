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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.security.*;

/******************************************************************************\
 *  Class to provide authentication panel
 *
 ******************************************************************************/
@SuppressWarnings({"rawtypes", "unchecked"})
public class AuthenJPanel extends JPanel implements ItemListener {

    protected JComboBox authTypeChoice;
    protected SecretKeyJPanel secretKeyPanel;
    protected PublicKeyJPanel publicKeyPanel;
    protected JPanel detailPanel;
    protected CardLayout detailLayout;
    protected final String SECRET = "Secret Key";
    protected final String PUBLIC = "Public Key";
    protected HandleValue[] values = null;

    public AuthenJPanel() {
        super(new GridBagLayout());

        authTypeChoice = new JComboBox();
        authTypeChoice.setToolTipText("Choose authentication type");
        authTypeChoice.setEditable(false);
        authTypeChoice.addItem(PUBLIC);
        authTypeChoice.addItem(SECRET);
        authTypeChoice.addItemListener(this);

        secretKeyPanel = new SecretKeyJPanel();
        publicKeyPanel = new PublicKeyJPanel();

        detailPanel = new JPanel();
        detailLayout = new CardLayout();
        detailPanel.setLayout(detailLayout);
        detailPanel.add(publicKeyPanel, PUBLIC);
        detailPanel.add(secretKeyPanel, SECRET);

        add(new JLabel("  Authentication Type: ", Label.RIGHT), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, true, true));
        add(authTypeChoice, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, false, false));
        add(detailPanel, AwtUtil.getConstraints(0, 1, 1, 1, 2, 1, true, true));

    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        Object src = evt.getSource();
        if (src == authTypeChoice) {
            authTypeSelected();
        }
    }

    protected void authTypeSelected() {
        detailLayout.show(detailPanel, (String) authTypeChoice.getSelectedItem());
    }

    public void setAuthInfo(AuthenticationInfo authInfo) {
        if (authInfo == null) return;
        if (authInfo instanceof SecretKeyAuthenticationInfo) {
            SecretKeyAuthenticationInfo secAuth = (SecretKeyAuthenticationInfo) authInfo;
            authTypeChoice.setSelectedItem(SECRET);
            authTypeSelected();
            secretKeyPanel.setUserIdHandle(Util.decodeString(secAuth.getUserIdHandle()));
            secretKeyPanel.setUserIdIndex(secAuth.getUserIdIndex());

        } else if (authInfo instanceof PublicKeyAuthenticationInfo) {
            PublicKeyAuthenticationInfo pubAuth = (PublicKeyAuthenticationInfo) authInfo;
            authTypeChoice.setSelectedItem(PUBLIC);
            authTypeSelected();
            publicKeyPanel.setUserIdHandle(Util.decodeString(pubAuth.getUserIdHandle()));
            publicKeyPanel.setUserIdIndex(pubAuth.getUserIdIndex());

        }
    }

    public AuthenticationInfo getAuthInfo() {
        if (((String) authTypeChoice.getSelectedItem()).equals(SECRET)) {
            try {
                String hdl = secretKeyPanel.getUserIdHandle();
                byte userIdHandle[] = Util.encodeString(hdl);
                int userIdIndex = secretKeyPanel.getUserIdIndex();
                byte[] secretKey = Util.encodeString(new String(secretKeyPanel.getSecretKey()));
                HDLToolConfig.table.put("SecHandle", hdl);
                HDLToolConfig.table.put("SecIndex", String.valueOf(userIdIndex));
                HDLToolConfig.table.put("ShadowPass", secretKeyPanel.isHashedPasswordEnabled());
                HDLToolConfig.save();
                return new SecretKeyAuthenticationInfo(userIdHandle, userIdIndex, secretKey, secretKeyPanel.isHashedPasswordEnabled());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Warning: ", JOptionPane.WARNING_MESSAGE);
                return null;
            }
        } else if (((String) authTypeChoice.getSelectedItem()).equals(PUBLIC)) {
            try {
                String hdl = publicKeyPanel.getUserIdHandle();
                byte userIdHandle[] = Util.encodeString(hdl);
                int userIdIndex = publicKeyPanel.getUserIdIndex();
                PrivateKey privKey = publicKeyPanel.getPrivKey();
                if (privKey != null) {
                    HDLToolConfig.table.put("PubHandle", hdl);
                    HDLToolConfig.table.put("PubIndex", String.valueOf(userIdIndex));
                    HDLToolConfig.table.put("PrivKey", publicKeyPanel.getPrivKeyPath());
                    HDLToolConfig.save();
                    return new PublicKeyAuthenticationInfo(userIdHandle, userIdIndex, privKey);
                } else {
                    return null;
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Invalid input, got exception: " + e, "Warning: ", JOptionPane.WARNING_MESSAGE);
                return null;
            }
        }
        return null;
    }

    public PrivateKey getPrivKey() {
        return publicKeyPanel.getPrivKey();
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jutil.*;
import net.handle.apps.gui.jwidget.*;
import net.handle.awt.*;
import net.handle.hdllib.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class AdministMasterJPanel extends JPanel implements ActionListener {
    private final ButtonGroup bgroup;
    private final MyRadioButton seckeyButton;
    private final MyRadioButton pubkeyButton;
    private final MyRadioButton admgrpButton;

    private int preSelection = -1;
    private int index = -1;
    private HandleValue handlevalue = null;

    public AdministMasterJPanel(int index) {
        super(new GridBagLayout());
        this.index = index;

        JPanel p = new JPanel(new FlowLayout());
        seckeyButton = new MyRadioButton("Secret Key", "Admin Secret Key Reference");
        pubkeyButton = new MyRadioButton("Public Key", "Admin Public Key Reference");
        admgrpButton = new MyRadioButton("Admin Group", "Admin Group Reference");
        seckeyButton.addActionListener(this);
        pubkeyButton.addActionListener(this);
        admgrpButton.addActionListener(this);

        bgroup = new ButtonGroup();
        bgroup.add(seckeyButton);
        bgroup.add(pubkeyButton);
        bgroup.add(admgrpButton);

        p.add(seckeyButton);
        p.add(pubkeyButton);
        p.add(admgrpButton);

        int x = 0, y = 0;
        add(p, AwtUtil.getConstraints(x, y, 1, 1, 3, 1, false, false));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("Secret Key")) onSecretKey();
        else if (ae.getActionCommand().equals("Public Key")) onPublicKey();
        else if (ae.getActionCommand().equals("Admin Group")) onAdminGroup();
        else {
        }
    }

    public HandleValue getAdmReferValue() {
        return handlevalue;
    }

    private void onSecretKey() {
        SecretKeyDataJPanel p = new SecretKeyDataJPanel(Common.STD_TYPE_HSSECKEY, true, true);
        p.setIndex(index);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input secret key: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
            setPreSelection();
            return;
        }
        preSelection = 0;
        handlevalue = p.getHandleValue();
        handlevalue.setIndex(p.getIndex());
    }

    private void onPublicKey() {
        PubkeyDataJPanel p = new PubkeyDataJPanel(true, true);
        p.setIndex(index);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input public key: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
            setPreSelection();
            return;
        }
        preSelection = 1;
        handlevalue = p.getHandleValue();
        handlevalue.setIndex(p.getIndex());

    }

    private void onAdminGroup() {
        VListDataJPanel p = new VListDataJPanel(true, true);
        p.setIndex(index);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input administrator group: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
            setPreSelection();
            return;
        }

        preSelection = 2;
        handlevalue = p.getHandleValue();
        handlevalue.setIndex(p.getIndex());

    }

    @SuppressWarnings("incomplete-switch")
    private void setPreSelection() {

        switch (preSelection) {
        case 0:
            seckeyButton.setSelected(true);
            break;
        case 1:
            pubkeyButton.setSelected(true);
            break;
        case 2:
            admgrpButton.setSelected(true);
            break;
        case -1:
            seckeyButton.setSelected(false);
            pubkeyButton.setSelected(false);
            admgrpButton.setSelected(false);
            break;
        }
    }
}

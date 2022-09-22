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

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings("rawtypes")
public class ServerInfoJPanel extends JPanel {
    protected JTextField ipField;
    protected JTextField indField;
    protected PubkeyDataJPanel pubkeyPanel;
    protected InterfaceList dataList;

    protected ServerInfo server;
    protected boolean editFlag;

    public ServerInfoJPanel(boolean editFlag) {
        super(new GridBagLayout());
        this.editFlag = editFlag;

        ipField = new JTextField("", 20);
        ipField.setEditable(editFlag);

        indField = new JTextField("", 5);
        indField.setEditable(editFlag);

        pubkeyPanel = new PubkeyDataJPanel(false, editFlag);
        dataList = new InterfaceList();

        int x = 0;
        int y = 0;
        Insets insets = new Insets(2, 5, 2, 5);
        add(new JLabel(" IP address: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, true));
        add(ipField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, insets, GridBagConstraints.WEST, false, false));
        add(new JLabel(" ServerId: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, true));
        add(indField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, insets, GridBagConstraints.WEST, false, false));
        EtchedBorder etchBorder = new EtchedBorder();

        pubkeyPanel.setBorder(etchBorder);
        add(pubkeyPanel, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, insets, GridBagConstraints.WEST, true, true));

        dataList.setBorder(new TitledBorder(etchBorder, " Interfaces: "));
        add(dataList, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, true, true));
        server = new ServerInfo();
    }

    public ServerInfo getServerInfo() {
        try {
            server.serverId = getServerId();
            server.ipAddress = getByteIP();
            server.publicKey = pubkeyPanel.getValueData();
            Vector v = dataList.getItems();
            if (v != null) {
                Interface[] interf = new Interface[v.size()];
                for (int i = 0; i < v.size(); i++)
                    interf[i] = (Interface) v.elementAt(i);
                server.interfaces = interf;
            }
            return server;

        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    public void setServerInfo(ServerInfo serv) {
        if (serv == null) return;
        try {
            indField.setText(Integer.toString(serv.serverId));
            ipField.setText(serv.getAddressString());
            if (serv.interfaces != null) {
                for (int i = 0; i < serv.interfaces.length; i++)
                    dataList.appendItem(serv.interfaces[i]);
            }

            if (serv.publicKey != null) pubkeyPanel.setValueData(serv.publicKey);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    public void setIP(int ip) {
        ipField.setText(String.valueOf(ip));
    }

    public int getIP() {
        try {
            return Integer.parseInt(ipField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public String getIPStr() {
        return ipField.getText().trim();
    }

    public byte[] getByteIP() {
        return JUtil.getByteIP(getIPStr());

    }

    public int getServerId() {
        try {
            return Integer.parseInt(indField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public void setServerId(int ind) {
        indField.setText(String.valueOf(ind));
    }

    class InterfaceList extends DataListJPanel {
        InterfaceList() {
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
            this.add(buttonPanel, AwtUtil.getConstraints(2, 0, 1, 1, 1, 8, false, true));

        }

        @Override
        protected Object addData() {
            InterfaceJPanel p = new InterfaceJPanel(true);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Add Interface: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            return p.getInterface();
        }

        @Override
        protected Object modifyData(int ind) {
            Interface interf = (Interface) this.items.elementAt(ind);

            InterfaceJPanel p = new InterfaceJPanel(true);
            p.setInterface(interf);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Modify Interface: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            return p.getInterface();
        }

        @Override
        protected boolean removeData(int ind) {
            return true;
        }

        @Override
        protected void viewData(int ind) {
            Interface interf = (Interface) this.items.elementAt(ind);
            InterfaceJPanel p = new InterfaceJPanel(false);
            p.setInterface(interf);
            JOptionPane.showMessageDialog(this, p, "View Interface: ", JOptionPane.PLAIN_MESSAGE);
        }
    }
}

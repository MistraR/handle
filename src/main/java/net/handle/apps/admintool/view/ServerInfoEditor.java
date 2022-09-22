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
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.text.JTextComponent;

import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.util.*;

@SuppressWarnings("incomplete-switch")
public class ServerInfoEditor extends JPanel implements ActionListener {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;
    private final JTextField serverAddressField;
    private final JTextField serverIDField;
    private final JTextArea pubKeyField;
    private final JTable ifTable; // interface table
    private final InterfaceTableModel ifTableModel;
    private final JButton addIFButton;
    private final JButton removeIFButton;

    private final String capAdminStr;
    private final String capQueryStr;
    private final String capAdminQueryStr;
    private final String capOutOfServiceStr;
    private final String protoUDPStr;
    private final String protoTCPStr;
    private final String protoHTTPStr;
    private final String protoHTTPSStr;

    public ServerInfoEditor(AdminToolUI ui) {
        super(new GridBagLayout());
        this.ui = ui;

        serverAddressField = new JTextField("", 25);
        serverIDField = new JTextField("", 5);
        pubKeyField = new JTextArea(4, 25);
        ifTableModel = new InterfaceTableModel();
        ifTable = new JTable(ifTableModel);
        ifTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ifTable.createDefaultColumnsFromModel();
        addIFButton = new JButton(ui.getStr("add"));
        removeIFButton = new JButton(ui.getStr("remove"));

        capAdminStr = ui.getStr("ifc_admin");
        capQueryStr = ui.getStr("ifc_query");
        capAdminQueryStr = ui.getStr("ifc_query_admin");
        capOutOfServiceStr = ui.getStr("ifc_none");
        protoUDPStr = ui.getStr("proto_udp");
        protoTCPStr = ui.getStr("proto_tcp");
        protoHTTPStr = ui.getStr("proto_http");
        protoHTTPSStr = ui.getStr("proto_https");

        ifTable.setDefaultEditor(String.class, new DefaultCellEditor(new JComboBox<>(new String[] { protoUDPStr, protoTCPStr, protoHTTPStr, protoHTTPSStr, })));

        int y = 0;
        add(new JLabel(ui.getStr("server_address") + ":", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        add(serverAddressField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        add(new JLabel(ui.getStr("server_id"), SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        add(serverIDField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        add(new JLabel(ui.getStr("public_key"), SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        add(new JScrollPane(pubKeyField), AwtUtil.getConstraints(1, y++, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        add(new JLabel(ui.getStr("server_interfaces"), SwingConstants.LEFT), AwtUtil.getConstraints(0, y++, 0, 0, 1, 1, new Insets(5, 5, 5, 0), true, false));
        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JScrollPane(ifTable), AwtUtil.getConstraints(0, 0, 1, 1, 1, 4, true, true));
        p.add(addIFButton, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(0, 5, 5, 0), true, false));
        p.add(removeIFButton, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, new Insets(0, 5, 5, 0), true, false));
        add(p, AwtUtil.getConstraints(0, y++, 1, 1, 2, 1, new Insets(5, 5, 5, 5), true, true));
        addIFButton.addActionListener(this);
        removeIFButton.addActionListener(this);
    }

    private class InterfaceTableModel extends AbstractTableModel {
        @SuppressWarnings("unused")
        private final DefaultCellEditor capabilityEditor;
        @SuppressWarnings("unused")
        private final DefaultCellEditor protocolEditor;
        @SuppressWarnings("unused")
        private final DefaultCellEditor portEditor;
        private final ArrayList<Interface> ifcs = new ArrayList<>();

        InterfaceTableModel() {
            capabilityEditor = new DefaultCellEditor(new JComboBox<>(new String[] { capAdminQueryStr, capQueryStr, capAdminStr, capOutOfServiceStr }));
            protocolEditor = new DefaultCellEditor(new JComboBox<>(new String[] { protoUDPStr, protoTCPStr, protoHTTPStr, protoHTTPSStr }));
            portEditor = new DefaultCellEditor(new JTextField("", 5));
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
            case 0:
                return ui.getStr("ifc_admin");
            case 1:
                return ui.getStr("ifc_query");
            case 2:
                return ui.getStr("protocol");
            case 3:
                return ui.getStr("port");
            default:
                return "?";
            }
        }

        public synchronized void addInterface() {
            ifcs.add(new Interface());
            fireTableRowsInserted(ifcs.size(), ifcs.size());
        }

        public synchronized void removeInterface(int row) {
            if (row < 0 || row >= ifcs.size()) return;
            ifcs.remove(row);
            fireTableRowsDeleted(row, row);
        }

        public synchronized void setInterfaces(Interface newIfcs[]) {
            ifcs.clear();
            for (int i = 0; newIfcs != null && i < newIfcs.length; i++) {
                if (newIfcs[i] == null) continue;
                ifcs.add(newIfcs[i].cloneInterface());
            }
        }

        public synchronized Interface[] getInterfaces() {
            Interface ifcA[] = new Interface[ifcs.size()];
            for (int i = 0; i < ifcA.length; i++)
                ifcA[i] = ifcs.get(i).cloneInterface();
            return ifcA;
        }

        @Override
        public int getRowCount() {
            return ifcs.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }

        @Override
        public Object getValueAt(int row, int column) {
            if (row < 0 || row >= ifcs.size()) return null;
            Interface ifc = ifcs.get(row);
            switch (column) {
            case 0:
                return Boolean.valueOf(ifc.type == Interface.ST_ADMIN || ifc.type == Interface.ST_ADMIN_AND_QUERY);
            case 1:
                return Boolean.valueOf(ifc.type == Interface.ST_QUERY || ifc.type == Interface.ST_ADMIN_AND_QUERY);
            case 2:
                switch (ifc.protocol) {
                case Interface.SP_HDL_UDP:
                    return protoUDPStr;
                case Interface.SP_HDL_TCP:
                    return protoTCPStr;
                case Interface.SP_HDL_HTTPS:
                    return protoHTTPSStr;
                case Interface.SP_HDL_HTTP:
                default:
                    return protoHTTPStr;
                }
            case 3:
                return Integer.valueOf(ifc.port);
            default:
                return null;
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (row < 0 || row >= ifcs.size()) return;
            Interface ifc = ifcs.get(row);
            switch (column) {
            case 0:
                boolean bval = ((Boolean) value).booleanValue();
                switch (ifc.type) {
                case Interface.ST_OUT_OF_SERVICE:
                case Interface.ST_ADMIN:
                    ifc.type = bval ? Interface.ST_ADMIN : Interface.ST_OUT_OF_SERVICE;
                    break;
                case Interface.ST_QUERY:
                case Interface.ST_ADMIN_AND_QUERY:
                    ifc.type = bval ? Interface.ST_ADMIN_AND_QUERY : Interface.ST_QUERY;
                    break;
                }
                break;
            case 1:
                bval = ((Boolean) value).booleanValue();
                switch (ifc.type) {
                case Interface.ST_OUT_OF_SERVICE:
                case Interface.ST_QUERY:
                    ifc.type = bval ? Interface.ST_QUERY : Interface.ST_OUT_OF_SERVICE;
                    break;
                case Interface.ST_ADMIN:
                case Interface.ST_ADMIN_AND_QUERY:
                    ifc.type = bval ? Interface.ST_ADMIN_AND_QUERY : Interface.ST_ADMIN;
                    break;
                }
                break;
            case 2:
                String val = String.valueOf(value);
                if (val.equalsIgnoreCase(protoUDPStr)) ifc.protocol = Interface.SP_HDL_UDP;
                else if (val.equalsIgnoreCase(protoTCPStr)) ifc.protocol = Interface.SP_HDL_TCP;
                else if (val.equalsIgnoreCase(protoHTTPStr)) ifc.protocol = Interface.SP_HDL_HTTP;
                else if (val.equalsIgnoreCase(protoHTTPSStr)) ifc.protocol = Interface.SP_HDL_HTTPS;
                break;
            case 3:
                try {
                    ifc.port = Integer.parseInt(String.valueOf(value));
                } catch (Exception e) {
                }
                return;
            default:
                return;
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
            case 0:
            case 1:
                return Boolean.class;
            case 3:
                return Integer.class;
            default:
                return String.class;
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addIFButton) {
            ifTableModel.addInterface();
        } else if (src == removeIFButton) {
            int row = ifTable.getSelectedRow();
            ifTableModel.removeInterface(row);
        }
    }

    public void loadServerInfo(ServerInfo info) {
        if (info == null) info = new ServerInfo();
        ifTableModel.setInterfaces(info.interfaces);
        pubKeyField.setText(info.publicKey == null ? "" : Util.decodeHexString(info.publicKey, true));
        serverAddressField.setText(info.getAddressString());
        serverIDField.setText(String.valueOf(info.serverId));
    }

    public boolean saveServerInfo(ServerInfo info) {
        if (info == null) return false;
        int serverID;

        if (ifTable.isEditing()) {
            TableCellEditor tc = ifTable.getCellEditor(ifTable.getEditingRow(), ifTable.getEditingColumn());
            if (tc != null) tc.stopCellEditing();
        }

        try {
            serverID = Integer.parseInt(serverIDField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, ui.getStr("error") + ": " + e, ui.getStr("error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        byte ipAddress[] = null;
        try {
            InetAddress addr = InetAddress.getByName(serverAddressField.getText());
            byte addr1[] = addr.getAddress();
            ipAddress = new byte[Common.IP_ADDRESS_LENGTH];
            for (int i = 0; i < Common.IP_ADDRESS_LENGTH; i++)
                ipAddress[i] = (byte) 0;
            System.arraycopy(addr1, 0, ipAddress, ipAddress.length - addr1.length, addr1.length);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, ui.getStr("error") + ": " + e, ui.getStr("error"), JOptionPane.ERROR_MESSAGE);
            return false;
        }

        info.ipAddress = ipAddress;
        info.interfaces = ifTableModel.getInterfaces();
        info.publicKey = Util.encodeHexString(pubKeyField.getText());
        info.serverId = serverID;
        return true;
    }

    public void setComponentEnabled(boolean enabled) {
        setComponentEnabled(enabled, this);
    }

    private void setComponentEnabled(boolean enabled, Component component) {
        if (component instanceof JTextField || component instanceof JComboBox || component instanceof JCheckBox || component instanceof JButton || component instanceof JTextArea || component instanceof JTable) {
            if (component instanceof JTextComponent) {
                ((JTextComponent) component).setEditable(enabled);
                Color color = component.getForeground();
                ((JTextComponent) component).setDisabledTextColor(color);
            } else if (component instanceof JComboBox && ((JComboBox<?>) component).isEditable()) {
                component.setEnabled(enabled);
                Color color = component.getForeground();
                try {
                    ((JTextField) ((JComboBox<?>) component).getEditor().getEditorComponent()).setDisabledTextColor(color);
                } catch (Exception e) {
                    //ignore
                }
            } else {
                component.setEnabled(enabled);
            }
        }
        if (component instanceof Container) {
            Component[] components = ((Container) component).getComponents();
            if (components != null && components.length > 0) {
                for (Component heldComponent : components) {
                    setComponentEnabled(enabled, heldComponent);
                }
            }
        }
    }

}

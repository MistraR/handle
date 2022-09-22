/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.hdllib.*;
import net.handle.apps.gui.jutil.*;
import net.handle.awt.*;
import java.util.*;
import java.awt.*;
import java.net.InetAddress;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class IPRedirectPanel extends JPanel implements ActionListener {
    private final Configuration config;
    private final RedirectTableModel redirectModel;
    private final JTable redirectTable;
    private final JButton addButton;
    private final JButton delButton;

    public IPRedirectPanel(Configuration config) {
        this.config = config;

        redirectModel = new RedirectTableModel();
        redirectTable = new JTable(redirectModel);
        redirectTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        redirectTable.setRowSelectionAllowed(true);
        addButton = new JButton("Add");
        delButton = new JButton("Remove");

        setLayout(new GridBagLayout());

        int x = 0, y = 0;

        InfoPanel infoPanel = new InfoPanel();
        infoPanel.setTitle("Redirect IP Addresses");
        infoPanel.setText("If you are behind the same NAT firewall as a \n" + "handle server, you may have problems accessing \n" + "that handle server using the external IP address \n"
            + "for that server.  Setting an IP address redirect \n" + "allows you to tell this handle resolver to use a \n" + "different internal IP address instead of the specified \n" + "external addresses.");
        add(infoPanel, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, true, true));
        add(new JScrollPane(redirectTable), AwtUtil.getConstraints(x, y, 1, 1, 1, 3, true, true));
        add(addButton, AwtUtil.getConstraints(x + 1, y++, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        add(delButton, AwtUtil.getConstraints(x + 1, y++, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        add(new JLabel(" "), AwtUtil.getConstraints(x + 1, y++, 0, 1, 1, 1, new Insets(4, 4, 4, 4), true, true));

        addButton.addActionListener(this);
        delButton.addActionListener(this);

        loadRecords();
    }

    /** Reads any existing IP address redirections from the current configuration. */
    private void loadRecords() {
        Map m = config.getLocalAddressMap();
        if (m != null) {
            for (Iterator it = m.keySet().iterator(); it.hasNext();) {
                Object key = it.next();
                Object val = m.get(key);
                if (key instanceof InetAddress) key = Util.rfcIpRepr(((InetAddress) key));
                if (val instanceof InetAddress) val = Util.rfcIpRepr(((InetAddress) val));
                redirectModel.addRedirect(String.valueOf(key), String.valueOf(val));
            }
        }
    }

    /** Saves any IP address redirections to the current configuration. */
    public void saveRecords() {
        try {
            config.setLocalAddressMap(redirectModel.getRedirects());
            config.saveLocalAddressMap();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "<html>An error occured while saving the address " + "mapping:<br>&nbsp;&nbsp; " + e + "</html>");
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addButton) {
            int idx = redirectModel.addRedirect("", "");
            redirectTable.setRowSelectionInterval(idx, idx);
        } else if (src == delButton) {
            int selectedRow = redirectTable.getSelectedRow();
            if (selectedRow >= 0) redirectModel.deleteRedirect(selectedRow);
        }
    }

    private class RedirectTableModel extends AbstractTableModel {
        private final Vector redirects = new Vector();

        @Override
        public String getColumnName(int column) {
            switch (column) {
            case 0:
                return "External Address";
            case 1:
                return "Connection Address";
            default:
                return "";
            }
        }

        public Map getRedirects() {
            HashMap m = new HashMap();
            for (int i = 0; i < redirects.size(); i++) {
                Redirect r = (Redirect) redirects.elementAt(i);
                if (r.fromAddress != null && r.fromAddress.trim().length() > 0 && r.toAddress != null && r.toAddress.trim().length() > 0) {
                    m.put(r.fromAddress, r.toAddress);
                }
            }
            return m;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            return true;
        }

        /** Add a new redirect pair and return the index of it */
        public int addRedirect(String key, String value) {
            if (key == null) key = "";
            if (value == null) value = "";
            redirects.addElement(new Redirect(key, value));
            int idx = redirects.size() - 1;
            fireTableRowsInserted(idx, idx);
            return idx;
        }

        public void deleteRedirect(int row) {
            if (row < 0) return;
            redirects.removeElementAt(row);
            fireTableRowsDeleted(row, row);
        }

        @Override
        public int getRowCount() {
            return redirects.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Redirect r = (Redirect) redirects.elementAt(row);
            return column == 0 ? r.fromAddress : r.toAddress;
        }

        @Override
        public void setValueAt(Object obj, int row, int col) {
            Redirect r = (Redirect) redirects.elementAt(row);
            if (col == 0) r.fromAddress = String.valueOf(obj);
            else r.toAddress = String.valueOf(obj);
        }

    }

    private class Redirect {
        String fromAddress = "";
        String toAddress = "";

        Redirect(String from, String to) {
            this.fromAddress = from;
            this.toAddress = to;
        }
    }

}

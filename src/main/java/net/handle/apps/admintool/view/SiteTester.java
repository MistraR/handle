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
import javax.swing.*;
import javax.swing.table.*;

import java.awt.*;

public class SiteTester extends JDialog {
    private SiteInfo site;
    private DefaultTableModel statusModel;
    private AdminToolUI ui;
    private HandleResolver resolver;

    public SiteTester(Dialog owner, AdminToolUI ui, HandleValue siteValue) throws Exception {
        super(owner, "placeholder", false);
        setupWindow(ui, siteValue);
    }

    public SiteTester(Frame owner, AdminToolUI ui, HandleValue siteValue) throws Exception {
        super(owner, "placeholder", false);
        setupWindow(ui, siteValue);
    }

    public SiteTester(Dialog owner, AdminToolUI ui, SiteInfo siteInfo) {
        super(owner, "placeholder", false);
        setupWindow(ui, siteInfo);
    }

    public SiteTester(Frame owner, AdminToolUI ui, SiteInfo siteInfo) {
        super(owner, "placeholder", false);
        setupWindow(ui, siteInfo);
    }

    private void setupWindow(AdminToolUI ui, HandleValue siteValue) throws Exception {
        SiteInfo siteInfo = new SiteInfo();
        Encoder.decodeSiteInfoRecord(siteValue.getData(), 0, siteInfo);
        setupWindow(ui, siteInfo);
    }

    private void setupWindow(AdminToolUI ui, SiteInfo siteInfo) {
        this.setTitle(ui.getStr("testing_site:") + " " + siteInfo);

        this.ui = ui;
        this.site = siteInfo;
        this.resolver = ui.getMain().getResolver();

        JPanel p = new JPanel(new GridBagLayout());

        statusModel = new DefaultTableModel();
        statusModel.addColumn(ui.getStr("address_interface"));
        statusModel.addColumn(ui.getStr("status"));
        for (int serverIdx = 0; serverIdx < site.servers.length; serverIdx++) {
            ServerInfo server = site.servers[serverIdx];
            statusModel.addRow(new Object[] { server.getAddressString(), "" });
            for (int ifcIdx = 0; ifcIdx < server.interfaces.length; ifcIdx++) {
                int ifcRow = statusModel.getRowCount();
                statusModel.addRow(new Object[] { "  " + server.interfaces[ifcIdx], ui.getStr("checking...") });
                new InterfaceChecker(server, server.interfaces[ifcIdx], ifcRow).start();
            }
        }

        p.add(new JScrollPane(new JTable(statusModel)), GridC.getc(0, 0).wxy(1, 1).fillboth().insets(10, 10, 10, 10));

        getContentPane().add(p, BorderLayout.CENTER);

        pack();
        Dimension psize = getPreferredSize();
        psize.width = Math.max(psize.width, 200);
        setSize(psize);
    }

    class InterfaceChecker extends Thread {
        private final ServerInfo server;
        private final Interface ifc;
        private final int tableRow;

        InterfaceChecker(ServerInfo server, Interface ifc, int tableRow) {
            this.server = server;
            this.ifc = ifc;
            this.tableRow = tableRow;
        }

        @Override
        public void run() {
            GenericRequest req = new GenericRequest(Util.encodeString("0.SITE/status"), AbstractMessage.OC_GET_SITE_INFO, (AuthenticationInfo) null);
            long startTime = System.currentTimeMillis();
            try {
                AbstractResponse resp = resolver.sendRequestToInterface(req, server, ifc);
                long finishTime = System.currentTimeMillis();
                if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                    statusModel.setValueAt(ui.getStr("ifc_test_success") + " " + (finishTime - startTime) + " ms", tableRow, 1);
                } else {
                    statusModel.setValueAt(ui.getStr("error") + ": " + AbstractMessage.getResponseCodeMessage(resp.responseCode) + "; time: " + (finishTime - startTime) + " ms", tableRow, 1);
                }
            } catch (Exception e) {
                long finishTime = System.currentTimeMillis();
                statusModel.setValueAt(ui.getStr("error") + ": " + e + "; time: " + (finishTime - startTime) + " ms", tableRow, 1);
            } finally {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        statusModel.fireTableRowsUpdated(tableRow, tableRow);
                    }
                });
            }
        }
    }

}

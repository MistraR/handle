/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.handle.hdllib.*;
import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.net.*;

public class ServerInfoPanel extends Panel implements ActionListener {
    private final TextField serverIdField;
    private final TextField addressField;
    private final TextArea publicKeyField;

    private final Vector<Interface> interfaces;
    private final java.awt.List interfaceList;
    private final Button publicKeyButton;
    private final Button addInterfaceButton;
    private final Button remInterfaceButton;
    private final Button editInterfaceButton;
    private final Button upInterfaceButton;
    private final Button downInterfaceButton;

    public ServerInfoPanel() {
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        addressField = new TextField("", 15);
        serverIdField = new TextField("1", 5);
        publicKeyField = new TextArea("", 4, 25);
        interfaces = new Vector<>();
        interfaceList = new java.awt.List(5);

        addInterfaceButton = new Button("Add");
        remInterfaceButton = new Button("Remove");
        editInterfaceButton = new Button("Modify");
        upInterfaceButton = new Button("^");
        downInterfaceButton = new Button("v");
        publicKeyButton = new Button("Public Key: ");
        int y = 0;
        add(new Label("IP Address: ", Label.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, true, true));
        add(addressField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, true, true));

        add(new Label("Server ID: ", Label.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, true, true));
        add(serverIdField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, true, true));

        add(publicKeyButton, AwtUtil.getConstraints(0, y, 0, 0, 1, 1, true, false));
        add(publicKeyField, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, true, true));

        Panel interfacePanel = new Panel(gridbag);
        interfacePanel.add(interfaceList, AwtUtil.getConstraints(0, 0, 1, 1, 1, 10, true, true));
        interfacePanel.add(addInterfaceButton, AwtUtil.getConstraints(1, 0, 0, 0, 2, 1, true, true));
        interfacePanel.add(remInterfaceButton, AwtUtil.getConstraints(1, 1, 0, 0, 2, 1, true, true));
        interfacePanel.add(editInterfaceButton, AwtUtil.getConstraints(1, 2, 0, 0, 2, 1, true, true));
        interfacePanel.add(upInterfaceButton, AwtUtil.getConstraints(1, 3, 0.1, 0, 1, 1, true, true));
        interfacePanel.add(downInterfaceButton, AwtUtil.getConstraints(2, 3, 0.1, 0, 1, 1, true, true));
        add(new Label("Interfaces: ", Label.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, true, true));
        add(interfacePanel, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, true, true));

        interfaceList.addActionListener(this);
        addInterfaceButton.addActionListener(this);
        remInterfaceButton.addActionListener(this);
        editInterfaceButton.addActionListener(this);
        upInterfaceButton.addActionListener(this);
        downInterfaceButton.addActionListener(this);
        publicKeyButton.addActionListener(this);
    }

    public void getServerInfo(ServerInfo server) {
        try {
            server.serverId = Integer.parseInt(serverIdField.getText().trim());
        } catch (Exception e) {
            getToolkit().beep();
        }

        try {
            InetAddress addr = InetAddress.getByName(addressField.getText().trim());
            byte addr1[] = addr.getAddress();
            byte addr2[] = new byte[Common.IP_ADDRESS_LENGTH];
            for (int i = 0; i < Common.IP_ADDRESS_LENGTH; i++)
                addr2[i] = (byte) 0;
            System.arraycopy(addr1, 0, addr2, addr2.length - addr1.length, addr1.length);
            server.ipAddress = addr2;
        } catch (Exception e) {
            getToolkit().beep();
        }

        try {
            server.publicKey = Util.encodeHexString(publicKeyField.getText());
        } catch (Exception e) {
            getToolkit().beep();
        }

        server.interfaces = new Interface[interfaces.size()];
        for (int i = 0; i < server.interfaces.length; i++) {
            server.interfaces[i] = interfaces.elementAt(i);
        }
    }

    public void setServerInfo(ServerInfo server) {
        serverIdField.setText(String.valueOf(server.serverId));
        publicKeyField.setText(Util.decodeHexString(server.publicKey, true));
        addressField.setText(server.getAddressString());

        interfaces.removeAllElements();
        if (server.interfaces != null) {
            for (int i = 0; i < server.interfaces.length; i++)
                interfaces.addElement(server.interfaces[i]);
        }

        rebuildInterfaceList();
    }

    private void rebuildInterfaceList() {
        interfaceList.removeAll();
        for (int i = 0; i < interfaces.size(); i++)
            interfaceList.add(String.valueOf(interfaces.elementAt(i)));
    }

    private void evtAddInterface() {
        InterfacePanel svrPanel = new InterfacePanel();
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Add Interface", svrPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        Interface intrfc = new Interface();
        svrPanel.getInterface(intrfc);
        interfaces.addElement(intrfc);
        rebuildInterfaceList();
    }

    private void evtRemInterface() {
        int idx = interfaceList.getSelectedIndex();
        if (idx < 0) return;
        interfaces.removeElementAt(idx);
        rebuildInterfaceList();
    }

    private void evtEditInterface() {
        int idx = interfaceList.getSelectedIndex();
        if (idx < 0) return;
        Interface intrfc = interfaces.elementAt(idx);
        InterfacePanel svrPanel = new InterfacePanel();
        svrPanel.setInterface(intrfc);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Edit Interface", svrPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        svrPanel.getInterface(intrfc);
        rebuildInterfaceList();
    }

    private void evtUpInterface() {
        int idx = interfaceList.getSelectedIndex();
        if (idx <= 0) return;
        Interface intrfc = interfaces.elementAt(idx);
        interfaces.removeElementAt(idx);
        interfaces.insertElementAt(intrfc, idx - 1);
        rebuildInterfaceList();
        interfaceList.select(idx - 1);
    }

    private void evtDownInterface() {
        int idx = interfaceList.getSelectedIndex();
        if (idx < 0 || idx == (interfaces.size() - 1)) return;
        Interface intrfc = interfaces.elementAt(idx);
        interfaces.removeElementAt(idx);
        interfaces.insertElementAt(intrfc, idx + 1);
        rebuildInterfaceList();
        interfaceList.select(idx + 1);
    }

    private void evtPublicKey() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Select Public Key File", FileDialog.LOAD);
        fwin.setFile("pubkey.bin");
        fwin.setVisible(true);

        String dir = fwin.getDirectory();
        String file = fwin.getFile();

        if (dir == null || file == null || file.trim().length() <= 0) return;

        try {
            java.io.File keyFile = new java.io.File(dir, file);
            byte rawKey[] = new byte[(int) keyFile.length()];
            java.io.InputStream in = new java.io.FileInputStream(keyFile);
            int n = 0;
            int r = 0;
            while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0) {
                n += r;
            }
            in.close();
            publicKeyField.setText(Util.decodeHexString(rawKey, true));
        } catch (Throwable e) {
            e.printStackTrace(System.err);
            GenericDialog.askQuestion("Error", "Error unable to load key: " + e, GenericDialog.QUESTION_OK, this);
        }

    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addInterfaceButton) evtAddInterface();
        else if (src == remInterfaceButton) evtRemInterface();
        else if (src == editInterfaceButton) evtEditInterface();
        else if (src == upInterfaceButton) evtUpInterface();
        else if (src == downInterfaceButton) evtDownInterface();
        else if (src == publicKeyButton) evtPublicKey();
    }
}

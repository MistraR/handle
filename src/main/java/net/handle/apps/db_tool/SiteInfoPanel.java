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

public class SiteInfoPanel extends Panel implements ActionListener {
    private final TextField dataVersionField;
    private final TextField protoMajorVersionField;
    private final TextField protoMinorVersionField;
    private final TextField serialNumberField;
    private final Checkbox isPrimaryCheckbox;
    private final Checkbox multiplePrimaryCheckbox;
    private final Choice hashOptionChoice;

    private final java.awt.List attributeList;
    private final Button addAttributeButton;
    private final Button remAttributeButton;
    private final Button editAttributeButton;
    private final Button upAttributeButton;
    private final Button downAttributeButton;

    private final java.awt.List serverList;
    private final Button addServerButton;
    private final Button remServerButton;
    private final Button editServerButton;
    private final Button upServerButton;
    private final Button downServerButton;

    private final Vector<Attribute> attributes;
    private final Vector<ServerInfo> servers;

    public SiteInfoPanel() {
        super(new GridBagLayout());

        attributes = new Vector<>();
        servers = new Vector<>();

        dataVersionField = new TextField("1");
        dataVersionField.setEditable(false);
        protoMajorVersionField = new TextField("2");
        protoMinorVersionField = new TextField("0");
        serialNumberField = new TextField("1");
        isPrimaryCheckbox = new Checkbox("Primary Site", false);
        multiplePrimaryCheckbox = new Checkbox("Multiple Primary Sites", false);
        hashOptionChoice = new Choice();
        hashOptionChoice.addItem("By Entire Handle");
        hashOptionChoice.addItem("By Prefix");
        hashOptionChoice.addItem("By Local Name");

        attributeList = new java.awt.List(5);
        addAttributeButton = new Button("Add");
        remAttributeButton = new Button("Remove");
        editAttributeButton = new Button("Modify");
        upAttributeButton = new Button("^");
        downAttributeButton = new Button("v");

        serverList = new java.awt.List(5);
        addServerButton = new Button("Add");
        remServerButton = new Button("Remove");
        editServerButton = new Button("Modify");
        upServerButton = new Button("^");
        downServerButton = new Button("v");

        int x = 0;
        int y = 0;

        add(new Label("Data Version: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(dataVersionField, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        add(new Label("Protocol: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(protoMajorVersionField, AwtUtil.getConstraints(x + 1, y, 1f, 0f, 1, 1, true, true));
        add(protoMinorVersionField, AwtUtil.getConstraints(x + 2, y++, 1f, 0f, 1, 1, true, true));

        add(new Label("Serial #: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(serialNumberField, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        add(isPrimaryCheckbox, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        add(multiplePrimaryCheckbox, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        add(new Label("Hash: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(hashOptionChoice, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        Panel attributePanel = new Panel(new GridBagLayout());
        attributePanel.add(attributeList, AwtUtil.getConstraints(0, 0, 1, 1, 1, 10, true, true));
        attributePanel.add(addAttributeButton, AwtUtil.getConstraints(1, 0, 0, 0, 2, 1, true, true));
        attributePanel.add(remAttributeButton, AwtUtil.getConstraints(1, 1, 0, 0, 2, 1, true, true));
        attributePanel.add(editAttributeButton, AwtUtil.getConstraints(1, 2, 0, 0, 2, 1, true, true));
        attributePanel.add(upAttributeButton, AwtUtil.getConstraints(1, 3, 0.1, 0, 1, 1, true, true));
        attributePanel.add(downAttributeButton, AwtUtil.getConstraints(2, 3, 0.1, 0, 1, 1, true, true));

        add(new Label("Attributes: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(attributePanel, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        Panel serverPanel = new Panel(new GridBagLayout());
        serverPanel.add(serverList, AwtUtil.getConstraints(0, 0, 1, 1, 1, 10, true, true));
        serverPanel.add(addServerButton, AwtUtil.getConstraints(1, 0, 0, 0, 2, 1, true, true));
        serverPanel.add(remServerButton, AwtUtil.getConstraints(1, 1, 0, 0, 2, 1, true, true));
        serverPanel.add(editServerButton, AwtUtil.getConstraints(1, 2, 0, 0, 2, 1, true, true));
        serverPanel.add(upServerButton, AwtUtil.getConstraints(1, 3, 0.1, 0, 1, 1, true, true));
        serverPanel.add(downServerButton, AwtUtil.getConstraints(2, 3, 0.1, 0, 1, 1, true, true));

        add(new Label("Servers: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0f, 0f, 1, 1, true, true));
        add(serverPanel, AwtUtil.getConstraints(x + 1, y++, 1f, 0f, 2, 1, true, true));

        attributeList.addActionListener(this);
        addAttributeButton.addActionListener(this);
        remAttributeButton.addActionListener(this);
        editAttributeButton.addActionListener(this);
        upAttributeButton.addActionListener(this);
        downAttributeButton.addActionListener(this);

        serverList.addActionListener(this);
        addServerButton.addActionListener(this);
        remServerButton.addActionListener(this);
        editServerButton.addActionListener(this);
        upServerButton.addActionListener(this);
        downServerButton.addActionListener(this);
    }

    public void setSiteInfo(SiteInfo site) {
        dataVersionField.setText(String.valueOf(site.dataFormatVersion));
        protoMajorVersionField.setText(String.valueOf(site.majorProtocolVersion));
        protoMinorVersionField.setText(String.valueOf(site.minorProtocolVersion));
        serialNumberField.setText(String.valueOf(site.serialNumber));
        isPrimaryCheckbox.setState(site.isPrimary);
        multiplePrimaryCheckbox.setState(site.multiPrimary);
        switch (site.hashOption) {
        case SiteInfo.HASH_TYPE_BY_PREFIX:
            hashOptionChoice.select(1);
            break;
        case SiteInfo.HASH_TYPE_BY_SUFFIX:
            hashOptionChoice.select(2);
            break;
        default:
            hashOptionChoice.select(0);
            break;
        }

        servers.removeAllElements();
        if (site.servers != null) {
            for (int i = 0; i < site.servers.length; i++) {
                servers.addElement(site.servers[i]);
            }
        }
        rebuildServerList();

        attributes.removeAllElements();
        if (site.attributes != null) {
            for (int i = 0; i < site.attributes.length; i++) {
                attributes.addElement(site.attributes[i]);
            }
        }
        rebuildAttributeList();
    }

    public void getSiteInfo(SiteInfo site) {
        try {
            site.dataFormatVersion = Integer.parseInt(dataVersionField.getText().trim());

            site.majorProtocolVersion = (byte) Integer.parseInt(protoMajorVersionField.getText().trim());

            site.minorProtocolVersion = (byte) Integer.parseInt(protoMinorVersionField.getText().trim());

            site.serialNumber = Integer.parseInt(serialNumberField.getText().trim());

            site.isPrimary = isPrimaryCheckbox.getState();
            site.multiPrimary = multiplePrimaryCheckbox.getState();
            switch (hashOptionChoice.getSelectedIndex()) {
            case 0: // by handle
                site.hashOption = SiteInfo.HASH_TYPE_BY_ALL;
                break;
            case 1: // by NA
                site.hashOption = SiteInfo.HASH_TYPE_BY_PREFIX;
                break;
            case 2: // by local ID
                site.hashOption = SiteInfo.HASH_TYPE_BY_SUFFIX;
                break;
            default:
                site.hashOption = SiteInfo.HASH_TYPE_BY_ALL;
            }

            site.servers = new ServerInfo[servers.size()];
            for (int i = 0; i < servers.size(); i++) {
                site.servers[i] = servers.elementAt(i);
            }

            site.attributes = new Attribute[attributes.size()];
            for (int i = 0; i < attributes.size(); i++) {
                site.attributes[i] = attributes.elementAt(i);
            }
        } catch (Exception e) {
            getToolkit().beep();
            System.err.println("There was an error getting the data");
        }
    }

    private void rebuildServerList() {
        serverList.removeAll();
        for (int i = 0; i < servers.size(); i++) {
            serverList.add(String.valueOf(servers.elementAt(i)));
        }
    }

    private void rebuildAttributeList() {
        attributeList.removeAll();
        for (int i = 0; i < attributes.size(); i++) {
            attributeList.add(String.valueOf(attributes.elementAt(i)));
        }
    }

    private void evtAddAttribute() {
        AttributePanel attPanel = new AttributePanel();
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Add Attribute", attPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        attributes.addElement(new Attribute(Util.encodeString(attPanel.getName()), Util.encodeString(attPanel.getValue())));
        rebuildAttributeList();
    }

    private void evtRemAttribute() {
        int idx = attributeList.getSelectedIndex();
        if (idx < 0) return;
        attributes.removeElementAt(idx);
        rebuildAttributeList();
    }

    private void evtEditAttribute() {
        int idx = attributeList.getSelectedIndex();
        if (idx < 0) return;
        Attribute attribute = attributes.elementAt(idx);
        AttributePanel attPanel = new AttributePanel();
        attPanel.setName(Util.decodeString(attribute.name));
        attPanel.setValue(Util.decodeString(attribute.value));
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Edit Attribute", attPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        attribute.name = Util.encodeString(attPanel.getName());
        attribute.value = Util.encodeString(attPanel.getValue());
        rebuildAttributeList();
    }

    private void evtUpAttribute() {
        int idx = attributeList.getSelectedIndex();
        if (idx <= 0) return;
        Attribute attribute = attributes.elementAt(idx);
        attributes.removeElementAt(idx);
        attributes.insertElementAt(attribute, idx - 1);
        rebuildAttributeList();
        attributeList.select(idx - 1);
    }

    private void evtDownAttribute() {
        int idx = attributeList.getSelectedIndex();
        if (idx < 0 || idx == (attributes.size() - 1)) return;
        Attribute attribute = attributes.elementAt(idx);
        attributes.removeElementAt(idx);
        attributes.insertElementAt(attribute, idx + 1);
        rebuildAttributeList();
        attributeList.select(idx + 1);
    }

    private void evtAddServer() {
        ServerInfoPanel svrPanel = new ServerInfoPanel();
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Add Server", svrPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        ServerInfo server = new ServerInfo();
        svrPanel.getServerInfo(server);
        servers.addElement(server);
        rebuildServerList();
    }

    private void evtRemServer() {
        int idx = serverList.getSelectedIndex();
        if (idx < 0) return;
        servers.removeElementAt(idx);
        rebuildServerList();
    }

    private void evtEditServer() {
        int idx = serverList.getSelectedIndex();
        if (idx < 0) return;
        ServerInfo server = servers.elementAt(idx);
        ServerInfoPanel svrPanel = new ServerInfoPanel();
        svrPanel.setServerInfo(server);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Edit Server", svrPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        svrPanel.getServerInfo(server);
        rebuildServerList();
    }

    private void evtUpServer() {
        int idx = serverList.getSelectedIndex();
        if (idx <= 0) return;
        ServerInfo server = servers.elementAt(idx);
        servers.removeElementAt(idx);
        servers.insertElementAt(server, idx - 1);
        rebuildServerList();
        serverList.select(idx - 1);
    }

    private void evtDownServer() {
        int idx = serverList.getSelectedIndex();
        if (idx < 0 || idx == (servers.size() - 1)) return;
        ServerInfo server = servers.elementAt(idx);
        servers.removeElementAt(idx);
        servers.insertElementAt(server, idx + 1);
        rebuildServerList();
        serverList.select(idx + 1);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == attributeList) evtEditAttribute();
        else if (src == addAttributeButton) evtAddAttribute();
        else if (src == remAttributeButton) evtRemAttribute();
        else if (src == editAttributeButton) evtEditAttribute();
        else if (src == upAttributeButton) evtUpAttribute();
        else if (src == downAttributeButton) evtDownAttribute();
        else if (src == serverList) evtEditServer();
        else if (src == addServerButton) evtAddServer();
        else if (src == remServerButton) evtRemServer();
        else if (src == editServerButton) evtEditServer();
        else if (src == upServerButton) evtUpServer();
        else if (src == downServerButton) evtDownServer();
    }

}

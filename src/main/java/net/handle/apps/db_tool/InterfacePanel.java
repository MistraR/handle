/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.handle.hdllib.*;
import java.awt.*;

public class InterfacePanel extends Panel {
    private final Choice typeChoice;
    private final Choice protocolChoice;
    private final TextField portField;

    public InterfacePanel() {
        typeChoice = new Choice();
        typeChoice.addItem("OUT_OF_SERVICE");
        typeChoice.addItem("ADMIN");
        typeChoice.addItem("QUERY");
        typeChoice.addItem("ADMIN_AND_QUERY");

        protocolChoice = new Choice();
        protocolChoice.addItem("HDL_UDP");
        protocolChoice.addItem("HDL_TCP");
        protocolChoice.addItem("HDL_HTTP");
        protocolChoice.addItem("HDL_HTTPS");
        portField = new TextField("2641", 6);

        add(new Label("Type: "));
        add(typeChoice);
        add(new Label("  Protocol: "));
        add(protocolChoice);
        add(new Label("  Port: "));
        add(portField);
    }

    public void getInterface(Interface intfc) {
        switch (protocolChoice.getSelectedIndex()) {
        case 0:
            intfc.protocol = Interface.SP_HDL_UDP;
            break;
        case 1:
            intfc.protocol = Interface.SP_HDL_TCP;
            break;
        case 2:
            intfc.protocol = Interface.SP_HDL_HTTP;
            break;
        case 3:
            intfc.protocol = Interface.SP_HDL_HTTPS;
            break;
        default:
            intfc.protocol = Interface.SP_HDL_UDP;
            break;
        }

        switch (typeChoice.getSelectedIndex()) {
        case 0:
            intfc.type = Interface.ST_OUT_OF_SERVICE;
            break;
        case 1:
            intfc.type = Interface.ST_ADMIN;
            break;
        case 2:
            intfc.type = Interface.ST_QUERY;
            break;
        case 3:
            intfc.type = Interface.ST_ADMIN_AND_QUERY;
            break;
        default:
            intfc.type = Interface.ST_OUT_OF_SERVICE;
            break;
        }

        try {
            intfc.port = Integer.parseInt(portField.getText().trim());
        } catch (Exception e) {
            getToolkit().beep();
            intfc.port = 2641;
            portField.setText("2641");
        }
    }

    public void setInterface(Interface intfc) {
        switch (intfc.protocol) {
        case Interface.SP_HDL_UDP:
            protocolChoice.select(0);
            break;
        case Interface.SP_HDL_TCP:
            protocolChoice.select(1);
            break;
        case Interface.SP_HDL_HTTP:
            protocolChoice.select(2);
            break;
        case Interface.SP_HDL_HTTPS:
            protocolChoice.select(3);
            break;
        default:
            protocolChoice.select(0);
        }

        switch (intfc.type) {
        case Interface.ST_OUT_OF_SERVICE:
            typeChoice.select(0);
            break;
        case Interface.ST_ADMIN:
            typeChoice.select(1);
            break;
        case Interface.ST_QUERY:
            typeChoice.select(2);
            break;
        case Interface.ST_ADMIN_AND_QUERY:
            typeChoice.select(3);
            break;
        default:
            typeChoice.select(0);
        }

        portField.setText(String.valueOf(intfc.port));
    }
}

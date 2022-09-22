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

public class AdminInfoPanel extends Panel {
    private final TextField adminIdField;
    private final TextField adminIdIndexField;
    private final Checkbox addHandleChkbx;
    private final Checkbox deleteHandleChkbx;
    private final Checkbox addNAChkbx;
    private final Checkbox deleteNAChkbx;
    private final Checkbox modifyValueChkbx;
    private final Checkbox removeValueChkbx;
    private final Checkbox addValueChkbx;
    private final Checkbox readValueChkbx;
    private final Checkbox modifyAdminChkbx;
    private final Checkbox removeAdminChkbx;
    private final Checkbox addAdminChkbx;
    private final Checkbox listHandleChkbx;

    public AdminInfoPanel() {
        adminIdField = new TextField("", 20);
        adminIdIndexField = new TextField("0", 5);
        addHandleChkbx = new Checkbox("Add Handle", false);
        deleteHandleChkbx = new Checkbox("Delete Handle", false);
        addNAChkbx = new Checkbox("Add Prefix", false);
        deleteNAChkbx = new Checkbox("Delete Prefix", false);
        modifyValueChkbx = new Checkbox("Modify Value", true);
        removeValueChkbx = new Checkbox("Remove Value", true);
        addValueChkbx = new Checkbox("Add Value", true);
        readValueChkbx = new Checkbox("Read Value", true);
        modifyAdminChkbx = new Checkbox("Modify Admin", false);
        removeAdminChkbx = new Checkbox("Remove Admin", false);
        addAdminChkbx = new Checkbox("Add Admin", false);
        listHandleChkbx = new Checkbox("List Handles", false);

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        int x = 0, y = 0;
        Panel topPanel = new Panel(gridbag);
        topPanel.add(new Label("Admin ID Handle: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        topPanel.add(adminIdField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 1, 1, true, true));
        topPanel.add(new Label("Admin ID Index: ", Label.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        topPanel.add(adminIdIndexField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 1, 1, true, true));
        x = y = 0;
        add(topPanel, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, true, false));
        add(addHandleChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(deleteHandleChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(addNAChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(deleteNAChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(modifyValueChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(removeValueChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(addValueChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(readValueChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(modifyAdminChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(removeAdminChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
        add(addAdminChkbx, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(listHandleChkbx, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));
    }

    public void setAdminInfo(AdminRecord admin) {
        try {
            adminIdField.setText(Util.decodeString(admin.adminId));
            adminIdIndexField.setText(String.valueOf(admin.adminIdIndex));
            addHandleChkbx.setState(admin.perms[AdminRecord.ADD_HANDLE]);
            deleteHandleChkbx.setState(admin.perms[AdminRecord.DELETE_HANDLE]);
            addNAChkbx.setState(admin.perms[AdminRecord.ADD_DERIVED_PREFIX]);
            deleteNAChkbx.setState(admin.perms[AdminRecord.DELETE_DERIVED_PREFIX]);
            modifyValueChkbx.setState(admin.perms[AdminRecord.MODIFY_VALUE]);
            removeValueChkbx.setState(admin.perms[AdminRecord.REMOVE_VALUE]);
            addValueChkbx.setState(admin.perms[AdminRecord.ADD_VALUE]);
            readValueChkbx.setState(admin.perms[AdminRecord.READ_VALUE]);
            modifyAdminChkbx.setState(admin.perms[AdminRecord.MODIFY_ADMIN]);
            removeAdminChkbx.setState(admin.perms[AdminRecord.REMOVE_ADMIN]);
            addAdminChkbx.setState(admin.perms[AdminRecord.ADD_ADMIN]);
            listHandleChkbx.setState(admin.perms[AdminRecord.LIST_HANDLES]);
        } catch (Exception e) {
            System.err.println("error setting admin info: " + e);
            e.printStackTrace(System.err);
        }
    }

    public void getAdminInfo(AdminRecord admin) {
        try {
            admin.perms[AdminRecord.ADD_HANDLE] = addHandleChkbx.getState();
            admin.perms[AdminRecord.DELETE_HANDLE] = deleteHandleChkbx.getState();
            admin.perms[AdminRecord.ADD_DERIVED_PREFIX] = addNAChkbx.getState();
            admin.perms[AdminRecord.DELETE_DERIVED_PREFIX] = deleteNAChkbx.getState();
            admin.perms[AdminRecord.MODIFY_VALUE] = modifyValueChkbx.getState();
            admin.perms[AdminRecord.REMOVE_VALUE] = removeValueChkbx.getState();
            admin.perms[AdminRecord.ADD_VALUE] = addValueChkbx.getState();
            admin.perms[AdminRecord.READ_VALUE] = readValueChkbx.getState();
            admin.perms[AdminRecord.MODIFY_ADMIN] = modifyAdminChkbx.getState();
            admin.perms[AdminRecord.REMOVE_ADMIN] = removeAdminChkbx.getState();
            admin.perms[AdminRecord.ADD_ADMIN] = addAdminChkbx.getState();
            admin.perms[AdminRecord.LIST_HANDLES] = listHandleChkbx.getState();
            admin.adminId = Util.encodeString(adminIdField.getText());
            admin.adminIdIndex = Integer.parseInt(adminIdIndexField.getText().trim());
        } catch (Exception e) {
            System.err.println("error getting admin info: " + e);
            e.printStackTrace(System.err);
        }
    }
}

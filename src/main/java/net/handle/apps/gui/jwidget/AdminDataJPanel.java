/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/****************************************************************\
 *setup administrator record gui panel
 ****************************************************************/

public class AdminDataJPanel extends GenDataJPanel {
    protected ValueRefJPanel adminIdPanel;
    protected JCheckBox addHandleChkbx;
    protected JCheckBox deleteHandleChkbx;
    protected JCheckBox addNAChkbx;
    protected JCheckBox deleteNAChkbx;
    protected JCheckBox modifyValueChkbx;
    protected JCheckBox removeValueChkbx;
    protected JCheckBox addValueChkbx;
    protected JCheckBox readValueChkbx;
    protected JCheckBox modifyAdminChkbx;
    protected JCheckBox removeAdminChkbx;
    protected JCheckBox addAdminChkbx;
    protected JCheckBox listHdlsChkbx;

    /**
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, only for view
     **/
    public AdminDataJPanel(boolean moreFlag, boolean editFlag) {
        this(moreFlag, editFlag, 0);
    }

    public AdminDataJPanel(boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, String.valueOf(index));
        adminIdPanel = new ValueRefJPanel("Admin ID Handle:", "Input Admin ID Handle Name", "Admin ID Index:", "Input Admin ID Handle Index Value", editFlag);

        addHandleChkbx = new JCheckBox("Add Handle", false);
        addHandleChkbx.setEnabled(editFlag);

        deleteHandleChkbx = new JCheckBox("Delete Handle", true);
        deleteHandleChkbx.setEnabled(editFlag);

        addNAChkbx = new JCheckBox("Add Derived Prefix", false);
        addNAChkbx.setEnabled(editFlag);

        deleteNAChkbx = new JCheckBox("Delete Derived Prefix", false);
        deleteNAChkbx.setEnabled(editFlag);

        modifyValueChkbx = new JCheckBox("Modify Value", true);
        modifyValueChkbx.setEnabled(editFlag);

        removeValueChkbx = new JCheckBox("Remove Value", true);
        removeValueChkbx.setEnabled(editFlag);

        addValueChkbx = new JCheckBox("Add Value", true);
        addValueChkbx.setEnabled(editFlag);

        readValueChkbx = new JCheckBox("Read Value", true);
        readValueChkbx.setEnabled(editFlag);

        modifyAdminChkbx = new JCheckBox("Modify Admin", true);
        modifyAdminChkbx.setEnabled(editFlag);

        removeAdminChkbx = new JCheckBox("Remove Admin", true);
        removeAdminChkbx.setEnabled(editFlag);

        addAdminChkbx = new JCheckBox("Add Admin", true);
        addAdminChkbx.setEnabled(editFlag);

        listHdlsChkbx = new JCheckBox("List Handles", true);
        listHdlsChkbx.setEnabled(editFlag);

        JPanel p = new JPanel(new GridLayout(4, 2));
        int x = 0;
        int y = 0;
        JPanel naPerms = new JPanel(new GridLayout(2, 2));
        p.add(deleteHandleChkbx);
        p.add(modifyValueChkbx);
        p.add(removeValueChkbx);
        p.add(addValueChkbx);
        p.add(readValueChkbx);
        // p.add(readValueChkbx);
        p.add(modifyAdminChkbx);
        p.add(removeAdminChkbx);
        p.add(addAdminChkbx);

        naPerms.add(addHandleChkbx);
        naPerms.add(addNAChkbx);
        naPerms.add(deleteNAChkbx);
        naPerms.add(listHdlsChkbx);

        x = y = 0;

        naPerms.setBorder(new TitledBorder(new EtchedBorder(), "Prefix Permissions"));
        p.setBorder(new TitledBorder(new EtchedBorder(), "Handle Permissions"));

        panel.add(adminIdPanel, AwtUtil.getConstraints(x, y++, 1f, 0f, 3, 1, new Insets(1, 1, 1, 8), true, true));
        panel.add(p, AwtUtil.getConstraints(x, y++, 1f, 0f, 3, 1, new Insets(5, 10, 5, 10), true, true));

        panel.add(naPerms, AwtUtil.getConstraints(x, y, 1f, 0f, 3, 1, new Insets(5, 10, 5, 10), true, true));

        handlevalue.setType(Common.STD_TYPE_HSADMIN);
    }

    public void setAdmin(String handle, int index) {
        adminIdPanel.setHandleIdName(handle);
        adminIdPanel.setHandleIdIndex(index);
    }

    @Override
    public byte[] getValueData() {
        AdminRecord record = getAdminInfo();
        if (record != null) return (Encoder.encodeAdminRecord(record));
        else {
            System.err.println("warning message: Admini data is empty");
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY) {
            System.err.println("warning message: Handle value data is empty");
            return;
        }

        AdminRecord admRec = new AdminRecord();
        try {
            Encoder.decodeAdminRecord(data, 0, admRec);
        } catch (HandleException e) {
        }
        setAdminInfo(admRec);
    }

    public AdminRecord getAdminInfo() {
        AdminRecord admin = new AdminRecord();
        try {
            admin.perms[AdminRecord.ADD_HANDLE] = addHandleChkbx.isSelected();
            admin.perms[AdminRecord.DELETE_HANDLE] = deleteHandleChkbx.isSelected();
            admin.perms[AdminRecord.ADD_DERIVED_PREFIX] = addNAChkbx.isSelected();
            admin.perms[AdminRecord.DELETE_DERIVED_PREFIX] = deleteNAChkbx.isSelected();
            admin.perms[AdminRecord.MODIFY_VALUE] = modifyValueChkbx.isSelected();
            admin.perms[AdminRecord.REMOVE_VALUE] = removeValueChkbx.isSelected();
            admin.perms[AdminRecord.ADD_VALUE] = addValueChkbx.isSelected();
            admin.perms[AdminRecord.READ_VALUE] = readValueChkbx.isSelected();
            admin.perms[AdminRecord.MODIFY_ADMIN] = modifyAdminChkbx.isSelected();
            admin.perms[AdminRecord.REMOVE_ADMIN] = removeAdminChkbx.isSelected();
            admin.perms[AdminRecord.ADD_ADMIN] = addAdminChkbx.isSelected();
            admin.perms[AdminRecord.LIST_HANDLES] = listHdlsChkbx.isSelected();

            admin.adminId = Util.encodeString(adminIdPanel.getHandleIdName());
            admin.adminIdIndex = adminIdPanel.getHandleIdIndex();
            return admin;
        } catch (Exception e) {
            System.err.println("error message: Exception at getAdminInfo");
            e.printStackTrace(System.err);
            return null;
        }
    }

    public void setAdminInfo(AdminRecord admin) {
        if (admin == null) return;

        try {
            addHandleChkbx.setSelected(admin.perms[AdminRecord.ADD_HANDLE]);
            deleteHandleChkbx.setSelected(admin.perms[AdminRecord.DELETE_HANDLE]);
            addNAChkbx.setSelected(admin.perms[AdminRecord.ADD_DERIVED_PREFIX]);
            deleteNAChkbx.setSelected(admin.perms[AdminRecord.DELETE_DERIVED_PREFIX]);
            modifyValueChkbx.setSelected(admin.perms[AdminRecord.MODIFY_VALUE]);
            removeValueChkbx.setSelected(admin.perms[AdminRecord.REMOVE_VALUE]);
            addValueChkbx.setSelected(admin.perms[AdminRecord.ADD_VALUE]);
            readValueChkbx.setSelected(admin.perms[AdminRecord.READ_VALUE]);
            modifyAdminChkbx.setSelected(admin.perms[AdminRecord.MODIFY_ADMIN]);
            removeAdminChkbx.setSelected(admin.perms[AdminRecord.REMOVE_ADMIN]);
            addAdminChkbx.setSelected(admin.perms[AdminRecord.ADD_ADMIN]);
            listHdlsChkbx.setSelected(admin.perms[AdminRecord.LIST_HANDLES]);

            adminIdPanel.setHandleIdName(Util.decodeString(admin.adminId));
            adminIdPanel.setHandleIdIndex(admin.adminIdIndex);

        } catch (Exception e) {
            System.err.println("error message:  Exception at setting admin info");
            e.printStackTrace(System.err);

        }
    }
}

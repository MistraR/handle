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
import javax.swing.border.*;
import java.awt.*;

public class AdminValueEditor extends JPanel implements HandleValueEditor {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;

    private final JTextField handleField;
    private final JTextField indexField;

    private final JCheckBox deleteHandleCB;
    private final JCheckBox addValueCB;
    private final JCheckBox removeValueCB;
    private final JCheckBox modifyValueCB;
    private final JCheckBox readValueCB;
    private final JCheckBox addAdminCB;
    private final JCheckBox removeAdminCB;
    private final JCheckBox modifyAdminCB;
    private final JCheckBox addHandleCB;
    private final JCheckBox listHandlesCB;
    private final JCheckBox addDerivedPrefixCB;
    private final JCheckBox deleteDerivedPrefixCB;

    public AdminValueEditor(AdminToolUI ui) {
        super(new GridBagLayout());
        this.ui = ui;

        handleField = new JTextField("", 14);
        indexField = new JTextField("", 5);

        deleteHandleCB = new JCheckBox(ui.getStr("perm_del_hdl"), true);
        addValueCB = new JCheckBox(ui.getStr("perm_add_val"), true);
        removeValueCB = new JCheckBox(ui.getStr("perm_del_val"), true);
        modifyValueCB = new JCheckBox(ui.getStr("perm_mod_val"), true);
        readValueCB = new JCheckBox(ui.getStr("perm_read_val"), true);
        addAdminCB = new JCheckBox(ui.getStr("perm_add_adm"), true);
        removeAdminCB = new JCheckBox(ui.getStr("perm_rem_adm"), true);
        modifyAdminCB = new JCheckBox(ui.getStr("perm_mod_adm"), true);
        addHandleCB = new JCheckBox(ui.getStr("perm_add_hdl"), true);
        listHandlesCB = new JCheckBox(ui.getStr("perm_list_hdl"), true);
        addDerivedPrefixCB = new JCheckBox(ui.getStr("perm_add_na"), true);
        deleteDerivedPrefixCB = new JCheckBox(ui.getStr("perm_del_na"), true);

        JPanel idPanel = new JPanel(new GridBagLayout());
        idPanel.add(new JLabel(ui.getStr("adm_handle") + ":", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        idPanel.add(handleField, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        idPanel.add(new JLabel(ui.getStr("adm_validx") + ":", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        idPanel.add(indexField, AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        add(idPanel, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, true, true));

        JPanel permPanel = new JPanel(new GridBagLayout());
        int y = 0;
        permPanel.add(addValueCB, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(removeValueCB, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(modifyValueCB, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(readValueCB, AwtUtil.getConstraints(0, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        y = 0;
        permPanel.add(addAdminCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(removeAdminCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(modifyAdminCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(deleteHandleCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        y++;

        permPanel.add(addHandleCB, AwtUtil.getConstraints(0, y, 1, 0, 1, 1, new Insets(14, 4, 4, 4), true, true));
        permPanel.add(addDerivedPrefixCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(14, 4, 4, 4), true, true));
        permPanel.add(listHandlesCB, AwtUtil.getConstraints(0, y, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        permPanel.add(deleteDerivedPrefixCB, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(4, 4, 4, 4), true, true));
        add(permPanel, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));
        setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    /**
     * Saves the handle value data in the given HandleValue.  Returns false
     * if there was an error and the operation was canceled.
     */
    @Override
    public boolean saveValueData(HandleValue value) {
        AdminRecord adminInfo = new AdminRecord();
        adminInfo.adminId = Util.encodeString(handleField.getText().trim());
        try {
            adminInfo.adminIdIndex = Integer.parseInt(indexField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, ui.getStr("invalid_idx_msg") + "\n\nError: " + e);
            return false;
        }

        if (adminInfo.adminIdIndex < 0) {
            JOptionPane.showMessageDialog(this, ui.getStr("invalid_idx_msg"));
            return false;
        }

        adminInfo.perms[AdminRecord.DELETE_HANDLE] = deleteHandleCB.isSelected();
        adminInfo.perms[AdminRecord.ADD_VALUE] = addValueCB.isSelected();
        adminInfo.perms[AdminRecord.REMOVE_VALUE] = removeValueCB.isSelected();
        adminInfo.perms[AdminRecord.MODIFY_VALUE] = modifyValueCB.isSelected();
        adminInfo.perms[AdminRecord.READ_VALUE] = readValueCB.isSelected();
        adminInfo.perms[AdminRecord.ADD_ADMIN] = addAdminCB.isSelected();
        adminInfo.perms[AdminRecord.REMOVE_ADMIN] = removeAdminCB.isSelected();
        adminInfo.perms[AdminRecord.MODIFY_ADMIN] = modifyAdminCB.isSelected();
        adminInfo.perms[AdminRecord.ADD_HANDLE] = addHandleCB.isSelected();
        adminInfo.perms[AdminRecord.LIST_HANDLES] = listHandlesCB.isSelected();
        adminInfo.perms[AdminRecord.ADD_DERIVED_PREFIX] = addDerivedPrefixCB.isSelected();
        adminInfo.perms[AdminRecord.DELETE_DERIVED_PREFIX] = deleteDerivedPrefixCB.isSelected();

        value.setData(Encoder.encodeAdminRecord(adminInfo));
        return true;
    }

    /**
     * Sets the handle value data from the given HandleValue.
     */
    @Override
    public void loadValueData(HandleValue value) {
        AdminRecord adminInfo = new AdminRecord();
        adminInfo.perms[AdminRecord.DELETE_HANDLE] = true;
        adminInfo.perms[AdminRecord.ADD_VALUE] = true;
        adminInfo.perms[AdminRecord.REMOVE_VALUE] = true;
        adminInfo.perms[AdminRecord.MODIFY_VALUE] = true;
        adminInfo.perms[AdminRecord.READ_VALUE] = true;
        adminInfo.perms[AdminRecord.ADD_ADMIN] = true;
        adminInfo.perms[AdminRecord.REMOVE_ADMIN] = true;
        adminInfo.perms[AdminRecord.MODIFY_ADMIN] = true;
        adminInfo.perms[AdminRecord.ADD_HANDLE] = true;
        adminInfo.perms[AdminRecord.LIST_HANDLES] = false;
        adminInfo.perms[AdminRecord.ADD_DERIVED_PREFIX] = false;
        adminInfo.perms[AdminRecord.DELETE_DERIVED_PREFIX] = false;

        try {
            Encoder.decodeAdminRecord(value.getData(), 0, adminInfo);
        } catch (Exception e) {
        }

        if (adminInfo.adminId != null) handleField.setText(Util.decodeString(adminInfo.adminId));
        else handleField.setText("");
        indexField.setText(String.valueOf(adminInfo.adminIdIndex));
        deleteHandleCB.setSelected(adminInfo.perms[AdminRecord.DELETE_HANDLE]);
        addValueCB.setSelected(adminInfo.perms[AdminRecord.ADD_VALUE]);
        removeValueCB.setSelected(adminInfo.perms[AdminRecord.REMOVE_VALUE]);
        modifyValueCB.setSelected(adminInfo.perms[AdminRecord.MODIFY_VALUE]);
        readValueCB.setSelected(adminInfo.perms[AdminRecord.READ_VALUE]);
        addAdminCB.setSelected(adminInfo.perms[AdminRecord.ADD_ADMIN]);
        removeAdminCB.setSelected(adminInfo.perms[AdminRecord.REMOVE_ADMIN]);
        modifyAdminCB.setSelected(adminInfo.perms[AdminRecord.MODIFY_ADMIN]);
        addHandleCB.setSelected(adminInfo.perms[AdminRecord.ADD_HANDLE]);
        listHandlesCB.setSelected(adminInfo.perms[AdminRecord.LIST_HANDLES]);
        addDerivedPrefixCB.setSelected(adminInfo.perms[AdminRecord.ADD_DERIVED_PREFIX]);
        deleteDerivedPrefixCB.setSelected(adminInfo.perms[AdminRecord.DELETE_DERIVED_PREFIX]);
    }

}

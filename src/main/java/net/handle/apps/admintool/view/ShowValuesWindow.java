/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import net.handle.hdllib.trust.HandleClaimsSet;
import net.handle.hdllib.trust.HandleSigner;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.TrustException;
import net.handle.awt.*;
import net.cnri.guiutil.*;
import net.cnri.util.StreamUtil;

import javax.swing.*;

import java.awt.FileDialog;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.event.*;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShowValuesWindow extends JFrame implements ActionListener, MouseListener, ViewConstants {
    private int mode = VIEW_HDL_MODE;
    private String handle = null;
    private final DefaultListModel<HandleValue> valuesListModel;
    private final JList<HandleValue> valuesList;
    private final AdminToolUI ui;

    private final HDLAction saveValuesJsonAction;
    private final HDLAction saveValuesBinaryAction;
    private final HDLAction saveValueAction;
    private final HDLAction editHandleAction;
    private final HDLAction signHandleAction;
    private final HDLAction viewValueAction;
    private final HDLAction editValueAction;
    private final HDLAction testSiteAction;

    private final JPanel modValPanel;
    private final JButton copyButton;
    private final JButton doneButton;
    private final JButton cancelButton;
    private final JButton editValButton;

    private final JButton addValButton;
    private final JButton delValButton;
    private final JButton editHdlButton;
    private final JButton replaceButton;
    private final JPopupMenu addValPopup;

    private final JButton saveToFileButton;
    private final JButton loadFromFileButton;

    boolean overwriteWhenExists = false;

    public ShowValuesWindow(AdminToolUI appUI) {
        super("Show Handle Values");
        this.ui = appUI;

        setJMenuBar(appUI.getAppMenu());

        addValPopup = new JPopupMenu();
        addValPopup.add(new HDLAction(ui, "add_url_val", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addValueWithParams(1, Common.STD_TYPE_URL, Util.encodeString("http://example.com/"));
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_email_val", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addValueWithParams(1, Common.STD_TYPE_EMAIL, Util.encodeString("you@example.com"));
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_admin_val", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                HandleValue adminValue = ui.getMainWindow().getDefaultAdminRecord(handle);
                adminValue.setIndex(getNextUnusedIndex(adminValue.getIndex()));
                addValue(adminValue);
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_blank_val", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addValueWithParams(1, new byte[0], Util.encodeString(""));
            }
        }));

        addValPopup.addSeparator();

        addValPopup.add(new HDLAction(ui, "add_digest_and_sig_all", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addDigestAndSignatureForAllValues();
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_jose_signature_selected", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addJoseSignatureOfSelectedValues();
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_jose_signature_all", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addJoseSignatureOfAllValues();
            }
        }));

        addValPopup.add(new HDLAction(ui, "add_cert", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                addCert();
            }
        }));

        addValPopup.pack();

        valuesListModel = new DefaultListModel<>();
        valuesList = new JList<>(valuesListModel);
        valuesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        valuesList.setCellRenderer(new HandleValueRenderer());
        doneButton = new JButton(ui.getStr("ok"));
        cancelButton = new JButton(ui.getStr("cancel"));
        editValButton = new JButton(ui.getStr("edit_val_button"));
        addValButton = new JButton(ui.getStr("add_val_button"));
        delValButton = new JButton(ui.getStr("remove_val_button"));
        editHdlButton = new JButton(ui.getStr("edit_hdl_button"));
        replaceButton = new JButton("Replace Mode");
        copyButton = new JButton(ui.getStr("copy_hdl_button"));
        saveToFileButton = new JButton("Save to file");
        loadFromFileButton = new JButton("Load from file");

        saveValuesJsonAction = new HDLAction(ui, "save_values_json", "save_values_json", this);
        saveValuesBinaryAction = new HDLAction(ui, "save_values_binary", "save_values_binary", this);
        saveValueAction = new HDLAction(ui, "save_value", "save_value", this);
        editHandleAction = new HDLAction(ui, "edit_handle", "edit_handle", this);
        viewValueAction = new HDLAction(ui, "view_value", "view_value", this);
        editValueAction = new HDLAction(ui, "edit_value", "edit_value", this);
        testSiteAction = new HDLAction(ui, "run_site_test", "run_site_test", this);
        signHandleAction = new HDLAction(ui, "sign_handle", "sign_handle", this);

        JPanel p = new JPanel(new GridBagLayout());
        p.add(new JScrollPane(valuesList), GridC.getc(0, 0).wxy(1, 1).colspan(3).fillboth());
        modValPanel = new JPanel(new GridBagLayout());
        modValPanel.add(addValButton, GridC.getc(0, 0).insets(0, 0, 0, 10));
        modValPanel.add(editValButton, GridC.getc(1, 0).insets(0, 0, 0, 10));
        modValPanel.add(delValButton, GridC.getc(2, 0).insets(0, 0, 0, 10));
        modValPanel.add(new JButton(signHandleAction), GridC.getc(3, 0).insets(0, 0, 0, 10));
        p.add(modValPanel, GridC.getc(0, 1).insets(10, 10, 2, 10).fillboth());
        p.add(editHdlButton, GridC.getc(0, 1).insets(10, 10, 2, 10).west());
        p.add(Box.createHorizontalStrut(200), GridC.getc(1, 1).wx(1).fillx().insets(5, 5, 0, 5));
        p.add(copyButton, GridC.getc(2, 1).insets(10, 5, 2, 10).east());
        JPanel bottomLineLeft = new JPanel(new GridBagLayout());
        bottomLineLeft.add(cancelButton, GridC.getc(1, 0).insets(0, 0, 0, 10));
        bottomLineLeft.add(doneButton, GridC.getc(2, 0).insets(0, 0, 0, 10));
        JPanel bottomLineRight = new JPanel(new GridBagLayout());
        bottomLineRight.add(replaceButton, GridC.getc(0, 0).insets(0, 0, 0, 10).west());
        bottomLineRight.add(loadFromFileButton, GridC.getc(1, 0).insets(0, 0, 0, 10).west());
        bottomLineRight.add(saveToFileButton, GridC.getc(2, 0).insets(0, 0, 0, 0).west());
        p.add(bottomLineRight, GridC.getc(0, 2).insets(2, 10, 10, 0).west());
        p.add(bottomLineLeft, GridC.getc(1, 2).insets(2, 10, 10, 0).colspan(2).east());

        getContentPane().add(p);

        cancelButton.addActionListener(this);
        doneButton.addActionListener(this);
        editValButton.addActionListener(this);
        addValButton.addActionListener(this);
        delValButton.addActionListener(this);
        editHdlButton.addActionListener(this);
        copyButton.addActionListener(this);
        saveToFileButton.addActionListener(this);
        loadFromFileButton.addActionListener(this);
        replaceButton.addActionListener(this);
        valuesList.addMouseListener(this);

        getRootPane().setDefaultButton(doneButton);

        setSize(700, 330);
    }

    @Override
    public void mousePressed(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            showPopupMenu(evt);
        }
    }

    @Override
    public void mouseReleased(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            showPopupMenu(evt);
        }
    }

    @Override
    public void mouseExited(MouseEvent evt) {
    }

    @Override
    public void mouseEntered(MouseEvent evt) {
    }

    @Override
    public void mouseClicked(MouseEvent evt) {
        if (evt.isPopupTrigger()) {
            showPopupMenu(evt);
        } else if (evt.getClickCount() >= 2) {
            if (mode == EDIT_HDL_MODE || mode == CREATE_HDL_MODE) {
                editValue();
            } else {
                viewSelectedValue();
            }
        }
    }

    private boolean performOperation(String opName, AbstractRequest req) {
        AuthenticationInfo auth = ui.getAuthentication(false);
        if (auth == null) {
            return false;
        }
        req.authInfo = auth;

        try {
            AbstractResponse resp;
            SiteInfo site = ui.getSpecificSite();
            if (ui.getSpecificSiteDoNotRefer()) req.doNotRefer = true;
            if (site == null) {
                resp = ui.getMain().getResolver().processRequest(req);
            } else {
                resp = ui.getMain().getResolver().sendRequestToSite(req, site);
            }
            if (resp == null || resp.responseCode != AbstractMessage.RC_SUCCESS) {
                JOptionPane.showMessageDialog(this, "The '" + opName + "' operation" + " was not successful.  Response was: " + resp);
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "There was an error processing the" + " '" + opName + "' operation.\n\nMessage: " + e);
            return false;
        }
    }

    private void deleteValue() {
        List<HandleValue> selObjs = valuesList.getSelectedValuesList();
        if (selObjs == null || selObjs.isEmpty()) {
            return;
        }

        if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "Are you sure you would like to delete the selected values?", "Delete Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
            if (mode == EDIT_HDL_MODE) {
                int idxList[] = new int[selObjs.size()];
                for (int i = 0; i < idxList.length; i++) {
                    idxList[i] = selObjs.get(i).getIndex();
                }
                if (performOperation("remove value", new RemoveValueRequest(Util.encodeString(this.handle), idxList, null))) {
                    for (int i = 0; i < selObjs.size(); i++) {
                        valuesListModel.removeElement(selObjs.get(i));
                    }
                }
            } else {
                for (int i = 0; i < selObjs.size(); i++) {
                    valuesListModel.removeElement(selObjs.get(i));
                }
            }
        }
    }

    private void viewSelectedValue() {
        int selIdx = valuesList.getSelectedIndex();
        if (selIdx < 0) {
            return;
        }
        HandleValue value = valuesListModel.getElementAt(selIdx);
        if (value == null) {
            return;
        }

        EditValueWindow editWin = new EditValueWindow(ui, this, handle, getValues());
        editWin.loadValueData(value.duplicate(), false);
        editWin.setMode(EditValueWindow.VIEW_MODE);
        editWin.setVisible(true);
    }

    private void showPopupMenu(MouseEvent evt) {
        int index = valuesList.locationToIndex(evt.getPoint());

        HandleValue value = null;
        if (index >= 0) {
            valuesList.setSelectedIndex(index);
            value = valuesListModel.getElementAt(index);
        }

        JPopupMenu popup = new JPopupMenu();
        if (mode == VIEW_HDL_MODE) {
            if (index >= 0) {
                popup.add(viewValueAction);
            }
            popup.add(editHandleAction);
            popup.addSeparator();
        }
        if (mode == EDIT_HDL_MODE && index >= 0) {
            popup.add(editValueAction);
            popup.addSeparator();
        }
        if (value != null && (value.hasType(Common.STD_TYPE_HSSITE) || value.hasType(Common.LEGACY_DERIVED_PREFIX_SITE_TYPE))) {
            popup.add(testSiteAction);
            popup.addSeparator();
        }

        if (index >= 0) {
            popup.add(saveValueAction);
        }
        popup.add(saveValuesJsonAction);
        popup.add(saveValuesBinaryAction);

        popup.pack();
        popup.show(valuesList, evt.getX(), evt.getY());
    }

    private void editValue() {
        final int selIdx = valuesList.getSelectedIndex();
        if (selIdx < 0 || valuesList.getSelectedIndices().length > 1) {
            return;
        }
        HandleValue originalValue = valuesListModel.getElementAt(selIdx);
        if (originalValue == null) {
            return;
        }

        final HandleValue valueToEdit = originalValue.duplicate();

        EditValueWindow editWin = new EditValueWindow(ui, this, handle, getValues());
        editWin.loadValueData(valueToEdit, false);
        AwtUtil.setWindowPosition(editWin, this);
        @SuppressWarnings("hiding")
        final String handle = this.handle;
        editWin.setSaveCallback(new Runnable() {
            @Override
            public void run() {
                if (mode == EDIT_HDL_MODE) { // actually save the modified value
                    if (performOperation("edit value", new ModifyValueRequest(Util.encodeString(handle), valueToEdit, null))) {
                        valuesListModel.setElementAt(valueToEdit, selIdx);
                        valuesList.repaint();
                    }
                } else {
                    valuesListModel.setElementAt(valueToEdit, selIdx);
                    valuesList.repaint();
                }
            }
        });
        editWin.setVisible(true);
    }

    private void saveValuesBinary() {
        Object[] valueObjects = valuesListModel.toArray();

        if (valueObjects == null || valueObjects.length <= 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        FileDialog fwin = new FileDialog(this, // AwtUtil.getFrame(this),
            ui.getStr("choose_values_file_to_save"), FileDialog.SAVE);
        fwin.setVisible(true);
        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) {
            return;
        }

        try {
            HandleValue values[] = new HandleValue[valueObjects.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = (HandleValue) valueObjects[i];
            }

            byte[] buffer = Encoder.encodeGlobalValues(values);
            try (FileOutputStream fout = new FileOutputStream(new File(dirName, fileName))) {
                fout.write(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, ui.getStr("error_saving_values") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        }

    }

    private void saveSelectedValue() {
        int selectedIdx = valuesList.getSelectedIndex();
        if (selectedIdx < 0 || valuesList.getSelectedIndices().length > 1) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        HandleValue val = valuesListModel.getElementAt(selectedIdx);
        if (val == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        FileDialog fwin = new FileDialog(this, ui.getStr("choose_value_file_to_save"), FileDialog.SAVE);
        fwin.setVisible(true);
        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) {
            return;
        }

        try (FileOutputStream fout = new FileOutputStream(new File(dirName, fileName))) {
            fout.write(val.getData());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, ui.getStr("error_saving_value") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public int getNextUnusedIndex(int firstIdx) {
        int nextIdx = firstIdx - 1;
        boolean duplicate = true;
        while (duplicate) {
            nextIdx++;
            duplicate = false;
            for (int i = valuesListModel.getSize() - 1; i >= 0; i--) {
                HandleValue val = valuesListModel.getElementAt(i);
                if (val != null && val.getIndex() == nextIdx) {
                    duplicate = true;
                    break;
                }
            }
        }
        return nextIdx;
    }

    private void addValue(final HandleValue newValue) {
        EditValueWindow editWin = new EditValueWindow(ui, this, handle, getValues());
        editWin.setTitle("Add Handle Value");
        editWin.setMode(EditValueWindow.ADD_MODE);
        editWin.loadValueData(newValue, true);
        AwtUtil.setWindowPosition(editWin, this);
        @SuppressWarnings("hiding")
        final String handle = this.handle;
        editWin.setSaveCallback(new Runnable() {
            @Override
            public void run() {
                if (mode == EDIT_HDL_MODE) { // actually save the modified value
                    if (performOperation("add value", new AddValueRequest(Util.encodeString(handle), newValue, null))) {
                        valuesListModel.addElement(newValue);
                    }
                } else {
                    valuesListModel.addElement(newValue);
                }
            }
        });
        editWin.setVisible(true);
    }

    public HandleValue[] getValues() {
        HandleValue[] values = new HandleValue[valuesListModel.size()];
        for (int i = 0; i < valuesListModel.size(); i++) {
            values[i] = valuesListModel.get(i);
        }
        return values;
    }

    private void replaceLegacyAdminValues() {
        if (valuesListModel == null) return;
        for (int i = 0; i < valuesListModel.getSize(); i++) {
            HandleValue value = valuesListModel.get(i);
            if (value.hasType(Common.ADMIN_TYPE)) {
                try {
                    AdminRecord adminRecord = Encoder.decodeAdminRecord(value.getData(), 0);
                    if (adminRecord.legacyByteLength) {
                        adminRecord.legacyByteLength = false;
                        value.setData(Encoder.encodeAdminRecord(adminRecord));
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    public void setValues(String handle, HandleValue values[], int mode) {
        if (values != null) {
        }
        this.mode = mode;
        this.handle = handle;

        if (values != null) {
            valuesListModel.removeAllElements();
            for (int i = 0; i < values.length; i++) {
                valuesListModel.addElement(values[i].duplicate());
            }
        }

        if (mode == CREATE_HDL_MODE) {
            if (overwriteWhenExists) {
                replaceLegacyAdminValues();
            }
            setTitle("Create Handle: " + handle);
            doneButton.setText(overwriteWhenExists ? "Replace Handle" : ui.getStr("create_hdl_button"));
            cancelButton.setVisible(true);
            editHdlButton.setVisible(false);
            replaceButton.setVisible(false);
            loadFromFileButton.setVisible(true);
            // copyButton.setVisible(true);
            modValPanel.setVisible(true);
            modValPanel.setEnabled(true);
        } else if (mode == EDIT_HDL_MODE) {
            setTitle("Edit Handle: " + handle);
            doneButton.setText(ui.getStr("dismiss"));
            cancelButton.setVisible(false);
            editHdlButton.setVisible(false);
            replaceButton.setVisible(true);
            loadFromFileButton.setVisible(false);
            // copyButton.setVisible(true);
            modValPanel.setVisible(true);
            modValPanel.setEnabled(true);
        } else if (mode == VIEW_HDL_MODE) {
            setTitle("View Handle: " + handle);
            doneButton.setText(ui.getStr("dismiss"));
            cancelButton.setVisible(false);
            // copyButton.setVisible(true);
            editHdlButton.setVisible(true);
            replaceButton.setVisible(false);
            loadFromFileButton.setVisible(false);
            modValPanel.setVisible(false);
            modValPanel.setEnabled(false);
        } else {
            setTitle("????: " + handle);
            doneButton.setText("????");
            cancelButton.setVisible(true);
            // copyButton.setVisible(false);
            editHdlButton.setVisible(false);
            modValPanel.setVisible(false);
            modValPanel.setEnabled(false);
        }
    }

    private void cancelButtonPressed() {
        setVisible(false);
    }

    private void doneButtonPressed() {
        if (mode == CREATE_HDL_MODE) {
            HandleValue values[] = new HandleValue[valuesListModel.getSize()];
            for (int i = 0; i < values.length; i++) {
                values[i] = valuesListModel.getElementAt(i);
            }

            CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(this.handle), values, null);
            req.overwriteWhenExists = overwriteWhenExists;
            boolean success = performOperation(overwriteWhenExists ? "replace handle" : "create handle", req);
            if (success) {
                JOptionPane.showMessageDialog(this, "The handle '" + this.handle + "' " + " was " + (overwriteWhenExists ? "replaced!" : "created!"));

                // if (!overwriteWhenExists)
                setVisible(false);
            }
        } else if (mode == EDIT_HDL_MODE) {
            // values are added/deleted/modified individually
            // so nothing needs to be done here
            setVisible(false);
        } else if (mode == VIEW_HDL_MODE) {
            setVisible(false);
        }
    }

    private void saveToFile() {
        HandleValue values[] = new HandleValue[valuesListModel.getSize()];
        valuesListModel.copyInto(values);
        HandleRecord record = new HandleRecord(handle, values);
        String json = GsonUtility.getPrettyGson().toJson(record);
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Save", FileDialog.SAVE);

        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        try {
            FileOutputStream fout = new FileOutputStream(new File(dirName, fileName));
            fout.write(Util.encodeString(json));
            fout.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: saving file: " + e);
        }
    }

    private void loadFromFile() {
        FileDialog fwin = new FileDialog(AwtUtil.getFrame(this), "Choose File to Load", FileDialog.LOAD);

        fwin.setVisible(true);

        String fileName = fwin.getFile();
        String dirName = fwin.getDirectory();
        if (fileName == null || dirName == null) return;

        try {
            byte[] buf;
            FileInputStream fin = new FileInputStream(new File(dirName, fileName));
            try {
                buf = StreamUtil.readFully(fin);
            } finally {
                fin.close();
            }
            HandleValue[] values;
            if (Util.looksLikeBinary(buf)) {
                values = Encoder.decodeHandleValues(buf);
            } else {
                String json = Util.decodeString(buf);
                HandleRecord record = GsonUtility.getGson().fromJson(json, HandleRecord.class);
                values = record.getValuesAsArray();
            }
            valuesListModel.clear();
            for (HandleValue value : values) {
                valuesListModel.addElement(value);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: loading file: " + e);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == doneButton) {
            doneButtonPressed();
        } else if (src == cancelButton) {
            cancelButtonPressed();
        } else if (src == delValButton) {
            deleteValue();
        } else if (src == editValButton) {
            editValue();
        } else if (src == addValButton) {
            showAddValuePopup();
        } else if (src == editHdlButton || editHandleAction.matchesCommand(evt)) {
            String hdl = handle;
            if (ui.getMainWindow().editHandle(this, hdl)) {
                cancelButtonPressed();
            }
        } else if (src == replaceButton) {
            overwriteWhenExists = true;
            setValues(handle, null, CREATE_HDL_MODE);
        } else if (src == copyButton) {
            String newHdlStr = JOptionPane.showInputDialog(this, ui.getStr("copy_hdl_prompt"));
            if (newHdlStr == null) {
                return;
            }
            MainWindow.ActionHandler handler = (ui.getMainWindow()).new ActionHandler(MainWindow.ActionHandler.CREATE_ACTION, newHdlStr);
            HandleValue newValues[] = new HandleValue[valuesListModel.getSize()];
            for (int i = 0; i < newValues.length; i++) {
                newValues[i] = valuesListModel.getElementAt(i);
            }
            handler.setValues(newValues);
            handler.performAction();
        } else if (src == saveToFileButton) {
            saveToFile();
        } else if (src == loadFromFileButton) {
            loadFromFile();
        } else if (saveValuesJsonAction.matchesCommand(evt)) {
            saveToFile();
        } else if (saveValuesBinaryAction.matchesCommand(evt)) {
            saveValuesBinary();
        } else if (saveValueAction.matchesCommand(evt)) {
            saveSelectedValue();
        } else if (viewValueAction.matchesCommand(evt)) {
            viewSelectedValue();
        } else if (editValueAction.matchesCommand(evt)) {
            editValue();
        } else if (signHandleAction.matchesCommand(evt)) {
            addJoseSignatureOfAllValues();
        } else if (testSiteAction.matchesCommand(evt)) {
            List<HandleValue> selObjs = valuesList.getSelectedValuesList();
            for (int i = 0; selObjs != null && i < selObjs.size(); i++) {
                try {
                    HandleValue siteValue = selObjs.get(i);
                    if (siteValue != null) {
                        SiteTester tester = new SiteTester(this, ui, siteValue);
                        tester.setLocationRelativeTo(this);
                        tester.setVisible(true);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Error parsing site information: " + e, "Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    private void showError(Throwable error) {
        JOptionPane.showMessageDialog(this, ui.getStr("error_message") + "\n\n" + error, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        error.printStackTrace();
    }

    private void showError(String error) {
        JOptionPane.showMessageDialog(this, ui.getStr("error_message") + "\n\n" + error, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
    }

    private void addJoseSignatureOfSelectedValues() {
        List<HandleValue> selObjs = valuesList.getSelectedValuesList();
        if (selObjs == null || selObjs.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        HandleValue values[] = selObjs.toArray(new HandleValue[0]);
        addJoseSignatureOfValues(values);
    }

    private void addJoseSignatureOfAllValues() {
        Object objVals[] = valuesListModel.toArray();
        HandleValue values[] = new HandleValue[objVals.length];
        System.arraycopy(objVals, 0, values, 0, values.length);
        addJoseSignatureOfValues(values);
    }

    private void addJoseSignatureOfValues(HandleValue[] values) {
        SignerInfo sigInfo = ui.getSignatureInfo(this, false);
        if (sigInfo == null) {
            return;
        }
        long now = System.currentTimeMillis() / 1000L - 600;
        long oneYearInSeconds = 366L * 24L * 60L * 60L;
        long expiration = System.currentTimeMillis() / 1000L + (oneYearInSeconds * 2);
        HandleSigner handleSigner = new HandleSigner();
        HandleClaimsSet claims = handleSigner.createPayload(handle, Util.filterOnlyPublicValues(Arrays.asList(values)), sigInfo.getUserValueReference(), null, now, expiration);
        try {
            JsonWebSignature jws = sigInfo.signClaimsSet(claims);
            HandleValue signatureHandleValue = new HandleValue(getNextUnusedIndex(400), "HS_SIGNATURE", jws.serialize());
            addValue(signatureHandleValue);
        } catch (TrustException e) {
            showError(e);
        }
    }

    private void addCert() {
        SignerInfo sigInfo = ui.getSignatureInfo(this, false);
        if (sigInfo == null) {
            return;
        }

        ValueReference signer = sigInfo.getUserValueReference();

        long oneYearInSeconds = 366L * 24L * 60L * 60L;
        long expiration = System.currentTimeMillis() / 1000L + (oneYearInSeconds * 2);
        HandleClaimsSet claims = new HandleClaimsSet();
        claims.sub = "";
        if (signer != null) {
            claims.iss = signer.toString();
        } else {
            claims.iss = "";
        }
        claims.iat = System.currentTimeMillis() / 1000L;
        claims.nbf = System.currentTimeMillis() / 1000L - 600;
        claims.exp = expiration;
        claims.chain = null;
        claims.perms = Collections.emptyList();

        try {
            JsonWebSignature jws = sigInfo.signClaimsSet(claims);
            HandleValue signatureHandleValue = new HandleValue(getNextUnusedIndex(400), "HS_CERT", jws.serialize());
            addValue(signatureHandleValue);
        } catch (TrustException e) {
            showError(e);
        }
    }

    private void addDigestAndSignatureForAllValues() {
        try {
            SignerInfo sigInfo = ui.getSignatureInfo(this, false);
            if (sigInfo == null) {
                return;
            }
            if (sigInfo.isRemoteSigner()) {
                showError("Remote signer does not support legacy signatures.");
                return;
            }

            HandleValue values[] = new HandleValue[valuesListModel.getSize()];
            valuesListModel.copyInto(values);

            @SuppressWarnings("deprecation")
            HandleValue digestValue = net.handle.hdllib.HandleSignature.createDigestsValue(getNextUnusedIndex(400), handle, values);

            PublicKeyAuthenticationInfo localSigInfo = sigInfo.getLocalSignerInfo();

            ValueReference signer = new ValueReference(localSigInfo.getUserIdHandle(), localSigInfo.getUserIdIndex());
            @SuppressWarnings("deprecation")
            HandleValue sigValue = net.handle.hdllib.HandleSignature.createSignatureValue(getNextUnusedIndex(digestValue.getIndex() + 1), handle, signer, null, localSigInfo.getPrivateKey(), digestValue);

            if (mode == EDIT_HDL_MODE) {
                if (performOperation("sign values", new AddValueRequest(Util.encodeString(this.handle), new HandleValue[] { digestValue, sigValue }, null))) {
                    valuesListModel.addElement(digestValue);
                    valuesListModel.addElement(sigValue);
                }
            } else {
                valuesListModel.addElement(digestValue);
                valuesListModel.addElement(sigValue);
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void addValueWithParams(int idx, byte valType[], byte valData[]) {
        HandleValue newValue = new HandleValue();
        newValue.setType(valType);
        newValue.setData(valData);
        newValue.setIndex(getNextUnusedIndex(idx));
        addValue(newValue);
    }

    private synchronized void showAddValuePopup() {
        addValPopup.show(addValButton, 0, 0 - addValPopup.getPreferredSize().height);
    }

}

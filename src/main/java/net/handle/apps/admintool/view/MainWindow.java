/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.cnri.guiutil.GridC;
import net.cnri.util.StreamUtil;
import net.handle.awt.AwtUtil;
import net.handle.hdllib.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

public class MainWindow extends JFrame implements ActionListener {
    private static final String HDL_HIST_PREF_KEY = "handle_history";
    private static final int HISTORY_LIMIT = 50;

    private final AdminToolUI appUI;
    private final MainWindow thisObj;
    private final JTextField handleField;
    private final JLabel statusField;
    private final JLabel siteField;
    private final JLabel configDirField;
    private final JButton historyButton;
    private final JButton lookupButton;
    private final JButton createButton;
    private final JButton editButton;
    private final JButton deleteButton;
    private final JButton authButton;
    private final JButton signerButton;

    public MainWindow(AdminToolUI ui) {
        super(ui.getStr("main_win_title") + " " + ui.getMain().getVersion());
        this.appUI = ui;
        this.thisObj = this;

        setJMenuBar(ui.getAppMenu());
        handleField = new JTextField("", 30);
        historyButton = new JButton(HDLImages.singleton().getIcon(HDLImages.DOWN_TRIANGLE));
        historyButton.setBorder(null);
        lookupButton = new JButton(ui.getStr("lookup"));
        createButton = new JButton(ui.getStr("create") + "...");
        editButton = new JButton(ui.getStr("modify"));
        deleteButton = new JButton(ui.getStr("delete"));
        authButton = new JButton(ui.getStr("authenticate"));
        signerButton = new JButton(ui.getStr("signer"));
        statusField = new JLabel(" ");
        statusField.setForeground(Color.gray);
        siteField = new JLabel(" ");
        siteField.setForeground(Color.gray);
        configDirField = new JLabel(" ");
        updateResolverConfigDirLabel(ui.getMain().getResolverConfigDir());
        configDirField.setForeground(Color.gray);

        JPanel p = new JPanel(new GridBagLayout());
        JPanel p2 = new JPanel(new GridBagLayout());

        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JLabel(ui.getStr("handle") + ":"), GridC.getc(0, 0).insets(0, 0, 0, 6));
        p.add(handleField, GridC.getc(1, 0).field());
        p.add(historyButton, GridC.getc(2, 0).center().filly().insets(0, 0, 0, 6));
        p.add(lookupButton, GridC.getc(3, 0).fillx());
        p.add(configDirField, GridC.getc(0, 1).colspan(4).fillx().insets(0, 15, 0, 6));
        p.add(siteField, GridC.getc(0, 1).colspan(4).fillx().insets(0, 15, 0, 6));
        p.add(p2, GridC.getc(0, 2).colspan(4).fillx().insets(16, 0, 0, 0));

        p2.add(createButton, GridC.getc(0, 1).insets(5, 0, 0, 5));
        p2.add(editButton, GridC.getc(1, 1).insets(5, 5, 0, 5));
        p2.add(deleteButton, GridC.getc(2, 1).insets(5, 5, 0, 5));
        p2.add(statusField, GridC.getc(3, 1).wx(1).fillx().insets(5, 5, 0, 5));
        p2.add(Box.createHorizontalStrut(200), GridC.getc(3, 1).wx(1).fillx().insets(5, 5, 0, 5));
        p2.add(signerButton, GridC.getc(4, 1).fillx().insets(5, 0, 0, 0));
        p2.add(authButton, GridC.getc(5, 1).fillx().insets(5, 0, 0, 0));
        p2.add(Box.createHorizontalStrut(200), GridC.getc(3, 1));

        getContentPane().add(p);
        getRootPane().setDefaultButton(lookupButton);

        createButton.addActionListener(this);
        editButton.addActionListener(this);
        deleteButton.addActionListener(this);
        lookupButton.addActionListener(this);
        handleField.addActionListener(this);
        authButton.addActionListener(this);
        signerButton.addActionListener(this);
        historyButton.addActionListener(this);

        pack();
        setSize(getPreferredSize());
        // setMinimumSize isn't actually in 1.4
        // setMinimumSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
                appUI.shutdown();
            }
        });

    }

    @Override
    public void setVisible(boolean val) {
        super.setVisible(val);
        handleField.requestFocus();
    }

    public void setSiteFieldText(SiteInfo site, boolean doNotRefer) {
        if (site == null) {
            if (doNotRefer) {
                siteField.setText("(Sending doNotRefer)");
            } else {
                siteField.setText("");
            }
        } else {
            StringBuilder serverList = new StringBuilder();
            if (site.servers != null) {
                serverList.append(site.servers[0].getAddressString());
                for (int i = 1; i < site.servers.length; i++) {
                    serverList.append(", ").append(site.servers[i].getAddressString());
                }
            }
            byte[] descAttr = site.getAttribute(Util.encodeString("desc"));
            String desc = "";
            if (descAttr != null) {
                desc = Util.decodeString(descAttr) + "; ";
            }
            String doNotReferText = "";
            if (doNotRefer) doNotReferText = " (sending doNotRefer)";
            String text = "Querying specific site" + doNotReferText + ": " + desc + "primary:" + (site.isPrimary ? "y; " : "n; ") + "servers=[" + serverList + "]";
            siteField.setText(text);
        }
    }

    public void signerChanged(SignerInfo newSigner) {
        if (newSigner == null) {
            signerButton.setText(appUI.getStr("signer"));
        } else {
            try {
                String signerId;
                if (newSigner.isLocalSigner()) {
                    signerId = newSigner.getUserValueReference().toString();
                } else {
                    signerId = "Remote signer";
                }
                signerButton.setText(appUI.getStr("signer") + ": " + signerId);
            } catch (Exception e) {
                signerButton.setText(String.valueOf(e));
            }
        }
    }

    public void authenticationChanged(AuthenticationInfo newAuth) {
        if (newAuth == null) {
            authButton.setText(appUI.getStr("authenticate"));
        } else {
            try {
                authButton.setText(String.valueOf(newAuth.getUserIdIndex()) + " : " + Util.decodeString(newAuth.getUserIdHandle()));
            } catch (Exception e) {
                authButton.setText(String.valueOf(e));
            }
        }
    }

    public void showAsyncError(String msg) {
        SwingUtilities.invokeLater(new ErrMsgRunner(msg));
    }

    private void createHandleFromFile(String handleStr) {
        @SuppressWarnings("resource")
        FileInputStream fin = null;
        try {
            FileDialog fwin = new FileDialog(this, appUI.getStr("choose_values_file_to_load"), FileDialog.LOAD);
            fwin.setVisible(true);
            String fileName = fwin.getFile();
            String dirName = fwin.getDirectory();
            if (fileName != null && dirName != null) {
                fin = new FileInputStream(new File(dirName, fileName));
                byte[] buf = StreamUtil.readFully(fin);
                HandleValue[] values;
                if (Util.looksLikeBinary(buf)) {
                    values = Encoder.decodeHandleValues(buf);
                } else {
                    String json = Util.decodeString(buf);
                    HandleRecord record = GsonUtility.getGson().fromJson(json, HandleRecord.class);
                    values = record.getValuesAsArray();
                }
                System.err.println("parsed " + values.length + " values:");
                for (HandleValue val : values) {
                    System.err.println(" val: " + val);
                }
                ActionHandler creator = new ActionHandler(ActionHandler.CREATE_ACTION, handleStr);
                creator.setValues(values);
                creator.performAction();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, appUI.getStr("error_loading_values") + "\n\n" + e, appUI.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        } finally {
            try {
                if (fin != null) fin.close();
            } catch (Throwable t) {
            }
        }
    }

    private void createHandle(String hdl, HandleValue[] defaultValues) {
        // resolve the handle to make sure it doesn't already exist or that there
        // is some other problem with the server where it lives.
        AbstractResponse resp = null;
        try {
            ResolutionRequest req = new ResolutionRequest(Util.encodeString(hdl), null, null, null);
            req.authoritative = true;
            resp = appUI.getMain().sendMessage(req);
        } catch (Exception e) {
            showAsyncError("Warning: error checking '" + hdl + "'\n\nMessage: " + e);
            e.printStackTrace(System.err);
            return;
        }

        if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
            showAsyncError("The handle '" + hdl + "' already exists!");
            return;
        }

        AuthenticationInfo auth = appUI.getAuthentication(false);
        if (auth == null) return;

        showValues(hdl, defaultValues, ViewConstants.CREATE_HDL_MODE);
    }

    private void showValues(String hdl, HandleValue[] values, int mode) {
        SwingUtilities.invokeLater(new ShowValuesRunner(hdl, values, mode));
    }

    private void showValues(ShowValuesWindow window, String hdl, HandleValue[] values, int mode) {
        SwingUtilities.invokeLater(new ShowValuesRunner(window, hdl, values, mode));
    }

    public boolean editHandle(String hdl) {
        return editHandle(null, hdl);
    }

    public boolean editHandle(ShowValuesWindow window, String hdl) {
        if (hdl == null || hdl.trim().length() <= 0) return false;

        AuthenticationInfo auth = appUI.getAuthentication(false);
        if (auth == null) return false;

        AbstractResponse resp = null;
        try {
            ResolutionRequest req = new ResolutionRequest(Util.encodeString(hdl), null, null, auth);
            req.authoritative = true;
            req.certify = true;
            req.ignoreRestrictedValues = false;
            resp = appUI.getMain().sendMessage(req);
        } catch (Exception e) {
            showAsyncError("Error resolving '" + hdl + "'\n\nMessage: " + e);
            e.printStackTrace();
            return false;
        }

        if (resp != null && resp instanceof ResolutionResponse) {
            try {
                ResolutionResponse rresp = (ResolutionResponse) resp;
                showValues(window, Util.decodeString(rresp.handle), rresp.getHandleValues(), ViewConstants.EDIT_HDL_MODE);
            } catch (Exception e) {
                showAsyncError("Error parsing handle values for '" + hdl + "'\n\nMessage: " + e);
                return false;
            }
        } else {
            showAsyncError("Received unexpected response: " + resp);
            return false;
        }
        return true;
    }

    public void deleteHandle(String hdl) {
        if (hdl == null || hdl.trim().length() <= 0) return;

        AuthenticationInfo auth = appUI.getAuthentication(false);
        if (auth == null) return;

        DeleteHandleRequest dreq = new DeleteHandleRequest(Util.encodeString(hdl), auth);

        try {
            AbstractResponse resp = appUI.getMain().sendMessage(dreq);
            if (resp == null || resp.responseCode != AbstractMessage.RC_SUCCESS) {
                JOptionPane.showMessageDialog(this, "The 'delete handle' operation" + " was not successful.\nThe response was: " + resp);
                return;
            } else {
                JOptionPane.showMessageDialog(this, "The handle '" + hdl + "' " + " was deleted!");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "There was an error processing the" + " 'delete handle' operation: " + e);
            return;
        }
    }

    public void lookupHandle(String hdl) {
        AbstractResponse resp = null;
        try {
            resp = appUI.getMain().sendMessage(new ResolutionRequest(Util.encodeString(hdl), null, null, null));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error resolving '" + hdl + "'\nMessage: " + e);
            e.printStackTrace();
            return;
        }

        if (resp instanceof ResolutionResponse) {
            try {
                ResolutionResponse rresp = (ResolutionResponse) resp;
                ShowValuesWindow showValsWindow = new ShowValuesWindow(appUI);
                showValsWindow.setValues(Util.decodeString(rresp.handle), rresp.getHandleValues(), ViewConstants.VIEW_HDL_MODE);
                AwtUtil.setWindowPosition(showValsWindow, this);
                showValsWindow.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error parsing handle values for '" + hdl + "'\n\nMessage: " + e);
                e.printStackTrace(System.err);
                return;
            }
        } else {
            JOptionPane.showMessageDialog(this, "Received unexpected response: " + resp);
            return;
        }
    }

    HandleValue makeValueWithParams(int idx, byte[] valType, byte[] valData) {
        HandleValue newValue = new HandleValue();
        newValue.setType(valType);
        newValue.setData(valData);
        newValue.setIndex(idx);
        return newValue;
    }

    HandleValue getDefaultAdminRecord(String hdl) {
        AdminRecord adminInfo = new AdminRecord();
        if (hdl == null) hdl = "0.NA/test";
        String defaultPrefixAdmin = null;
        if (hdl.startsWith("0.NA/")) {
            defaultPrefixAdmin = appUI.getMain().prefs().getStr("default_prefix_admin");
            if (defaultPrefixAdmin == null) {
                String suffix = hdl.substring(5);
                if (suffix.length() > 2 && suffix.charAt(2) == '.') {
                    defaultPrefixAdmin = "200:0.NA/" + suffix.substring(0, 2) + ".ADMIN";
                } else if (suffix.length() > 3 && suffix.charAt(3) == '.') {
                    defaultPrefixAdmin = "200:0.NA/" + suffix.substring(0, 3) + ".ADMIN";
                }
            }
        }
        if (defaultPrefixAdmin == null) {
            byte[] hdlBytes = Util.encodeString(hdl);
            adminInfo.adminId = Util.getZeroNAHandle(hdlBytes);
            adminInfo.adminIdIndex = 200;
        } else {
            ValueReference defaultPrefixAdminValRef = ValueReference.fromString(defaultPrefixAdmin);
            adminInfo.adminId = defaultPrefixAdminValRef.handle;
            adminInfo.adminIdIndex = defaultPrefixAdminValRef.index;
        }
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
        return makeValueWithParams(100, Common.STD_TYPE_HSADMIN, Encoder.encodeAdminRecord(adminInfo));
    }

    JPopupMenu createHdlPopup = null;

    private synchronized void showCreateHandlePopup() {
        if (createHdlPopup == null) {
            createHdlPopup = new JPopupMenu();
            createHdlPopup.add(new HDLAction(appUI, "create_simple_url_hdl", "", new ActionListener() {
                // template handler for creating simple-URL style handles
                @Override
                public void actionPerformed(ActionEvent evt) {
                    String hdlStr = handleField.getText().trim();
                    if (hdlStr.length() <= 0) {
                        JOptionPane.showMessageDialog(thisObj, appUI.getStr("invalid_hdl_msg"));
                        return;
                    }
                    String urlStr = JOptionPane.showInputDialog(thisObj, appUI.getStr("enter_url_prompt") + hdlStr);
                    if (urlStr == null) return;

                    ActionHandler handler = new ActionHandler(ActionHandler.CREATE_ACTION, hdlStr);
                    handler.setValues(new HandleValue[] { makeValueWithParams(1, Common.STD_TYPE_URL, Util.encodeString(urlStr)), getDefaultAdminRecord(hdlStr) });
                    handler.performAction();
                }
            }));

            createHdlPopup.add(new HDLAction(appUI, "create_simple_email_hdl", "", new ActionListener() {
                // template handler for creating simple-URL style handles
                @Override
                public void actionPerformed(ActionEvent evt) {
                    String hdlStr = handleField.getText().trim();
                    String emailStr = JOptionPane.showInputDialog(thisObj, appUI.getStr("enter_email_prompt") + hdlStr);
                    if (emailStr == null) return;

                    ActionHandler handler = new ActionHandler(ActionHandler.CREATE_ACTION, hdlStr);
                    handler.setValues(new HandleValue[] { makeValueWithParams(1, Common.STD_TYPE_EMAIL, Util.encodeString(emailStr)), getDefaultAdminRecord(hdlStr) });
                    handler.performAction();
                }
            }));

            createHdlPopup.add(new HDLAction(appUI, "create_blank_hdl", "", new ActionListener() {
                // template handler for creating simple-URL style handles
                @Override
                public void actionPerformed(ActionEvent evt) {
                    String hdlStr = handleField.getText().trim();
                    ActionHandler handler = new ActionHandler(ActionHandler.CREATE_ACTION, hdlStr);
                    handler.setValues(new HandleValue[] { getDefaultAdminRecord(hdlStr) });
                    handler.performAction();
                }
            }));

            createHdlPopup.addSeparator();

            createHdlPopup.add(new HDLAction(appUI, "create_handle_from_file", "", new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    createHandleFromFile(handleField.getText().trim());
                }
            }));

            createHdlPopup.pack();
        } // end createHdlPopup construction

        createHdlPopup.show(createButton, 0, 0 - createHdlPopup.getPreferredSize().height);
    }

    private void showHistoryPopup() {
        JPopupMenu popup = new JPopupMenu();
        String[] hdlHist = appUI.getMain().prefs().getStr(HDL_HIST_PREF_KEY, "").split("\t");
        for (int i = 0; i < hdlHist.length; i++) {
            if (hdlHist[i] == null || hdlHist[i].trim().length() <= 0) continue;
            popup.add(new HDLAction(null, hdlHist[i], hdlHist[i], popupAction));
        }
        popup.pack();
        popup.setSize(popup.getPreferredSize());
        popup.show(historyButton, historyButton.getWidth() - popup.getWidth(), historyButton.getHeight());
    }

    // add the currently entered handle into the history of handles and save the current
    // history to the user's preferences
    private void saveHandleToHistory() {
        String hdl = handleField.getText().trim();
        if (hdl.length() <= 0) return;

        String[] hdlHist = appUI.getMain().prefs().getStr(HDL_HIST_PREF_KEY, "").split("\t");

        if (hdlHist.length > 0 && hdlHist[0].equals(hdl)) return; // no need to update history

        ArrayList<String> history = new ArrayList<>();
        history.addAll(Arrays.asList(hdlHist));
        if (history.contains(hdl)) history.remove(hdl);
        history.add(0, hdl);

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < history.size() && i < HISTORY_LIMIT; i++) {
            if (i != 0) sb.append('\t');
            sb.append(history.get(i));
        }
        appUI.getMain().prefs().put(HDL_HIST_PREF_KEY, sb.toString());
        try {
            appUI.getMain().savePreferences();
        } catch (Exception e) {
            System.err.println("Error saving preferences");
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        ActionHandler handler = null;
        if (src == createButton) {
            saveHandleToHistory();
            showCreateHandlePopup();
        } else if (src == editButton) {
            saveHandleToHistory();
            handler = new ActionHandler(ActionHandler.EDIT_ACTION, handleField.getText());
        } else if (src == deleteButton) {
            saveHandleToHistory();
            handler = new ActionHandler(ActionHandler.DELETE_ACTION, handleField.getText());
        } else if (src == lookupButton || src == handleField) {
            saveHandleToHistory();
            handler = new ActionHandler(ActionHandler.RESOLVE_ACTION, handleField.getText());
        } else if (src == authButton) {
            appUI.getAuthentication(true);
        } else if (src == signerButton) {
            appUI.getSignatureInfo(true);
        } else if (src == historyButton) {
            showHistoryPopup();
        }

        if (handler != null) {
            handler.performAction();
        }
    }

    public void updateResolverConfigDirLabel(String newConfigDir) {
        if (newConfigDir == null) {
            configDirField.setText("Current resolver configuration not usign a config directory");
            return;
        }
        boolean isDefaultDirectory = false;
        try {
            isDefaultDirectory = Files.isSameFile(Paths.get(newConfigDir), FilesystemConfiguration.getDefaultConfigDir().toPath());
        } catch (IOException e) {
            // ignore
        }
        if (isDefaultDirectory) {
            configDirField.setText(" ");
        } else {
            configDirField.setText("Current config directory: " + newConfigDir);
        }
    }

    public class ActionHandler implements Runnable {
        public static final String CREATE_ACTION = "create_action";
        public static final String EDIT_ACTION = "edit_action";
        public static final String DELETE_ACTION = "delete_action";
        public static final String RESOLVE_ACTION = "resolve_action";
        private final String action;
        private final String hdl;
        private HandleValue[] values = null; // used when creating a handle from a template

        public ActionHandler(String action, String hdl) {
            this.action = action;
            this.hdl = hdl;
        }

        public void setValues(HandleValue[] newValues) {
            this.values = newValues;
        }

        public void performAction() {
            if (hdl == null || hdl.trim().length() <= 0) return;

            if (action.equals(DELETE_ACTION)) {
                int result = JOptionPane.showConfirmDialog(thisObj, "Are you sure that you want to delete '" + hdl + "'?\n\nThis cannot be undone.", "Delete Handle Confirmation", JOptionPane.OK_CANCEL_OPTION);
                if (result != JOptionPane.OK_OPTION) {
                    return;
                }
            }

            statusField.setText(toString());
            Thread t = new Thread(this);
            t.start();
        }

        @Override
        public void run() {
            if (action.equals(CREATE_ACTION)) createHandle(hdl, values);
            else if (action.equals(EDIT_ACTION)) editHandle(hdl);
            else if (action.equals(DELETE_ACTION)) deleteHandle(hdl);
            else if (action.equals(RESOLVE_ACTION)) lookupHandle(hdl);
            statusField.setText("");
        }

        @Override
        public String toString() {
            return appUI.getStr(action) + " " + hdl;
        }

    }

    private class ShowValuesRunner implements Runnable {
        private final ShowValuesWindow window;
        private final String hdl;
        private final HandleValue[] values;
        private final int mode;

        ShowValuesRunner(String hdl, HandleValue[] values, int mode) {
            this(null, hdl, values, mode);
        }

        ShowValuesRunner(ShowValuesWindow window, String hdl, HandleValue[] values, int mode) {
            this.window = window;
            this.hdl = hdl;
            this.values = values;
            this.mode = mode;
        }

        @Override
        public void run() {
            ShowValuesWindow showValsWindow = window;
            if (window == null) {
                showValsWindow = new ShowValuesWindow(appUI);
            }
            showValsWindow.setValues(hdl, values, mode);
            if (window == null) AwtUtil.setWindowPosition(showValsWindow, thisObj);
            showValsWindow.setVisible(true);
        }

    }

    private class ErrMsgRunner implements Runnable {
        private final String msg;

        ErrMsgRunner(String msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            JOptionPane.showMessageDialog(thisObj, msg);
        }
    }

    private final ActionListener popupAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent evt) {
            handleField.setText(evt.getActionCommand());
        }
    };

}

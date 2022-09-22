/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.apps.admintool.controller.AuthenticationUtil;
import net.handle.apps.admintool.controller.Main;
import net.handle.apps.admintool.view.resources.Resources;
import net.handle.awt.TaskIndicator;
import net.handle.hdllib.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.Locale;
import java.util.ResourceBundle;

public class AdminToolUI {
    private Main main = null;
    private AdminToolUI ui = null;
    private Resources resources = null;
    private MainWindow mainWin = null;
    @Deprecated
    private TxnReviewWindow txnReviewWindow = null;
    private BatchRunnerWindow batchWin = null;
    private Console consoleWin = null;
    private AuthenticationInfo authInfo = null;
    private SignerInfo sigInfo = null;
    private SiteInfo specificSite = null;
    private boolean specificSiteDoNotRefer;
    private final Hashtable<String, ImageIcon> imageCache = new Hashtable<>();

    public static boolean isMacOSX = false;
    private final HDLAction openBatchFileAction;
    private final HDLAction changeConfigDirAction;
    private final HDLAction clearCacheAction;
    private final HDLAction authenticateAction;
    private final HDLAction shutdownAction;
    private final HDLAction showBatchAction;
    private final HDLAction showConsoleAction;
    private final HDLAction showHomingAction;
    private final HDLAction showCheckpointAction;
    private final HDLAction showListHandlesAction;
    private final HDLAction querySpecificSite;

    public AdminToolUI(Main main) {
        this.main = main;
        this.ui = this;

        String osName = System.getProperty("os.name", "");
        isMacOSX = osName.toUpperCase().indexOf("MAC OS X") >= 0;

        resources = (Resources) ResourceBundle.getBundle("net.handle.apps.admintool.view.resources.Resources", Locale.getDefault());

        openBatchFileAction = new HDLAction(ui, "open_batch_file", "Meta-B", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                openBatchFile();
            }
        });

        changeConfigDirAction = new HDLAction(ui, "change_config_dir", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                changeConfigDir();
            }
        });

        clearCacheAction = new HDLAction(ui, "clear_cache", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                getMain().clearHandleCache();
                JOptionPane.showMessageDialog(null, ui.getStr("cache_cleared_msg"), "Error", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        authenticateAction = new HDLAction(ui, "authenticate", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                getAuthentication(true);
            }
        });

        shutdownAction = new HDLAction(ui, "quit", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                shutdown();
            }
        });

        showBatchAction = new HDLAction(ui, "batch_processor", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showBatchProcessor();
            }
        });

        showConsoleAction = new HDLAction(ui, "console", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showConsoleWindow();
            }
        });

        showHomingAction = new HDLAction(ui, "home_unhome_prefix", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showHomeUnhomeWindow();
            }
        });

        showListHandlesAction = new HDLAction(ui, "list_handles", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showListHandlesWindow();
            }
        });

        showCheckpointAction = new HDLAction(ui, "checkpoint_server", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                showCheckpointWindow();
            }
        });

        querySpecificSite = new HDLAction(ui, "query_specific_site", "", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                querySpecificSiteTool(evt);
            }
        });
        querySpecificSite.putValue(Action.NAME, querySpecificSite.getValue(Action.NAME) + "...");
    }

    /**
     * Return the current authentication information, if any. If no authentication information has been provided then this returns null.
     */
    public SignerInfo getSignatureInfo(boolean changeInfo) {
        return getSignatureInfo(mainWin, changeInfo);
    }

    public SignerInfo getSignatureInfo(Frame owner, boolean changeInfo) {
        if (sigInfo != null && !changeInfo) {
            return sigInfo;
        }
        try {
            GetSignerInfoWindow sigWin = new GetSignerInfoWindow(owner);
            sigWin.setVisible(true);
            SignerInfo newSigInfo = sigWin.getSignatureInfo();
            if (newSigInfo == null) {
                return changeInfo ? null : sigInfo;
            } else {
                try {
                    if (main.prefs().getBoolean("check_auth", true) && newSigInfo.isLocalSigner()) {
                        CheckAuthentication checkAuth = new CheckAuthentication(newSigInfo.getLocalSignerInfo());
                        TaskIndicator ti = new TaskIndicator(null);
                        ti.invokeTask(checkAuth, "Checking Authentication ...");
                        if (checkAuth.wasSuccessful()) {
                            PublicKeyAuthenticationInfo tmpAuth = newSigInfo.getLocalSignerInfo();
                            if (tmpAuth.getUserIdIndex() == 0 && checkAuth.getIndex() != null) {
                                tmpAuth = new PublicKeyAuthenticationInfo(tmpAuth.getUserIdHandle(), checkAuth.getIndex().intValue(), tmpAuth.getPrivateKey());
                                newSigInfo = new SignerInfo(tmpAuth);
                            }
                            sigInfo = newSigInfo;
                            return sigInfo;
                        } else {
                            String msg;
                            String errorMsg = checkAuth.getErrorMessage();
                            if (errorMsg == null) {
                                msg = "Authentication failed";
                            } else {
                                msg = "Authentication failed: " + errorMsg;
                            }
                            // System.err.print("showing error message ");
                            JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
                            return null;
                        }
                    } else {
                        sigInfo = newSigInfo;
                        return sigInfo;
                    }
                } catch (Throwable t) {
                    JOptionPane.showMessageDialog(null, "Error: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        } finally {
            if (mainWin != null) {
                mainWin.signerChanged(sigInfo);
            }
            txnReviewWindowSignerChanged();
        }
    }

    @SuppressWarnings("deprecation")
    private void txnReviewWindowSignerChanged() {
        if (txnReviewWindow != null) {
            txnReviewWindow.signerChanged(sigInfo);
        }
    }

    public synchronized AuthenticationInfo getAuthentication(boolean changeAuthentication) {
        if (authInfo != null && !changeAuthentication) {
            return authInfo;
        }

        try {
            AuthWindow authWin = new AuthWindow(null);
            authWin.setVisible(true);
            AuthenticationInfo tmpAuth = authWin.getAuthentication();
            if (tmpAuth == null) {
                return changeAuthentication ? null : authInfo;
            } else {
                try {
                    if (main.prefs().getBoolean("check_auth", true)) {
                        CheckAuthentication checkAuth = new CheckAuthentication(tmpAuth);
                        TaskIndicator ti = new TaskIndicator(null);
                        ti.invokeTask(checkAuth, "Checking Authentication ...");
                        if (checkAuth.wasSuccessful()) {
                            if (tmpAuth instanceof PublicKeyAuthenticationInfo && tmpAuth.getUserIdIndex() == 0 && checkAuth.getIndex() != null) {
                                tmpAuth = new PublicKeyAuthenticationInfo(tmpAuth.getUserIdHandle(), checkAuth.getIndex().intValue(), ((PublicKeyAuthenticationInfo) tmpAuth).getPrivateKey());
                            }
                            authInfo = tmpAuth;
                            ClientSessionTracker sessionTracker = new ClientSessionTracker();
                            sessionTracker.setSessionSetupInfo(new SessionSetupInfo());
                            main.getResolver().setSessionTracker(sessionTracker);
                            return authInfo;
                        } else {
                            String msg;
                            String errorMsg = checkAuth.getErrorMessage();
                            if (errorMsg == null) {
                                msg = "Authentication failed";
                            } else {
                                msg = "Authentication failed: " + errorMsg;
                            }
                            // System.err.print("showing error message ");
                            JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
                            return null;
                        }
                    } else {
                        authInfo = tmpAuth;
                        ClientSessionTracker sessionTracker = new ClientSessionTracker();
                        sessionTracker.setSessionSetupInfo(new SessionSetupInfo());
                        main.getResolver().setSessionTracker(sessionTracker);
                        return authInfo;
                    }
                } catch (Throwable t) {
                    t.printStackTrace(System.err);
                    JOptionPane.showMessageDialog(null, "Error: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        } finally {
            if (mainWin != null) {
                mainWin.authenticationChanged(authInfo);
            }
            txnReviewWindowAuthenticationChanged();
        }
    }

    @SuppressWarnings("deprecation")
    private void txnReviewWindowAuthenticationChanged() {
        if (txnReviewWindow != null) {
            txnReviewWindow.authenticationChanged(authInfo);
        }
    }

    public synchronized SiteInfo getSpecificSite() {
        return specificSite;
    }

    public synchronized boolean getSpecificSiteDoNotRefer() {
        return specificSiteDoNotRefer;
    }

    public MainWindow getMainWindow() {
        return mainWin;
    }

    @Deprecated
    public TxnReviewWindow getTxnReviewWindow() {
        return txnReviewWindow;
    }

    @Deprecated
    public void txnReviewWindowClosed(TxnReviewWindow win) {
        if (this.txnReviewWindow == win) {
            this.txnReviewWindow = null;
        }
    }

    public Main getMain() {
        return main;
    }

    public synchronized void go() {
        if (mainWin == null) {
            mainWin = new MainWindow(this);
        }
        mainWin.setVisible(true);
        mainWin.toFront();
    }

    public final String getStr(String key) {
        return resources.getString(key);
    }

    public ImageIcon getIcon(String iconPath) {
        if (imageCache.containsKey(iconPath)) {
            return imageCache.get(iconPath);
        }

        try {
            java.net.URL imgURL = getClass().getResource(iconPath);
            if (imgURL == null) {
                return null;
            }
            Image img = Toolkit.getDefaultToolkit().getImage(imgURL);
            if (img == null) {
                return null;
            }

            ImageIcon icon = new ImageIcon(img);
            imageCache.put(iconPath, icon);
            return icon;
        } catch (Exception e) {
        }

        if (imageCache.containsKey("BLANK")) {
            return imageCache.get("BLANK");
        } else {
            ImageIcon icon = new ImageIcon();
            imageCache.put("BLANK", icon);
            return icon;
        }
    }

    synchronized void clearConsole() {
        if (consoleWin != null) {
            consoleWin = null;
        }
    }

    public synchronized void showConsoleWindow() {
        if (consoleWin == null) {
            consoleWin = new Console(this);
        }
        consoleWin.setVisible(true);
        consoleWin.toFront();
    }

    public void shutdown() {
        System.exit(0);
    }

    public synchronized void showHomeUnhomeWindow() {
        HomePrefixWindow hpw = new HomePrefixWindow(this);
        hpw.setVisible(true);
        hpw.toFront();
    }

    public synchronized void showListHandlesWindow() {
        ListHandlesWindow lhw = new ListHandlesWindow(this);
        lhw.setVisible(true);
        lhw.toFront();
    }

    public synchronized void showCheckpointWindow() {
        CheckpointWindow cw = new CheckpointWindow(this);
        cw.setVisible(true);
        cw.toFront();
    }

    public synchronized void querySpecificSiteTool(ActionEvent evt) {
        AbstractButton aButton = (AbstractButton) evt.getSource();
        ButtonModel model = aButton.getModel();
        model.isSelected();
        try {
            GetSiteInfoPanel siteInfoPanel = new GetSiteInfoPanel(this, getStr("query_specific_site_description"), true);
            int result = JOptionPane.showConfirmDialog(null, siteInfoPanel, getStr("query_specific_site"), JOptionPane.OK_CANCEL_OPTION);
            switch (result) {
            case JOptionPane.OK_OPTION:
                specificSite = siteInfoPanel.getSiteInfo();
                specificSiteDoNotRefer = siteInfoPanel.getDoNotRefer();
                mainWin.setSiteFieldText(specificSite, specificSiteDoNotRefer);
                getMain().clearHandleCache();
                break;
            case JOptionPane.CANCEL_OPTION:
            default:
                // retain previous value
            }
        } finally {
            model.setSelected(specificSite != null);
        }
    }

    public synchronized void showBatchProcessor() {
        if (batchWin == null || batchWin.isRunning()) {
            batchWin = new BatchRunnerWindow(this);
        }
        batchWin.setVisible(true);
        batchWin.toFront();
    }

    public synchronized void openBatchFile() {
        showBatchProcessor();
        batchWin.addBatchFile();
    }

    public synchronized void changeConfigDir() {
        String oldFileDialogProp = System.getProperty("apple.awt.fileDialogForDirectories");
        System.setProperty("apple.awt.fileDialogForDirectories", "true");
        try {
            FileDialog fwin = new FileDialog(mainWin, ui.getStr("change_config_dir"), FileDialog.LOAD);
            fwin.setVisible(true);
            String parentStr = fwin.getDirectory();
            String dirStr = fwin.getFile();
            if (parentStr == null || dirStr == null) return;
            String newConfigDir = Paths.get(parentStr, dirStr).toString();
            main.setResolverConfigDir(newConfigDir);
            mainWin.updateResolverConfigDirLabel(main.getResolverConfigDir());
        } finally {
            if (null != oldFileDialogProp) {
                System.setProperty("apple.awt.fileDialogForDirectories", oldFileDialogProp);
            } else {
                System.clearProperty("apple.awt.fileDialogForDirectories");
            }
        }
    }

    private class CheckAuthentication implements Runnable {
        @SuppressWarnings("hiding")
        private AuthenticationInfo authInfo = null;
        private boolean success = false;
        private String errorMessage = null;
        private Integer index;

        CheckAuthentication(AuthenticationInfo authInfo) {
            this.authInfo = authInfo;
        }

        public boolean wasSuccessful() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Integer getIndex() {
            return index;
        }

        @Override
        public void run() {
            this.success = false;
            try {
                AuthenticationUtil authUtil = new AuthenticationUtil(getMain().getResolver());
                this.success = authUtil.checkAuthentication(authInfo);
                this.index = authUtil.getIndex();
            } catch (Exception e) {
                this.errorMessage = String.valueOf(e);
                e.printStackTrace(System.err);
            }
        }
    }

    public JMenuBar getAppMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu, toolsMenu;
        menuBar.add(fileMenu = new JMenu(new HDLAction(ui, "file", null, null)));
        menuBar.add(toolsMenu = new JMenu(new HDLAction(ui, "tools", null, null)));

        fileMenu.add(openBatchFileAction);
        fileMenu.add(clearCacheAction);
        fileMenu.add(authenticateAction);
        fileMenu.add(changeConfigDirAction);

        if (!isMacOSX) {
            fileMenu.addSeparator();
            fileMenu.add(shutdownAction);
        }

        toolsMenu.add(showBatchAction);
        toolsMenu.add(showConsoleAction);
        toolsMenu.add(showHomingAction);
        toolsMenu.add(showCheckpointAction);
        toolsMenu.add(showListHandlesAction);
        // toolsMenu.add(showTxnReviewerAction);
        toolsMenu.add(new JCheckBoxMenuItem(querySpecificSite));
        return menuBar;
    }

    /**
     * Retrieve the siteinfo for the server from the given hostname or IP address and port, displaying an error message and returning null if no
     * siteinfo could be retrieved.
     */
    public SiteInfo getSiteInfoFromHost(String hostname, int svrPort) {
        InetAddress svrAddr;
        try {
            svrAddr = InetAddress.getByName(hostname);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, ui.getStr("error_retrieve_siteinfo") + "\n\n" + e, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
            return null;
        }
        // retrieve the site information...
        GenericRequest siReq = new GenericRequest(Common.BLANK_HANDLE, AbstractMessage.OC_GET_SITE_INFO, null);
        siReq.majorProtocolVersion = 2;
        siReq.minorProtocolVersion = 0;
        siReq.certify = false;
        AbstractResponse response = null;
        HandleResolver resolver = ui.getMain().getResolver();
        Exception error = null;
        int preferredProtocols[] = { Interface.SP_HDL_UDP, Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP };

        // try each protocol, until one works...
        for (int i = 0; i < preferredProtocols.length; i++) {
            try {
                switch (preferredProtocols[i]) {
                case Interface.SP_HDL_TCP:
                    response = resolver.sendHdlTcpRequest(siReq, svrAddr, svrPort);
                    break;
                case Interface.SP_HDL_UDP:
                    response = resolver.sendHdlUdpRequest(siReq, svrAddr, svrPort);
                    break;
                case Interface.SP_HDL_HTTP:
                default:
                    response = resolver.sendHttpRequest(siReq, svrAddr, svrPort);
                    break;
                }
                if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                    return ((GetSiteInfoResponse) response).siteInfo;
                }
            } catch (Exception e) {
                error = e;
                System.err.println("Error sending get-site-info request: " + e);
            }
        }

        if (error != null) {
            error.printStackTrace();
            JOptionPane.showMessageDialog(null, ui.getStr("error_retrieve_siteinfo") + "\n\n" + error, ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(null, ui.getStr("error_retrieve_siteinfo"), ui.getStr("error_title"), JOptionPane.ERROR_MESSAGE);
        }
        return null;
    }

}

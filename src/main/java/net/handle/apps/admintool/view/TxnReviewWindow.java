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
import net.cnri.guiutil.*;
import net.cnri.util.*;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 *
 */
@Deprecated
@SuppressWarnings({"incomplete-switch", "rawtypes", "unchecked"})
public class TxnReviewWindow extends JFrame implements ActionListener {
    private static final String MARKER_MSG = "-stop receiving transactions marker-";

    private AdminToolUI appUI;
    private TxnReviewWindow thisObj;

    private DefaultListModel pendingListModel;
    private DefaultListModel valuesListModel;
    private JList pendingList;
    private JList valuesList;
    private String handle;
    private JButton signerButton;
    private JButton authButton;
    private JButton updateButton;
    private JCheckBox removeOldSignaturesBox;
    private JLabel sourceSiteLabel;
    private JLabel statusField;
    private JProgressBar progressBar;
    private Action updateActions[];

    private Action signAction;
    private Action signAllAction;

    private int numTxnsToRetrieve = 50;

    private StreamTable replicationInfo;
    private String txnPrefsKey;
    private SiteInfo replicationSite;
    private SecureResolver resolver;

    private SignerInfo signerInfo;
    private AuthenticationInfo authInfo;

    public static final String prefKeyForSite(SiteInfo site) {
        if (site == null) {
            return "none";
        }
        StringBuffer sb = new StringBuffer();
        sb.append("site:");
        for (int i = 0; i < site.servers.length; i++) {
            if (i != 0) {
                sb.append('|');
            }
            try {
                sb.append(Util.rfcIpRepr(site.servers[i].getInetAddress()));
                sb.append(':');
                sb.append(site.servers[i].interfaces[0].port);
            } catch (Exception e) {
                System.err.println("Error: " + e);
            }
        }
        return sb.toString();
    }

    public TxnReviewWindow(AdminToolUI ui, SiteInfo replSite) {
        super(ui.getStr("signer_win_title") + " " + ui.getMain().getVersion());
        if (replSite == null) {
            throw new NullPointerException();
        }

        this.appUI = ui;
        this.thisObj = this;
        this.replicationSite = replSite;
        this.txnPrefsKey = prefKeyForSite(replSite);
        this.resolver = new SecureResolver(appUI.getMain().getResolver());
        this.resolver.ignoreUnsignedValues = false;
        this.resolver.ignoreInvalidSignatures = false;
        this.resolver.reportMissingValues = true;
        this.resolver.printState();

        // load the transaction information from the preferences
        Object txnPrefObj = appUI.getMain().prefs().get(txnPrefsKey);
        if (txnPrefObj instanceof StreamTable) {
            this.replicationInfo = (StreamTable) txnPrefObj;
        } else {
            this.replicationInfo = new StreamTable();
            appUI.getMain().prefs().put(txnPrefsKey, this.replicationInfo);
        }

        removeOldSignaturesBox = new JCheckBox(ui.getStr("remove_old_sigs"));
        removeOldSignaturesBox.setSelected(true);
        updateButton = new JButton(new AbstractAction(ui.getStr("update_txns_list")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JPopupMenu menu = new JPopupMenu();
                for (Action action : updateActions) {
                    menu.add(action);
                }
                menu.show(updateButton, 0, updateButton.getHeight());
            }
        });
        signAction = new AbstractAction(ui.getStr("sign_selected_handle")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                signSelectedHandle();
            }
        };
        signAllAction = new AbstractAction(ui.getStr("sign_all_handles")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                signAllHandles();
            }
        };
        signAction.putValue(Action.DEFAULT, Boolean.TRUE);

        updateActions = new Action[] { new AbstractAction(ui.getStr("get_next_5")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 5;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_20")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 20;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_50")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 50;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_100")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 100;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_500")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 500;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_1000")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 1000;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_5000")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 5000;
                getTransactions();
            }
        }, new AbstractAction(ui.getStr("get_next_100000")) {
            @Override
            public void actionPerformed(ActionEvent e) {
                numTxnsToRetrieve = 100000;
                getTransactions();
            }
        }, };

        pendingListModel = new DefaultListModel();
        valuesListModel = new DefaultListModel();
        pendingList = new JList(pendingListModel);
        valuesList = new JList(valuesListModel);
        signerButton = new JButton(ui.getStr("signer"));
        authButton = new JButton(ui.getStr("authenticate"));
        statusField = new JLabel(" ");
        statusField.setForeground(Color.gray);
        progressBar = new JProgressBar();

        sourceSiteLabel = new JLabel(ui.getStr("no_source_site"));

        valuesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        valuesList.setCellRenderer(new HandleValueRenderer());
        valuesList.addMouseListener(new MouseListener() {

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() > 1) {
                    Object val = valuesList.getSelectedValue();
                    if (val == null || (!(val instanceof HandleValue))) {
                        return;
                    }

                    EditValueWindow editWin = new EditValueWindow(appUI, thisObj, handle, getValues());
                    editWin.loadValueData(((HandleValue) val).duplicate(), false);
                    editWin.setMode(EditValueWindow.VIEW_MODE);
                    editWin.setVisible(true);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

        });

        JPanel p = new JPanel(new GridBagLayout());
        JPanel p2 = new JPanel(new GridBagLayout());
        p2.add(sourceSiteLabel, GridC.getc(0, 0).fillx().wx(1));
        p2.add(updateButton, GridC.getc(1, 0).insets(0, 12, 0, 0));
        p.add(p2, GridC.getc(0, 0).fillx().wx(1));
        p.add(new JScrollPane(pendingList), GridC.getc(0, 1).wxy(1, 0).fillboth());
        p.add(Box.createVerticalStrut(200), GridC.getc(0, 1));

        p2 = new JPanel(new GridBagLayout());
        p2.add(signerButton, GridC.getc(0, 0));
        p2.add(authButton, GridC.getc(1, 0));
        p2.add(statusField, GridC.getc(2, 0).wx(1).fillboth());
        p2.add(Box.createHorizontalStrut(200), GridC.getc(2, 0).wx(1));
        p2.add(progressBar, GridC.getc(3, 0).wx(1).fillboth());
        p2.add(new JButton(signAllAction), GridC.getc(4, 0));
        p2.add(new JButton(signAction), GridC.getc(5, 0));
        p.add(p2, GridC.getc(0, 2).fillx().wx(1));
        p.add(new JScrollPane(valuesList), GridC.getc(0, 4).wxy(1, 1).fillboth());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        getContentPane().add(p);
        // getRootPane().setDefaultButton(lookupButton);

        pack();
        setSize(getPreferredSize());
        // setMinimumSize isn't actually in 1.4
        // setMinimumSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
                // should probably cancel any pending jobs here
            }
        });

        authButton.addActionListener(this);
        signerButton.addActionListener(this);
        pendingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pendingList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectionUpdated();
            }
        });

        updateSiteLabel();
        updateEnabledItems();

        // setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        enableEvents(WindowEvent.WINDOW_CLOSED);
    }

    public HandleValue[] getValues() {
        HandleValue[] values = new HandleValue[valuesListModel.size()];
        for (int i = 0; i < valuesListModel.size(); i++) {
            values[i] = (HandleValue) valuesListModel.get(i);
        }
        return values;
    }

    @Override
    public final void processEvent(AWTEvent evt) {
        evt.getID();
        switch (evt.getID()) {
        case WindowEvent.WINDOW_CLOSED:
            appUI.txnReviewWindowClosed(this);
            return;
        }
        super.processEvent(evt);
    }

    private void selectionUpdated() {
        ToBeSigned tbs = (ToBeSigned) pendingList.getSelectedValue();

        signAction.setEnabled(tbs != null);
        signAllAction.setEnabled(pendingListModel.getSize() > 0);

        if (tbs == null) {
            handle = null;
            valuesListModel.removeAllElements();
        } else {
            handle = Util.decodeString(tbs.txn.handle);
            valuesList.setListData(tbs.txn.values);
        }
    }

    private void updateSiteLabel() {
        if (replicationSite == null) {
            sourceSiteLabel.setText(appUI.getStr("no_source_site"));
        } else {
            sourceSiteLabel.setText(appUI.getStr("source_site_label") + ": " + replicationSite.toString());
        }
    }

    public static TxnReviewWindow showReviewWindow(AdminToolUI appUI) {
        // if(appUI.getAuthentication(false)==null) return null;
        // if(appUI.getSignatureInfo(false)==null) return null;

        GetSiteInfoPanel siteInfoPanel = new GetSiteInfoPanel(appUI, appUI.getStr("select_source_site"));
        SiteInfo sourceSite = null;
        while (sourceSite == null) {
            int result = JOptionPane.showConfirmDialog(null, siteInfoPanel, appUI.getStr("select_source_site"), JOptionPane.OK_CANCEL_OPTION);
            switch (result) {
            case JOptionPane.OK_OPTION:
                sourceSite = siteInfoPanel.getSiteInfo();
                break;
            case JOptionPane.CANCEL_OPTION:
            default:
                return null;
            }
        }

        TxnReviewWindow win = new TxnReviewWindow(appUI, sourceSite);
        win.setVisible(true);
        return win;
    }

    private void updateEnabledItems() {

    }

    private void removeFromQueue(final ToBeSigned tbs) {
        if (SwingUtilities.isEventDispatchThread()) {
            pendingListModel.removeElement(tbs);
        } else {
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    @Override
                    public void run() {
                        pendingListModel.removeElement(tbs);
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Process the given transaction, returning true if we should continue receiving transactions.
     */
    private boolean receiveTxn(Transaction txn, GetTxnCallback callback) {
        ToBeSigned toBeSigned = new ToBeSigned(txn, callback);
        int prevIdx = pendingListModel.indexOf(toBeSigned);

        switch (txn.action) {
        case Transaction.ACTION_DELETE_HANDLE:
            if (prevIdx >= 0) {
                removeFromQueue(toBeSigned);
            }
            return true;
        case Transaction.ACTION_DELETE_ALL:
        case Transaction.ACTION_HOME_NA:
        case Transaction.ACTION_PLACEHOLDER:
        case Transaction.ACTION_UNHOME_NA:
            return true; // no values to update!
        case Transaction.ACTION_CREATE_HANDLE:
        case Transaction.ACTION_UPDATE_HANDLE:
            if (prevIdx >= 0) {
                removeFromQueue(toBeSigned);
            }
            break;
        }

        if (pendingListModel.size() >= numTxnsToRetrieve) {
            return false;
        }

        if (pendingListModel.size() <= 0) {
            toBeSigned.savePreviousTransactionMarker();
        }

        HandleValue secureValues[] = null;
        boolean signed = false;
        try {
            secureValues = resolver.secureHandleValues(toBeSigned.txn.handle, toBeSigned.txn.values);
            signed = true;
        } catch (Throwable t) {
            System.err.println("Error verifying secure values: " + t);
            t.printStackTrace(System.err);
        }

        if (secureValues == null || !signed) {
            // not all the values are signed, so it's a candidate!
            toBeSigned.run();
            // SwingUtilities.invokeLater(toBeSigned);
        }

        // the transactions are up to date as of this transaction
        if (pendingListModel.size() <= 0) {
            toBeSigned.saveTransactionMarker();
        }

        return true;
    }

    private class ToBeSigned implements Runnable {
        Transaction txn;
        String handleStr;
        GetTxnCallback siteCallback;

        ToBeSigned(Transaction origTxn, GetTxnCallback callback) {
            this.txn = origTxn;
            this.siteCallback = callback;
            this.handleStr = Util.decodeString(origTxn.handle);
        }

        @Override
        public void run() {
            pendingListModel.addElement(this);
        }

        @Override
        public int hashCode() {
            return handleStr.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ToBeSigned) {
                return ((ToBeSigned) obj).handleStr.equals(handleStr);
            }
            return false;
        }

        public void saveTransactionMarker() {
            siteCallback.saveTransactionMarker(this);
        }

        public void savePreviousTransactionMarker() {
            siteCallback.savePreviousTransactionMarker(this);
        }

        @Override
        public String toString() {
            return handleStr;
        }

    }

    private class GetTxnCallback implements TransactionCallback {
        private final StreamTable siteSettings;
        private final String serverTxnIDKey;
        private final String serverDateKey;

        GetTxnCallback(StreamTable siteSettings, String serverTxnKey, String serverDateKey) {
            this.siteSettings = siteSettings;
            this.serverTxnIDKey = serverTxnKey;
            this.serverDateKey = serverDateKey;
        }

        @Override
        public void processTransaction(Transaction txn) throws HandleException {
            if (!receiveTxn(txn, this)) {
                throw new HandleException(HandleException.INTERNAL_ERROR, MARKER_MSG);
            }
        }

        public void saveTransactionMarker(ToBeSigned tbs) {
            siteSettings.put(serverTxnIDKey, tbs.txn.txnId);
            siteSettings.put(serverDateKey, tbs.txn.date);
            try {
                appUI.getMain().savePreferences();
            } catch (Throwable t) {
                System.err.println("Error saving preferences: " + t);
            }
        }

        public void savePreviousTransactionMarker(ToBeSigned tbs) {
            siteSettings.put(serverTxnIDKey, tbs.txn.txnId - 1);
            siteSettings.put(serverDateKey, tbs.txn.date);
            try {
                appUI.getMain().savePreferences();
            } catch (Throwable t) {
                System.err.println("Error saving preferences: " + t);
            }
        }

        @Override
        public void processTransaction(String queueName, Transaction txn) throws HandleException {

        }

        @Override
        public void finishProcessing() {

        }

        @Override
        public void finishProcessing(long sourceDate) {

        }

        @Override
        public void setQueueLastTimestamp(String queueName, long sourceDate) {

        }
    }

    private void getTransactions() {
        if (pendingListModel.size() >= numTxnsToRetrieve) {
            return; // we've already got the transactions
        }
        authInfo = appUI.getAuthentication(false);
        if (authInfo == null) {
            return;
        }

        Runnable getter = new Runnable() {

            @Override
            public void run() {
                try {
                    for (int i = 0; i < replicationSite.servers.length; i++) {
                        String serverTxnIDKey = "last_txn:" + replicationSite.servers[i].serverId;
                        String serverDateKey = "last_date:" + replicationSite.servers[i].serverId;
                        GetTxnCallback callback = new GetTxnCallback(replicationInfo, serverTxnIDKey, serverDateKey);
                        try {
                            appUI.getMain().retrieveHandlesSinceTime(authInfo, replicationSite, i, replicationInfo.getLong(serverTxnIDKey, 0), replicationInfo.getLong(serverDateKey, System.currentTimeMillis()), callback);
                        } catch (Throwable t) {
                            if (!(t instanceof HandleException && MARKER_MSG.equals(((HandleException) t).getMessage()))) {
                                t.printStackTrace(System.err);
                                JOptionPane.showMessageDialog(thisObj, "Error: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }

                    /*
                     * appUI.getMain().retrieveHandlesSinceTime(appUI.getAuthentication(false), replicationSite, serverNum, lastTxnID, callback);
                     */
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            selectionUpdated();
                        }
                    });
                } finally {
                    progressBar.setIndeterminate(false);
                }
            }
        };

        progressBar.setIndeterminate(true);
        statusField.setText(appUI.getStr("retrieving_txns"));
        new Thread(getter).start();
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() == signerButton) {
            appUI.getSignatureInfo(this, true);
        } else if (evt.getSource() == authButton) {
            appUI.getAuthentication(true);
        } else {
            System.err.println("need to implement action: " + evt);
        }
    }

    public static final int getNextUnusedIndex(HandleValue values[], int firstIdx) {
        int nextIdx = firstIdx - 1;
        boolean duplicate = true;
        while (duplicate) {
            nextIdx++;
            duplicate = false;
            for (int i = values.length - 1; i >= 0; i--) {
                HandleValue val = values[i];
                if (val != null && val.getIndex() == nextIdx) {
                    duplicate = true;
                    break;
                }
            }
        }
        return nextIdx;
    }

    /**
     * Sign the given entry, returning true if successful.
     *
     * @param entry
     *            The entry to be signed
     * @param recordMarker
     *            Whether a transaction marker for the replication process should be stored
     * @return
     */
    private boolean signEntry(ToBeSigned entry, boolean recordMarker) {
        System.err.println("signing: " + entry);
        try {
            if (signerInfo.isRemoteSigner()) {
                showAsyncError("Cannot perform legacy signatures using remote signer");
                return false;
            }
            PublicKeyAuthenticationInfo localSigner = signerInfo.getLocalSignerInfo();
            HandleValue digestVal = HandleSignature.createDigestsValue(getNextUnusedIndex(entry.txn.values, 400), entry.handleStr, entry.txn.values);
            ValueReference signer = signerInfo.getUserValueReference();
            HandleValue sigValue = HandleSignature.createSignatureValue(getNextUnusedIndex(entry.txn.values, digestVal.getIndex() + 1), entry.handleStr, signer, null, localSigner.getPrivateKey(), digestVal);

            AddValueRequest req = new AddValueRequest(entry.txn.handle, new HandleValue[] { digestVal, sigValue }, authInfo);
            if (removeOldSignaturesBox.isSelected()) {
                int indexesToRemove[] = new int[entry.txn.values.length];
                int nextIdx = 0;
                for (int i = 0; i < indexesToRemove.length; i++) {
                    if (entry.txn.values[i].hasType(SecureResolver.METADATA_TYPE) || entry.txn.values[i].hasType(SecureResolver.SIGNATURE_TYPE)) {
                        indexesToRemove[nextIdx++] = entry.txn.values[i].getIndex();
                    }
                }
                if (nextIdx > 0) {
                    int tmpIndexes[] = new int[nextIdx];
                    System.arraycopy(indexesToRemove, 0, tmpIndexes, 0, tmpIndexes.length);
                    RemoveValueRequest rvReq = new RemoveValueRequest(entry.txn.handle, tmpIndexes, authInfo);
                    AbstractResponse resp = appUI.getMain().getResolver().sendRequestToSite(rvReq, replicationSite);
                    if (resp.responseCode != AbstractMessage.RC_SUCCESS) {
                        if (resp.responseCode != AbstractMessage.RC_SERVER_NOT_RESP) {
                            // if the server is no longer responsible for this handle. consider it no-longer-needing-signing
                            SwingUtilities.invokeLater(new ErrMsgRunner("Unable to remove old signatures"));
                            return false;
                        }

                    }
                }
            }

            AbstractResponse resp = appUI.getMain().getResolver().sendRequestToSite(req, replicationSite);

            if (resp.responseCode == AbstractMessage.RC_SUCCESS || resp.responseCode == AbstractMessage.RC_SERVER_NOT_RESP) {
                pendingListModel.removeElement(entry);
                if (recordMarker) {
                    // only store the transaction ID if the selected item is the oldest item in the list
                    entry.saveTransactionMarker();
                }
            }
        } catch (Throwable t) {
            SwingUtilities.invokeLater(new ErrMsgRunner(String.valueOf(t)));
            t.printStackTrace(System.err);
            return false;
        }
        return true;
    }

    private void signSelectedHandle() {
        ToBeSigned tbs = (ToBeSigned) pendingList.getSelectedValue();
        if (tbs == null) {
            return;
        }

        signerInfo = appUI.getSignatureInfo(this, false);
        if (signerInfo == null) {
            return;
        }

        int selIdx = pendingList.getSelectedIndex();
        signEntry(tbs, selIdx == 0);
        selIdx = Math.min(selIdx, pendingListModel.getSize() - 1);
        if (selIdx >= 0) {
            pendingList.setSelectedIndex(selIdx);
        }
        selectionUpdated();
    }

    private void signAllHandles() {
        if (pendingListModel.size() <= 0) {
            return;
        }

        signerInfo = appUI.getSignatureInfo(this, false);
        if (signerInfo == null) {
            return;
        }

        signAction.setEnabled(false);
        signAllAction.setEnabled(false);
        Runnable signAllRunner = new Runnable() {
            @Override
            public void run() {
                try {
                    while (pendingListModel.size() > 0) {
                        ToBeSigned tbs = (ToBeSigned) pendingListModel.get(0);
                        if (!signEntry(tbs, true)) {
                            break;
                        }
                    }
                } finally {
                    // signAction.setEnabled(true);
                    // signAllAction.setEnabled(true);
                    selectionUpdated();
                }
            }
        };

        Thread t = new Thread(signAllRunner);
        t.setName("sign all handles thread");
        t.start();
    }

    public void signerChanged(SignerInfo newSigner) {
        this.signerInfo = newSigner;
        if (newSigner == null) {
            signerButton.setText(appUI.getStr("signer"));
        } else {
            try {
                String signerId;
                if (signerInfo.isLocalSigner()) {
                    signerId = signerInfo.getUserValueReference().toString();
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
                authButton.setText(appUI.getStr("authenticate") + ": " + newAuth.getUserIdIndex() + " : " + Util.decodeString(newAuth.getUserIdHandle()));
            } catch (Exception e) {
                authButton.setText(String.valueOf(e));
            }
        }
    }

    public void showAsyncError(String msg) {
        SwingUtilities.invokeLater(new ErrMsgRunner(msg));
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
}

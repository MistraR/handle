/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jwidget.*;
import net.handle.apps.gui.jutil.*;
import net.handle.hdllib.*;
import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import java.net.InetAddress;
import java.security.*;
import javax.swing.*;

@SuppressWarnings("incomplete-switch")
public class HandleTool extends JFrame implements ActionListener {
    private AuthenticationInfo authInfo = null;
    HandleResolver resolver = null;
    boolean errFlag = false;
    Console console;
    JCheckBoxMenuItem showConsoleMenuItem;

    JLabel authStatus;

    // buttons
    private final String[][] bname = { { "     Query Handle     ", "query", "Search24.gif" }, { "     Create Handle     ", "create", "New24.gif" }, { "     Modify Handle     ", "modify", "Edit24.gif" },
            { "     Remove Handle     ", "remove", "Delete24.gif" }, { "       Run Batch     ", "batch", "History24.gif" }, { "      Server Admin     ", "server", "Server24.gif" }, { "        Setup     ", "config", "Preferences24.gif" },
            { "         Help    ", "help", "Help24.gif" }, { "         Exit     ", "exit", "Stop24.gif" }, };

    // popup menus
    JPopupMenu configMenu = new JPopupMenu("Configuration");
    JPopupMenu serverMenu = new JPopupMenu("Server Tasks");

    public HandleTool() {
        this(new HandleResolver());
    }

    public HandleTool(HandleResolver resolver) {
        super("Handle Admin Tool");
        this.resolver = resolver;
        this.resolver.traceMessages = true;
        this.resolver.setPreferredProtocols(new int[] { Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP });

        java.net.URL res = this.getClass().getResource("icons/hs_logo.gif");
        java.awt.Image img = (new ImageIcon(res)).getImage();
        // setIconImage(img.getScaledInstance(16, 16, img.SCALE_SMOOTH));
        setIconImage(img);

        console = new Console();
        console.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                showConsoleMenuItem.setState(false);
            }
        });

        GridBagLayout grid = new GridBagLayout();
        JPanel content = new JPanel(grid);

        // panel
        Insets inset = new Insets(3, 3, 3, 3);
        JPanel p1 = new JPanel(grid);

        // buttons
        MyButton[] buttons = new MyButton[bname.length];
        int x = 0;
        int y = 0;
        for (int i = 0; i < bname.length; i++) {
            res = this.getClass().getResource("icons/" + bname[i][2]);
            buttons[i] = new MyButton(bname[i][0], null, bname[i][1], new ImageIcon(res));
            buttons[i].setHorizontalAlignment(SwingConstants.LEFT);
            buttons[i].setHorizontalTextPosition(SwingConstants.RIGHT);
            buttons[i].addActionListener(this);
            p1.add(buttons[i], AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, inset, true, false));

            if (bname[i][1].equals("server")) serverMenu.setInvoker(buttons[i]);
            else if (bname[i][1].equals("config")) configMenu.setInvoker(buttons[i]);

        }

        // setup popup menus
        String serverItems[][] = { { "Home Prefix", "home" }, { "Unhome Prefix", "unhome" }, { "Backup Server", "backup" }, { "List Handles", "list" } };
        JMenuItem item;
        for (int i = 0; i < serverItems.length; i++) {
            item = new JMenuItem(serverItems[i][0]);
            item.setActionCommand(serverItems[i][1]);
            item.addActionListener(this);
            serverMenu.add(item);
        }
        String configItems[][] = { { "Authentication", "auth" }, { "Session Settings", "session" }, { "Generate Key Pairs", "keys" }, { "IP/Firewall Redirection", "ipredirect" } };
        for (int i = 0; i < configItems.length; i++) {
            item = new JMenuItem(configItems[i][0]);
            item.setActionCommand(configItems[i][1]);
            configMenu.add(item);
            item.addActionListener(this);
        }
        showConsoleMenuItem = new JCheckBoxMenuItem("Show Console");
        showConsoleMenuItem.setActionCommand("console");
        configMenu.add(showConsoleMenuItem);
        showConsoleMenuItem.addActionListener(this);

        JPanel p2 = new JPanel();
        p2.setBorder(BorderFactory.createLoweredBevelBorder());
        authStatus = new JLabel("No authentication set.");
        authStatus.setFont(new Font("Dialog", Font.PLAIN, 10));
        p2.add(authStatus);
        // layout
        content.add(p1, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, inset, true, true));
        content.add(p2, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, inset, true, true));
        setContentPane(content);
        setSize(100, 200);
        pack();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent evt) {
                System.exit(0);
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String choice = ae.getActionCommand();
        if (choice.equals("exit")) System.exit(0);
        else if (choice.equals("help")) HelpPanel.show(this, CommonDef.HELP_DIR, CommonDef.INDEX_FILE);
        else if (choice.equals("create")) createHandle();
        else if (choice.equals("modify")) modifyHandle();
        else if (choice.equals("remove")) removeHandle();
        else if (choice.equals("query")) queryHandle();
        else if (choice.equals("batch")) batchHandle();
        else if (choice.equals("config")) configMenu.show(configMenu.getInvoker(), 100, 0);
        else if (choice.equals("server")) serverMenu.show(serverMenu.getInvoker(), 100, 0);
        else if (choice.equals("home")) homeNA();
        else if (choice.equals("unhome")) unhomeNA();
        else if (choice.equals("keys")) genKeys();
        else if (choice.equals("console")) console.setVisible(!console.isVisible());
        else if (choice.equals("list")) listHandles();
        else if (choice.equals("backup")) backupServer();
        else if (choice.equals("session")) sessionSetup();
        else if (choice.equals("auth")) changeAuthentication();
        else if (choice.equals("ipredirect")) setupIPRedirection();
        else System.err.println("uncaught action in HandleTool.java: " + choice);
    }

    /**********************************************************
     *                   private functions
     **********************************************************/

    private void createHandle() {
        CreateHandleJPanel createp = new CreateHandleJPanel(this);
        createp.reset();
        WorkWindow.show(this, "Create Handle:", createp, CommonDef.HELP_DIR, CommonDef.HELP_CREATE_HANDLE);
    }

    private void modifyHandle() {
        ModifyHandleJPanel modifyp = new ModifyHandleJPanel(this);
        WorkWindow.show(this, "Modify Handle:", modifyp, CommonDef.HELP_DIR, CommonDef.HELP_MODIFY_HANDLE);
    }

    private void removeHandle() {
        RemoveHandleJPanel removep = new RemoveHandleJPanel(this);
        WorkWindow.show(this, "Remove Handle:", removep, CommonDef.HELP_DIR, CommonDef.HELP_REMOVE_HANDLE);
    }

    private void queryHandle() {
        QueryHandleJPanel queryp = new QueryHandleJPanel(this);
        WorkWindow.show(this, "Query Handle:", queryp, CommonDef.HELP_DIR, CommonDef.HELP_QUERY_HANDLE);
    }

    private void batchHandle() {
        BatchHandleJPanel batchp = new BatchHandleJPanel(this);
        WorkWindow.show(this, "Batch Submit Handles:", batchp, CommonDef.HELP_DIR, CommonDef.HELP_BATCH_HANDLE);
    }

    private void homeNA() {
        AuthenticationInfo auth = getAuthentication();
        if (auth == null) return;
        HomeNAWindow homenaWin = new HomeNAWindow(this);
        homenaWin.setVisible(true);
    }

    private void unhomeNA() {
        AuthenticationInfo auth = getAuthentication();
        if (auth == null) return;
        UnhomeNAWindow unhomeWin = new UnhomeNAWindow(this);
        unhomeWin.setVisible(true);
    }

    private void backupServer() {
        AuthenticationInfo auth = getAuthentication();
        if (auth == null) return;
        BackupServerWindow backupWin = new BackupServerWindow(this);
        backupWin.setVisible(true);
    }

    public SessionSetupInfo getSessionSetupInfo() {
        ClientSessionTracker t = resolver.getSessionTracker();
        if (t == null) return null;
        return t.getSessionSetupInfo();
    }

    private void sessionSetup() {
        SessionSetupJPanel sessionWin = new SessionSetupJPanel(this, getSessionSetupInfo());

        sessionWin.setLocation(getLocation());
        sessionWin.setVisible(true);
    }

    private void genKeys() {
        GenerateKeyJPanel keygenWin = new GenerateKeyJPanel();
        WorkWindow.show(this, "Generate Key Pair:", keygenWin, CommonDef.HELP_DIR, CommonDef.HELP_GEN_KEY_PAIRS);
    }

    private void listHandles() {
        ListHandleJPanel listWin = new ListHandleJPanel(this);
        WorkWindow.show(this, "List Handles:", listWin, CommonDef.HELP_DIR, CommonDef.HELP_LIST_HANDLES);

    }

    public AuthenticationInfo getCurrentAuthentication() {
        return authInfo;
    }

    public AuthenticationInfo getAuthentication() {
        if (authInfo != null) return authInfo;
        AuthenJPanel authp = new AuthenJPanel();

        while (true) {
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, authp, "Authentication", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
                return null;
            }

            try {
                AuthenticationInfo tempAuthInfo = authp.getAuthInfo();
                if (tempAuthInfo == null) return null;
                CheckAuthentication checkAuth = new CheckAuthentication(tempAuthInfo);
                TaskIndicator ti = new TaskIndicator(this);
                ti.invokeTask(checkAuth, "Checking Authentication ...");

                if (checkAuth.getFlag()) {
                    if (tempAuthInfo instanceof PublicKeyAuthenticationInfo && tempAuthInfo.getUserIdIndex() == 0) {
                        int newIndex = checkAuth.getIndex();
                        if (newIndex > 0) {
                            tempAuthInfo = new PublicKeyAuthenticationInfo(tempAuthInfo.getUserIdHandle(), newIndex, ((PublicKeyAuthenticationInfo) tempAuthInfo).getPrivateKey());
                        }
                    }
                    authInfo = tempAuthInfo;

                    StringBuffer s = new StringBuffer();
                    s.append(new String(authInfo.getAuthType()));
                    s.append(" ");
                    s.append(new String(authInfo.getUserIdHandle()));
                    s.append(":");
                    s.append(authInfo.getUserIdIndex());
                    authStatus.setText(s.toString());
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
                    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
                }

            } catch (Throwable t) {
                JOptionPane.showMessageDialog(this, "Error: " + t.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void setupIPRedirection() {
        IPRedirectPanel redirectPanel = new IPRedirectPanel(resolver.getConfiguration());
        int result = JOptionPane.showConfirmDialog(this, redirectPanel, "Authentication", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            redirectPanel.saveRecords();
        }
    }

    public AuthenticationInfo changeAuthentication() {
        AuthenticationInfo oldInfo = authInfo;
        authInfo = null;
        authInfo = getAuthentication();
        if (authInfo == null) authInfo = oldInfo;
        return authInfo;
    }

    public AbstractResponse processRequest(Component parent, AbstractRequest req, String label) throws Exception {
        ProcessRequest proc = new ProcessRequest(req);
        TaskIndicator ti = new TaskIndicator(parent);
        ti.invokeTask(proc, label);
        return proc.getResponse();
    }

    public AbstractResponse processRequest(Component parent, AbstractRequest req, ServerInfo server, String label) throws Exception {
        ProcessRequest proc = new ProcessRequest(req, server);
        TaskIndicator ti = new TaskIndicator(parent);
        ti.invokeTask(proc, label);
        return proc.getResponse();
    }

    public AbstractResponse processRequest(Component parent, AbstractRequest req, InetAddress serverIP, int port, int protocol, String label) throws Exception {
        ProcessRequest proc = new ProcessRequest(req, serverIP, port, protocol);
        TaskIndicator ti = new TaskIndicator(parent);
        ti.invokeTask(proc, label);
        return proc.getResponse();
    }

    public void processStreamedResponse(Component parent, DumpHandlesResponse resp, DumpHandlesCallback callback, PublicKey publicKey, String label) throws Exception {
        ProcessStream proc = new ProcessStream(resp, callback, publicKey);
        TaskIndicator ti = new TaskIndicator(parent);
        ti.invokeTask(proc, label);
        proc.end();
    }

    //----------------------------------------------------------
    //Runnable inner classes  to be separated thread tasks
    //----------------------------------------------------------
    class CheckAuthentication implements Runnable {
        boolean succFlag = false;
        String errorMessage = null;
        AuthenticationInfo auth;
        int index;

        CheckAuthentication(AuthenticationInfo auth) {
            this.auth = auth;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public void run() {
            try {
                succFlag = checkAuth();
            } catch (Exception e) {
                errorMessage = String.valueOf(e);
                e.printStackTrace(System.err);
                succFlag = false;
            }
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean getFlag() {
            return succFlag;
        }

        boolean checkAuth() throws Exception {
            ResolutionRequest dummyReq = new ResolutionRequest(Common.BLANK_HANDLE, null, null, null);
            dummyReq.majorProtocolVersion = 2;
            dummyReq.minorProtocolVersion = 1;
            ChallengeResponse challResp = new ChallengeResponse(dummyReq, true);

            // so that the authenticator thinks we are using the old version...
            challResp.majorProtocolVersion = 2;
            challResp.minorProtocolVersion = 1;

            byte authBytes[] = auth.authenticate(challResp, dummyReq);
            if (Util.equals(auth.getAuthType(), Common.SECRET_KEY_TYPE)) {
                // we verify secret keys by asking the responsible server if the
                // given response (md5 hash of the secret_key+nonce+request_digest)
                // is valid for the given challenge.

                VerifyAuthRequest vaReq = new VerifyAuthRequest(auth.getUserIdHandle(), challResp.nonce, challResp.requestDigest, challResp.rdHashType, authBytes, auth.getUserIdIndex(), null);

                vaReq.certify = true;
                AbstractResponse response = resolver.processRequest(vaReq);

                if (!(response instanceof VerifyAuthResponse)) throw new Exception("Unable to verify authentication \n" + response);

                return ((VerifyAuthResponse) response).isValid;

            } else if (Util.equals(auth.getAuthType(), Common.PUBLIC_KEY_TYPE)) {
                // verify that the challenge response was signed by the private key
                // associated with the administrators public key.

                // first retrieve the public key (checking server signatures, of course)
                ResolutionRequest req = new ResolutionRequest(auth.getUserIdHandle(), auth.getUserIdIndex() > 0 ? null : Common.PUBLIC_KEY_TYPES, auth.getUserIdIndex() > 0 ? new int[] { auth.getUserIdIndex() } : null, null);
                req.certify = true;
                AbstractResponse response = resolver.processRequest(req);

                if (!(response instanceof ResolutionResponse)) throw new Exception("Unable to verify authentication \n" + response);

                HandleValue values[] = ((ResolutionResponse) response).getHandleValues();
                if (values == null || values.length < 1) throw new Exception("The admin index specified does not exist\n");

                // get the algorithm used to sign
                int offset = 0;
                byte hashAlgId[] = Encoder.readByteArray(authBytes, offset);
                offset += Encoder.INT_SIZE + hashAlgId.length;

                // get the actual bytes of the signature
                byte sigBytes[] = Encoder.readByteArray(authBytes, offset);
                offset += Encoder.INT_SIZE + sigBytes.length;

                for (int i = 0; i < values.length; i++) {
                    if (auth.getUserIdIndex() > 0 && auth.getUserIdIndex() != values[i].getIndex()) continue;

                    // decode the public key
                    PublicKey pubKey = Util.getPublicKeyFromBytes(values[i].getData(), 0);

                    Signature sig = Signature.getInstance(Util.getSigIdFromHashAlgId(hashAlgId, pubKey.getAlgorithm()));
                    sig.initVerify(pubKey);

                    // verify the signature
                    sig.update(challResp.nonce);
                    sig.update(challResp.requestDigest);

                    if (sig.verify(sigBytes)) {
                        index = values[i].getIndex();
                        return true;
                    }
                }
                return false;
            } else {
                System.err.println("Unknown auth type: \n" + Util.decodeString(auth.getAuthType()));
                throw new Exception("Unknown authentication type : \n" + Util.decodeString(auth.getAuthType()));
            }

        }

    }

    class ProcessRequest implements Runnable {
        private AbstractResponse response = null;
        private AbstractRequest request = null;
        private Exception exception = null;
        private InetAddress serverIP = null;
        private ServerInfo server = null;
        private int port = -1;
        private int protocol = -1;
        private int sendtype = 1;

        ProcessRequest(AbstractRequest req) {
            this.request = req;
            sendtype = 0;
        }

        ProcessRequest(AbstractRequest req, ServerInfo server) {
            this.request = req;
            this.server = server;
            sendtype = 1;
        }

        ProcessRequest(AbstractRequest req, InetAddress serverIP, int port, int protocol) {
            this.request = req;
            this.serverIP = serverIP;
            this.port = port;
            this.protocol = protocol;
            sendtype = 2;
        }

        @Override
        public void run() {
            try {
                switch (sendtype) {
                case 0:
                    response = resolver.processRequest(request);
                    break;
                case 1:
                    response = resolver.sendRequestToServer(request, server);
                    break;
                case 2:
                    switch (protocol) {
                    case Interface.SP_HDL_TCP:
                        response = resolver.sendHdlTcpRequest(request, serverIP, port);
                        break;
                    case Interface.SP_HDL_UDP:
                        response = resolver.sendHdlUdpRequest(request, serverIP, port);
                        break;
                    case Interface.SP_HDL_HTTP:
                        response = resolver.sendHttpRequest(request, serverIP, port);
                        break;
                    default:
                        new Exception("Invalid server protocol type").printStackTrace();
                    }
                    break;
                }
            } catch (Exception e) {
                this.exception = e;
                response = null;
            }
        }

        public AbstractResponse getResponse() throws Exception {
            if (exception != null) throw exception;
            return response;
        }
    }

    class ProcessStream implements Runnable {
        DumpHandlesCallback callback;
        PublicKey publicKey;
        DumpHandlesResponse streamedResponse;
        Exception exception;
        boolean endFlag = false;

        ProcessStream(DumpHandlesResponse streamedResponse, DumpHandlesCallback callback, PublicKey publicKey) {
            this.callback = callback;
            this.publicKey = publicKey;
            this.streamedResponse = streamedResponse;
        }

        @Override
        public void run() {
            try {
                streamedResponse.processStreamedPart(callback, publicKey);
                endFlag = true;
            } catch (Exception e) {
                e.printStackTrace(System.err);
                exception = e;
            }
        }

        public void end() {
            if (endFlag) return;
            try {
                AbstractResponse tmpResp = streamedResponse;
                if (tmpResp.stream != null) {
                    tmpResp.stream.close();
                    tmpResp.stream = null;
                }
                callback.processThisServerReplicationInfo(0, 0);
            } catch (Exception e) {
                e.printStackTrace(System.err);
                exception = e;
            }
        }

        public void getException() throws Exception {
            if (exception != null) throw exception;
        }
    }

    public static void main(String[] args) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                net.handle.hdllib.ChallengeResponse.initializeRandom();
            }
        });
        t.start();

        // use system look and feel if there is one for this platform
        String osName = System.getProperty("os.name");
        if (osName.indexOf("Windows") != -1 || osName.indexOf("Solaris") != -1 || osName.indexOf("SunOS") != -1 || osName.indexOf("Mac") != -1) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
            }
        }

        HandleTool ht = new HandleTool();
        ht.setLocation(200, 200);
        ht.setVisible(true);
    }

}

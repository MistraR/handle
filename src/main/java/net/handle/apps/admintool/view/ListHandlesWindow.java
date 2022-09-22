/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.apps.batch.GenericBatch;
import net.handle.hdllib.*;
import net.handle.awt.*;
import net.cnri.guiutil.GridC;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

public class ListHandlesWindow extends JFrame implements ActionListener, ItemListener {
    private final AdminToolUI ui;

    private final JTextField prefixField;

    private final GetSiteInfoPanel getSiteInfoPanel;

    private final JComboBox<String> outputChoice;
    private final JLabel outputFileLabel;
    private final JLabel consoleFillerLabel;
    private final JButton outputFileButton;
    private final JButton runButton;
    private final ListHandlesWindow thisObj;

    private final AbstractConsolePanel console;

    public ListHandlesWindow(AdminToolUI ui) {
        super(ui.getStr("list_handles"));
        this.ui = ui;
        this.thisObj = this;

        setJMenuBar(ui.getAppMenu());

        runButton = new JButton(ui.getStr("list_handles"));
        outputChoice = new JComboBox<>(new String[] { ui.getStr("to_window"), ui.getStr("to_file") });
        outputFileLabel = new JLabel("");
        consoleFillerLabel = new JLabel("");
        outputFileButton = new JButton(ui.getStr("choose_file"));
        console = new TextAreaBasedConsolePanel();

        prefixField = new JTextField("");

        getSiteInfoPanel = new GetSiteInfoPanel(ui, null);

        int y = 0;
        JPanel p = new JPanel(new GridBagLayout());
        JPanel p1 = new JPanel(new GridBagLayout());

        p1.add(new JLabel(ui.getStr("prefix") + ": "), GridC.getc(0, 0).label());
        p1.add(prefixField, GridC.getc(1, 0).field());
        p.add(p1, GridC.getc(0, y++).wx(1).fillboth());

        p.add(getSiteInfoPanel, GridC.getc(0, y++).wx(1).fillboth().insets(10, 0, 15, 0));

        JPanel p2 = new JPanel(new GridBagLayout());
        p2.add(new JLabel(ui.getStr("send_output_to") + ":"), GridC.getc(0, 0).label());
        p2.add(outputChoice, GridC.getc(1, 0).label());
        p2.add(outputFileLabel, GridC.getc(2, 0).field());
        p2.add(consoleFillerLabel, GridC.getc(2, 0).field());
        p2.add(outputFileButton, GridC.getc(3, 0).label());
        p.add(p2, GridC.getc(0, y++).wx(1).fillboth());
        //p.add(Box.createVerticalStrut(200), GridC.getc(0,y));
        p.add(console, GridC.getc(0, y++).wxy(1, 1).insets(5, 5, 5, 5).fillboth());

        p.add(runButton, GridC.getc(0, y++).east().insets(10, 0, 0, 0));

        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        getContentPane().add(p);

        runButton.addActionListener(this);
        outputFileButton.addActionListener(this);
        outputChoice.addItemListener(this);

        pack();
        setSize(400, 800);
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);

        outputSelected();
    }

    private AuthenticationInfo authInfo = null;
    private PrintWriter outputWriter = null;

    private void runListHandles() {
        String prefixStr = prefixField.getText().trim();
        if (prefixStr.length() <= 0) return;

        byte prefix[] = Util.upperCaseInPlace(Util.encodeString(prefixStr));
        if (!Util.hasSlash(prefix)) prefix = Util.convertSlashlessHandleToZeroNaHandle(prefix); // backwards compatibility

        authInfo = ui.getAuthentication(false);
        if (authInfo == null) {
            return;
        }

        SiteInfo site = getSiteInfoPanel.getSiteInfo();
        if (site == null) return;

        try {
            if (outputChoice.getSelectedIndex() == 1) { // output to a file
                OutputStream fout = null;
                while (fout == null) {
                    try {
                        fout = new FileOutputStream(outputFileLabel.getText());
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(thisObj, ui.getStr("output_file_err_reselect") + ":\n " + e);
                        if (!selectOutputFile()) {
                            if (fout != null) fout.close();
                            return;
                        }
                    }
                }
                outputWriter = new PrintWriter(new OutputStreamWriter(fout, GenericBatch.ENCODING), true);
            } else { // output to the console
                outputWriter = new PrintWriter(console.getOutputStream(), true);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(thisObj, "Error creating log: " + e);
            try {
                outputWriter.close();
            } catch (Throwable t) {
            }
            outputWriter = null;
            return;
        }

        // set the input fields disabled while the batches are run
        outputChoice.setEnabled(false);
        outputFileButton.setEnabled(false);
        runButton.setEnabled(false);
        console.clear();

        numActiveRunners = site.servers.length;
        for (int i = 0; i < site.servers.length; i++) {
            Thread batchRunner = new Thread(new ListHandlesRunner(site, site.servers[i], prefix, authInfo));
            batchRunner.setPriority(Thread.MIN_PRIORITY);
            batchRunner.start();
        }
    }

    private int numActiveRunners = 0;

    private class ListHandlesRunner implements Runnable, ResponseMessageCallback {
        private String listErrorMsg = null;

        private final SiteInfo site;
        private final ServerInfo server;
        private final byte prefix[];
        private final AuthenticationInfo auth;

        ListHandlesRunner(SiteInfo site, ServerInfo server, byte prefix[], AuthenticationInfo auth) {
            this.site = site;
            this.server = server;
            this.prefix = prefix;
            this.auth = auth;
        }

        @Override
        public void run() {
            try {
                ListHandlesRequest listReq = new ListHandlesRequest(prefix, auth);
                HandleResolver resolver = ui.getMain().getResolver();
                outputWriter.write("Sending list command " + listReq + "\n to server " + server + "\n");
                outputWriter.flush();
                resolver.sendRequestToServer(listReq, site, server, this);
            } catch (Exception e) {
                listErrorMsg = "Error listing handles: " + e + "\n";
            } finally {
                outputChoice.setEnabled(true);
                outputFileButton.setEnabled(true);
                numActiveRunners--;
                if (numActiveRunners <= 0) {
                    runButton.setEnabled(true);
                    try {
                        outputWriter.close();
                        outputWriter = null;
                    } catch (Throwable t) {
                        t.printStackTrace(System.err);
                    }
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(thisObj, ui.getStr("list_handles_complete_msg"));
                        }
                    });
                }
            }

            if (listErrorMsg != null) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        if (listErrorMsg != null) {
                            JOptionPane.showMessageDialog(thisObj, listErrorMsg);
                        }
                    }
                });
            }

        }

        @Override
        public void handleResponse(AbstractResponse message) throws HandleException {
            if (message instanceof ListHandlesResponse) {
                byte handles[][] = ((ListHandlesResponse) message).handles;
                for (int i = 0; handles != null && i < handles.length; i++) {
                    if (handles[i] == null) continue;
                    outputWriter.write(Util.decodeString(handles[i]) + "\n");
                }
            } else if (message instanceof ChallengeResponse) {
                // ignore
            } else {
                outputWriter.write("Unrecognized response: " + message + ";\n from server: " + server + "\n");
            }
            outputWriter.flush();
        }

    }

    private void outputSelected() {
        boolean toFile = outputChoice.getSelectedIndex() == 1;
        Dimension oldSize = getSize();

        console.setVisible(!toFile);
        consoleFillerLabel.setVisible(!toFile);
        outputFileLabel.setVisible(toFile);
        outputFileButton.setVisible(toFile);

        pack();
        Dimension prefSize = getPreferredSize();
        setSize(new Dimension(Math.max(oldSize.width, prefSize.width), prefSize.height));
    }

    public boolean selectOutputFile() {
        FileDialog fwin = new FileDialog(this, ui.getStr("send_output_to"), FileDialog.SAVE);
        String currentFile = outputFileLabel.getText().trim();
        if (currentFile.length() > 0) {
            try {
                File currentF = new File(currentFile);
                if (currentF.canWrite()) {
                    fwin.setDirectory(currentF.getParent());
                    fwin.setFile(currentF.getName());
                }
            } catch (Exception e) {
            }
        }

        fwin.setVisible(true);
        String fileStr = fwin.getFile();
        String dirStr = fwin.getDirectory();
        if (fileStr == null || dirStr == null) return false;
        File outputFile = new File(dirStr + fileStr);
        try {
            outputFileLabel.setText(outputFile.getPath());
            return true;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(thisObj, "Error checking file: " + dirStr + fileStr + "\n\nError Message: " + e);
        }
        return false;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == runButton) {
            runListHandles();
        } else if (src == outputFileButton) {
            selectOutputFile();
        }
    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        outputSelected();
    }

}

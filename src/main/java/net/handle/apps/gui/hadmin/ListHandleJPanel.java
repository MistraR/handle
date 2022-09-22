/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.hdllib.*;
import net.handle.awt.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ListHandleJPanel extends JPanel implements ActionListener, ResponseMessageCallback {

    private final JTextField naField;
    private final JRadioButton outFileRadio;
    private final JTextField outFileField;
    private final JRadioButton outWindowRadio;
    private final JButton browseFileButton;
    private final JButton submitButton;
    private final JButton changeAuthButton;
    private final JTextArea consoleArea;

    private Writer hdlOut = null;

    private final HandleTool tool;

    public ListHandleJPanel(HandleTool tool) {
        super();
        this.tool = tool;
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        consoleArea = new JTextArea(10, 30);
        consoleArea.setEditable(false);
        naField = new JTextField("", 12);
        outFileRadio = new JRadioButton("File:", false);
        outWindowRadio = new JRadioButton("Window:", true);
        ButtonGroup bg = new ButtonGroup();
        bg.add(outFileRadio);
        bg.add(outWindowRadio);
        outFileField = new JTextField("handles.txt", 12);
        browseFileButton = new JButton("Browse...");
        submitButton = new JButton("Submit");
        changeAuthButton = new JButton("Authentication");

        int x = 0, y = 0;
        add(new JLabel("Prefix: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(naField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 1, 1, true, true));

        JPanel tmpPanel = new JPanel(gridbag);
        tmpPanel.add(changeAuthButton, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, false, false));
        tmpPanel.add(submitButton, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, false, false));
        add(tmpPanel, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, true, true));
        add(new JLabel(" "), AwtUtil.getConstraints(x, y++, 0, 0, 1, 1, true, true));

        add(new JLabel("Output To: "), AwtUtil.getConstraints(x, y++, 0, 0, 1, 1, true, true));
        tmpPanel = new JPanel(gridbag);
        tmpPanel.add(outFileRadio, AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, true, true));
        tmpPanel.add(outFileField, AwtUtil.getConstraints(1, 0, 1, 0, 1, 1, true, true));
        tmpPanel.add(browseFileButton, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, true, true));
        add(tmpPanel, AwtUtil.getConstraints(x, y++, 1, 0, 2, 1, true, true));
        add(new JLabel(" "), AwtUtil.getConstraints(x, y++, 0, 0, 1, 1, true, true));

        tmpPanel = new JPanel(gridbag);
        tmpPanel.add(outWindowRadio, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, true, true));
        tmpPanel.add(new JScrollPane(consoleArea), AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));
        add(tmpPanel, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, true, true));

        submitButton.addActionListener(this);
        browseFileButton.addActionListener(this);
        changeAuthButton.addActionListener(this);
        outFileRadio.addActionListener(this);
        outWindowRadio.addActionListener(this);
        outputSelected();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == submitButton) {
            listHandles();
        } else if (src == browseFileButton) {
            selectFile();
        } else if (src == changeAuthButton) {
            tool.changeAuthentication();
        } else if (src == outFileRadio || src == outWindowRadio) {
            outputSelected();
        }
    }

    private void listHandles() {
        String prefix = naField.getText().trim();
        if (prefix.length() <= 0) {
            JOptionPane.showMessageDialog(this, "Missing prefix", "Error Message: ", JOptionPane.ERROR_MESSAGE);
            return;
        }

        byte naHandle[] = Util.encodeString(prefix);
        if (!Util.hasSlash(naHandle)) naHandle = Util.convertSlashlessHandleToZeroNaHandle(naHandle); // backwards compatibility

        AuthenticationInfo authInfo = tool.getAuthentication();
        if (authInfo == null) return;
        ListHandlesRequest req = new ListHandlesRequest(naHandle, authInfo);

        consoleArea.setText("");
        consoleArea.repaint();
        try {
            if (outFileRadio.isSelected()) {
                hdlOut = new OutputStreamWriter(new FileOutputStream(new File(outFileField.getText())), "UTF-8");
            } else {
                hdlOut = new ConsoleWriter();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error setting up output: " + e, "Error Message", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            ResolutionRequest dudReq = new ResolutionRequest(Util.encodeString(prefix + "/test"), null, null, null);
            System.err.println("finding local sites for " + dudReq);
            SiteInfo sites[] = tool.resolver.findLocalSites(dudReq);

            // pick a site at random...
            SiteInfo site = sites[Math.abs(new java.util.Random().nextInt()) % sites.length];
            System.err.println("contacting site: " + site);

            tool.resolver.setTcpTimeout(300000);

            //set the version of request according to the site info
            // check if we're communicating with an older server.
            // If so, use their protocol version
            if ((site.majorProtocolVersion == 5 && site.minorProtocolVersion == 0) || (site.majorProtocolVersion < Common.MAJOR_VERSION)
                || (site.majorProtocolVersion == Common.MAJOR_VERSION && site.minorProtocolVersion < Common.MINOR_VERSION)) {
                req.majorProtocolVersion = site.majorProtocolVersion;
                req.minorProtocolVersion = site.minorProtocolVersion;
            } else {
                req.majorProtocolVersion = Common.MAJOR_VERSION;
                req.minorProtocolVersion = Common.MINOR_VERSION;
            }

            // send a list-handles request to each server in the site...
            for (int i = 0; i < site.servers.length; i++) {
                ServerInfo server = site.servers[i];
                System.err.println(" contacting server: " + server);
                try {
                    AbstractResponse response = tool.resolver.sendRequestToServer(req, site, server, this);
                    if (response.responseCode != AbstractMessage.RC_SUCCESS) {
                        System.err.println("got error: " + response);
                        JOptionPane.showMessageDialog(this, "Error: " + response, "Error Message", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Error: " + e, "Error Message", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (hdlOut != null) {
                try {
                    hdlOut.close();
                } catch (Exception e) {
                }
                hdlOut = null;
            }
        }

        System.err.println("done listing handles...");
    }

    @Override
    public void handleResponse(AbstractResponse response) {
        if (response instanceof ListHandlesResponse) {
            try {
                ListHandlesResponse lhResp = (ListHandlesResponse) response;
                byte handles[][] = lhResp.handles;
                for (int i = 0; i < handles.length; i++) {
                    hdlOut.write(Util.decodeString(handles[i]));
                    hdlOut.write("\n");
                    hdlOut.flush();
                }
            } catch (Exception e) {
                System.err.println("Error: " + e);
                e.printStackTrace(System.err);
            }

        } else if (response.responseCode != AbstractMessage.RC_AUTHENTICATION_NEEDED) {
            JOptionPane.showMessageDialog(this, "Error: " + response, "Error Message", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectFile() {
        String path = outFileField.getText();
        JFileChooser jfc;
        if (path.trim().length() > 0) {
            jfc = new JFileChooser(path);
        } else {
            jfc = new JFileChooser();
        }

        int result = jfc.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            if (selectedFile != null) {
                outFileField.setText(selectedFile.getAbsolutePath());
            }
        }
    }

    private void outputSelected() {
        boolean toFile = outFileRadio.isSelected();
        consoleArea.setEnabled(!toFile);
        browseFileButton.setEnabled(toFile);
        outFileField.setEnabled(toFile);
    }

    private class ConsoleWriter extends Writer {

        @Override
        public void write(char cbuf[], int off, int len) {
            consoleArea.append(new String(cbuf, off, len));
            consoleArea.repaint();
        }

        @Override
        public void write(String str) {
            consoleArea.append(str);
            consoleArea.repaint();
        }

        @Override
        public void close() {
        }

        @Override
        public void flush() {
        }
    }
}

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
import net.handle.apps.batch.*;
import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings("rawtypes")
public class BatchHandleJPanel extends JPanel implements ActionListener {
    BatchFileList batchFileList;
    BrowsePanel logFileBrowser;
    JRadioButton fileLogRadio;
    JRadioButton winLogRadio;
    JRadioButton stdoutLogRadio;
    TextArea logArea;
    JScrollPane logScrollPane;
    JButton submitButton;
    JButton stopButton;

    private HandleTool tool = null;
    private volatile GenericBatch currentBatch = null;
    private volatile static boolean stopSubmit = true;
    private Thread submitThread = null;

    public BatchHandleJPanel() {
        this(new HandleTool());
    }

    public BatchHandleJPanel(HandleTool tool) {
        super();
        this.tool = tool;

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        //set batchFileList
        batchFileList = new BatchFileList();
        batchFileList.setBorder(new TitledBorder(new EtchedBorder(), " Batch File List "));

        //set submission panel
        submitButton = new JButton("Submit Batch");
        submitButton.addActionListener(this);
        stopButton = new JButton("Stop Batch");
        stopButton.addActionListener(this);
        stopButton.setEnabled(false);
        JPanel submitPanel = new JPanel(gridbag);
        submitPanel.add(submitButton, AwtUtil.getConstraints(1, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        submitPanel.add(stopButton, AwtUtil.getConstraints(2, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        //set log panel
        fileLogRadio = new JRadioButton("File", false);
        fileLogRadio.addActionListener(this);
        stdoutLogRadio = new JRadioButton("Stdout", false);
        stdoutLogRadio.addActionListener(this);
        winLogRadio = new JRadioButton("Window", true);
        winLogRadio.addActionListener(this);
        final ButtonGroup bgroup = new ButtonGroup();
        bgroup.add(fileLogRadio);
        bgroup.add(stdoutLogRadio);
        bgroup.add(winLogRadio);
        JPanel radioPanel = new JPanel(gridbag);
        radioPanel.add(new JLabel("Output Log: "), AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, true, true));
        radioPanel.add(fileLogRadio, AwtUtil.getConstraints(1, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, true, true));
        radioPanel.add(stdoutLogRadio, AwtUtil.getConstraints(2, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, true, true));
        radioPanel.add(winLogRadio, AwtUtil.getConstraints(3, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, true, true));

        logFileBrowser = new BrowsePanel("Log File:", true);
        logFileBrowser.setEnabled(false);

        logArea = new TextArea(15, 30);
        logArea.setEditable(false);

        JPanel logPanel = new JPanel(gridbag);
        logPanel.setBorder(new TitledBorder(new EtchedBorder(), " Batch Log "));
        logPanel.add(radioPanel, AwtUtil.getConstraints(0, 0, 1, 0.1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        logPanel.add(logFileBrowser, AwtUtil.getConstraints(0, 1, 1, 0.1, 1, 1, new Insets(1, 5, 1, 5), true, true));
        logPanel.add(logArea, AwtUtil.getConstraints(0, 2, 1, 1, 1, 3, new Insets(5, 5, 5, 5), true, true));

        add(submitPanel, AwtUtil.getConstraints(0, 0, 1, 0.1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        add(batchFileList, AwtUtil.getConstraints(0, 1, 1, 0.1, 1, 2, new Insets(5, 5, 5, 5), true, true));
        add(logPanel, AwtUtil.getConstraints(0, 3, 1, 1, 1, 5, new Insets(5, 5, 5, 5), true, true));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == fileLogRadio || src == stdoutLogRadio || src == winLogRadio) {
            logFileBrowser.setEnabled(fileLogRadio.isSelected());
        } else if (src == submitButton) {
            stopSubmit = false;
            submitButton.setEnabled(false);
            stopButton.setEnabled(true);
            submitThread = new Thread() {
                @Override
                public void run() {
                    submitBatch();
                }
            };
            submitThread.start();
        } else if (src == stopButton) {
            stopSubmit = true;
            if (currentBatch != null) currentBatch.stopBatch();
            logArea.append("-->>>Stop batch process, waiting for finalization ...\n");
            //if(submitThread != null) {
            //  submitThread.interrupt();
            //  submitThread = null;
            //}
        }
    }

    // older code, will not fix
    @SuppressWarnings("resource")
    private void submitBatch() {
        PrintWriter log = null;
        BufferedReader batchReader = null;
        try {
            Vector vt = batchFileList.getItems();
            if (vt == null || vt.size() < 1) return;

            //set log writer
            if (fileLogRadio.isSelected()) {
                File[] files = new File[1];
                if (logFileBrowser.getWriteFile(files) && files[0] != null) {
                    log = new PrintWriter(new OutputStreamWriter(new FileOutputStream(files[0]), "UTF-8"), true);
                    logArea.append("\n-->>>Output log to file: " + files[0].getAbsolutePath() + "...\n\n");
                } else {
                    log = new PrintWriter(new TextAreaOutputStream(logArea), true);
                    logArea.append("\n-->>>Invalid log file: " + files[0].getAbsolutePath() + "\nOutput log to this window...\n\n");
                }
            } else if (winLogRadio.isSelected()) {
                log = new PrintWriter(new TextAreaOutputStream(logArea), true);
                logArea.append("\n-->>>Output log to this window...\n\n");
            } else {
                logArea.append("\n-->>>Output log to stdout...\n\n");
            }

            //process batches
            for (int i = 0; i < vt.size() && !stopSubmit; i++) {
                File batchFile = (File) vt.elementAt(i);
                try {
                    logArea.append("\n-->>>Batch(" + batchFile.getAbsolutePath() + ") process started ...\n");

                    java.util.Calendar calStart = java.util.Calendar.getInstance();
                    String currenttime = calStart.getTime().toString();
                    //long timeStarted = System.currentTimeMillis();
                    logArea.append("\n-->>>Started at " + currenttime + "\n");

                    FileInputStream fin = new FileInputStream(batchFile);
                    batchReader = new BufferedReader(new InputStreamReader(fin, "UTF-8"));

                    //added session management testing
                    AuthenticationInfo authInfo = tool.getAuthentication();

                    currentBatch = new GenericBatch(batchReader, authInfo, log, tool.resolver);
                    currentBatch.processBatch();

                    java.util.Calendar calEnd = java.util.Calendar.getInstance();
                    String currenttime1 = calEnd.getTime().toString();
                    logArea.append("\n-->>>Finished at " + currenttime1 + "\n");
                    //long timeEnded = System.currentTimeMillis();

                    //logArea.append("\n-->>>started time: " + timeStarted);
                    //logArea.append("\n-->>>ended time: " + timeEnded);
                    //logArea.append("\n-->>>Total batch execution time: " + (timeEnded - timeStarted) + " milliseconds \n");

                } catch (Exception e1) {
                    //if(!(e1 instanceof InterruptedException))
                    logArea.append("-->>>Batch caught exception: " + e1.getMessage());
                } finally {
                    if (batchReader != null) try {
                        batchReader.close();
                    } catch (Exception e1) {
                    }
                    logArea.append("-->>>Batch(" + batchFile.getAbsolutePath() + ") process finished\n");
                }
            }
        } catch (Throwable e) {
            logArea.append("-->>>Caught exception: " + e.getMessage());
        } finally {
            if (log != null) try {
                log.flush();
                log.close();
            } catch (Exception e2) {
            }
            submitButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    class BatchFileList extends DataListJPanel implements ActionListener {
        BatchFileList() {
            super();
            int x = 0;
            int y = 0;
            GridBagLayout gridbag = new GridBagLayout();
            buttonPanel = new JPanel(gridbag);
            buttonPanel.add(addItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(editItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(remItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(viewItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(clearButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            y = 0;
            savePanel = new JPanel(gridbag);
            savePanel.add(saveButton, AwtUtil.getConstraints(x++, y, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));

            savePanel.add(loadButton, AwtUtil.getConstraints(x++, y, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
            this.add(pane, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, true, true));
            this.add(buttonPanel, AwtUtil.getConstraints(2, 0, 1, 1, 1, 10, true, true));
            this.add(savePanel, AwtUtil.getConstraints(0, 11, 1, 1, 2, 1, true, true));

        }

        @Override
        protected Object addData() {
            BrowsePanel browser = new BrowsePanel("Batch file: ", false);
            File[] files = new File[1];
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, browser, "Add batch file: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            if (!browser.getReadFile(files)) {
                return null;
            }

            return files[0];
        }

        @Override
        protected Object modifyData(int ind) {
            File[] files = new File[1];
            File file = (File) items.elementAt(ind);
            BrowsePanel browser = new BrowsePanel("Batch: ", FileOpt.getParent(file), file.getName(), null, false);

            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, browser, "Modify batch file: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
            if (!browser.getReadFile(files)) {
                return null;
            }

            return files[0];
        }

        @Override
        protected void viewData(int ind) {
            File file = (File) items.elementAt(ind);
            BrowsePanel browser = new BrowsePanel("Batch: ", FileOpt.getParent(file), file.getName(), null, false);
            JOptionPane.showMessageDialog(this, browser, "View batch file: ", JOptionPane.PLAIN_MESSAGE);
        }

        @Override
        protected boolean removeData(int ind) {
            return true;
        }

        @Override
        protected void readData(File file) {
            BufferedReader in = null;
            if (file == null) return;
            int option = JOptionPane.showOptionDialog(this, "reset all or append as new", "Option :", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[] { "Reset All", "Append", "Cancel" }, "Reset All");
            if (option == JOptionPane.YES_OPTION) clearAll();
            try {
                FileInputStream fin = new FileInputStream(file);
                in = new BufferedReader(new InputStreamReader(fin, "UTF-8"));
                String str = " ";
                while ((str = in.readLine()) != null) {
                    File f = new File(str);
                    if (f.exists() && f.canRead()) this.appendItem(f);
                    else {
                        logArea.append("Error: can not find the file->" + str + "\n");
                    }
                }

                in.close();
            } catch (Exception e) {
                e.printStackTrace(System.err);
                if (in != null) try {
                    in.close();
                } catch (Exception e1) {
                }
                return;
            }

        }

        @Override
        protected void writeData(File file) {

        }
    }

    class TextAreaOutputStream extends OutputStream {
        private TextArea textArea;

        TextAreaOutputStream(TextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void close() {
            textArea = null;
        }

        @Override
        public void write(byte b[]) {
            textArea.append(new String(b));
        }

        @Override
        public void write(byte b[], int off, int len) {
            textArea.append(new String(b, off, len));
        }

        @Override
        public void write(int b) {
            textArea.append(String.valueOf(b));
        }
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        Container c = f.getContentPane();
        c.add(new BatchHandleJPanel());
        f.setSize(500, 500);
        f.pack();
        f.setVisible(true);
    }

}

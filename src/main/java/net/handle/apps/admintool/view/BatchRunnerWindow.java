/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.cnri.guiutil.GridC;
import net.handle.apps.batch.GenericBatch;
import net.handle.hdllib.*;
import net.handle.awt.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

public class BatchRunnerWindow extends JFrame implements ActionListener, ItemListener {
    private final AdminToolUI ui;
    private final JList<String> batchList;
    private final DefaultListModel<String> batchListModel;
    private final JButton addButton;
    private final JButton delButton;
    private final JButton runButton;
    private final JComboBox<String> outputChoice;
    private final JLabel outputFileLabel;
    private final JLabel consoleFillerLabel;
    private final JButton outputFileButton;
    private final JButton consoleSaveButton;
    private boolean isRunning = false;
    private final BatchRunnerWindow thisObj;

    private final AbstractConsolePanel console;

    public BatchRunnerWindow(AdminToolUI ui) {
        super(ui.getStr("batch_processor"));
        this.ui = ui;
        this.thisObj = this;

        setJMenuBar(ui.getAppMenu());

        batchListModel = new DefaultListModel<>();
        batchList = new JList<>(batchListModel);
        batchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addButton = new JButton(ui.getStr("add"));
        delButton = new JButton(ui.getStr("remove"));
        runButton = new JButton(ui.getStr("run_batch"));
        outputChoice = new JComboBox<>(new String[] { ui.getStr("to_window"), ui.getStr("to_file") });
        outputFileLabel = new JLabel("");
        consoleFillerLabel = new JLabel("");
        outputFileButton = new JButton(ui.getStr("choose_file"));
        console = new TextAreaBasedConsolePanel();
        consoleSaveButton = new JButton(ui.getStr("save_console"));

        JPanel p = new JPanel(new GridBagLayout());
        JPanel p1 = new JPanel(new GridBagLayout());
        p1.add(new JScrollPane(batchList), AwtUtil.getConstraints(0, 0, 1, 1, 1, 4, new Insets(5, 5, 5, 5), true, true));
        p1.add(addButton, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        p1.add(delButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        p1.add(new JLabel(" "), AwtUtil.getConstraints(1, 2, 0, 1, 1, 1, new Insets(5, 5, 5, 5), true, false));
        p1.add(runButton, AwtUtil.getConstraints(1, 3, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, false));
        p.add(p1, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, true, true));
        JPanel p2 = new JPanel(new GridBagLayout());
        p2.add(new JLabel(ui.getStr("send_output_to") + ":"), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p2.add(outputChoice, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p2.add(outputFileLabel, AwtUtil.getConstraints(2, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p2.add(consoleFillerLabel, AwtUtil.getConstraints(2, 0, 1, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p2.add(outputFileButton, AwtUtil.getConstraints(3, 0, 0, 0, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(p2, AwtUtil.getConstraints(0, 1, 1, 0, 1, 1, true, true));
        p.add(console, AwtUtil.getConstraints(0, 2, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        p.add(consoleSaveButton, GridC.getc(0, 3).west().insets(5, 5, 5, 5));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));

        getContentPane().add(p);

        addButton.addActionListener(this);
        delButton.addActionListener(this);
        runButton.addActionListener(this);
        outputFileButton.addActionListener(this);
        consoleSaveButton.addActionListener(this);
        outputChoice.addItemListener(this);

        pack();
        setSize(600, 400);
        AwtUtil.setWindowPosition(this, AwtUtil.WINDOW_CENTER);

        outputSelected();
    }

    private AuthenticationInfo authInfo = null;
    private PrintWriter outputWriter = null;

    private void runBatches() {
        isRunning = true;

        authInfo = ui.getAuthentication(false);
        if (authInfo == null) {
            isRunning = false;
            return;
        }

        try {
            if (outputChoice.getSelectedIndex() == 1) { // output to a file
                OutputStream fout = null;
                while (fout == null) {
                    try {
                        fout = new FileOutputStream(outputFileLabel.getText());
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(thisObj, ui.getStr("output_file_err_reselect") + ":\n " + e);
                        if (selectOutputFile()) {
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
            outputWriter = null;
            isRunning = false;
            return;
        }

        // set the input fields disabled while the batches are run
        outputChoice.setEnabled(false);
        outputFileButton.setEnabled(false);
        addButton.setEnabled(false);
        delButton.setEnabled(false);
        batchList.setEnabled(false);
        runButton.setEnabled(false);
        console.clear();

        Thread batchRunner = new Thread(new Runnable() {
            @Override
            public void run() {
                runBatchesAsync();
            }
        });
        batchRunner.setPriority(Thread.MIN_PRIORITY);
        batchRunner.start();
    }

    private String batchErrorMsg = null;

    private void runBatchesAsync() {
        String currentFile = null;
        try {
            String batchFiles[] = new String[batchListModel.size()];
            for (int i = 0; i < batchFiles.length; i++) {
                batchFiles[i] = batchListModel.elementAt(i);
            }

            for (int i = 0; i < batchFiles.length; i++) {
                currentFile = batchFiles[i];
                System.err.println("processing: " + currentFile);
                try (BufferedReader rdr = new BufferedReader(new InputStreamReader(new FileInputStream(currentFile), GenericBatch.ENCODING))) {
                    GenericBatch batch = new GenericBatch(rdr, authInfo, outputWriter, ui.getMain().getResolver());
                    batch.processBatch();
                }
            }
        } catch (Exception e) {
            batchErrorMsg = "Error processing batches:\n" + " batch file: " + currentFile + "\n" + " error: " + e + "\n";
        } finally {
            outputChoice.setEnabled(true);
            outputFileButton.setEnabled(true);
            addButton.setEnabled(true);
            delButton.setEnabled(true);
            batchList.setEnabled(true);
            runButton.setEnabled(true);
            isRunning = false;
        }

        if (batchErrorMsg != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (batchErrorMsg != null) {
                        JOptionPane.showMessageDialog(thisObj, batchErrorMsg);
                    }
                }
            });
        } else {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JOptionPane.showMessageDialog(thisObj, ui.getStr("batch_complete_msg"));
                }
            });
        }
    }

    private void outputSelected() {
        boolean toFile = outputChoice.getSelectedIndex() == 1;
        Dimension oldSize = getSize();

        console.setVisible(!toFile);
        consoleSaveButton.setVisible(!toFile);
        consoleFillerLabel.setVisible(!toFile);
        outputFileLabel.setVisible(toFile);
        outputFileButton.setVisible(toFile);

        pack();
        Dimension prefSize = getPreferredSize();
        setSize(new Dimension(Math.max(oldSize.width, prefSize.width), toFile ? prefSize.height : 400));
    }

    public void addBatchFile() {
        FileDialog fwin = new FileDialog(this, ui.getStr("open_batch_file"), FileDialog.LOAD);
        fwin.setVisible(true);
        String fileStr = fwin.getFile();
        String dirStr = fwin.getDirectory();
        if (fileStr == null || dirStr == null) return;
        File batchFile = new File(dirStr + fileStr);
        try {
            if (!batchFile.exists() || !batchFile.canRead()) {
                JOptionPane.showMessageDialog(thisObj, "The selected file: \n  " + dirStr + fileStr + "\neither doesn't exist, or is not readable.");
                return;
            } else {
                addBatchFile(batchFile);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(thisObj, "Error accessing file: " + dirStr + fileStr + "\n\nError Message: " + e);
        }
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

    public synchronized void addBatchFile(File newBatchFile) {
        batchListModel.addElement(newBatchFile.getAbsolutePath());
    }

    private void removeBatchFile() {
        int selIdx = batchList.getSelectedIndex();
        if (selIdx < 0 || selIdx >= batchListModel.size()) return;
        batchListModel.removeElementAt(selIdx);
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addButton) {
            addBatchFile();
        } else if (src == delButton) {
            removeBatchFile();
        } else if (src == runButton) {
            runBatches();
        } else if (src == outputFileButton) {
            selectOutputFile();
        } else if (src == consoleSaveButton) {
            saveConsole();
        }
    }

    private void saveConsole() {
        FileDialog fwin = new FileDialog(ui.getMainWindow(), ui.getStr("choose_console_file"), FileDialog.SAVE);
        fwin.setVisible(true);
        String fileStr = fwin.getFile();
        String dirStr = fwin.getDirectory();
        if (fileStr == null || dirStr == null) return;
        File saveFile = new File(dirStr + fileStr);
        try {
            if (saveFile.exists() && !saveFile.canWrite()) {
                JOptionPane.showMessageDialog(this, "The selected file: \n  " + dirStr + fileStr + "\nis not writeable.");
                return;
            }

            Writer fout = new OutputStreamWriter(new FileOutputStream(saveFile), "UTF-8");
            console.writeConsoleContents(fout);
            fout.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving file:\n  " + dirStr + fileStr + "\n\nError Message: " + e);
        }

    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        outputSelected();
    }

}

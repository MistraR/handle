/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;

public class Console extends JFrame implements ActionListener {
    private final AdminToolUI ui;
    private final PrintStream oldErr;
    private final PrintStream oldOut;
    private final ConsolePanel console;
    private final JButton clearButton;
    private final JButton saveButton;
    private final JButton closeButton;

    public Console(AdminToolUI ui) {
        super(ui.getStr("console"));
        this.ui = ui;

        setJMenuBar(ui.getAppMenu());

        clearButton = new JButton(ui.getStr("clear_console"));
        saveButton = new JButton(ui.getStr("save_console"));
        closeButton = new JButton(ui.getStr("dismiss"));
        console = new ConsolePanel();

        JPanel p = new JPanel(new GridBagLayout());
        p.add(console, AwtUtil.getConstraints(0, 0, 1, 1, 4, 1, true, true));
        p.add(clearButton, AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, new Insets(5, 10, 10, 5), true, true));
        p.add(saveButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, new Insets(5, 5, 10, 10), true, true));
        p.add(new JLabel(" "), AwtUtil.getConstraints(2, 1, 1, 0, 1, 1, new Insets(5, 10, 10, 10), true, true));
        p.add(closeButton, AwtUtil.getConstraints(3, 1, 0, 0, 1, 1, new Insets(5, 10, 10, 10), true, true));

        getContentPane().add(p);
        setSize(new Dimension(700, 200));

        this.oldErr = System.err;
        this.oldOut = System.out;
        System.setErr(new PrintStream(console.getOutputStream(), true));
        System.setOut(new PrintStream(console.getOutputStream(), true));

        clearButton.addActionListener(this);
        closeButton.addActionListener(this);
        saveButton.addActionListener(this);
    }

    @Override
    public void setVisible(boolean vis) {
        super.setVisible(vis);
        if (!vis) {
            System.setErr(oldErr);
            System.setOut(oldOut);
            ui.clearConsole();
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
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == clearButton) {
            console.clear();
        } else if (src == closeButton) {
            setVisible(false);
        } else if (src == saveButton) {
            saveConsole();
        }
    }

}

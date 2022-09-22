/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jutil.*;
import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class WorkWindow extends JFrame implements ActionListener {
    private final JPanel closePanel;
    private final JButton closeButton;
    private final MyButton helpButton;
    private final String helpDir;
    private final String helpFile;
    private final JFrame parent;

    public WorkWindow(JFrame parent, String title, JPanel workp, String helpDir, String helpFile) {
        super(title);
        // setLocation(parent.getLocation().x+parent.getSize().width,
        //             parent.getLocation().y);
        Container container = getContentPane();
        this.helpDir = helpDir;
        this.helpFile = helpFile;
        this.parent = parent;

        //set menupanel
        // menuPanel = new JPanel();
        helpButton = new MyButton("Help", "");
        helpButton.addActionListener(this);
        // menuPanel.add(helpButton);

        closePanel = new JPanel();
        closePanel.setLayout(new FlowLayout());
        closeButton = new JButton("Close");
        closeButton.addActionListener(this);
        closePanel.add(closeButton);
        closePanel.add(helpButton);

        //container.add(menuPanel,BorderLayout.NORTH);
        container.add(workp, BorderLayout.CENTER);
        container.add(closePanel, BorderLayout.SOUTH);
        pack();
        setSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, parent);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == helpButton) {
            HelpPanel.show(this.parent, helpDir, helpFile);
        } else if (src == closeButton) {
            setVisible(false);
        }
    }

    public static void show(JFrame parent, String title, JPanel workp, String helpDir, String helpFile) {
        WorkWindow work = new WorkWindow(parent, title, workp, helpDir, helpFile);

        AwtUtil.setWindowPosition(work, AwtUtil.WINDOW_CENTER);
        work.setVisible(true);
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class NewDialog extends JDialog implements ActionListener {
    private final JButton okButton;
    private final String command;
    private static int lx = 0;
    private static int ly = 0;

    public NewDialog(String button, String title, Component c, Frame f) {
        this(button, title, c, f, true);
    }

    public NewDialog(String button, String title, Component c, Frame f, boolean modal) {
        super(f == null ? new Frame() : f, title, modal);
        Container container = getContentPane();
        GridBagLayout gridbag = new GridBagLayout();
        container.setLayout(gridbag);

        okButton = new JButton(button);
        okButton.addActionListener(this);
        okButton.setActionCommand(button);
        command = button;
        container.add(c, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, true));

        JPanel buttonPanel = new JPanel(gridbag);

        buttonPanel.add(okButton, AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, false, false));
        container.add(buttonPanel, AwtUtil.getConstraints(0, 1, 1, 0, 1, 1, new Insets(2, 2, 10, 2), true, true));

        pack();
        Dimension prefSz = getPreferredSize();
        setSize(Math.max(Math.min(prefSz.width, 800), 50), Math.max(Math.min(prefSz.height, 500), 50));

        Dimension sz = getSize();
        Dimension psz;
        Point ploc;
        if (f != null) {
            psz = f.getSize();
            ploc = f.getLocationOnScreen();
        } else {
            psz = getToolkit().getScreenSize();
            ploc = new Point(lx, ly);
            lx = (lx + 50) % sz.width;
            ly = (ly + 50) % sz.height;
        }

        setLocation(ploc.x + (psz.width / 2 - sz.width / 2), ploc.y + (psz.height / 2 - sz.height / 2));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals(command)) {
            setVisible(false);
        }
    }

    public static void showMesgDialog(String button, String title, Component comp, Component parentComp, boolean model) {
        NewDialog win = new NewDialog(button, title, comp, AwtUtil.getFrame(parentComp), model);
        win.setVisible(true);

    }
}

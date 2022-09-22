/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.awt.*;
import javax.swing.*;

public class MyButton extends JButton {
    protected Font font;

    public MyButton(String astr, String tip, String command, ImageIcon icon) {
        super(astr, icon);
        setActionCommand(command);
        setToolTipText(tip);
    }

    public MyButton(String astr, String tip, String command) {
        super(astr, null);
        setActionCommand(command);
        setToolTipText(tip);
    }

    public MyButton(String astr, String tip) {
        super(astr, null);
        setActionCommand(astr);
        setToolTipText(tip);
    }

    public MyButton(String astr) {
        super(astr, null);
        setActionCommand(astr);
    }

    public MyButton(String astr, String tip, String command, ImageIcon icon, int fontSize) {
        super(astr, icon);
        font = new Font("buttonFont", Font.BOLD, fontSize);
        setFont(font);
        setActionCommand(command);
        setToolTipText(tip);

    }

    public MyButton(String astr, String tip, ImageIcon icon, int fontSize) {
        this(astr, tip, astr, icon, fontSize);
    }

    public MyButton(String astr, String tip, String command, int fontSize) {
        this(astr, tip, command, null, fontSize);
    }

    public MyButton(String astr, String tip, int fontSize) {
        this(astr, tip, astr, null, fontSize);
    }

    public MyButton(String astr, int fontSize) {
        this(astr, null, astr, null, fontSize);
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        MyButton b = new MyButton("trysomemany", "try it on", 20);
        MyButton a = new MyButton("oooo");
        Container c = f.getContentPane();
        JPanel p = new JPanel();
        p.add(b);
        p.add(a);
        c.add(p);
        f.setSize(200, 200);
        f.pack();
        f.setVisible(true);
    }

}

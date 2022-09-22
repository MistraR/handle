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

public class MyRadioButton extends JRadioButton {

    public MyRadioButton(String key, String astr, String tip, ImageIcon icon) {
        super(key, icon, false);
        setMargin(new Insets(5, 5, 5, 5));
        setActionCommand(astr);
        setToolTipText(tip);

    }

    public MyRadioButton(String astr, String tip, ImageIcon icon) {
        super(icon, false);
        setMargin(new Insets(5, 5, 5, 5));
        setActionCommand(astr);
        setToolTipText(tip);
    }

    public MyRadioButton(String key, String astr, String tip) {
        super(key, false);
        setMargin(new Insets(5, 5, 5, 5));
        setActionCommand(astr);
        setToolTipText(tip);
    }

    public MyRadioButton(String key, String tip) {
        super(key, false);
        setMargin(new Insets(5, 5, 5, 5));
        setActionCommand(key);
        setToolTipText(tip);
    }

    public MyRadioButton(String key) {
        super(key, false);
        setMargin(new Insets(5, 5, 5, 5));
        setActionCommand(key);
    }
}

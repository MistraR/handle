/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;

public class JTextPanel extends JPanel {

    public JTextPanel(String text) {
        super(new GridBagLayout());

        StringTokenizer tokenizer = new StringTokenizer(text, "\n");
        int currentRow = 0;
        add(new JLabel(""), AwtUtil.getConstraints(0, currentRow, 1, 1, 1, 1, true, true));
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            add(new JLabel(token, SwingConstants.LEFT), AwtUtil.getConstraints(1, currentRow++, 0, 0, 1, 1, true, false));
        }
        add(new Label(" "), AwtUtil.getConstraints(2, currentRow++, 1, 1, 1, 1, true, true));

    }

}

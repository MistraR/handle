/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.awt;

import java.awt.*;
import java.util.*;

public class TextPanel extends Panel {

    public TextPanel(String text) {
        super(new GridBagLayout());

        StringTokenizer tokenizer = new StringTokenizer(text, "\n");
        int currentRow = 0;
        add(new Label(""), AwtUtil.getConstraints(0, currentRow, 1, 1, 1, 1, true, true));
        while (tokenizer.hasMoreTokens()) {
            add(new Label(tokenizer.nextToken(), Label.LEFT), AwtUtil.getConstraints(1, currentRow++, 0, 0, 1, 1, true, false));
        }
        add(new Label(" "), AwtUtil.getConstraints(2, currentRow++, 1, 1, 1, 1, true, true));

    }

}

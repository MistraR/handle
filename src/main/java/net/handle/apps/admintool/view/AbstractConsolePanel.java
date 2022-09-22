/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import java.awt.LayoutManager;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

import javax.swing.JPanel;

public abstract class AbstractConsolePanel extends JPanel {

    public AbstractConsolePanel(LayoutManager layout) {
        super(layout);
    }

    abstract void writeConsoleContents(Writer w) throws IOException;

    abstract void addText(String text);

    abstract void clear();

    abstract OutputStream getOutputStream();

}

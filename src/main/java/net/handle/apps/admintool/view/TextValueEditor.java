/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import javax.swing.*;
import java.awt.*;

public class TextValueEditor extends JPanel implements HandleValueEditor {
    private final JTextArea inputField;

    public TextValueEditor() {
        super(new BorderLayout());
        inputField = new JTextArea(3, 3);

        add(new JScrollPane(inputField), BorderLayout.CENTER);
    }

    @Override
    public boolean saveValueData(HandleValue value) {
        value.setData(Util.encodeString(inputField.getText()));
        return true;
    }

    @Override
    public void loadValueData(HandleValue value) {
        inputField.setText(value == null ? "" : Util.decodeString(value.getData()));
    }

}

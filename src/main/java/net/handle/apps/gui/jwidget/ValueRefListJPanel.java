/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import javax.swing.*;

/*******************************************************************\
 * Value Reference List Panel
 *******************************************************************/
public class ValueRefListJPanel extends DataListJPanel {
    protected boolean editFlag;

    /**
     *@param editFlag if not true, only has view button
     **/
    public ValueRefListJPanel(boolean editFlag) {
        super();
        this.editFlag = editFlag;

        buttonPanel = new JPanel(new GridBagLayout());
        int x = 0;
        int y = 0;
        if (editFlag) {
            buttonPanel.add(addItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(editItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(remItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        }

        buttonPanel.add(viewItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, editFlag));
        y = 0;
        add(pane, AwtUtil.getConstraints(x, y, 1, 1, 2, 10, true, true));
        add(buttonPanel, AwtUtil.getConstraints(x + 2, y, 1, 1, 1, 8, true, true));
    }

    @Override
    protected Object addData() {
        ValueRefJPanel p = new ValueRefJPanel(true);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input Value:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

        ValueReference ref = new ValueReference(Util.encodeString(p.getHandleIdName()), p.getHandleIdIndex());
        return ref;
    }

    protected boolean removeData() {
        return true;
    }

    @Override
    protected Object modifyData(int ind) {

        ValueRefJPanel p = new ValueRefJPanel(true);
        ValueReference ref = (ValueReference) items.elementAt(ind);
        p.setHandleIdName(Util.decodeString(ref.handle));
        p.setHandleIdIndex(ref.index);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Modify Value:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
        ref = new ValueReference(Util.encodeString(p.getHandleIdName()), p.getHandleIdIndex());
        return ref;
    }

    @Override
    protected void viewData(int ind) {
        ValueRefJPanel p = new ValueRefJPanel(false);
        ValueReference ref = (ValueReference) items.elementAt(ind);
        p.setHandleIdName(Util.decodeString(ref.handle));
        p.setHandleIdIndex(ref.index);

        JOptionPane.showMessageDialog(this, p, "View Value:", JOptionPane.PLAIN_MESSAGE);
    }
}

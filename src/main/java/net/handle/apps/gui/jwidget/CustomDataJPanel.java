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
import java.awt.event.*;
import javax.swing.*;

public class CustomDataJPanel extends GenDataJPanel implements ComponentListener {
    protected JTextArea textArea;
    protected JScrollPane scroll;

    /**
     *@param moreFlag to enable the More Button to work
     *@param editFlag if not true, only view
     **/
    public CustomDataJPanel(byte[] type, boolean moreFlag, boolean editFlag) {
        this(type, moreFlag, editFlag, 0);
    }

    public CustomDataJPanel(byte[] type, boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, index);

        textArea = new JTextArea();
        scroll = new JScrollPane(textArea, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        String labelStr = " " + Util.decodeString(type) + ": ";

        textArea.setEditable(editFlag);

        scroll.setPreferredSize(new Dimension(800, 400));
        panel.addComponentListener(this);
        addComponentListener(this);
        scroll.setMinimumSize(new Dimension(600, 300));
        JLabel l = new JLabel(labelStr, SwingConstants.RIGHT);
        panel.add(l, AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, false, false));

        panel.add(scroll, AwtUtil.getConstraints(1, 1, 1, 0, GridBagConstraints.REMAINDER, 1, new Insets(1, 1, 1, 1), true, true));

        textArea.scrollRectToVisible(new Rectangle(0, 0, 10, 10));
        textArea.revalidate();

        handlevalue.setType(type);
        if (handlevalue.hasType(Common.STD_TYPE_HSSECKEY)) handlevalue.setAnyoneCanRead(false);
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY || data == null) {
            System.err.println("warning message: Handle value data is empty");
            return;
        }

        String str;
        if (Util.looksLikeBinary(data)) str = Util.decodeHexString(data, false);
        else str = Util.decodeString(data);

        textArea.setText(str);

        //scroll to top
        textArea.scrollRectToVisible(new Rectangle(0, 0, 10, 10));
        textArea.revalidate();
        textArea.invalidate();
    }

    @Override
    public byte[] getValueData() {
        try {
            String str = textArea.getText().trim();
            return (Util.encodeString(str));
        } catch (Exception e) {
            System.err.println("warning message: Exception at getValueData");
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

    // not an efficient way to do it, but work somehow
    @Override
    public void componentHidden(ComponentEvent e) {
    }

    @Override
    public void componentMoved(ComponentEvent e) {
    }

    @Override
    public void componentResized(ComponentEvent e) {

        if (e.getComponent() == this) {
            panel.setPreferredSize(new Dimension((getSize().width / 100) * 100, (getSize().height / 100) * 100));
        } else if (e.getComponent() == panel) {
            scroll.setPreferredSize(new Dimension((panel.getSize().width / 100) * 100, (panel.getSize().height / 100) * 100));

        }
    }

    @Override
    public void componentShown(ComponentEvent e) {
    }
}

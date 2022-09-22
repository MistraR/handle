/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public abstract class GenDataJPanel extends JPanel implements ActionListener {
    protected JTextField indexField;
    protected MyButton moreButton;
    protected JPanel panel;

    public abstract void setValueData(byte[] data);

    public abstract byte[] getValueData();

    protected HandleValue handlevalue;
    protected boolean moreFlag = true;
    protected boolean editFlag = true;

    /**
     *@param moreFlag to enable the More Button to work
     **/

    public GenDataJPanel(boolean moreFlag, boolean editFlag, int index) {
        this(moreFlag, editFlag, String.valueOf(index));
    }

    public GenDataJPanel(boolean moreFlag, boolean editFlag, String indStr) {
        super();
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);
        this.moreFlag = moreFlag;
        this.editFlag = editFlag;

        if (moreFlag) {
            indexField = new JTextField(indStr, 5);
            indexField.setToolTipText("Input handle value index");
            indexField.setEditable(editFlag);
            moreButton = new MyButton("More", "Edit detail");
            moreButton.addActionListener(this);
            moreButton.setEnabled(moreFlag);

            JPanel p1 = new JPanel(gridbag);
            p1.add(new JLabel(" Index: ", SwingConstants.RIGHT), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(1, 1, 1, 1), GridBagConstraints.WEST, true, true));
            p1.add(indexField, AwtUtil.getConstraints(1, 0, 0, 1, 1, 1, new Insets(1, 1, 1, 1), GridBagConstraints.WEST, false, false));
            p1.add(moreButton, AwtUtil.getConstraints(2, 0, 0, 1, 1, 1, new Insets(1, 1, 10, 50), false, false));
            add(p1, AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(1, 1, 1, 1), GridBagConstraints.WEST, true, true));
        }
        panel = new JPanel(gridbag);
        add(panel, AwtUtil.getConstraints(0, 1, 0, 0, 2, 1, true, true));

        handlevalue = new HandleValue();
        handlevalue.setIndex(-1);
        handlevalue.setTimestamp((int) (System.currentTimeMillis() / 1000));
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getActionCommand().equals("More")) more();
        else System.err.println("Error Input");
    }

    protected void more() {
        HandleValueEntryJPanel hvp = new HandleValueEntryJPanel(editFlag);
        handlevalue.setIndex(getIndex());
        hvp.setHandleValue(handlevalue);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, hvp, "Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;

        handlevalue = hvp.getHandleValue();
        indexField.setText(Integer.toString(handlevalue.getIndex()));
    }

    public void setIndex(int newIndex) {
        indexField.setText(String.valueOf(newIndex));
    }

    public void setHandleValue(HandleValue value) {
        if (value == null) return;

        if (moreFlag) setIndex(value.getIndex());

        handlevalue.setTTL(value.getTTL());
        handlevalue.setTTLType(value.getTTLType());
        handlevalue.setTimestamp(value.getTimestamp());
        handlevalue.setAdminCanRead(value.getAdminCanRead());
        handlevalue.setAdminCanWrite(value.getAdminCanWrite());
        handlevalue.setAnyoneCanRead(value.getAnyoneCanRead());
        handlevalue.setAnyoneCanWrite(value.getAnyoneCanWrite());
        ValueReference refs[] = value.getReferences();
        if (refs != null) {
            ValueReference newRefs[] = new ValueReference[refs.length];
            System.arraycopy(refs, 0, newRefs, 0, refs.length);
            handlevalue.setReferences(newRefs);
        } else {
            handlevalue.setReferences(null);
        }
        handlevalue.setData(value.getData());
        setValueData(handlevalue.getData());
    }

    public int getIndex() {
        try {
            return Integer.parseInt(indexField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public HandleValue getHandleValue() {
        if (moreFlag) handlevalue.setIndex(getIndex());
        handlevalue.setData(getValueData());
        //other data update in the more part
        return handlevalue;
    }
}

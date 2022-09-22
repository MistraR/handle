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
import java.util.*;
import javax.swing.*;

@SuppressWarnings({"incomplete-switch", "rawtypes", "unchecked"})
public class HandleValueEntryJPanel extends JPanel {
    protected JTextField indexField;
    protected JComboBox typeField;
    protected JComboBox ttlTypeChoice;
    protected JTextField ttlField;
    protected JTextField timestampField;
    protected JCheckBox adminReadCheckbox;
    protected JCheckBox adminWriteCheckbox;
    protected JCheckBox publicReadCheckbox;
    protected JCheckBox publicWriteCheckbox;
    protected ValueRefListJPanel referenceList;
    protected JPanel panel1;
    protected JPanel panel2;
    protected JPanel panel3;

    protected HandleValue handlevalue = null;

    public HandleValueEntryJPanel(boolean editFlag) {
        super();
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        indexField = new JTextField("", 5);
        indexField.setEditable(editFlag);

        ttlTypeChoice = new JComboBox();
        ttlTypeChoice.setToolTipText("Time_TO_Live of the have value");
        ttlTypeChoice.setEditable(false);
        ttlTypeChoice.setEnabled(editFlag);
        ttlTypeChoice.addItem("Relative");
        ttlTypeChoice.addItem("Absolute");
        ttlField = new JTextField("86400", 10);
        ttlField.setToolTipText("Input the Alive time");
        ttlField.setEditable(editFlag);

        timestampField = new JTextField("", 20);
        timestampField.setEditable(editFlag);

        adminReadCheckbox = new JCheckBox("Admin Read", true);
        adminReadCheckbox.setToolTipText("check to allow admin read");
        adminReadCheckbox.setEnabled(editFlag);

        adminWriteCheckbox = new JCheckBox("Admin Write", true);
        adminWriteCheckbox.setToolTipText("check to allow admin write");
        adminWriteCheckbox.setEnabled(editFlag);

        publicReadCheckbox = new JCheckBox("Public Read", true);
        publicReadCheckbox.setToolTipText("check to allow public read");
        publicReadCheckbox.setEnabled(editFlag);

        publicWriteCheckbox = new JCheckBox("Public Write", false);
        publicWriteCheckbox.setToolTipText("check to allow public write");
        publicWriteCheckbox.setEnabled(editFlag);

        referenceList = new ValueRefListJPanel(editFlag);

        typeField = new JComboBox();
        typeField.setToolTipText("Handle Value Type");
        typeField.setEditable(true);
        typeField.setEnabled(editFlag);
        for (int i = 0; i < CommonDef.DATA_TYPE_STR.length; i++)
            typeField.addItem(CommonDef.DATA_TYPE_STR[i]);

        panel1 = new JPanel(gridbag);
        panel2 = new JPanel(gridbag);
        panel3 = new JPanel(gridbag);

        int x = 0, y = 0;
        panel1.add(new JLabel("Index:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, new Insets(1, 10, 1, 5), GridBagConstraints.WEST, true, true));
        panel1.add(indexField, AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, false, true));

        panel1.add(new JLabel(" Type:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, new Insets(1, 20, 1, 0), GridBagConstraints.EAST, false, true));
        panel1.add(typeField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, false, true));

        x = 0;
        y = 0;
        panel2.add(new JLabel(" TTL:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, new Insets(1, 10, 1, 5), GridBagConstraints.WEST, true, true));
        panel2.add(ttlTypeChoice, AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel2.add(ttlField, AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));

        panel2.add(new JLabel(" Timestamp:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, new Insets(1, 20, 1, 0), GridBagConstraints.EAST, true, true));
        panel2.add(timestampField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, new Insets(1, 5, 1, 10), GridBagConstraints.WEST, true, true));

        x = 0;
        y = 0;
        JPanel tmpPanel = new JPanel(gridbag);
        tmpPanel.add(new JLabel(" Permissions:", SwingConstants.CENTER), AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, new Insets(10, 10, 10, 10), true, true));
        tmpPanel.add(adminReadCheckbox, AwtUtil.getConstraints(0, 1, 1, 0, 1, 1, new Insets(10, 10, 10, 5), true, true));
        tmpPanel.add(adminWriteCheckbox, AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, new Insets(10, 5, 10, 10), true, true));
        tmpPanel.add(publicReadCheckbox, AwtUtil.getConstraints(0, 2, 1, 0, 1, 1, new Insets(10, 10, 10, 5), true, true));
        tmpPanel.add(publicWriteCheckbox, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, new Insets(10, 5, 10, 10), true, true));
        panel3.add(tmpPanel, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, new Insets(10, 10, 10, 10), true, true));

        x++;
        tmpPanel = new JPanel(gridbag);
        tmpPanel.add(new JLabel(" References:", SwingConstants.CENTER), AwtUtil.getConstraints(0, 0, 0, 0, 2, 1, true, true));
        tmpPanel.add(referenceList, AwtUtil.getConstraints(0, 1, 1, 0, 1, 5, true, true));
        panel3.add(tmpPanel, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, new Insets(10, 10, 10, 10), GridBagConstraints.WEST, true, true));

        add(panel1, AwtUtil.getConstraints(0, 0, 1, 0, 3, 1, new Insets(10, 10, 10, 10), GridBagConstraints.WEST, false, true));
        add(panel2, AwtUtil.getConstraints(0, 1, 1, 1, 4, 1, new Insets(10, 10, 10, 10), true, true));
        add(panel3, AwtUtil.getConstraints(0, 2, 1, 0, 4, 1, new Insets(10, 10, 10, 10), true, true));

        handlevalue = new HandleValue();
        handlevalue.setIndex(-1);
        handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
        handlevalue.setTimestamp((int) (System.currentTimeMillis() / 1000));
        timestampField.setText(new Date(((long) handlevalue.getTimestamp()) * ((long) 1000)).toString());
    }

    public void setHandleValue(HandleValue value) {
        setIndex(value.getIndex());
        typeField.setSelectedItem(Util.decodeString(value.getType()));

        switch (value.getTTLType()) {
        case HandleValue.TTL_TYPE_ABSOLUTE:
            ttlTypeChoice.setSelectedIndex(1);
            break;
        case HandleValue.TTL_TYPE_RELATIVE:
            ttlTypeChoice.setSelectedIndex(0);
            break;
        }
        ttlField.setText(String.valueOf(value.getTTL()));
        timestampField.setText(new Date(((long) value.getTimestamp()) * ((long) 1000)).toString());
        adminReadCheckbox.setSelected(value.getAdminCanRead());
        adminWriteCheckbox.setSelected(value.getAdminCanWrite());
        publicReadCheckbox.setSelected(value.getAnyoneCanRead());
        publicWriteCheckbox.setSelected(value.getAnyoneCanWrite());
        ValueReference refs[] = value.getReferences();
        referenceList.clearAll();
        if (refs != null) {
            for (int i = 0; i < refs.length; i++)
                referenceList.appendItem(refs[i]);
        }

        handlevalue.setTimestamp(value.getTimestamp());
        handlevalue.setType(value.getType());
        handlevalue.setData(value.getData());
    }

    public void setIndex(int index) {
        indexField.setText(String.valueOf(index));
    }

    public HandleValue getHandleValue() {
        handlevalue.setIndex(getIndex());

        try {
            handlevalue.setTTL(Integer.parseInt(ttlField.getText().trim()));
            if (ttlTypeChoice.getSelectedIndex() == 0) {
                handlevalue.setTTLType(HandleValue.TTL_TYPE_RELATIVE);
            } else {
                handlevalue.setTTLType(HandleValue.TTL_TYPE_ABSOLUTE);
            }
        } catch (Exception e) {
            handlevalue.setTTL(-1);
        }

        handlevalue.setAdminCanRead(adminReadCheckbox.isSelected());
        handlevalue.setAdminCanWrite(adminWriteCheckbox.isSelected());
        handlevalue.setAnyoneCanRead(publicReadCheckbox.isSelected());
        handlevalue.setAnyoneCanWrite(publicWriteCheckbox.isSelected());

        Vector v = referenceList.getItems();
        if (v != null) {
            ValueReference[] vr = new ValueReference[v.size()];
            for (int i = 0; i < v.size(); i++)
                vr[i] = (ValueReference) v.elementAt(i);
            handlevalue.setReferences(vr);
        }
        //timestamp is the server system time
        //value data keeps same
        //type keeps same
        return handlevalue;
    }

    public int getIndex() {
        try {
            return Integer.parseInt(indexField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import net.handle.awt.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ValueReferenceListEditor extends JPanel implements ActionListener, HandleValueEditor {
    @SuppressWarnings("hiding")
    private final AdminToolUI ui;
    private final DefaultListModel<ValueReference> valueListModel;
    private final JList<ValueReference> valueList;
    private final JButton addButton;
    private final JButton editButton;
    private final JButton delButton;

    public ValueReferenceListEditor(AdminToolUI ui) {
        super(new GridBagLayout());
        this.ui = ui;

        valueListModel = new DefaultListModel<>();
        valueList = new JList<>(valueListModel);
        valueList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        addButton = new JButton(ui.getStr("add"));
        editButton = new JButton(ui.getStr("modify"));
        delButton = new JButton(ui.getStr("remove"));

        add(new JScrollPane(valueList), AwtUtil.getConstraints(0, 0, 1, 1, 1, 4, new Insets(4, 4, 4, 4), true, true));
        add(addButton, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));
        add(editButton, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));
        add(delButton, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, new Insets(4, 4, 4, 4), true, false));

        addButton.addActionListener(this);
        editButton.addActionListener(this);
        delButton.addActionListener(this);
        valueList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && editButton.isEnabled()) {
                    editValue();
                }
            }
        });
    }

    /**
     * Saves the handle value data in the given HandleValue.  Returns false
     * if there was an error and the operation was canceled.
     */
    @Override
    public boolean saveValueData(HandleValue value) {
        ValueReference values[] = new ValueReference[valueListModel.getSize()];
        valueListModel.copyInto(values);
        value.setData(Encoder.encodeValueReferenceList(values));
        return true;
    }

    /**
     * Sets the handle value data from the given HandleValue.
     */
    @Override
    public void loadValueData(HandleValue value) {
        ValueReference values[] = null;
        try {
            values = Encoder.decodeValueReferenceList(value.getData(), 0);
        } catch (Exception e) {
            System.err.println("Unable to extract value reference list from " + value);
            values = new ValueReference[0];
        }

        valueListModel.removeAllElements();
        for (int i = 0; values != null && i < values.length; i++)
            if (values[i] != null) valueListModel.addElement(values[i]);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addButton) addValue();
        else if (src == editButton) editValue();
        else if (src == delButton) removeValue();
        else if (src == valueList) editValue();
    }

    private void addValue() {
        ValueReferenceEditor valRefPanel = new ValueReferenceEditor();
        while (true) {
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, valRefPanel, ui.getStr("enter_value") + ": ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
            ValueReference valRef = valRefPanel.getValue();
            if (valRef != null) {
                valueListModel.addElement(valRef);
                return;
            }
        }

    }

    private void editValue() {
        int selIdx = valueList.getSelectedIndex();
        if (selIdx < 0) return;
        ValueReference valRef = valueListModel.get(selIdx);
        ValueReferenceEditor valRefPanel = new ValueReferenceEditor(valRef);
        while (true) {
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, valRefPanel, ui.getStr("enter_value") + ": ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
            valRef = valRefPanel.getValue();
            if (valRef != null) {
                valueListModel.set(selIdx, valRef);
                return;
            }
        }

    }

    private void removeValue() {
        java.util.List<?> obj = valueList.getSelectedValuesList();
        for (int i = 0; obj != null && i < obj.size(); i++) {
            if (obj.get(i) == null) continue;
            valueListModel.removeElement(obj.get(i));
        }
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.handle.hdllib.*;
import net.handle.awt.*;

import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;

import java.awt.*;

public class HandleInfoPanel extends JPanel implements ActionListener, ListSelectionListener, ChangeListener {
    private final JList<HandleValue> valueList;
    private final DefaultListModel<HandleValue> valueListModel;
    private final JButton addValueButton;
    private final JButton remValueButton;
    private final JButton upValueButton;
    private final JButton downValueButton;

    private final HandleValuePanel valuePanel;
    private HandleValue currentValue = null;

    public HandleInfoPanel() {
        super(new GridBagLayout());

        valueListModel = new DefaultListModel<>();
        valueList = new JList<>(valueListModel);
        addValueButton = new JButton("Add");
        remValueButton = new JButton("Remove");
        upValueButton = new JButton("^");
        downValueButton = new JButton("v");

        valuePanel = new HandleValuePanel();
        valuePanel.addChangeListener(this);

        JPanel tmpPanel = new JPanel(new GridBagLayout());
        tmpPanel.add(new JScrollPane(valueList), AwtUtil.getConstraints(0, 0, 1, 1, 5, 1, true, true));
        tmpPanel.add(addValueButton, AwtUtil.getConstraints(0, 1, 1, 0, 1, 1, true, true));
        tmpPanel.add(remValueButton, AwtUtil.getConstraints(1, 1, 1, 0, 1, 1, true, true));
        tmpPanel.add(upValueButton, AwtUtil.getConstraints(3, 1, 1, 0, 1, 1, true, true));
        tmpPanel.add(downValueButton, AwtUtil.getConstraints(4, 1, 1, 0, 1, 1, true, true));

        add(new JLabel("Values: ", Label.RIGHT), AwtUtil.getConstraints(0, 0, 0f, 0f, 1, 1, true, true));
        add(tmpPanel, AwtUtil.getConstraints(0, 1, 1f, 1f, 2, 1, true, true));
        add(new JLabel("Click Add or select an existing handle value in the list above, then edit the selected value using the controls below."), AwtUtil.getConstraints(0, 2, 0f, 0.5f, 2, 1, true, true));
        //    add(new JLabel(" "),
        //        AwtUtil.getConstraints(0,2,1f,0f,2,1,true,true));
        add(valuePanel, AwtUtil.getConstraints(0, 3, 1f, 0f, 2, 1, true, true));

        //valueList.addActionListener(this);
        valueList.addListSelectionListener(this);
        addValueButton.addActionListener(this);
        remValueButton.addActionListener(this);
        upValueButton.addActionListener(this);
        downValueButton.addActionListener(this);
        listValueSelected();
    }

    public void setHandleValues(HandleValue newValues[]) {
        valueListModel.clear();
        if (newValues != null) {
            for (int i = 0; i < newValues.length; i++) {
                valueListModel.addElement(newValues[i]);
            }
        }
    }

    public HandleValue[] getHandleValues() {
        listValueSelected();
        HandleValue values[] = new HandleValue[valueListModel.size()];
        for (int i = 0; i < valueListModel.size(); i++) {
            values[i] = valueListModel.elementAt(i);
        }
        return values;
    }

    private void listValueSelected() {
        if (currentValue != null) {
            // save the current edits, if necessary
            valuePanel.getHandleValue(currentValue);
        }

        int newIndex = valueList.getSelectedIndex();
        if (newIndex < 0 || newIndex >= valueListModel.size()) {
            valuePanel.setEnabled(false);
            currentValue = null;
        } else {
            HandleValue selValue = valueListModel.elementAt(newIndex);
            if (selValue != currentValue) {
                // a new value has been selected
                //        System.err.println("new value selected: "+selValue);
                currentValue = selValue;
                valuePanel.setEnabled(true);
                valuePanel.setHandleValue(currentValue);
            }
        }
    }

    private void evtAddValue() {
        HandleValue newValue = new HandleValue();
        valueListModel.addElement(newValue);
        valueList.setSelectedIndex(valueListModel.size() - 1);
        listValueSelected();
    }

    private void evtRemValue() {
        int idx = valueList.getSelectedIndex();
        if (idx < 0) return;
        valueListModel.removeElementAt(idx);
        listValueSelected();
    }

    private void evtUpValue() {
        int idx = valueList.getSelectedIndex();
        if (idx <= 0) return;
        HandleValue value = valueListModel.elementAt(idx);
        valueListModel.removeElementAt(idx);
        valueListModel.insertElementAt(value, idx - 1);
        valueList.setSelectedIndex(idx - 1);
    }

    private void evtDownValue() {
        int idx = valueList.getSelectedIndex();
        if (idx < 0 || idx == (valueListModel.size() - 1)) return;
        HandleValue value = valueListModel.elementAt(idx);
        valueListModel.removeElementAt(idx);
        valueListModel.insertElementAt(value, idx + 1);
        valueList.setSelectedIndex(idx + 1);
    }

    @Override
    public void valueChanged(ListSelectionEvent evt) {
        if (evt.getSource() == valueList) {
            listValueSelected();
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == addValueButton) evtAddValue();
        else if (src == remValueButton) evtRemValue();
        else if (src == upValueButton) evtUpValue();
        else if (src == downValueButton) evtDownValue();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (currentValue != null) {
            // save the current edits, if necessary
            valuePanel.getHandleValue(currentValue);
        }
        int index = valueList.getSelectedIndex();
        if (index >= 0 && index < valueListModel.size()) {
            valueListModel.set(index, valueListModel.get(index));
        }
    }
}

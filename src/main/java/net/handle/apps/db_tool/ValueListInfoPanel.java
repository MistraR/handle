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
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class ValueListInfoPanel extends Panel implements ActionListener, ItemListener {
    private final java.awt.List referenceList;
    private final Button newValueButton;
    private final Button deleteValueButton;
    private final Button upValueButton;
    private final Button downValueButton;

    private final TextField valueNameField;
    private final TextField valueIndexField;

    private ValueReference currentValue = null;
    private final Vector<ValueReference> references;

    public ValueListInfoPanel() {
        references = new Vector<>();

        referenceList = new java.awt.List(6);
        newValueButton = new Button("New Value");
        deleteValueButton = new Button("Delete Value");
        upValueButton = new Button("^");
        downValueButton = new Button("v");

        valueNameField = new TextField("", 15);
        valueIndexField = new TextField("0", 5);

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);

        int x = 0, y = 0;
        Panel topPanel = new Panel(gridbag);
        topPanel.add(referenceList, AwtUtil.getConstraints(x, y, 1, 1, 1, 4, true, true));
        topPanel.add(newValueButton, AwtUtil.getConstraints(x + 1, y++, 1, 0, 2, 1, true, true));
        topPanel.add(deleteValueButton, AwtUtil.getConstraints(x + 1, y++, 1, 0, 2, 1, true, true));
        topPanel.add(upValueButton, AwtUtil.getConstraints(x + 1, y, 1, 0, 1, 1, true, true));
        topPanel.add(downValueButton, AwtUtil.getConstraints(x + 2, y, 1, 0, 1, 1, true, true));
        x = y = 0;
        add(topPanel, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, true, false));
        add(new Label("Handle", Label.CENTER), AwtUtil.getConstraints(x, y, 1, 0, 1, 1, true, false));
        add(new Label("Index#", Label.CENTER), AwtUtil.getConstraints(x + 1, y++, 1, 0, 1, 1, true, false));
        add(valueNameField, AwtUtil.getConstraints(x, y, 1, 1, 1, 1, true, false));
        add(valueIndexField, AwtUtil.getConstraints(x + 1, y++, 1, 1, 1, 1, true, false));

        newValueButton.addActionListener(this);
        deleteValueButton.addActionListener(this);
        upValueButton.addActionListener(this);
        downValueButton.addActionListener(this);
        referenceList.addItemListener(this);

        rebuildReferenceList();
        updateEntryFields();
    }

    public void setValueListInfo(ValueReference newRefs[]) {
        referenceList.removeAll();
        currentValue = null;
        referenceList.select(-1);
        updateEntryFields();
        if (newRefs != null) {
            for (int i = 0; i < newRefs.length; i++) {
                references.addElement(new ValueReference(newRefs[i].handle, newRefs[i].index));
            }
        }
        rebuildReferenceList();
    }

    private void updateEntryFields() {
        int selectedIndex = referenceList.getSelectedIndex();
        System.err.println("updating fields, index=" + selectedIndex);
        if (selectedIndex < 0) {
            valueNameField.setText("");
            valueIndexField.setText("");
            currentValue = null;
        } else {
            ValueReference val = references.elementAt(selectedIndex);
            valueNameField.setText(Util.decodeString(val.handle));
            valueIndexField.setText(String.valueOf(val.index));
            currentValue = val;
        }
        valueNameField.setEnabled(currentValue != null);
        valueIndexField.setEnabled(currentValue != null);
    }

    private void saveCurrentValue() {
        if (currentValue == null) return;
        currentValue.handle = Util.encodeString(valueNameField.getText());
        try {
            currentValue.index = Integer.parseInt(valueIndexField.getText().trim());
        } catch (Exception e) {
            System.err.println("Invalid index value: " + valueIndexField.getText());
            getToolkit().beep();
        }
        rebuildReferenceList();
    }

    private void rebuildReferenceList() {
        referenceList.removeAll();
        for (int i = 0; i < references.size(); i++) {
            referenceList.add(String.valueOf(references.elementAt(i)));
        }

        int sel = -1;
        if (currentValue != null) sel = references.indexOf(currentValue);
        referenceList.select(sel);
    }

    public ValueReference[] getValueListInfo() {
        saveCurrentValue();
        ValueReference refs[] = new ValueReference[references.size()];
        for (int i = 0; i < refs.length; i++) {
            ValueReference ref = references.elementAt(i);
            System.err.println("getting value: " + ref);
            refs[i] = new ValueReference(ref.handle, ref.index);
        }
        return refs;
    }

    private void itemSelected() {
        saveCurrentValue();
        updateEntryFields();
    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        if (evt.getSource() == referenceList) {
            itemSelected();
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == newValueButton) {
            saveCurrentValue();
            references.addElement(new ValueReference(Util.encodeString(""), 0));
            rebuildReferenceList();
            referenceList.select(references.size() - 1);
            updateEntryFields();
        } else if (src == deleteValueButton) {
            if (currentValue != null) {
                references.removeElement(currentValue);
                currentValue = null;
                rebuildReferenceList();
            }
        } else if (src == upValueButton) {
            if (currentValue != null) {
                int idx = references.indexOf(currentValue);
                if (idx > 0) {
                    references.removeElement(currentValue);
                    references.insertElementAt(currentValue, idx - 1);
                    rebuildReferenceList();
                }
            }
        } else if (src == downValueButton) {
            if (currentValue != null) {
                int idx = references.indexOf(currentValue);
                if (idx >= 0 && idx < (references.size() - 1)) {
                    references.removeElement(currentValue);
                    references.insertElementAt(currentValue, idx + 1);
                    rebuildReferenceList();
                }
            }
        }
    }
}

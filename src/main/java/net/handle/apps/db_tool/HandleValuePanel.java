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

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class HandleValuePanel extends JPanel implements ActionListener, DocumentListener {
    private final CopyOnWriteArrayList<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    private boolean isTriggerChanges = false;

    private final JTextField indexField;
    private final JTextField typeField;
    private final JTextField dataField;
    private byte data[];
    private final JButton editDataButton;
    private final JComboBox<String> ttlTypeChoice;
    private final JTextField ttlField;
    private final JTextField timestampField;
    private final JCheckBox adminReadCheckbox;
    private final JCheckBox adminWriteCheckbox;
    private final JCheckBox publicReadCheckbox;
    private final JCheckBox publicWriteCheckbox;

    private final JPanel refsPanel;
    private final JList<String> referenceList;
    private final DefaultListModel<String> referenceListModel;
    private final Vector<ValueReference> references;
    private final JButton addReferenceButton;
    private final JButton remReferenceButton;
    private final JButton editReferenceButton;

    public HandleValuePanel() {
        super(new GridBagLayout());
        references = new Vector<>();

        indexField = new JTextField("", 10);
        indexField.getDocument().addDocumentListener(this);
        indexField.addActionListener(this);
        typeField = new JTextField("", 10);
        typeField.getDocument().addDocumentListener(this);
        typeField.addActionListener(this);
        dataField = new JTextField("", 20);
        dataField.setEditable(false);
        editDataButton = new JButton("Edit");
        ttlTypeChoice = new JComboBox<>();
        ttlTypeChoice.addItem("Relative");
        ttlTypeChoice.addItem("Absolute");
        ttlTypeChoice.addActionListener(this);
        ttlField = new JTextField("", 6);
        ttlField.getDocument().addDocumentListener(this);
        ttlField.addActionListener(this);
        timestampField = new JTextField("", 10);
        timestampField.setEditable(false);
        adminReadCheckbox = new JCheckBox("AR", true);
        adminReadCheckbox.addActionListener(this);
        adminWriteCheckbox = new JCheckBox("AW", true);
        adminWriteCheckbox.addActionListener(this);
        publicReadCheckbox = new JCheckBox("PR", true);
        publicReadCheckbox.addActionListener(this);
        publicWriteCheckbox = new JCheckBox("PW", false);
        publicWriteCheckbox.addActionListener(this);

        referenceListModel = new DefaultListModel<>();
        referenceList = new JList<>(referenceListModel);
        addReferenceButton = new JButton("Add");
        remReferenceButton = new JButton("Remove");
        editReferenceButton = new JButton("Modify");

        JPanel panel1 = new JPanel(new GridBagLayout());
        JPanel panel2 = new JPanel(new GridBagLayout());
        JPanel panel3 = new JPanel(new GridBagLayout());

        int x = 0, y = 0;
        panel1.add(new JLabel("Index:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel1.add(indexField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        panel1.add(new JLabel(" Type:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel1.add(typeField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        panel1.add(new JLabel(" Data:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel1.add(dataField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));
        panel1.add(editDataButton, AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));

        x = 0;
        y = 0;
        panel2.add(new JLabel(" TTL:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel2.add(ttlTypeChoice, AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel2.add(ttlField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        panel2.add(new JLabel(" Timestamp:", SwingConstants.RIGHT), AwtUtil.getConstraints(x++, y, 0, 0, 1, 1, true, true));
        panel2.add(timestampField, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        x = 0;
        y = 0;
        JPanel tmpPanel = new JPanel(new GridBagLayout());
        tmpPanel.add(new JLabel(" Permissions:", SwingConstants.LEFT), AwtUtil.getConstraints(0, 0, 0, 0, 2, 1, true, true));
        tmpPanel.add(adminReadCheckbox, AwtUtil.getConstraints(0, 1, 0, 0, 1, 1, true, true));
        tmpPanel.add(adminWriteCheckbox, AwtUtil.getConstraints(1, 1, 0, 0, 1, 1, true, true));
        tmpPanel.add(publicReadCheckbox, AwtUtil.getConstraints(0, 2, 0, 0, 1, 1, true, true));
        tmpPanel.add(publicWriteCheckbox, AwtUtil.getConstraints(1, 2, 0, 0, 1, 1, true, true));
        panel3.add(tmpPanel, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        x++;
        refsPanel = new JPanel(new GridBagLayout());
        refsPanel.add(new JLabel(" Refs:", SwingConstants.LEFT), AwtUtil.getConstraints(0, 0, 0, 0, 2, 1, true, true));
        refsPanel.add(new JScrollPane(referenceList), AwtUtil.getConstraints(0, 1, 1, 0, 1, 5, true, true));
        //    refsPanel.add(addReferenceButton,
        //                 AwtUtil.getConstraints(1,1,0,0,1,1,true,true));
        //    refsPanel.add(remReferenceButton,
        //                 AwtUtil.getConstraints(1,2,0,0,1,1,true,true));
        //    refsPanel.add(editReferenceButton,
        //                 AwtUtil.getConstraints(1,3,0,0,1,1,true,true));
        panel3.add(refsPanel, AwtUtil.getConstraints(x++, y, 1, 0, 1, 1, true, true));

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refsPanel.setVisible(false);
            }
        });

        add(panel1, AwtUtil.getConstraints(0, 0, 0, 0, 1, 1, true, true));
        add(panel2, AwtUtil.getConstraints(0, 1, 0, 1, 1, 1, true, true));
        add(panel3, AwtUtil.getConstraints(0, 2, 0, 0, 1, 1, true, true));

        addReferenceButton.addActionListener(this);
        remReferenceButton.addActionListener(this);
        editReferenceButton.addActionListener(this);
        editDataButton.addActionListener(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        indexField.setEditable(enabled);
        typeField.setEditable(enabled);
        editDataButton.setEnabled(enabled);
        ttlTypeChoice.setEnabled(enabled);
        ttlField.setEditable(enabled);
        adminReadCheckbox.setEnabled(enabled);
        adminWriteCheckbox.setEnabled(enabled);
        publicReadCheckbox.setEnabled(enabled);
        publicWriteCheckbox.setEnabled(enabled);
    }

    private void updateDataLabel() {
        if (data == null) {
            dataField.setText("<null>");
        } else if (Util.looksLikeBinary(data)) {
            dataField.setText(Util.decodeHexString(data, false));
        } else {
            dataField.setText(Util.decodeString(data));
        }
    }

    private void rebuildReferenceList() {
        referenceListModel.clear();
        for (int i = 0; i < references.size(); i++) {
            referenceListModel.addElement(String.valueOf(references.elementAt(i)));
        }
        refsPanel.setVisible(!references.isEmpty());
    }

    public void setHandleValue(HandleValue value) {
        setTriggerChanges(false);

        data = value.getData();
        updateDataLabel();
        indexField.setText(String.valueOf(value.getIndex()));
        typeField.setText(Util.decodeString(value.getType()));

        switch (value.getTTLType()) {
        case HandleValue.TTL_TYPE_ABSOLUTE:
            ttlTypeChoice.setSelectedIndex(1);
            break;
        case HandleValue.TTL_TYPE_RELATIVE:
        default:
            ttlTypeChoice.setSelectedIndex(0);
        }
        ttlField.setText(String.valueOf(value.getTTL()));
        timestampField.setText(String.valueOf(new Date(value.getTimestamp() * 1000)));
        adminReadCheckbox.setSelected(value.getAdminCanRead());
        adminWriteCheckbox.setSelected(value.getAdminCanWrite());
        publicReadCheckbox.setSelected(value.getAnyoneCanRead());
        publicWriteCheckbox.setSelected(value.getAnyoneCanWrite());

        references.removeAllElements();
        ValueReference refs[] = value.getReferences();
        if (refs != null) {
            for (int i = 0; i < refs.length; i++) {
                references.addElement(refs[i]);
            }
        }
        rebuildReferenceList();

        setTriggerChanges(true);
    }

    public void getHandleValue(HandleValue value) {
        try {
            value.setIndex(Integer.parseInt(indexField.getText().trim()));
        } catch (Exception e) {
            String val = indexField.getText().trim();
            if (!val.equals("-") && !val.isEmpty()) {
                System.err.println("Error: invalid index value: " + indexField.getText());
            }
        }
        try {
            value.setTTL(Integer.parseInt(ttlField.getText().trim()));
            if (ttlTypeChoice.getSelectedIndex() == 0) {
                value.setTTLType(HandleValue.TTL_TYPE_RELATIVE);
            } else {
                value.setTTLType(HandleValue.TTL_TYPE_ABSOLUTE);
            }
        } catch (Exception e) {
            String val = ttlField.getText().trim();
            if (!val.equals("-") && !val.isEmpty()) {
                System.err.println("Error: invalid ttl: " + ttlField.getText());
            }
        }

        value.setData(data);
        value.setType(Util.encodeString(typeField.getText()));
        value.setAdminCanRead(adminReadCheckbox.isSelected());
        value.setAdminCanWrite(adminWriteCheckbox.isSelected());
        value.setAnyoneCanRead(publicReadCheckbox.isSelected());
        value.setAnyoneCanWrite(publicWriteCheckbox.isSelected());
        ValueReference refs[] = new ValueReference[references.size()];
        for (int i = 0; i < refs.length; i++) {
            refs[i] = references.elementAt(i);
        }
        value.setReferences(refs);
    }

    private void editData() {
        DefaultDataPanel dataPanel = new DefaultDataPanel();
        dataPanel.setValue(data);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Edit Data", dataPanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        data = dataPanel.getValue();
        updateDataLabel();
        triggerChange();
    }

    private void evtAddReference() {
    }

    private void evtRemoveReference() {
    }

    private void evtEditReference() {
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == editDataButton) {
            editData();
        } else if (src == addReferenceButton) {
            evtAddReference();
        } else if (src == remReferenceButton) {
            evtRemoveReference();
        } else if (src == editReferenceButton) {
            evtEditReference();
        } else {
            triggerChange();
        }
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        triggerChange();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        triggerChange();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        triggerChange();
    }

    private void setTriggerChanges(boolean isTriggerChanges) {
        this.isTriggerChanges = isTriggerChanges;
    }

    private void triggerChange() {
        if (isTriggerChanges) {
            for (ChangeListener listener : changeListeners) {
                listener.stateChanged(new ChangeEvent(this));
            }
        }
    }

    public void addChangeListener(ChangeListener listener) {
        this.changeListeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        this.changeListeners.remove(listener);
    }

}

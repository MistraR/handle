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
import javax.swing.border.*;
import javax.swing.text.JTextComponent;

import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * This class is used when adding a new HandleValue to a handle.  It provides
 * a set of templates for common HandleValues, or allows entry of the raw
 * HandleValue fields.
 */
@SuppressWarnings("incomplete-switch")
public class EditValueWindow extends JDialog implements ItemListener, ActionListener {
    public static final int ADD_MODE = 3;
    public static final int VIEW_MODE = 2;
    public static final int EDIT_MODE = 1;

    private static final String KNOWN_TYPES[] = { Util.decodeString(Common.STD_TYPE_URL), Util.decodeString(Common.STD_TYPE_EMAIL), Util.decodeString(Common.STD_TYPE_HSALIAS), Util.decodeString(Common.STD_TYPE_HSADMIN),
            Util.decodeString(Common.STD_TYPE_HSSITE), Util.decodeString(Common.STD_TYPE_HSVALLIST), Util.decodeString(Common.STD_TYPE_HSSECKEY), Util.decodeString(Common.STD_TYPE_HSPUBKEY), Util.decodeString(Common.STD_TYPE_HSSERV),
            //Util.decodeString(Common.STD_TYPE_URN),
            //Util.decodeString(Common.STD_TYPE_HOSTNAME)
    };
    private final AdminToolUI ui;
    private final JPanel valueEditorPanel;
    private final CardLayout valueEditorLayout;
    private final JComboBox<String> valueTypeChoice;
    private final Hashtable<String, HandleValueEditor> valueEditors;
    private final JPanel bp; // button panel

    private boolean detailsVisible = true;
    private final JButton detailsButton;
    private final JTextField indexField;
    private final JCheckBox adminRBox;
    private final JCheckBox adminWBox;
    private final JCheckBox publicRBox;
    private final JCheckBox publicWBox;
    private final JComboBox<String> ttlTypeChoice;
    private final JTextField ttlField;
    private JComponent detailComponents[] = null;

    private final JButton saveButton;
    private final JButton cancelButton;
    private boolean canceled = true;

    private int mode = EDIT_MODE;
    private boolean doneEnabled = true;
    private HandleValue handleValue;
    private Runnable saveCallback;

    public EditValueWindow(AdminToolUI ui, Frame owner, String handle, HandleValue[] values) {
        super(owner, "Edit Handle Value", false);
        this.ui = ui;
        JLabel permLabel = new JLabel("Permissions:", SwingConstants.RIGHT);
        JLabel ttlTypeLabel = new JLabel("TTL Type:", SwingConstants.RIGHT);
        JLabel ttlLabel = new JLabel("TTL (seconds):", SwingConstants.RIGHT);

        detailsButton = new JButton("----------");
        saveButton = new JButton("Done");
        cancelButton = new JButton("Cancel");

        indexField = new JTextField("", 7);
        adminRBox = new JCheckBox("Readable by Admins");
        adminWBox = new JCheckBox("Writeable by Admins");
        publicRBox = new JCheckBox("Readable by Everyone");
        publicWBox = new JCheckBox("Writeable by Everyone");
        valueTypeChoice = new JComboBox<>(KNOWN_TYPES);
        valueTypeChoice.setEditable(true);
        ttlTypeChoice = new JComboBox<>(new String[] { "Relative", "Absolute" });
        ttlField = new JTextField("86400");

        valueEditorLayout = new CardLayout();
        valueEditorPanel = new JPanel(valueEditorLayout);

        valueEditors = new Hashtable<>();
        valueEditors.put("", new DefaultValueEditor());
        valueEditors.put("hs_alias", new TextValueEditor());
        valueEditors.put("hs_seckey", new SecretKeyValueEditor());
        valueEditors.put("hs_serv", new TextValueEditor());
        valueEditors.put("hs_serv.prefix", new TextValueEditor());
        valueEditors.put("hs_site", new SiteInfoEditor(ui));
        valueEditors.put("hs_site.6", new SiteInfoEditor(ui));
        valueEditors.put("hs_na_delegate", new SiteInfoEditor(ui));
        valueEditors.put("hs_site.prefix", new SiteInfoEditor(ui));
        valueEditors.put("email", new TextValueEditor());
        valueEditors.put("url", new TextValueEditor());
        //valueEditors.put("urn", new TextValueEditor());
        valueEditors.put("hs_admin", new AdminValueEditor(ui));
        //valueEditors.put("inet_host", new TextValueEditor());
        valueEditors.put("hs_vlist", new ValueReferenceListEditor(ui));
        valueEditors.put("hs_signature", new HandleClaimsSetJwsValueEditor(ui, this, false, handle, values));
        valueEditors.put("hs_cert", new HandleClaimsSetJwsValueEditor(ui, this, true, handle, values));

        for (Enumeration<String> enumeration = valueEditors.keys(); enumeration.hasMoreElements();) {
            String key = enumeration.nextElement();
            valueEditorPanel.add((Component) valueEditors.get(key), key);
            //valueEditorLayout.addLayoutComponent((Component)valueEditors.get(key), key);
        }
        valueEditorLayout.show(valueEditorPanel, "");

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        int y = 0;
        p.add(new JLabel("Index:", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(0, 0, 4, 4), true, false));
        p.add(indexField, AwtUtil.getConstraints(1, y, 1, 0, 1, 1, new Insets(0, 4, 4, 4), true, false));
        p.add(detailsButton, AwtUtil.getConstraints(2, y++, 1, 0, 1, 1, new Insets(0, 4, 4, 0), true, false));

        p.add(permLabel, AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(0, 0, 4, 4), true, false));
        p.add(adminRBox, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(0, 4, 4, 0), GridBagConstraints.WEST, false, false));
        p.add(adminWBox, AwtUtil.getConstraints(1, y++, 1, 0, 1, 1, new Insets(0, 4, 4, 0), GridBagConstraints.WEST, false, false));
        y -= 2;
        p.add(publicRBox, AwtUtil.getConstraints(2, y++, 1, 0, 1, 1, new Insets(0, 4, 4, 0), GridBagConstraints.WEST, false, false));
        p.add(publicWBox, AwtUtil.getConstraints(2, y++, 1, 0, 1, 1, new Insets(0, 4, 4, 0), GridBagConstraints.WEST, false, false));

        p.add(ttlTypeLabel, AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(0, 0, 4, 4), true, false));
        p.add(ttlTypeChoice, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(0, 4, 4, 0), true, false));

        p.add(ttlLabel, AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(0, 0, 4, 4), true, false));
        p.add(ttlField, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(0, 4, 4, 0), true, false));

        p.add(new JLabel("Value Type:", SwingConstants.RIGHT), AwtUtil.getConstraints(0, y, 0, 0, 1, 1, new Insets(0, 0, 4, 4), true, false));
        p.add(valueTypeChoice, AwtUtil.getConstraints(1, y++, 1, 0, 2, 1, new Insets(0, 4, 4, 0), true, false));

        p.add(valueEditorPanel, AwtUtil.getConstraints(0, y++, 1, 1, 3, 1, new Insets(0, 0, 0, 0), true, true));

        bp = new JPanel(new GridBagLayout());
        bp.add(new JLabel(" "), AwtUtil.getConstraints(0, 0, 1, 0, 1, 1, true, true));
        bp.add(cancelButton, AwtUtil.getConstraints(1, 0, 0, 0, 1, 1, new Insets(0, 20, 0, 0), false, false));
        bp.add(saveButton, AwtUtil.getConstraints(2, 0, 0, 0, 1, 1, new Insets(0, 8, 0, 0), false, false));
        p.add(bp, AwtUtil.getConstraints(0, y++, 1, 0, 3, 1, new Insets(10, 0, 0, 0), true, false));

        getContentPane().add(p);

        detailsButton.addActionListener(this);
        cancelButton.addActionListener(this);
        saveButton.addActionListener(this);
        valueTypeChoice.addItemListener(this);

        detailComponents = new JComponent[] { permLabel, adminRBox, adminWBox, publicRBox, publicWBox, ttlTypeLabel, ttlTypeChoice, ttlLabel, ttlField };

        toggleVisibleDetails();

        //getRootPane().setDefaultButton(saveButton);
    }

    public void setSaveCallback(Runnable saveCallback) {
        this.saveCallback = saveCallback;
    }

    public void toggleVisibleDetails() {
        detailsVisible = !detailsVisible;
        Dimension oldSize = getSize();
        for (int i = 0; i < detailComponents.length; i++) {
            detailComponents[i].setVisible(detailsVisible);
        }
        detailsButton.setText(detailsVisible ? "Hide Details" : "Show Details");

        pack();
        Dimension prefSize = getPreferredSize();
        setSize(new Dimension(Math.max(oldSize.width, prefSize.width), prefSize.height));
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src == cancelButton) {
            canceled = true;
            setVisible(false);
        } else if (src == saveButton) {
            canceled = false;
            if (this.mode == VIEW_MODE || handleValue == null || saveCallback == null) {
                setVisible(false);
            } else if (saveValueData(handleValue)) {
                setVisible(false);
                saveCallback.run();
            }
        } else if (src == detailsButton) {
            toggleVisibleDetails();
        }
    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        Object src = evt.getSource();
        if (src == valueTypeChoice) {
            updateValueType();
        }
    }

    private HandleValueEditor lastEditor = null;

    private void updateValueType() {
        String selectedType = String.valueOf(valueTypeChoice.getSelectedItem()).toLowerCase();
        Dimension oldSize = getSize();
        if (!valueEditors.containsKey(selectedType)) selectedType = "";
        HandleValueEditor editor = valueEditors.get(selectedType);
        if (lastEditor != null && editor == lastEditor) return; // the editor is already in use

        HandleValue tmpVal = new HandleValue();
        if (lastEditor != null) // save the value from the last editor...
            lastEditor.saveValueData(tmpVal);
        editor.loadValueData(tmpVal);
        valueEditorLayout.show(valueEditorPanel, selectedType);
        pack();
        Dimension prefSize = getPreferredSize();
        setSize(new Dimension(Math.max(oldSize.width, prefSize.width), prefSize.height));
        lastEditor = editor;

        if (selectedType.equals("hs_seckey")) {
            // make sure that the public-read permission is not set
            boolean pubread = publicRBox.isSelected();
            if (pubread && mode == ADD_MODE) publicRBox.setSelected(false);

            // if it was set, show the detail panel so the user isn't surprised
            if (/*pubread &&*/ !detailsVisible) toggleVisibleDetails();
        }
    }

    public synchronized void setMode(int mode) {
        this.mode = mode;
        switch (mode) {
        case EDIT_MODE:
        case ADD_MODE:
            getContentPane().setEnabled(true);
            //setComponentEnabled(true);
            bp.setVisible(true);
            saveButton.setEnabled(doneEnabled);
            break;
        case VIEW_MODE:
            getContentPane().setEnabled(false);
            setComponentEnabled(false);
            bp.setVisible(false);
            break;
        }
    }

    static {
        UIManager.put("ComboBox.disabledForeground", Color.BLACK);
        UIManager.put("ComboBox.disabledText", Color.BLACK);
        UIManager.put("CheckBox.disabledText", Color.BLACK);
    }

    public void setComponentEnabled(boolean enabled) {
        setComponentEnabled(enabled, getContentPane());
    }

    private void setComponentEnabled(boolean enabled, Component component) {
        if (component instanceof JTextComponent || component instanceof JComboBox || component instanceof JCheckBox || (component instanceof JButton && component != detailsButton && !buttonShouldRemainEnabled((JButton) component))) {
            if (component instanceof JTextComponent) {
                ((JTextComponent) component).setEditable(enabled);
                Color color = component.getForeground();
                ((JTextComponent) component).setDisabledTextColor(color);
            } else if (component instanceof JComboBox && ((JComboBox<?>) component).isEditable()) {
                component.setEnabled(enabled);
                Color color = component.getForeground();
                try {
                    ((JTextField) ((JComboBox<?>) component).getEditor().getEditorComponent()).setDisabledTextColor(color);
                } catch (Exception e) {
                    //ignore
                }
            } else {
                component.setEnabled(enabled);
            }
        }
        if (component instanceof Container) {
            Component[] components = ((Container) component).getComponents();
            if (components != null && components.length > 0) {
                for (Component heldComponent : components) {
                    setComponentEnabled(enabled, heldComponent);
                }
            }
        }
    }

    private boolean buttonShouldRemainEnabled(JButton button) {
        if (button.getText().equals(ui.getStr("run_site_test"))) return true;
        if (button.getText().equals("Verify")) return true;
        return false;
    }

    public void loadValueData(HandleValue val, boolean isNewValue) {
        this.handleValue = val;
        indexField.setEditable(isNewValue);
        indexField.setEnabled(isNewValue);
        int index = val.getIndex();
        if (index < 0) {
            indexField.setText("");
        } else {
            indexField.setText(String.valueOf(index));
        }
        adminRBox.setSelected(val.getAdminCanRead());
        adminWBox.setSelected(val.getAdminCanWrite());
        publicRBox.setSelected(val.getAnyoneCanRead());
        publicWBox.setSelected(val.getAnyoneCanWrite());
        if (val.getTTLType() == HandleValue.TTL_TYPE_ABSOLUTE) {
            ttlTypeChoice.setSelectedIndex(1);
        } else { // TTL_TYPE_RELATIVE
            ttlTypeChoice.setSelectedIndex(0);
        }
        ttlField.setText(String.valueOf(val.getTTL()));

        lastEditor = null;
        valueTypeChoice.setSelectedItem(val.getTypeAsString());
        updateValueType();
        lastEditor.loadValueData(val);
    }

    public boolean wasCanceled() {
        return canceled;
    }

    public boolean saveValueData(HandleValue editVal) {
        int idx = -1;
        try {
            idx = Integer.parseInt(indexField.getText().trim());
            if (idx <= 0) throw new Exception("Index must be positive");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: invalid index value: " + indexField.getText());
            return false;
        }

        int ttl = -1;
        try {
            ttl = Integer.parseInt(ttlField.getText().trim());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: invalid TTL value: " + ttlField.getText());
            return false;
        }

        if (lastEditor != null) if (!lastEditor.saveValueData(editVal)) return false;

        editVal.setIndex(idx);
        editVal.setType(Util.encodeString(String.valueOf(valueTypeChoice.getSelectedItem()).trim()));
        editVal.setTTL(ttl);
        editVal.setAdminCanRead(adminRBox.isSelected());
        editVal.setAdminCanWrite(adminWBox.isSelected());
        editVal.setAnyoneCanRead(publicRBox.isSelected());
        editVal.setAnyoneCanWrite(publicWBox.isSelected());
        if (ttlTypeChoice.getSelectedIndex() == 1) { // absolute TTL
            editVal.setTTLType(HandleValue.TTL_TYPE_ABSOLUTE);
        } else { // relative, the default
            editVal.setTTLType(HandleValue.TTL_TYPE_RELATIVE);
        }
        return true;
    }

    public void setDoneEnabled(boolean enabled) {
        this.doneEnabled = enabled;
        if (this.mode != VIEW_MODE) saveButton.setEnabled(enabled);
    }

}

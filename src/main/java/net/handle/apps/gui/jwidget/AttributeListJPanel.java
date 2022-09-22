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

/****************************************************************\
 * Service attribute list panel
 ****************************************************************/
public class AttributeListJPanel extends DataListJPanel {

    protected boolean editFlag;

    /**
     *@param editFlag  if true, panel has edit related button, else just view button
     **/
    public AttributeListJPanel(boolean editFlag) {
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
        AttributePanel p = new AttributePanel();
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input Attribute:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
        return p.getAttribute();
    }

    @Override
    protected boolean removeData(int ind) {
        return true;
    }

    @Override
    protected Object modifyData(int ind) {
        Attribute attr = (Attribute) items.elementAt(ind);

        AttributePanel p = new AttributePanel();
        p.setAttribute(attr);

        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Motify Attribute:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
        return p.getAttribute();
    }

    @Override
    protected void viewData(int ind) {
        Attribute attr = (Attribute) items.elementAt(ind);

        AttributePanel p = new AttributePanel();
        p.setAttribute(attr);
        JOptionPane.showMessageDialog(this, p, "View Attribute:", JOptionPane.PLAIN_MESSAGE);
    }

    class AttributePanel extends JPanel {

        protected JTextField nameField;
        protected JTextField valueField;

        AttributePanel() {
            nameField = new JTextField("", 15);
            nameField.setToolTipText("Input Attribute's name");
            nameField.setEditable(editFlag);
            valueField = new JTextField("", 20);
            valueField.setEditable(editFlag);
            valueField.setScrollOffset(0);
            valueField.setToolTipText("Input Attribute's value");
            this.add(new JLabel("  Name: ", SwingConstants.RIGHT));
            this.add(nameField);
            this.add(new JLabel("  Value: ", SwingConstants.RIGHT));
            this.add(valueField);
        }

        @Override
        public void setName(String name) {
            nameField.setText(name);
        }

        @Override
        public String getName() {
            return nameField.getText();
        }

        public void setValue(String value) {
            valueField.setText(value);
        }

        public String getValue() {
            return valueField.getText();
        }

        public void setAttribute(Attribute attr) {
            setName(Util.decodeString(attr.name));
            setValue(Util.decodeString(attr.value));
        }

        public Attribute getAttribute() {
            return (new Attribute(Util.encodeString(getName()), Util.encodeString(getValue())));
        }
    }
}

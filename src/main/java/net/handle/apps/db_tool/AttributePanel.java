/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import java.awt.*;

public class AttributePanel extends Panel {

    private final TextField nameField;
    private final TextField valueField;

    public AttributePanel() {
        nameField = new TextField("", 10);
        valueField = new TextField("", 10);
        add(new Label("  Name: ", Label.RIGHT));
        add(nameField);
        add(new Label("  Value: ", Label.RIGHT));
        add(valueField);
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

}

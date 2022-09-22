/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import java.awt.event.*;
import javax.swing.*;
import java.net.*;

public class HDLAction extends AbstractAction {
    private static final String ICON_DIR = "/net/handle/apps/admintool/view/resources/";

    private final AdminToolUI ui;
    private final String labelKey;
    private final ActionListener callback;
    private final String cmd;
    private Object targetObject;
    private final boolean usesKey;

    public HDLAction(AdminToolUI ui, String labelKey, String commandKey, ActionListener callback) {
        super(ui == null ? labelKey : ui.getStr(labelKey));
        usesKey = ui != null;
        this.ui = ui;
        this.labelKey = labelKey;
        this.callback = callback;
        this.cmd = commandKey;

        super.putValue(ACTION_COMMAND_KEY, commandKey);
        super.putValue(SMALL_ICON, getIcon(ICON_DIR + labelKey));
        preferencesUpdated();
    }

    public void setTargetObject(Object obj) {
        this.targetObject = obj;
    }

    public Object getTargetObject() {
        return targetObject;
    }

    public String getCommand() {
        return cmd;
    }

    public String getName() {
        return String.valueOf(getValue(NAME));
    }

    public void preferencesUpdated() {
        if (usesKey) super.putValue(NAME, ui.getStr(labelKey));
    }

    public void setMnemonicKey(int key) {
        putValue(MNEMONIC_KEY, Integer.valueOf(key));
    }

    public void setAccelerator(KeyStroke ks) {
        putValue(ACCELERATOR_KEY, ks);
    }

    public void setShortDescription(String shortDesc) {
        putValue(SHORT_DESCRIPTION, shortDesc);
    }

    public void setAccelerator(String ksString) {
        setAccelerator(KeyStroke.getKeyStroke(ksString));
    }

    public ImageIcon getIcon(String path) {
        URL url = this.getClass().getResource(path);
        if (url != null) {
            return new ImageIcon(url);
        }
        return null;
    }

    public boolean matchesCommand(ActionEvent evt) {
        if (evt.getSource() == this) return true;
        if (getCommand().equals(evt.getActionCommand())) return true;
        if (getName().equals(evt.getActionCommand())) return true;
        return false;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        if (callback != null) callback.actionPerformed(evt);
    }

    // DEFAULT, LONG_DESCRIPTION

}

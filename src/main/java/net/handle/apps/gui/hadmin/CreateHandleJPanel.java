/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jwidget.*;
import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

@SuppressWarnings("rawtypes")
public class CreateHandleJPanel extends HandleAdminJPanel implements ActionListener {
    protected CreateDataList dataList;

    public CreateHandleJPanel() {
        this(new HandleTool());
    }

    public CreateHandleJPanel(HandleTool tool) {
        super(tool);

        //set datapanel
        dataList = new CreateDataList();
        dataPanel.add(dataList, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));

        //set up panel
        submitButton.setText("Create");
        submitButton.setActionCommand("Create");
        submitButton.addActionListener(this);

        upPanel.add(handlePanel, AwtUtil.getConstraints(0, 0, 1, 1, 2, 3, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, false, true));
        upPanel.add(submitButton, AwtUtil.getConstraints(2, 0, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, false));

        //set midpanel
        midPanel.add(toolPanel, AwtUtil.getConstraints(2, 0, 1, 1, 2, 8, new Insets(5, 5, 5, 5), true, false));

    }

    public void reset() {
        nameField.setText("");
        dataList.clearAll();
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String choice = ae.getActionCommand();
        if (ae.getSource() == nameField) tool.getAuthentication();
        else if (choice.equals("Create")) submitCreate();
        else if (choice.equals("Add Admin")) addAdmin();
        else if (choice.equals("Add URL")) addURL();
        else if (choice.equals("Add EMAIL")) addEmail();
        else if (choice.equals("Add Custom Data")) addCustomData();
    }

    @Override
    public int getNextIndex(int start) {
        int i = 0;
        java.util.Vector v = dataList.getItems();
        HandleValue val;
        while (i < v.size()) {
            val = (HandleValue) v.elementAt(i);
            if (val.getIndex() == start) {
                start++;
                i = 0;
            } else {
                i++;
            }
        }
        return start;
    }

    private void submitCreate() {
        handleName = nameField.getText().trim();
        nameField.requestFocus();

        if (handleName == null || handleName.length() <= 0) {
            warn("Did not input handle name");
            return;
        }

        Vector vt = dataList.getItems();
        if (vt == null) {
            warn("Did not input handle value");
            return;
        } else if (vt.size() < 1) {
            warn("Did not input handle value");
            return;
        }

        handleValues = new HandleValue[vt.size()];
        boolean admflag = false;
        for (int i = 0; i < vt.size(); i++) {
            handleValues[i] = (HandleValue) vt.elementAt(i);
            if (handleValues[i].hasType(Common.ADMIN_TYPE)) admflag = true;
        }
        if (!admflag) {
            warn("Did not input handle administrator");
            return;
        }

        AuthenticationInfo authInfo = tool.getAuthentication();
        if (authInfo == null) return;
        CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(handleName), handleValues, authInfo);
        try {
            // AbstractResponse resp = tool.resolver.processRequest(req);
            AbstractResponse resp = tool.processRequest(this, req, "Creating Handle ...");
            if (resp == null) {
                warn("Can not process the request");
                return;
            }

            if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                info("Response: Handle created successfully \n" + resp);
                dataList.clearAll();
                nameField.setText("");
            } else {
                info("Response: Handle creation failed \n" + resp);
                if (resp.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED || resp.responseCode == AbstractMessage.RC_AUTHENTICATION_NEEDED || resp.responseCode == AbstractMessage.RC_INVALID_ADMIN) {
                    tool.changeAuthentication();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            warn("Exception " + e.getMessage());
        }
    }

    @Override
    protected void addDataToList(HandleValue value) {
        if (value == null) return;
        dataList.appendItem(value);
    }

    @Override
    protected void createAdminRef(int index) {
        Vector v = dataList.getItems();
        if ((v != null) && (v.size() > 0)) for (int i = 0; i < v.size(); i++)
            if (((HandleValue) v.elementAt(i)).getIndex() == index) {
                return;
            }

        AdministMasterJPanel p = new AdministMasterJPanel(index);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Add admininst Reference: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        HandleValue value = p.getAdmReferValue();
        addDataToList(value);

    }

    protected class CreateDataList extends HandleValueListJPanel {
        CreateDataList() {
            super();
            int x = 0;
            int y = 0;
            GridBagLayout gridbag = new GridBagLayout();
            buttonPanel = new JPanel(gridbag);
            buttonPanel.add(editItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(remItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(viewItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(clearButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            y = 0;
            savePanel = new JPanel(gridbag);
            savePanel.add(saveButton, AwtUtil.getConstraints(x++, y, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
            savePanel.add(loadButton, AwtUtil.getConstraints(x++, y, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));

            this.add(pane, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, true, true));
            this.add(buttonPanel, AwtUtil.getConstraints(2, 0, 1, 1, 1, 10, true, true));
            this.add(savePanel, AwtUtil.getConstraints(0, 11, 1, 1, 2, 1, true, true));

        }
    }
}

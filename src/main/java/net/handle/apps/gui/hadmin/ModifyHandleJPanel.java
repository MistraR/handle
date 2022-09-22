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
public class ModifyHandleJPanel extends HandleAdminJPanel implements ActionListener {
    protected ModifyDataList dataList;
    protected Component this_panel;

    public ModifyHandleJPanel() {
        this(new HandleTool());
    }

    public ModifyHandleJPanel(HandleTool tool) {
        super(tool);
        this_panel = this;

        // no submit for modify
        submitButton.setVisible(false);

        //set datapanel
        dataList = new ModifyDataList();
        dataPanel.add(dataList, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));

        //set uppanel
        upPanel.add(handlePanel, AwtUtil.getConstraints(0, 0, 1, 1, 2, 3, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, false, true));
        //set midpanel
        midPanel.add(toolPanel, AwtUtil.getConstraints(2, 0, 1, 1, 2, 8, new Insets(5, 5, 5, 5), true, false));

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

    @Override
    public void actionPerformed(ActionEvent ae) {
        String choice = ae.getActionCommand();
        if (ae.getSource() == nameField) {
            AuthenticationInfo authInfo = tool.getAuthentication();
            if (authInfo == null) return;
            fetchHandleValues(authInfo);
        } else if (choice.equals("Add Admin")) {
            addAdmin();
        } else if (choice.equals("Add URL")) {
            addURL();
        } else if (choice.equals("Add EMAIL")) {
            addEmail();
        } else if (choice.equals("Add Custom Data")) {
            addCustomData();
        }
    }

    @Override
    protected void createAdminRef(int index) {
        Vector v = dataList.getItems();
        if ((v != null) && (v.size() > 0)) for (int i = 0; i < v.size(); i++)
            if (((HandleValue) v.elementAt(i)).getIndex() == index) {
                return;
            }

        AdministMasterJPanel p = new AdministMasterJPanel(index);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Add administrator: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        HandleValue value = p.getAdmReferValue();
        addDataToList(value);

    }

    @Override
    protected void addDataToList(HandleValue value) {
        handleName = nameField.getText().trim();
        if (handleName == null || handleName.length() <= 0) {
            warn("Invalid handle name, re-enter please");
            return;
        }
        AuthenticationInfo authInfo = tool.getAuthentication();

        byte[] handle = Util.encodeString(handleName);

        AddValueRequest req = new AddValueRequest(handle, value, authInfo);

        try {
            AbstractResponse resp = tool.processRequest(this_panel, req, "Adding handle value...");

            if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                info("Response: Successfully added handle value\n" + resp);
                dataList.appendItem(value);
            } else {
                info("Response: There was an error adding the handle value\n" + resp);
                if (resp.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED || resp.responseCode == AbstractMessage.RC_AUTHENTICATION_NEEDED || resp.responseCode == AbstractMessage.RC_INVALID_ADMIN) tool.changeAuthentication();
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            warn("There was an error adding the handle value: " + e.getMessage());
        }
    }

    public void reset() {
        dataList.clearAll();
        nameField.setText("");
    }

    public void fetchHandleValues(AuthenticationInfo authInfo) {
        dataList.clearAll();

        handleName = nameField.getText().trim();
        if (handleName == null || handleName.length() <= 0) {
            warn("Invalid handle name, re-enter please");
            nameField.requestFocus();
            return;
        }
        byte[] handlebytes = Util.encodeString(handleName);

        ResolutionRequest queryReq = null;
        AbstractResponse response = null;

        try {
            if (authInfo == null) {
                queryReq = new ResolutionRequest(handlebytes, null, null, authInfo);
                queryReq.authoritative = true;
                queryReq.ignoreRestrictedValues = true;
                queryReq.certify = true;
                warn("Retrieving only publicly readable handle values");

            } else {
                queryReq = new ResolutionRequest(handlebytes, null, null, authInfo);
                queryReq.authoritative = true;
                queryReq.ignoreRestrictedValues = false;
                queryReq.certify = true;
            }

            response = tool.processRequest(this_panel, queryReq, "Fetching handle values ...");

            if (!(response instanceof ResolutionResponse) && (authInfo != null)) {

                warn("Unable to retrieve handle values: Authentication error.\n" + "Retrieving only publicly readable handle values");
                queryReq = new ResolutionRequest(handlebytes, null, null, authInfo);
                queryReq.authoritative = true;
                queryReq.ignoreRestrictedValues = true;
                queryReq.certify = true;
                response = tool.processRequest(this_panel, queryReq, "Fetching handle values ...");
                if (response == null) {
                    warn("There was an error processing your request");
                    return;
                }
            }

            if (!(response instanceof ResolutionResponse)) {
                warn("Unable to retrieve handle: \n" + response);
                return;
            }

            HandleValue[] values = ((ResolutionResponse) response).getHandleValues();
            if (values == null) return;
            for (int i = 0; i < values.length; i++)
                dataList.appendItem(values[i]);
        } catch (Exception e) {
            warn("Unable to retrieve handle: \n" + e.getMessage());
            return;
        }
    }

    protected class ModifyDataList extends HandleValueListJPanel {

        protected ModifyDataList() {
            super();
            int x = 0;
            int y = 0;
            GridBagLayout gridbag = new GridBagLayout();
            buttonPanel = new JPanel(gridbag);
            buttonPanel.add(editItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(remItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            buttonPanel.add(viewItemButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
            y = 0;
            savePanel = new JPanel(gridbag);
            savePanel.add(saveButton, AwtUtil.getConstraints(x++, y, 1, 1, 1, 1, false, true));

            this.add(pane, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, true, true));
            this.add(buttonPanel, AwtUtil.getConstraints(2, 0, 1, 1, 1, 8, true, true));
            this.add(savePanel, AwtUtil.getConstraints(0, 11, 1, 1, 1, 1, true, true));
        }

        @Override
        protected Object modifyData(int ind) {
            handleName = nameField.getText().trim();
            if (handleName == null || handleName.length() <= 0) {
                warn("Please enter a valid handle");
                return null;
            }

            HandleValue value = (HandleValue) super.modifyData(ind);
            if (value == null) return null;

            AuthenticationInfo authInfo = tool.getAuthentication();
            if (authInfo == null) return null;

            byte[] handle = Util.encodeString(handleName);

            ModifyValueRequest req = new ModifyValueRequest(handle, value, authInfo);

            try {
                AbstractResponse resp = tool.processRequest(this_panel, req, "Updating handle value ...");
                if (resp == null) {
                    warn("Unable to process the request");
                    return null;
                }

                if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                    info("Handle value successfully updated");
                    return value;
                } else {
                    info("Error updating handle value: \n" + resp);
                    /*
                    if(resp.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED||
                       resp.responseCode == AbstractMessage.RC_AUTHENTICATION_NEEDED||
                       resp.responseCode == AbstractMessage.RC_INVALID_ADMIN) {
                       tool.changeAuthentication(this);
                    
                     }
                     */
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                warn("Error updating handle value: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected boolean removeData(int ind) {
            handleName = nameField.getText().trim();
            if (handleName == null || handleName.length() <= 0) {
                warn("Please enter a valid handle");
                return false;
            }

            AuthenticationInfo authInfo = tool.getAuthentication();
            if (authInfo == null) return false;
            byte[] handle = Util.encodeString(handleName);

            int removeInd = ((HandleValue) items.elementAt(ind)).getIndex();
            RemoveValueRequest req = new RemoveValueRequest(handle, removeInd, authInfo);

            try {
                AbstractResponse resp = tool.processRequest(this_panel, req, "Removing handle value ...");
                if (resp == null) {
                    warn("Unable to process the request");
                    return false;
                }

                if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                    info("Handle value successfully removed");
                    return true;
                } else {
                    info("Error removing handle value: \n" + resp);
                    /*
                    if(resp.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED||
                     resp.responseCode == AbstractMessage.RC_AUTHENTICATION_NEEDED||
                     resp.responseCode == AbstractMessage.RC_INVALID_ADMIN)
                    tool.changeAuthentication(this);
                     */
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
                warn("Error removing handle value: " + e.getMessage());
                return false;
            }
        }

        @Override
        public void warn(String message) {
            JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
        }

        @Override
        public void info(String message) {
            JOptionPane.showMessageDialog(this, message);
        }
    }//End ModifyDataList

}

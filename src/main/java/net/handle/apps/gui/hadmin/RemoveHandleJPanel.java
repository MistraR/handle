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
import javax.swing.*;

public class RemoveHandleJPanel extends HandleAdminJPanel implements ActionListener {
    protected RemoveDataList dataList;

    public RemoveHandleJPanel() {
        this(new HandleTool());
    }

    public RemoveHandleJPanel(HandleTool tool) {
        super(tool);

        //set datapanel
        dataList = new RemoveDataList();
        dataPanel.add(dataList, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));

        //set uppanel
        submitButton.setText("Remove");
        submitButton.setActionCommand("Remove");
        submitButton.addActionListener(this);
        upPanel.add(handlePanel, AwtUtil.getConstraints(0, 0, 1, 1, 2, 3, new Insets(5, 5, 5, 5), GridBagConstraints.WEST, false, true));
        //set midpanel

    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String choice = ae.getActionCommand();
        if (ae.getSource() == nameField) fetchHandleValues(tool.getAuthentication());
        else if (choice.equals("Remove")) submitRemove();
    }

    private void submitRemove() {
        handleName = nameField.getText().trim();
        if (handleName == null || handleName.length() <= 0) {
            warn("Invalid handle name, re-enter please ");
            nameField.requestFocus();
            return;
        }

        if (JOptionPane.NO_OPTION == JOptionPane.showConfirmDialog(this, "Remove this handle from service?", "Remove: ", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE)) return;

        AuthenticationInfo authInfo = tool.getAuthentication();
        if (authInfo == null) return;

        DeleteHandleRequest req = new DeleteHandleRequest(Util.encodeString(handleName), authInfo);
        try {
            AbstractResponse resp = tool.processRequest(this, req, "Removing handle ...");
            if (resp == null) {
                warn("Can not process this request");
                return;
            }

            if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                info("Response: Handle removed successfully \n" + resp);
                dataList.clearAll();
            } else {
                info("Response: Handle removal failed \n" + resp);
                if (resp.responseCode == AbstractMessage.RC_AUTHENTICATION_FAILED || resp.responseCode == AbstractMessage.RC_AUTHENTICATION_NEEDED || resp.responseCode == AbstractMessage.RC_INVALID_ADMIN) tool.changeAuthentication();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            warn("Unexpected Exception " + e.getMessage());
        }
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
                queryReq.ignoreRestrictedValues = true;
                queryReq.certify = true;
                warn("Retrieving handle values without restricted data");

            } else {
                queryReq = new ResolutionRequest(handlebytes, null, null, authInfo);
                queryReq.authoritative = true;
                queryReq.ignoreRestrictedValues = false;
                queryReq.certify = true;
            }

            response = tool.resolver.processRequest(queryReq);

            if (!(response instanceof ResolutionResponse) && (authInfo != null)) {

                warn("Unable to resolve the handle, illegal authentication\n" + "Retrieving handle values without restricted data");
                queryReq = new ResolutionRequest(handlebytes, null, null, authInfo);
                queryReq.ignoreRestrictedValues = true;
                queryReq.certify = true;
                response = tool.resolver.processRequest(queryReq);
                if (response == null) {
                    warn("There was an error processing your request");
                    return;
                }
            }

            if (!(response instanceof ResolutionResponse)) {
                warn("Unable to retrieve handle:  \n" + response);
                return;
            }

            HandleValue[] values = ((ResolutionResponse) response).getHandleValues();
            if (values == null) return;
            for (int i = 0; i < values.length; i++)
                dataList.appendItem(values[i]);
        } catch (Exception e) {
            warn("Unable to retrieve handle:\n" + e.getMessage());
            return;
        }
    }

    protected class RemoveDataList extends HandleValueListJPanel {
        protected RemoveDataList() {
            super();
            this.add(pane, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, true, true));
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jwidget.*;
import net.handle.apps.gui.jutil.*;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public class QueryHandleJPanel extends JPanel implements ActionListener {

    protected HandleValue[] handleValues;
    protected HandleTool tool;

    protected JTextField nameField;
    protected JTextField indexField;
    protected JList typeChoice;
    protected JTextField customTypeField;
    protected JCheckBox certifyCheckbox;
    protected JCheckBox cacheCertifyCheckbox;
    protected JCheckBox authoritativeCheckbox;
    protected JCheckBox ignoreRestrictedCheckbox;
    protected HandleValueTable dataPanel;
    protected JPanel workPanel;
    protected MyButton submitButton;

    public QueryHandleJPanel() {
        this(new HandleTool());
    }

    public QueryHandleJPanel(HandleTool tool) {
        super();
        this.tool = tool;

        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);
        EtchedBorder etchBorder = new EtchedBorder();
        int x = 0;
        int y = 0;

        //set workpanel
        workPanel = new JPanel(gridbag);

        nameField = new JTextField("", 20);
        nameField.setScrollOffset(0);
        nameField.addActionListener(this);
        nameField.setToolTipText("Input the new handle name");
        indexField = new JTextField("", 20);
        indexField.addActionListener(this);
        indexField.setToolTipText("Input the index value");

        String[] typeStrs = new String[CommonDef.DATA_TYPE_STR.length];
        for (int i = 0; i < CommonDef.DATA_TYPE_STR.length - 1; i++)
            typeStrs[i] = CommonDef.DATA_TYPE_STR[i];
        typeStrs[CommonDef.DATA_TYPE_STR.length - 1] = "All_DATA_TYPE";

        customTypeField = new JTextField("", 20);
        customTypeField.addActionListener(this);
        customTypeField.setToolTipText("Enter a comma-separated list of types, or choose from the list below");
        typeChoice = new JList(typeStrs);
        typeChoice.setToolTipText("choose query type");
        DefaultListSelectionModel m = new DefaultListSelectionModel();
        m.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        m.setLeadAnchorNotificationEnabled(true);
        typeChoice.setSelectionModel(m);

        submitButton = new MyButton(" Submit ", "click to retrieve the handle", "Submit");
        submitButton.addActionListener(this);

        x = 0;
        y = 0;
        JPanel p1 = new JPanel(gridbag);
        p1.add(new JLabel(" Handle Name: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 0, 1, 1, new Insets(5, 2, 5, 1), true, false));
        p1.add(nameField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 2, 1, new Insets(1, 2, 5, 5), true, false));
        p1.add(new JLabel(" Query Indexes: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 0, 1, 1, new Insets(5, 2, 5, 1), true, false));
        p1.add(indexField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 1, 1, new Insets(1, 2, 5, 5), true, false));
        p1.add(new JLabel(" Query Types: ", SwingConstants.RIGHT), AwtUtil.getConstraints(x, y, 1, 0, 1, 1, new Insets(5, 2, 5, 1), true, false));
        p1.add(customTypeField, AwtUtil.getConstraints(x + 1, y++, 1, 0, 2, 9, new Insets(1, 2, 5, 5), true, false));
        p1.add(new JScrollPane(typeChoice), AwtUtil.getConstraints(x + 1, y++, 1, 1, 2, 9, new Insets(1, 2, 5, 5), true, true));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(submitButton);
        p1.add(buttonPanel, AwtUtil.getConstraints(x + 1, y + 9, 1, 1, 1, 1, new Insets(5, 2, 10, 2), false, true));

        certifyCheckbox = new JCheckBox("Certify", false);
        cacheCertifyCheckbox = new JCheckBox("Certify Cache", true);
        authoritativeCheckbox = new JCheckBox("Authoritative", false);
        ignoreRestrictedCheckbox = new JCheckBox("Ignore Restricted Values", true);

        x = 0;
        y = 0;
        JPanel p2 = new JPanel(gridbag);
        p2.setBorder(new TitledBorder(etchBorder, "Query Properties"));

        p2.add(certifyCheckbox, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, false));
        p2.add(cacheCertifyCheckbox, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, false));
        p2.add(authoritativeCheckbox, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, false));
        p2.add(ignoreRestrictedCheckbox, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, false));

        JPanel p3 = new JPanel(gridbag);
        p3.add(p1, AwtUtil.getConstraints(0, 0, 1, 1, 1, 3, new Insets(10, 10, 10, 10), true, true));
        p3.add(p2, AwtUtil.getConstraints(1, 1, 1, 1, 1, 1, new Insets(10, 10, 10, 10), true, false));

        //data panel
        dataPanel = new HandleValueTable();
        dataPanel.setBorder(new TitledBorder(etchBorder, "Handle Data"));

        x = 0;
        y = 0;
        workPanel.add(p3, AwtUtil.getConstraints(x, y++, 1, 1, 2, 1, new Insets(5, 5, 5, 5), true, true));
        workPanel.add(dataPanel, AwtUtil.getConstraints(x, y, 1, 1, 2, 10, new Insets(5, 5, 5, 5), true, true));
        add(workPanel, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, new Insets(2, 5, 10, 5), GridBagConstraints.CENTER, true, true));

    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == submitButton || src == nameField || src == indexField || src == customTypeField) {
            submit();
        }
    }

    public void reset() {
        indexField.setText("");
        nameField.setText("");
        customTypeField.setText("");
        typeChoice.clearSelection();
        certifyCheckbox.setSelected(false);
        cacheCertifyCheckbox.setSelected(true);
        authoritativeCheckbox.setSelected(false);
        ignoreRestrictedCheckbox.setSelected(true);
        dataPanel.setHandleValues(new HandleValue[0]);
    }

    protected void submit() {
        // get the types to query
        Vector queryTypes = new Vector();

        StringTokenizer token = new StringTokenizer(customTypeField.getText().trim(), ", ");
        while (token.hasMoreTokens()) {
            String str = token.nextToken().trim();
            if (str.length() > 0) {
                queryTypes.addElement(Util.encodeString(str));
            }
        }

        if (!typeChoice.isSelectedIndex(CommonDef.DATA_TYPE_STR.length - 1)) {
            List<Object> items = typeChoice.getSelectedValuesList();
            for (int i = 0; i < items.size(); i++)
                queryTypes.addElement(Util.encodeString((String) items.get(i)));
        }

        byte[][] types = null;
        if (queryTypes.size() > 0) {
            types = new byte[queryTypes.size()][];
            for (int i = 0; i < types.length; i++)
                types[i] = (byte[]) queryTypes.elementAt(i);
        }

        // get the indexes to query
        token = new StringTokenizer(indexField.getText().trim(), ",");
        Vector it = new Vector();
        while (token.hasMoreTokens()) {
            String str = token.nextToken();
            try {
                it.addElement(Integer.valueOf(str));
            } catch (Exception e) {
                System.err.println("Error: Invalid index: " + e);
            }
        }
        int[] indexes = new int[it.size()];
        for (int i = 0; i < it.size(); i++)
            indexes[i] = ((Integer) it.elementAt(i)).intValue();
        ResolutionRequest req = new ResolutionRequest(Util.encodeString(nameField.getText().trim()), types, indexes, tool.getCurrentAuthentication());
        req.certify = certifyCheckbox.isSelected();
        req.cacheCertify = cacheCertifyCheckbox.isSelected();
        req.authoritative = authoritativeCheckbox.isSelected();
        req.ignoreRestrictedValues = ignoreRestrictedCheckbox.isSelected();

        try {
            AbstractResponse resp = tool.processRequest(this, req, "Resolving handle ...");
            if (resp == null) return;
            if (resp.responseCode == AbstractMessage.RC_SUCCESS) {
                HandleValue[] vals = ((ResolutionResponse) resp).getHandleValues();
                //        dataPanel.clearAll();
                dataPanel.setHandleValues(vals);
            } else {
                warn("Can not process this request: \n  " + resp);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            warn(String.valueOf(e));
        }
    }

    //general methods
    void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    void info(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        Container c = f.getContentPane();
        c.add(new QueryHandleJPanel());
        f.setSize(500, 500);
        f.pack();
        f.setVisible(true);
    }

}

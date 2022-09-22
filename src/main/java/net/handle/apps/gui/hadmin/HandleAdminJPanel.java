/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import net.handle.apps.gui.jutil.*;
import net.handle.apps.gui.jwidget.*;

import net.handle.awt.*;
import net.handle.hdllib.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

public class HandleAdminJPanel extends JPanel implements ActionListener {
    //UI Panel
    protected JPanel upPanel;
    protected JPanel midPanel;
    protected JPanel lowPanel;
    protected JPanel handlePanel;
    protected JPanel dataPanel;
    protected JPanel toolPanel;

    //UI component
    protected JTextField nameField;
    protected JScrollPane sp;
    protected JPanel workPanel;
    protected MyButton changeAuthButton;
    protected MyButton addAdmButton;
    protected MyButton addSiteButton;
    protected MyButton urlButton;
    protected MyButton emailButton;
    protected MyButton submitButton;
    protected MyButton goButton;
    protected MyButton customDataButton;
    //protected net.handle.apps.gui.jwidget.DataListJPanel dataList;

    //relate data
    protected HandleValue[] handleValues;
    protected String handleName;
    protected HandleTool tool;

    public HandleAdminJPanel() {
        this(new HandleTool());
    }

    public HandleAdminJPanel(HandleTool tool) {
        this(tool, "Handle Data View");
    }

    public HandleAdminJPanel(HandleTool tool, String name) {
        super();
        submitButton = new MyButton("");
        GridBagLayout gridbag = new GridBagLayout();
        setLayout(gridbag);
        EtchedBorder etchBorder = new EtchedBorder();
        int x = 0;
        int y = 0;

        this.tool = tool;

        //set handlepanel
        handlePanel = new JPanel(new FlowLayout());
        nameField = new JTextField("", 20);
        nameField.setScrollOffset(0);
        nameField.setToolTipText("Input the new handle name");
        nameField.addActionListener(this);
        handlePanel.add(new JLabel(" Handle: ", SwingConstants.RIGHT));
        handlePanel.add(nameField);
        handlePanel.add(submitButton);
        //set toolpanel
        JPanel p2 = new JPanel(gridbag);
        addAdmButton = new MyButton("Add Admin", "click to add administrator");
        urlButton = new MyButton("Add URL", " click to add URL data");
        emailButton = new MyButton("Add EMAIL", "click to add Email data");

        urlButton.addActionListener(this);
        emailButton.addActionListener(this);
        addAdmButton.addActionListener(this);
        x = 0;
        y = 0;
        p2.add(addAdmButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p2.add(urlButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p2.add(emailButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        customDataButton = new MyButton("Add Custom Data", "click to add custom data");
        customDataButton.addActionListener(this);
        p2.add(customDataButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));

        toolPanel = new JPanel(gridbag);
        toolPanel.add(p2, AwtUtil.getConstraints(0, 0, 1, 1, 1, 3, new Insets(2, 1, 5, 1), true, false));
        toolPanel.setBorder(new TitledBorder(etchBorder, " Add Handle Data "));

        //set datapanel
        dataPanel = new JPanel(gridbag);
        dataPanel.setBorder(new TitledBorder(etchBorder, name));

        //set uppanel
        upPanel = new JPanel(gridbag);
        //upPanel.add(handlePanel,
        //AwtUtil.getConstraints(0,0,1,1,2,3,new Insets(5,5,5,5),
        //                                   GridBagConstraints.WEST,false, true));

        //set midpanel
        midPanel = new JPanel(gridbag);
        midPanel.add(dataPanel, AwtUtil.getConstraints(0, 0, 1, 1, 2, 10, new Insets(5, 5, 5, 5), true, true));

        //set lowpanel
        lowPanel = new JPanel(gridbag);

        //set workpanel
        workPanel = new JPanel(gridbag);
        x = 0;
        y = 0;
        workPanel.add(upPanel, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        workPanel.add(midPanel, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        workPanel.add(lowPanel, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, new Insets(5, 5, 5, 5), true, true));
        //set all
        x = 0;
        y = 0;
        add(workPanel, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, new Insets(2, 5, 10, 5), GridBagConstraints.CENTER, true, true));
    }

    //general methods
    public void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    public void info(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    //-------------------------------------------------------------------------
    //override methods
    //-------------------------------------------------------------------------

    //Overrided by children
    @Override
    public void actionPerformed(ActionEvent ae) {
    }

    @SuppressWarnings("unused")
    public int getNextIndex(int start) {
        return 0;
    }

    protected void addAdmin() {
        handleName = nameField.getText().trim();
        int index = getNextIndex(100);
        AdminDataJPanel p = new AdminDataJPanel(true, true, index);
        AuthenticationInfo auth = tool.getCurrentAuthentication();
        if (auth != null) {
            p.setAdmin(new String(auth.getUserIdHandle()), auth.getUserIdIndex());
        }
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input Administrators Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        HandleValue value = p.getHandleValue();
        addDataToList(value);

        //add more as ref of admin
        try {
            AdminRecord record = new AdminRecord();
            Encoder.decodeAdminRecord(value.getData(), 0, record);
            String admHandleName = Util.decodeString(record.adminId);
            if (handleName.equals(admHandleName)) {
                createAdminRef(record.adminIdIndex);
            }
        } catch (HandleException e) {
            getToolkit().beep();
        }
    }

    protected void createAdminRef(int index) {
        AdministMasterJPanel p = new AdministMasterJPanel(index);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Add admininst Reference: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        HandleValue value = p.getAdmReferValue();
        addDataToList(value);

    }

    protected void addSite() {
        SiteDataJPanel p = new SiteDataJPanel(true, true, getNextIndex(1));
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Add Site Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        addDataToList(p.getHandleValue());
    }

    protected void addURL() {
        int i = getNextIndex(1);
        TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_URL, true, true, i);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input URL Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        addDataToList(p.getHandleValue());
    }

    protected void addEmail() {
        int i = getNextIndex(1);
        TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_EMAIL, true, true, i);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input Email Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        addDataToList(p.getHandleValue());
    }

    protected void addCustomData() {
        HandleValueJPanel p = new HandleValueJPanel(true, getNextIndex(1));
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(this, p, "Input Custom Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        addDataToList(p.getHandleValue());
    }

    @SuppressWarnings("unused")
    protected void addDataToList(HandleValue value) {
    }

    public static void main(String[] args) {
        JFrame f = new JFrame();
        Container c = f.getContentPane();
        c.add(new HandleAdminJPanel());
        f.setSize(500, 500);
        f.pack();
        f.setVisible(true);
    }

}

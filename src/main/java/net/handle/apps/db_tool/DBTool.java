/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.db_tool;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;
import net.handle.server.*;
import net.handle.awt.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DBTool extends JFrame implements ActionListener {
    private final HandleStorage storage;

    private final JButton deleteHandleButton;
    private final JButton addHandleButton;
    private final JButton modifyHandleButton;
    private final JButton listHandlesButton;
    private final JButton addNAButton;
    private final JButton deleteNAButton;
    private final JButton listNAsButton;
    private final JButton exitButton;

    public DBTool(HandleStorage storage) {
        super("Handle Database Tool");
        this.storage = storage;

        JPanel p = new JPanel(new GridBagLayout());
        getContentPane().add(p, BorderLayout.CENTER);

        deleteHandleButton = new JButton("Delete Handle");
        addHandleButton = new JButton("Create Handle");
        modifyHandleButton = new JButton("Modify Handle");
        listHandlesButton = new JButton("List Handles");
        addNAButton = new JButton("Add Homed Prefix");
        deleteNAButton = new JButton("Delete Homed Prefix");
        listNAsButton = new JButton("List Homed Prefixes");
        exitButton = new JButton("Exit");

        int y = 0, x = 0;
        p.add(addHandleButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(deleteHandleButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(modifyHandleButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(listHandlesButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(new Label(" "), AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));

        p.add(addNAButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(deleteNAButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(listNAsButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));

        p.add(new Label(" "), AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));
        p.add(exitButton, AwtUtil.getConstraints(x, y++, 1, 1, 1, 1, true, true));

        deleteHandleButton.addActionListener(this);
        addHandleButton.addActionListener(this);
        modifyHandleButton.addActionListener(this);
        listHandlesButton.addActionListener(this);
        addNAButton.addActionListener(this);
        deleteNAButton.addActionListener(this);
        listNAsButton.addActionListener(this);
        exitButton.addActionListener(this);

        pack();
        setSize(getPreferredSize());
    }

    private void listHandles() throws Exception {
        System.out.println("\nListing handles: ");
        storage.scanHandles(new ScanCallback() {
            @Override
            public void scanHandle(byte handle[]) {
                System.out.println(Util.decodeString(handle));
            }
        });
    }

    private void createHandle() throws Exception {
        JTextField handleField = new JTextField("", 25);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter New Handle", handleField, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        String handle = handleField.getText();
        HandleInfoPanel handlePanel = new HandleInfoPanel();
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter Values for \"" + handle + "\"", handlePanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        HandleValue values[] = handlePanel.getHandleValues();

        // set the timestamps
        for (int i = 0; values != null && i < values.length; i++) {
            if (values[i] != null) values[i].setTimestamp((int) (System.currentTimeMillis() / 1000));
        }

        storage.createHandle(Util.encodeString(handle), values);
    }

    private void addNA() throws Exception {
        JTextField naHandleField = new JTextField("", 25);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter Prefix Handle [NEW]", naHandleField, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        storage.setHaveNA(Util.upperCaseInPlace(Util.encodeString(naHandleField.getText())), true);
    }

    private void deleteNA() throws Exception {
        JTextField naHandleField = new JTextField("", 25);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter Prefix Handle [DELETE]", naHandleField, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        storage.setHaveNA(Util.upperCaseInPlace(Util.encodeString(naHandleField.getText())), false);
    }

    private void listNAs() throws Exception {
        System.out.println("\nListing Prefixes: ");
        storage.scanNAs(new ScanCallback() {
            @Override
            public void scanHandle(byte handle[]) {
                System.out.println(Util.decodeString(handle));
            }
        });
    }

    private void modifyHandle() throws Exception {
        JTextField handleField = new JTextField("", 25);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter Handle To Modify", handleField, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        byte handle[] = Util.encodeString(handleField.getText());

        byte rawValues[][] = storage.getRawHandleValues(handle, null, null);
        if (rawValues == null) {
            GenericDialog.askQuestion("Error", "The handle \"" + Util.decodeString(handle) + "\" does not exist in this database", GenericDialog.QUESTION_OK, this);
            return;
        }
        HandleValue values[] = new HandleValue[rawValues.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = new HandleValue();
            Encoder.decodeHandleValue(rawValues[i], 0, values[i]);
        }

        HandleInfoPanel handlePanel = new HandleInfoPanel();
        handlePanel.setHandleValues(values);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Edit Values for \"" + Util.decodeString(handle) + "\"", handlePanel, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        values = handlePanel.getHandleValues();

        // set the timestamps
        for (int i = 0; values != null && i < values.length; i++) {
            if (values[i] != null) values[i].setTimestamp((int) (System.currentTimeMillis() / 1000));
        }

        storage.updateValue(handle, values);
    }

    private void deleteHandle() throws Exception {
        JTextField handleField = new JTextField("", 25);
        if (GenericDialog.ANSWER_CANCEL == GenericDialog.showDialog("Enter Handle to Delete", handleField, GenericDialog.QUESTION_OK_CANCEL, this)) return;
        byte handle[] = Util.encodeString(handleField.getText());
        storage.deleteHandle(handle);
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        try {
            if (src == listHandlesButton) {
                listHandles();
            } else if (src == addHandleButton) {
                createHandle();
            } else if (src == modifyHandleButton) {
                modifyHandle();
            } else if (src == deleteHandleButton) {
                deleteHandle();
            } else if (src == addNAButton) {
                addNA();
            } else if (src == deleteNAButton) {
                deleteNA();
            } else if (src == listNAsButton) {
                listNAs();
            } else if (src == exitButton) {
                storage.shutdown();
                System.exit(0);
            }
        } catch (Exception e) {
            getToolkit().beep();
            e.printStackTrace(System.err);
        }
    }

    public static void main(String argv[]) throws Exception {
        if (argv == null || argv.length < 1) {
            System.err.println("usage: java net.handle.apps.db_tool.DBTool <server-directory>");
            return;
        }

        java.io.File serverDir = new java.io.File(argv[0]);
        StreamTable serverInfo = new StreamTable();
        serverInfo.readFromFile(new java.io.File(serverDir, "config.dct"));
        serverInfo = (StreamTable) serverInfo.get("server_config");
        HandleStorage storage = HandleStorageFactory.getStorage(serverDir, serverInfo, true);

        DBTool dbTool = new DBTool(storage);
        dbTool.setVisible(true);
    }

}

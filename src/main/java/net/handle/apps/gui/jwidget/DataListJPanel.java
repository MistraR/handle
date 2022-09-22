/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import java.io.*;

/*********************************************************************\
 * general Data List Panel
 *
 *********************************************************************/
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public class DataListJPanel extends JPanel implements ActionListener {
    public Vector items;
    public JList itemList;

    public MyButton addItemButton;
    public MyButton remItemButton;
    public MyButton editItemButton;
    public MyButton viewItemButton;
    public MyButton clearButton;
    public MyButton saveButton;
    public MyButton loadButton;

    public JPanel buttonPanel;
    public JPanel savePanel;
    public JScrollPane pane;

    public DataListJPanel() {
        super(new GridBagLayout());

        items = new Vector();

        addItemButton = new MyButton("Add", "Add a new value");
        remItemButton = new MyButton("Remove", "Remove the selected value");
        editItemButton = new MyButton("Modify", "Modify the selected value");
        viewItemButton = new MyButton("View", "View the selected value");
        clearButton = new MyButton("Clear All", "Clear all values");
        addItemButton.addActionListener(this);
        remItemButton.addActionListener(this);
        editItemButton.addActionListener(this);
        viewItemButton.addActionListener(this);
        clearButton.addActionListener(this);

        saveButton = new MyButton("Save", "Save current data");
        loadButton = new MyButton("Load", "Load data from file");
        saveButton.addActionListener(this);
        loadButton.addActionListener(this);

        itemList = new JList(items);
        itemList.setVisibleRowCount(8);
        itemList.setFixedCellWidth(200);
        pane = new JScrollPane(itemList);

    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == addItemButton) {
            addItem();
        } else if (src == remItemButton) {
            removeItem();
        } else if (src == editItemButton) {
            modifyItem();
        } else if (src == viewItemButton) {
            viewItem();
        } else if (src == clearButton) {
            clearAll();
        } else if (src == saveButton) {
            saveToFile();
        } else if (src == loadButton) {
            loadFromFile();
        }
    }

    public Vector getItems() {
        return items;
    }

    public void setItems(Vector vt) {
        if (vt == null) return;
        clearAll();
        for (int i = 0; i < vt.size(); i++) {
            items.addElement(vt.elementAt(i));
        }
        rebuildItemList();
    }

    public void appendItemVector(Vector vt) {
        if (vt == null) return;
        for (int i = 0; i < vt.size(); i++) {
            items.addElement(vt.elementAt(i));
        }
        rebuildItemList();
    }

    public void appendItem(Object it) {
        if (it == null) return;
        items.addElement(it);
        rebuildItemList();
    }

    public void clearAll() {
        items.removeAllElements();
        rebuildItemList();
    }

    public void addItem() {
        Object it = addData();
        if (it != null) items.addElement(it);
        rebuildItemList();
    }

    public void modifyItem() {
        int ind = itemList.getSelectedIndex();
        if (ind < 0) return;
        Object it = modifyData(ind);
        if (it != null) {
            items.removeElementAt(ind);
            items.insertElementAt(it, ind);
        }
        rebuildItemList();
    }

    public void viewItem() {
        int ind = itemList.getSelectedIndex();
        if (ind < 0) return;
        viewData(ind);

    }

    public void removeItem() {
        int ind = itemList.getSelectedIndex();
        if (ind < 0) return;
        if (removeData(ind)) items.removeElementAt(ind);
        rebuildItemList();
    }

    public void rebuildItemList() {
        itemList.setListData(items);
    }

    public void saveToFile() {
        BrowsePanel browser = new BrowsePanel("save file: ", (File) null, "", null, true);

        File[] files = new File[1];
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, browser, "save values to file:", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;

        if (!browser.getWriteFile(files)) return;
        writeData(files[0]);
    }

    public void loadFromFile() {
        BrowsePanel browser = new BrowsePanel("load file: ", (File) null, "", null, false);
        File[] files = new File[1];
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, browser, "load values from file: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
        if (!browser.getReadFile(files)) {
            return;
        }

        readData(files[0]);

    }

    public void warn(String message) {
        JOptionPane.showMessageDialog(this, "Warning:\n  " + message, "Warning:", JOptionPane.WARNING_MESSAGE);
    }

    public void info(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    //--------------------------------------------------
    //  method for override
    //--------------------------------------------------

    protected Object addData() {
        return null;
    }

    protected Object modifyData(int ind) {
        return null;
    }

    protected void viewData(int ind) {
    }

    protected boolean removeData(int ind) {
        return true;
    }

    protected void readData(File file) {

    }

    protected void writeData(File file) {

    }
}

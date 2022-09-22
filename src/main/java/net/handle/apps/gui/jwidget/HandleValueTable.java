/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;

import net.handle.hdllib.*;
import net.handle.awt.*;
import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

@SuppressWarnings("incomplete-switch")
public class HandleValueTable extends JPanel implements ListSelectionListener {
    Object[][] dataObj;
    Object[] header;
    DefaultTableModel tmodel;
    ListSelectionModel smodel;
    JTable table;
    HandleValue[] this_values;

    public HandleValueTable() {
        this(null);
    }

    public HandleValueTable(HandleValue[] values) {
        super(new GridBagLayout());
        this.this_values = values;

        dataObj = toObjects(values);
        header = CommonDef.HANDLE_VALUE_HEADER;
        tmodel = new DefaultTableModel(dataObj, header);
        table = new JTable(tmodel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setCellSelectionEnabled(false);
        table.setColumnSelectionAllowed(false);
        smodel = table.getSelectionModel();
        smodel.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        smodel.setValueIsAdjusting(true);
        smodel.addListSelectionListener(this);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(550, 150));
        scroll.setMinimumSize(new Dimension(200, 100));
        add(scroll, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() || smodel.isSelectionEmpty()) return;
        int r = smodel.getMinSelectionIndex();
        String typeStr = (String) tmodel.getValueAt(r, 1);
        if (typeStr == null) return;
        HandleValue value = this_values[r];
        viewValue(value);
        smodel.clearSelection();
    }

    protected void viewValue(HandleValue value) {
        if (value == null) return;

        JPanel p = null;
        //HS_ADMIN
        if (value.hasType(Common.STD_TYPE_HSADMIN)) {
            p = new AdminDataJPanel(true, false);
            ((AdminDataJPanel) p).setHandleValue(value);
        }
        //HS_VLIST
        else if (value.hasType(Common.STD_TYPE_HSVALLIST)) {
            p = new VListDataJPanel(true, false);
            ((VListDataJPanel) p).setHandleValue(value);
        }
        //HS_SITE
        else if (value.hasType(Common.STD_TYPE_HSSITE)) {
            p = new SiteDataJPanel(true, false);
            ((SiteDataJPanel) p).setHandleValue(value);
        }
        //HS_PUBKEY
        else if (value.hasType(Common.STD_TYPE_HSPUBKEY)) {
            p = new PubkeyDataJPanel(true, false);
            ((PubkeyDataJPanel) p).setHandleValue(value);
        }
        //HS_SECKEY
        else if (value.hasType(Common.STD_TYPE_HSSECKEY)) {
            p = new TextDataJPanel(Common.STD_TYPE_HSSECKEY, true, false);
            ((TextDataJPanel) p).setHandleValue(value);
        }
        //URL
        else if (value.hasType(Common.STD_TYPE_URL)) {
            p = new TextDataJPanel(Common.STD_TYPE_URL, true, false);
            ((TextDataJPanel) p).setHandleValue(value);
        }
        //EMAIL
        else if (value.hasType(Common.STD_TYPE_EMAIL)) {
            p = new TextDataJPanel(Common.STD_TYPE_EMAIL, true, false);
            ((TextDataJPanel) p).setHandleValue(value);
        }
        //HS_SERV
        else if (value.hasType(Common.STD_TYPE_HSSERV)) {
            p = new TextDataJPanel(Common.STD_TYPE_HSSERV, true, false);
            ((TextDataJPanel) p).setHandleValue(value);
        }
        //    //INET_HOST
        //    else if(value.hasType(Common.STD_TYPE_HOSTNAME)){
        //        p = new TextDataJPanel(Common.STD_TYPE_HOSTNAME,true, false);
        //        ((TextDataJPanel)p).setHandleValue(value);
        //    }
        //    //URN
        //    else if(value.hasType(Common.STD_TYPE_URN)){
        //        p = new TextDataJPanel(Common.STD_TYPE_URN,true, false);
        //        ((TextDataJPanel)p).setHandleValue(value);
        //    }
        //Other data
        else {
            p = new HandleValueJPanel(false);
            ((HandleValueJPanel) p).setHandleValue(value);
        }

        JOptionPane.showMessageDialog(this, p, "View handle Value: ", JOptionPane.PLAIN_MESSAGE);
    }

    public void setHandleValues(HandleValue[] values) {
        dataObj = toObjects(values);
        tmodel.setDataVector(dataObj, header);
        this_values = values;
    }

    private Object[][] toObjects(HandleValue[] values) {
        if (values == null) return (new Object[0][0]);
        Object[][] objs = new Object[values.length][header.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) continue;
            for (int j = 0; j < header.length; j++) {
                switch (j) {
                case 0:
                    objs[i][j] = Integer.valueOf(values[i].getIndex());
                    break;
                case 1:
                    objs[i][j] = values[i].getTypeAsString();
                    break;
                case 2:
                    objs[i][j] = values[i].getDataAsString();
                    break;
                case 3:
                    objs[i][j] = values[i].getPermissionString();
                    break;
                case 4:
                    if (values[i].getTTLType() == HandleValue.TTL_TYPE_RELATIVE) objs[i][j] = "RELATIVE";
                    else objs[i][j] = "ABSOLUTE";
                    break;
                case 5:
                    if (values[i].getTTLType() == HandleValue.TTL_TYPE_RELATIVE) objs[i][j] = String.valueOf(values[i].getTTL()) + " secs";
                    else objs[i][j] = new Date(values[i].getTTL() * 1000L);
                    break;
                case 6:
                    objs[i][j] = new Date(values[i].getTimestamp() * 1000L);
                    break;
                case 7:
                    ValueReference refs[] = values[i].getReferences();
                    if (refs == null || refs.length < 1) objs[i][j] = "null";
                    else objs[i][j] = refs;
                    break;
                }
            }
        }

        return objs;
    }
}

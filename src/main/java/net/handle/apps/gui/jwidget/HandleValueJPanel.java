/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.apps.gui.jutil.*;
import net.handle.awt.*;
import net.handle.hdllib.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

@SuppressWarnings({"incomplete-switch", "rawtypes"})
public class HandleValueJPanel extends HandleValueEntryJPanel implements ActionListener, ItemListener {
    protected MyButton dataButton;
    protected boolean editFlag;
    int index;

    public HandleValueJPanel(boolean editFlag) {
        this(editFlag, 1);
    }

    public HandleValueJPanel(boolean editFlag, int index) {
        super(editFlag);
        this.editFlag = editFlag;
        if (editFlag) {
            typeField.addItemListener(this);
            indexField.setText(String.valueOf(index));
        }
        dataButton = new MyButton(" Value Data ", "Click to modify the data portion of this value", "Value Data");
        dataButton.addActionListener(this);

        this.index = index;
        panel1.add(dataButton, AwtUtil.getConstraints(4, 0, 1, 0, 1, 1, new Insets(1, 30, 1, 10), GridBagConstraints.WEST, true, true));
    }

    @Override
    public void itemStateChanged(ItemEvent it) {
        if (it.getSource() != typeField) return;

        String itemStr = (String) it.getItem();
        byte item[] = Util.encodeString(itemStr);
        publicReadCheckbox.setSelected(true);

        if (Util.equalsCI(item, Common.STD_TYPE_HSADMIN)) {
            indexField.setText("100");
        } else if (Util.equalsCI(item, Common.STD_TYPE_HSSITE)) {
            indexField.setText("1");
        } else if (Util.equalsCI(item, Common.STD_TYPE_HSVALLIST)) {
            indexField.setText("200");
        } else if (Util.equalsCI(item, Common.STD_TYPE_HSSECKEY)) {
            indexField.setText("300");
            publicReadCheckbox.setSelected(false);
        } else if (Util.equalsCI(item, Common.STD_TYPE_HSPUBKEY)) {
            indexField.setText("300");
        } else {
            indexField.setText("");
        }

        indexField.invalidate();
        publicReadCheckbox.invalidate();
        validate();
    }

    //Here to change the handle vlaue data
    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() == dataButton) {
            String typeStr = (String) typeField.getSelectedItem();
            //HS_ADMIN
            if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSADMIN)) {
                AdminDataJPanel p = new AdminDataJPanel(false, editFlag, index);

                if (!handlevalue.hasType(Common.STD_TYPE_HSADMIN)) {
                    handlevalue.setType(Common.STD_TYPE_HSADMIN);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Administrator Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSPUBKEY)) {
                PubkeyDataJPanel p = new PubkeyDataJPanel(false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_HSPUBKEY)) {
                    handlevalue.setType(Common.STD_TYPE_HSPUBKEY);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Public Key Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSSITE)) { //HS_SITE
                SiteDataJPanel p = new SiteDataJPanel(false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_HSSITE)) {
                    handlevalue.setType(Common.STD_TYPE_HSSITE);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Site Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSVALLIST)) { //HS_VLIST
                VListDataJPanel p = new VListDataJPanel(false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_HSVALLIST)) {
                    handlevalue.setType(Common.STD_TYPE_HSVALLIST);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Administrator Group Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSSECKEY)) { //HS_SECKEY
                //TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_HSSECKEY,false,editFlag,index);
                SecretKeyDataJPanel p = new SecretKeyDataJPanel(Common.STD_TYPE_HSSECKEY, false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_HSSECKEY)) {
                    handlevalue.setType(Common.STD_TYPE_HSSECKEY);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Secret Key Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_EMAIL)) { //EMAIL
                TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_EMAIL, false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_EMAIL)) {
                    handlevalue.setType(Common.STD_TYPE_EMAIL);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Email Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_URL)) { //URL
                TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_URL, false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_URL)) {
                    handlevalue.setType(Common.STD_TYPE_URL);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input URL Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

            } else if (Util.equals(Util.encodeString(typeStr), Common.STD_TYPE_HSSERV)) { //HS_SERV
                TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_HSSERV, false, editFlag, index);
                if (!handlevalue.hasType(Common.STD_TYPE_HSSERV)) {
                    handlevalue.setType(Common.STD_TYPE_HSSERV);
                    handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                }
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Service Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();

                //      } else if(Util.equals(Util.encodeString(typeStr),Common.STD_TYPE_HOSTNAME)) { //INET_HOST
                //        TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_HOSTNAME,false,editFlag,index);
                //        if(!handlevalue.hasType(Common.STD_TYPE_HOSTNAME)){
                //          handlevalue.setType(Common.STD_TYPE_HOSTNAME);
                //          handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                //        }
                //        p.setHandleValue(handlevalue);
                //        if(JOptionPane.CANCEL_OPTION ==
                //           JOptionPane.showConfirmDialog(null, p,
                //                                         "Input host server Info: ",
                //                                         JOptionPane.OK_CANCEL_OPTION,
                //                                         JOptionPane.PLAIN_MESSAGE))
                //          return;
                //        handlevalue = p.getHandleValue();
                //
                //      } else if(Util.equals(Util.encodeString(typeStr),Common.STD_TYPE_URN)) { //URN
                //        TextDataJPanel p = new TextDataJPanel(Common.STD_TYPE_URN,false,editFlag,index);
                //        if(!handlevalue.hasType(Common.STD_TYPE_URN)){
                //          handlevalue.setType(Common.STD_TYPE_URN);
                //          handlevalue.setData(Common.EMPTY_BYTE_ARRAY);
                //        }
                //        p.setHandleValue(handlevalue);
                //        if(JOptionPane.CANCEL_OPTION ==
                //           JOptionPane.showConfirmDialog(null, p,
                //                                         "Input URN: ",
                //                                         JOptionPane.OK_CANCEL_OPTION,
                //                                         JOptionPane.PLAIN_MESSAGE))
                //          return;
                //        handlevalue = p.getHandleValue();
                //
                //
            } else {//New data type , handle as text
                System.err.println("warning message: Unknown data type");
                byte[] typeBytes = Util.encodeString(typeStr);
                CustomDataJPanel p = new CustomDataJPanel(typeBytes, false, editFlag, index);
                p.setHandleValue(handlevalue);
                if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Input Data Info: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return;
                handlevalue = p.getHandleValue();
            }
        } else {
            System.err.println("Error Input");
        }
    }

    @Override
    public void setHandleValue(HandleValue value) {
        if (value == null) return;

        setIndex(value.getIndex());
        String type = Util.decodeString(value.getType());
        typeField.setSelectedItem(type);

        switch (value.getTTLType()) {
        case HandleValue.TTL_TYPE_ABSOLUTE:
            ttlTypeChoice.setSelectedIndex(1);
            break;
        case HandleValue.TTL_TYPE_RELATIVE:
            ttlTypeChoice.setSelectedIndex(0);
            break;
        }
        ttlField.setText(String.valueOf(value.getTTL()));
        timestampField.setText(new Date(((long) value.getTimestamp()) * ((long) 1000)).toString());
        adminReadCheckbox.setSelected(value.getAdminCanRead());
        adminWriteCheckbox.setSelected(value.getAdminCanWrite());
        publicReadCheckbox.setSelected(value.getAnyoneCanRead());
        publicWriteCheckbox.setSelected(value.getAnyoneCanWrite());
        ValueReference refs[] = value.getReferences();
        if (refs != null) for (int i = 0; i < refs.length; i++)
            referenceList.appendItem(refs[i]);

        handlevalue.setType(value.getType());
        handlevalue.setTimestamp(value.getTimestamp());
        handlevalue.setData(value.getData());
    }

    @Override
    public void setIndex(int index) {
        indexField.setText(String.valueOf(index));
    }

    @Override
    public HandleValue getHandleValue() {
        handlevalue.setIndex(getIndex());
        //get new data type
        handlevalue.setType(Util.encodeString(String.valueOf(typeField.getSelectedItem()).trim()));

        try {
            handlevalue.setTTL(Integer.parseInt(ttlField.getText().trim()));
            if (ttlTypeChoice.getSelectedIndex() == 0) {
                handlevalue.setTTLType(HandleValue.TTL_TYPE_RELATIVE);
            } else {
                handlevalue.setTTLType(HandleValue.TTL_TYPE_ABSOLUTE);
            }
        } catch (Exception e) {
            handlevalue.setTTL(-1);
        }

        handlevalue.setAdminCanRead(adminReadCheckbox.isSelected());
        handlevalue.setAdminCanWrite(adminWriteCheckbox.isSelected());
        handlevalue.setAnyoneCanRead(publicReadCheckbox.isSelected());
        handlevalue.setAnyoneCanWrite(publicWriteCheckbox.isSelected());

        Vector v = referenceList.getItems();
        ValueReference[] vr = new ValueReference[v.size()];
        for (int i = 0; i < v.size(); i++)
            vr[i] = (ValueReference) v.elementAt(i);
        handlevalue.setReferences(vr);
        //timestamp is the time when panel construct
        //value's data modify in the data button part.
        return handlevalue;
    }

    @Override
    public int getIndex() {
        try {
            return Integer.parseInt(indexField.getText().trim());
        } catch (Exception e) {
            return -1;
        }
    }
}

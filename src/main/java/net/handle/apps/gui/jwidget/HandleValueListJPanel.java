/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.hdllib.*;

import javax.swing.*;
import java.awt.event.*;
import java.io.*;

/*********************************************************\
 * HandleValue data list
 *********************************************************/
public class HandleValueListJPanel extends DataListJPanel implements ActionListener {

    public HandleValueListJPanel() {
        super();
    }

    @Override
    protected Object addData() {
        HandleValueJPanel p = new HandleValueJPanel(true);
        if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, " Add Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;
        HandleValue value = p.getHandleValue();
        return value;

    }

    @Override
    protected Object modifyData(int ind) {
        HandleValue value = (HandleValue) items.elementAt(ind);

        JPanel p = null;

        //HS_ADMIN
        if (value.hasType(Common.STD_TYPE_HSADMIN)) {
            p = new AdminDataJPanel(true, true);
            ((AdminDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((AdminDataJPanel) p).getHandleValue();
        }
        //HS_VLIST
        else if (value.hasType(Common.STD_TYPE_HSVALLIST)) {
            p = new VListDataJPanel(true, true);
            ((VListDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((VListDataJPanel) p).getHandleValue();
        }
        //HS_SITE
        else if (value.hasType(Common.STD_TYPE_HSSITE)) {
            p = new SiteDataJPanel(true, true);
            ((SiteDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((SiteDataJPanel) p).getHandleValue();
        }
        //HS_PUBKEY
        else if (value.hasType(Common.STD_TYPE_HSPUBKEY)) {
            p = new PubkeyDataJPanel(true, true);
            ((PubkeyDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((PubkeyDataJPanel) p).getHandleValue();
        }
        //HS_SECKEY
        else if (value.hasType(Common.STD_TYPE_HSSECKEY)) {
            p = new SecretKeyDataJPanel(Common.STD_TYPE_HSSECKEY, true, true);

            // Create copy of original handle value
            byte[] b = value.getData().clone();
            value.setData(Util.encodeString(""));
            ((SecretKeyDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
                value.setData(b);
                return null;
            }
            value = ((SecretKeyDataJPanel) p).getHandleValue();
        }
        //URL
        else if (value.hasType(Common.STD_TYPE_URL)) {
            p = new TextDataJPanel(Common.STD_TYPE_URL, true, true);
            ((TextDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((TextDataJPanel) p).getHandleValue();
        }
        //EMAIL
        else if (value.hasType(Common.STD_TYPE_EMAIL)) {
            p = new TextDataJPanel(Common.STD_TYPE_EMAIL, true, true);
            ((TextDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((TextDataJPanel) p).getHandleValue();
        }
        //HS_SERV
        else if (value.hasType(Common.STD_TYPE_HSSERV)) {
            p = new TextDataJPanel(Common.STD_TYPE_HSSERV, true, true);
            ((TextDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify Handle Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((TextDataJPanel) p).getHandleValue();
        }
        //    //INET_HOST
        //    else if(value.hasType(Common.STD_TYPE_HOSTNAME)){
        //      p = new TextDataJPanel(Common.STD_TYPE_HOSTNAME,true, true);
        //      ((TextDataJPanel)p).setHandleValue(value);
        //      if(JOptionPane.CANCEL_OPTION ==
        //         JOptionPane.showConfirmDialog(null, p ,
        //                                       "Modify Host address: ",
        //                                       JOptionPane.OK_CANCEL_OPTION,
        //                                       JOptionPane.PLAIN_MESSAGE))
        //        return null;
        //
        //      value = ((TextDataJPanel)p).getHandleValue();
        //    }
        //    //URN
        //    else if(value.hasType(Common.STD_TYPE_URN)){
        //      p = new TextDataJPanel(Common.STD_TYPE_URN,true, true);
        //      ((TextDataJPanel)p).setHandleValue(value);
        //      if(JOptionPane.CANCEL_OPTION ==
        //         JOptionPane.showConfirmDialog(null, p ,
        //                                       "Modify URN Value: ",
        //                                       JOptionPane.OK_CANCEL_OPTION,
        //                                       JOptionPane.PLAIN_MESSAGE))
        //        return null;
        //
        //      value = ((TextDataJPanel)p).getHandleValue();
        //    }

        //Other data
        else {
            p = new CustomDataJPanel(value.getType(), true, true);
            ((CustomDataJPanel) p).setHandleValue(value);
            if (JOptionPane.CANCEL_OPTION == JOptionPane.showConfirmDialog(null, p, "Modify " + Util.decodeString(value.getType()) + " Value: ", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) return null;

            value = ((CustomDataJPanel) p).getHandleValue();

        }

        return value;
    }

    @Override
    protected boolean removeData(int ind) {
        return true;
    }

    @Override
    protected void viewData(int ind) {
        HandleValue value = (HandleValue) items.elementAt(ind);

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
        //      p = new TextDataJPanel(Common.STD_TYPE_HOSTNAME,true, false);
        //      ((TextDataJPanel)p).setHandleValue(value);
        //    }
        //    //URN
        //    else if(value.hasType(Common.STD_TYPE_URN)){
        //      p = new TextDataJPanel(Common.STD_TYPE_URN,true, false);
        //      ((TextDataJPanel)p).setHandleValue(value);
        //    }
        //Other data
        else {
            p = new HandleValueJPanel(false);
            ((HandleValueJPanel) p).setHandleValue(value);
        }

        JOptionPane.showMessageDialog(this, p, "View handle Value: ", JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    protected void readData(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            HandleValue[] handlevalues = Encoder.decodeGlobalValues(in);
            int option = JOptionPane.showOptionDialog(this, "reset all values or append as new values", "Option :", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, new Object[] { "Reset All", "Append", "Cancel" },
                "Reset All");
            if (option == JOptionPane.YES_OPTION) {
                clearAll();
                for (int i = 0; i < handlevalues.length; i++)
                    appendItem(handlevalues[i]);
            } else if (option == JOptionPane.NO_OPTION) for (int i = 0; i < handlevalues.length; i++)
                appendItem(handlevalues[i]);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Override
    protected void writeData(File file) {
        HandleValue[] handlevalues = new HandleValue[items.size()];
        for (int i = 0; i < items.size(); i++)
            handlevalues[i] = (HandleValue) items.elementAt(i);
        FileOutputStream out = null;
        try {
            byte[] buffer = Encoder.encodeGlobalValues(handlevalues);
            out = new FileOutputStream(file);
            out.write(buffer);
            out.close();
        } catch (Exception e) {
            if (out != null) try {
                out.close();
            } catch (Exception e1) {
            }
            e.printStackTrace();
        }
    }
}

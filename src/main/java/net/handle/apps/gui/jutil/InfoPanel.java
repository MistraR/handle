/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.awt.*;
import javax.swing.*;

public class InfoPanel extends JPanel {
    String titleStr = "Title";
    String textStr = " ";

    ImageIcon icon = null;
    JLabel label;
    JTextArea textArea;
    Font font;

    public InfoPanel() {
        super(new BorderLayout());
        font = new Font("mesg1", Font.BOLD, 20);
        label = new JLabel(titleStr, SwingConstants.CENTER);
        label.setFont(font);
        label.setHorizontalTextPosition(SwingConstants.CENTER);

        textArea = new JTextArea();
        textArea.setColumns(30);
        textArea.setBackground(label.getBackground());
        textArea.setFont(new Font("mesg2", Font.BOLD, 16));
        textArea.setForeground(label.getForeground());
        textArea.setEditable(false);
        textArea.append(textStr);
        textArea.setMargin(new Insets(5, 5, 5, 5));
        add(label, BorderLayout.NORTH);
        add(textArea, BorderLayout.CENTER);
    }

    public void setTitle(String title) {
        label.setText(title);
        titleStr = title;
    }

    public void setText(String text) {
        textArea.replaceRange(text, 0, textStr.length());
        textStr = text;
    }

    public static void warn(String mesg) {
        JOptionPane.showMessageDialog(null, "Warning:\n  " + mesg, "Warning:", JOptionPane.WARNING_MESSAGE);
    }

    public static void error(String mesg) {
        JOptionPane.showMessageDialog(null, "Unexpected Error:\n  " + mesg, "Error:", JOptionPane.ERROR_MESSAGE);
    }

    public static void message(String mesg) {
        JOptionPane.showMessageDialog(null, mesg);
    }
}

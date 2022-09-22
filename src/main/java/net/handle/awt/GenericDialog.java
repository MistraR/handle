/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.awt;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class GenericDialog extends JDialog implements ActionListener {
    public static final int ANSWER_YES = 0;
    public static final int ANSWER_NO = 1;
    public static final int ANSWER_CANCEL = 2;

    public static final int QUESTION_OK = 0;
    public static final int QUESTION_YES_NO = 1;
    public static final int QUESTION_YES_NO_CANCEL = 2;
    public static final int QUESTION_OK_CANCEL = 3;

    private JButton yesButton = null;
    private JButton noButton = null;
    private JButton cancelButton = null;

    private int answer = ANSWER_CANCEL;

    public GenericDialog(String title, Component c, int questionType, Frame f) {
        this(title, c, questionType, f, true);
    }

    public GenericDialog(String title, Component c, int questionType, Frame f, boolean modal) {
        super(f, title, modal);

        JPanel p = new JPanel(new GridBagLayout());

        if (questionType == QUESTION_YES_NO_CANCEL) {
            yesButton = new JButton("Yes");
            noButton = new JButton("No");
            cancelButton = new JButton("Cancel");
        } else if (questionType == QUESTION_YES_NO) {
            yesButton = new JButton("Yes");
            noButton = new JButton("No");
        } else if (questionType == QUESTION_OK_CANCEL) {
            yesButton = new JButton("Ok");
            cancelButton = new JButton("Cancel");
        } else { // questionType==QUESTION_OK
            yesButton = new JButton("Ok");
        }

        p.setBorder(new javax.swing.border.EmptyBorder(10, 10, 10, 10));
        p.add(c, AwtUtil.getConstraints(1, 1, 1, 1, 1, 1, true, true));

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        int x = 0;
        if (yesButton != null) {
            buttonPanel.add(yesButton, AwtUtil.getConstraints(x++, 0, 1, 1, 1, 1, false, false));
            yesButton.addActionListener(this);
        }
        if (noButton != null) {
            buttonPanel.add(noButton, AwtUtil.getConstraints(x++, 0, 1, 1, 1, 1, false, false));
            noButton.addActionListener(this);
        }
        if (cancelButton != null) {
            buttonPanel.add(cancelButton, AwtUtil.getConstraints(x++, 0, 1, 1, 1, 1, false, false));
            cancelButton.addActionListener(this);
        }
        p.add(buttonPanel, AwtUtil.getConstraints(0, 3, 0, 0, 3, 1, true, true));

        setContentPane(p);
        pack();
        Dimension prefSz = getPreferredSize();
        setSize(Math.max(Math.min(prefSz.width, 800), 50), Math.max(Math.min(prefSz.height, 700), 50));

        Dimension sz = getSize();
        Dimension psz;
        Point ploc;
        if (f != null) {
            psz = f.getSize();
            ploc = f.getLocationOnScreen();
        } else {
            psz = getToolkit().getScreenSize();
            ploc = new Point(0, 0);
        }

        setLocation(ploc.x + (psz.width / 2 - sz.width / 2), ploc.y + (psz.height / 2 - sz.height / 2));
    }

    public int getAnswer() {
        return answer;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object src = evt.getSource();
        if (src instanceof JButton) {
            if (src == yesButton) {
                answer = ANSWER_YES;
            } else if (src == noButton) {
                answer = ANSWER_NO;
            } else {
                answer = ANSWER_CANCEL;
            }
            setVisible(false);
            dispose();
        }
    }

    public static int askQuestion(String title, String question, int questionType, Component parentComp) {
        GenericDialog win = new GenericDialog(title, new TextPanel(question), questionType, AwtUtil.getFrame(parentComp), questionType != QUESTION_OK);
        win.setVisible(true);
        return win.getAnswer();
    }

    public static int showDialog(String title, Component comp, int questionType, Component parentComp) {
        GenericDialog win = new GenericDialog(title, comp, questionType, AwtUtil.getFrame(parentComp), questionType != QUESTION_OK);
        win.setVisible(true);
        return win.getAnswer();
    }

}

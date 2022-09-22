/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import net.handle.awt.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.border.*;
import javax.swing.event.*;

public class HelpPanel extends JScrollPane implements HyperlinkListener {
    protected String titleStr = null;
    protected JEditorPane html;
    protected Font font;
    protected URL baseURL;
    protected Document doc = null;
    protected String dir = null;

    public HelpPanel(String dir, String inputFile) {
        super();
        html = new JEditorPane();
        if (!dir.endsWith("/")) {
            dir += '/';
        }
        this.dir = dir;
        try {
            baseURL = getClass().getResource(dir + inputFile);
            html.setPage(baseURL);
        } catch (Exception e) {
            baseURL = getClass().getResource(dir + CommonDef.INDEX_FILE);
            try {
                html.setPage(baseURL);
            } catch (Exception e1) {
                e1.printStackTrace(System.err);
                html.setText(" Not Found");
            }
        }
        html.setEditable(false);
        html.addHyperlinkListener(this);
        html.setMargin(new Insets(10, 10, 10, 10));
        JViewport vp = getViewport();
        vp.add(html);
        setPreferredSize(new Dimension(640, 700));
        setMinimumSize(new Dimension(640, 700));
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            String newURL = e.getDescription();
            try {
                URL newBaseURL = new URL(baseURL, newURL);
                linkActived(newBaseURL);
                //              if(newBaseURL!=null)
                baseURL = newBaseURL;
                //          else{
                //          newBaseURL = new URL(baseURL, CommonDef.INDEX_FILE);
                //          if(newBaseURL!=null)
                //              baseURL = newBaseURL;
                //          }
            } catch (Exception e1) {
                e1.printStackTrace();
                System.err.println("load file: " + baseURL);
            }
        }
    }

    protected void linkActived(URL u) {
        Cursor c = html.getCursor();
        Cursor waitCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
        html.setCursor(waitCursor);
        SwingUtilities.invokeLater(new PageLoader(u, c));
    }

    protected class PageLoader implements Runnable {
        URL url;
        Cursor cursor;

        PageLoader(URL u, Cursor c) {
            url = u;
            cursor = c;
        }

        @Override
        public void run() {
            if (url == null) {
                html.setCursor(cursor);
                Container parent = html.getParent();
                parent.repaint();
            } else {
                try {
                    doc = html.getDocument();
                    html.setPage(url);

                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    html.setDocument(doc);
                } finally {
                    url = null;
                    SwingUtilities.invokeLater(this);
                }
            }
        }
    }

    private static class HelpWindow extends JDialog implements ActionListener {
        private final JButton doneButton;

        HelpWindow(JFrame parent, HelpPanel helpPanel) {
            super(parent, "Help", false);

            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBorder(new EmptyBorder(2, 2, 5, 2));
            helpPanel.setBorder(new EmptyBorder(0, 0, 5, 0));
            doneButton = new JButton("Close");
            mainPanel.add(helpPanel, AwtUtil.getConstraints(0, 1, 1, 1, 1, 1, true, true));
            mainPanel.add(doneButton, AwtUtil.getConstraints(0, 3, 1, 0, 1, 1, false, false));

            getContentPane().add(mainPanel);

            doneButton.addActionListener(this);
            setSize(getPreferredSize());
            setLocation(50, 50);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            setVisible(false);
        }
    }

    /** Show the help window as a dialog of the given frame */
    public static void show(JFrame parent, String dir, String inputFile) {
        HelpPanel hp = new HelpPanel(dir, inputFile);
        HelpWindow hw = new HelpWindow(parent, hp);
        hw.setVisible(true);
    }

    public static void main(String[] args) {
        final JFrame f = new JFrame();
        MyButton a = new MyButton("oooo");
        a.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HelpPanel.show(f, CommonDef.HELP_DIR, CommonDef.HELP_CREATE_HANDLE);
            }
        });

        Container c = f.getContentPane();
        JPanel p = new JPanel();
        p.add(a);
        c.add(p);
        f.setSize(200, 200);
        f.pack();
        f.setVisible(true);
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.awt.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;

public class TextAreaBasedConsolePanel extends AbstractConsolePanel {
    public boolean debug = false;
    private Font textFont;
    private final JTextArea textArea;
    private final JScrollPane scrollPane;

    public TextAreaBasedConsolePanel() {
        super(new GridBagLayout());
        setBorder(new EtchedBorder());
        Graphics graphics = this.getGraphics();
        if (graphics != null) {
            textFont = new Font("Monospaced", Font.PLAIN, 10);
        }

        textArea = new JTextArea(5, 20);
        textArea.setEditable(false);
        if (textFont != null) textArea.setFont(textFont);
        scrollPane = new JScrollPane(textArea);
        add(scrollPane, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
    }

    @Override
    public void writeConsoleContents(Writer w) throws IOException {
        w.write(textArea.getText());
    }

    @Override
    public synchronized void addText(String text) {
        try {
            if (text == null) return;
            textArea.append(text);
        } catch (Throwable t) {
            //t.printStackTrace(System.out);
        }
    }

    @Override
    public synchronized void clear() {
        textArea.setText("");
        repaint();
    }

    private ConsoleStream outputStream = null;

    @Override
    public synchronized OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new ConsoleStream();
        }
        return outputStream;
    }

    private class ConsoleStream extends OutputStream {
        @Override
        public void write(byte buf[]) throws IOException {
            addText(new String(buf, "UTF-8"));
        }

        @Override
        public void write(byte buf[], int offset, int length) throws IOException {
            addText(new String(buf, offset, length, "UTF-8"));
        }

        @Override
        public void write(int i) throws IOException {
            addText(String.valueOf((char) i));
        }
    }
}

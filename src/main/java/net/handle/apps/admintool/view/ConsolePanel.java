/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.awt.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;

public class ConsolePanel extends AbstractConsolePanel implements AdjustmentListener, Runnable {
    public boolean debug = false;
    private int rowHeight = 18;
    private final ArrayList<String> lines;
    private final JScrollBar scrollBar;
    private final ConsoleCanvas canvas;
    private int firstIdx = 0;
    private Font textFont;

    public ConsolePanel() {
        super(new GridBagLayout());
        setBorder(new EtchedBorder());
        FontMetrics fm = null;
        Graphics graphics = this.getGraphics();
        if (graphics != null) {
            textFont = new Font("Monospaced", Font.PLAIN, 10);
            fm = graphics.getFontMetrics(textFont);
        }
        if (fm != null) {
            int lineHeight = fm.getHeight();
            if (lineHeight > 0) rowHeight = lineHeight;
        }

        canvas = new ConsoleCanvas();
        lines = new ArrayList<>();
        lines.add("");
        scrollBar = new JScrollBar(Adjustable.VERTICAL, 0, 0, 0, 0);
        add(canvas, AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
        add(scrollBar, AwtUtil.getConstraints(1, 0, 0, 1, 1, 1, true, true));

        scrollBar.addAdjustmentListener(this);
    }

    @Override
    public void writeConsoleContents(Writer w) throws IOException {
        for (int i = 0; i < lines.size(); i++) {
            w.write(String.valueOf(lines.get(i)));
            w.write('\n');
        }
    }

    @Override
    public synchronized void addText(String text) {
        try {
            if (text == null) return;
            boolean isAtEnd = (scrollBar.getValue() + scrollBar.getVisibleAmount()) >= scrollBar.getMaximum();
            boolean newline = false;
            do {
                int nlIdx = text.indexOf('\n');
                int lastLineNum = lines.size() - 1;
                if (nlIdx < 0) {
                    lines.set(lastLineNum, lines.get(lastLineNum) + text);
                    text = "";
                } else {
                    lines.add(text.substring(0, nlIdx));
                    text = text.substring(nlIdx + 1);
                    if (isAtEnd) {
                        firstIdx = Math.max(0, lines.size() - canvas.getNumRows());
                    }
                    newline = true;
                }
            } while (text.length() > 0);

            if (newline) {
                if (isAtEnd) {
                    if (debug) {
                        System.err.println("set (end) values" + "; first=" + Math.max(firstIdx, lines.size() - canvas.getNumRows()) + "; extent=" + Math.min(lines.size(), canvas.getNumRows()) + "; min=" + 0 + "; max=" + lines.size());
                    }
                    scrollBar.setValues(Math.max(firstIdx, lines.size() - canvas.getNumRows()), Math.min(lines.size(), canvas.getNumRows()), 0, lines.size());
                } else {
                    if (debug) {
                        System.err.println("set values" + "; first=" + firstIdx + "; extent=" + Math.min(lines.size(), canvas.getNumRows()) + "; min=" + 0 + "; max=" + lines.size());
                    }
                    scrollBar.setValues(firstIdx, Math.min(lines.size(), canvas.getNumRows()), 0, lines.size());
                }
            }
            SwingUtilities.invokeLater(this);
        } catch (Throwable t) {
            //t.printStackTrace(System.out);
        }
    }

    @Override
    public synchronized void clear() {
        lines.clear();
        addText("\n");
        repaint();
    }

    @Override
    public void run() {
        canvas.repaint();
    }

    @Override
    public void adjustmentValueChanged(AdjustmentEvent evt) {
        firstIdx = evt.getValue();
        canvas.repaint();
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

    private class ConsoleCanvas extends JComponent {
        int w = 0;
        int h = 0;

        @Override
        public void setBounds(int x, int y, int w, int h) {
            this.w = w;
            this.h = h;
            super.setBounds(x, y, w, h);
        }

        int getNumRows() {
            return h / rowHeight;
        }

        @Override
        public void paint(Graphics g) {
            update(g);
        }

        @Override
        public void update(Graphics g) {
            g.setFont(textFont);
            g.clearRect(0, 0, w, h);
            int x = 4;
            int y = rowHeight;
            for (int i = firstIdx; y < h + rowHeight && i < lines.size(); i++) {
                g.drawString(lines.get(i), x, y);
                y += rowHeight;
            }
        }

    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.hadmin;

import java.awt.*;
import javax.swing.*;
import java.io.*;

public class Console extends JFrame {

    private final JTextArea console;

    // older code, will not fix
    @SuppressWarnings("resource")
    public Console() {
        super("Console");

        console = new JTextArea(40, 80);
        console.setEditable(false);
        // setPreferredSize(new Dimension(800, 400));
        setSize(new Dimension(800, 400));
        getContentPane().add(new JScrollPane(console));

        ConsoleStream errStream = new ConsoleStream(System.err);
        PrintStream errPrintStream = new PrintStream(errStream, true);
        ConsoleStream outStream = new ConsoleStream(System.out);
        PrintStream outPrintStream = new PrintStream(outStream, true);
        System.setErr(errPrintStream);
        System.setOut(outPrintStream);

        setVisible(false);
    }

    class ConsoleStream extends OutputStream {
        PrintStream stream;
        int MAX_CHARS = 32 * 1024; // 32k

        public ConsoleStream(PrintStream stream) {
            this.stream = stream;
        }

        private void checkSize() {
            String txt = console.getText();
            if (txt.length() > MAX_CHARS) {
                txt = txt.substring(MAX_CHARS / 2);
                int i = txt.indexOf("\n");
                if (i > 0) txt = txt.substring(i);
                console.setText(txt);
            }
        }

        @Override
        public void write(byte buf[]) throws IOException {
            console.append(new String(buf));
            stream.write(buf);
            checkSize();
        }

        @Override
        public void write(byte buf[], int offset, int length) throws IOException {
            console.append(new String(buf, offset, length));
            stream.write(buf, offset, length);
            checkSize();
        }

        @Override
        public void write(int i) throws IOException {
            console.append(String.valueOf((char) i));
            stream.write(i);
            checkSize();
        }

    }

}

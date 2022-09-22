/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.resolver;

import net.handle.hdllib.*;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/**
 * Window that lets the user resolve handles and displays the resolution
 * process.  Sortof a "traceroute" for handle resolution.
 */

public class Main extends Frame implements ActionListener, ItemListener {
    private final HandleResolver resolver;

    private final TextField handleField;
    private final Button goButton;
    private final TextArea consoleArea;
    private final TextField indexesField;
    private final TextField typesField;
    private final Choice typesChoice;

    private final Checkbox certifyCheckbox;
    private final Checkbox authCheckbox;

    public Main() {
        super("Handle Resolution Visualizer");

        resolver = new HandleResolver();
        resolver.traceMessages = true;

        handleField = new TextField("", 30);
        certifyCheckbox = new Checkbox("Certify Responses", false);
        authCheckbox = new Checkbox("Authoritative Requests", false);
        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        goButton = new Button("Resolve Handle");
        indexesField = new TextField("", 20);
        typesField = new TextField("", 20);
        typesChoice = new Choice();
        typesChoice.addItem("--Common Types--");
        for (int i = 0; i < Common.STD_TYPES.length; i++)
            typesChoice.addItem(Util.decodeString(Common.STD_TYPES[i]));

        setLayout(new GridBagLayout());

        int x = 0;
        int y = 0;

        add(new Label("Handle: ", Label.RIGHT), getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(handleField, getConstraints(x + 1, y, 1, 0, 1, 1, true, true));
        add(goButton, getConstraints(x + 2, y++, 0, 0, 1, 1, true, true));

        add(new Label("Types: ", Label.RIGHT), getConstraints(x, y, 0, 0, 1, 1, true, true));
        add(typesField, getConstraints(x + 1, y, 1, 0, 1, 1, true, true));
        add(typesChoice, getConstraints(x + 2, y++, 0, 0, 1, 1, true, true));

        add(new Label("Indexes: ", Label.RIGHT), getConstraints(0, y, 0, 0, 1, 1, true, true));
        add(indexesField, getConstraints(x + 1, y++, 1, 0, 2, 1, true, true));

        Panel p = new Panel(new GridBagLayout());
        p.add(authCheckbox, getConstraints(0, 0, 1, 0, 1, 1, false, false));
        p.add(certifyCheckbox, getConstraints(1, 0, 1, 0, 1, 1, false, false));
        add(p, getConstraints(x + 1, y++, 1, 0, 2, 1, true, true));

        add(consoleArea, getConstraints(x, y++, 1, 1, 3, 1, true, true));

        goButton.addActionListener(this);
        handleField.addActionListener(this);
        typesField.addActionListener(this);
        indexesField.addActionListener(this);

        ConsoleStream consoleStream = new ConsoleStream(consoleArea);
        // older code, will not fix
        @SuppressWarnings("resource")
        PrintStream consolePrintStream = new PrintStream(consoleStream, true);

        System.setErr(consolePrintStream);
        System.setOut(consolePrintStream);

        typesChoice.addItemListener(this);

        Dimension sz = new Dimension(800, 500);
        setSize(sz);
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (tk == null) {
            setLocation(20, 20);
        } else {
            Dimension ssz = tk.getScreenSize();
            setLocation(ssz.width / 2 - sz.width / 2, ssz.height / 2 - sz.height / 2);
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent evt) {
                setVisible(false);
                System.exit(0);
            }
        });
    }

    class ConsoleStream extends OutputStream {
        private final TextArea console;

        ConsoleStream(TextArea console) {
            this.console = console;
        }

        @Override
        public void write(byte buf[]) {
            console.append(new String(buf));
        }

        @Override
        public void write(byte buf[], int offset, int length) {
            console.append(new String(buf, offset, length));
        }

        @Override
        public void write(int ich) {
            console.append(String.valueOf((char) ich));
        }

    }

    private void typeSelected() {
        int selIdx = typesChoice.getSelectedIndex();
        if (selIdx > 0) {
            String selItem = typesChoice.getSelectedItem();
            typesField.setText((typesField.getText().trim() + ' ' + selItem).trim());
            typesChoice.select(0);
        }
    }

    private void doResolution() {
        consoleArea.setText("");
        try {
            // get the types...
            byte types[][] = null;
            String typeStr = typesField.getText().trim();
            if (typeStr.length() > 0) {
                StringTokenizer st = new StringTokenizer(typeStr);
                types = new byte[st.countTokens()][];
                for (int i = 0; i < types.length; i++) {
                    types[i] = Util.encodeString(st.nextToken().trim());
                }
            }

            // get the indexes to request
            int indexes[] = null;
            String indexStr = indexesField.getText().trim();
            if (indexStr.length() > 0) {
                Vector<Integer> indexVect = new Vector<>();
                StringTokenizer st = new StringTokenizer(indexStr);
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    try {
                        int index = Integer.parseInt(token);
                        if (index < 0) {
                            System.err.println("Invalid index value: " + index + " - indexes must be non-negative");
                            continue;
                        }

                        indexVect.addElement(Integer.valueOf(index));
                    } catch (Exception e) {
                        System.err.println("Invalid index value: " + token);
                    }
                }
                indexes = new int[indexVect.size()];
                for (int i = 0; i < indexes.length; i++) {
                    indexes[i] = indexVect.elementAt(i).intValue();
                }
            }

            ResolutionRequest req = new ResolutionRequest(Util.encodeString(handleField.getText()), types, indexes, null);

            req.certify = certifyCheckbox.getState();
            req.authoritative = authCheckbox.getState();

            AbstractResponse response = resolver.processRequest(req);
            System.err.println("\nGot Response: \n" + response);
        } catch (Throwable t) {
            System.err.println("\nError: " + t);
            t.printStackTrace(System.err);
        }
    }

    @Override
    public void itemStateChanged(ItemEvent evt) {
        typeSelected();
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        doResolution();
    }

    public static void main(String argv[]) {
        Main m = new Main();
        m.setVisible(true);
    }

    private static GridBagConstraints c = new GridBagConstraints();

    private static GridBagConstraints getDefaultGC() {
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        c.anchor = GridBagConstraints.CENTER;
        c.gridwidth = 1;
        c.gridheight = 1;
        c.fill = GridBagConstraints.BOTH;
        return c;
    }

    private static GridBagConstraints getConstraints(int x, int y, double weightx, double weighty, int gridwidth, int gridheight, boolean fillHorizontal, boolean fillVertical) {
        GridBagConstraints gc = getDefaultGC();
        gc.weightx = weightx;
        gc.weighty = weighty;
        gc.gridx = x;
        gc.gridy = y;
        gc.gridwidth = gridwidth;
        gc.gridheight = gridheight;

        if (fillHorizontal && fillVertical) {
            gc.fill = GridBagConstraints.BOTH;
        } else if (fillHorizontal) {
            gc.fill = GridBagConstraints.HORIZONTAL;
        } else if (fillVertical) {
            gc.fill = GridBagConstraints.VERTICAL;
        } else {
            gc.fill = GridBagConstraints.NONE;
        }
        return gc;
    }

}

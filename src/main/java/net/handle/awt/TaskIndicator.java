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

public class TaskIndicator extends JDialog implements Runnable, ActionListener {

    private Runnable task = null;
    private Thread taskThread = null;
    private final JLabel label;
    private final Component parent;

    private static JFrame testFrame;

    public static void main(String argv[]) {
        // test the TaskIndicator...

        testFrame = new JFrame("test");
        JButton testButton = new JButton("Click Me");
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                TaskIndicator ti = new TaskIndicator(testFrame);
                Runnable runner = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                        } catch (Throwable e) {
                            System.err.println("Runner got exception: " + e);
                            e.printStackTrace(System.err);
                        }
                    }
                };

                ti.invokeTask(runner, "Sleeping a bit...");
            }
        });
        testFrame.getContentPane().add(testButton);
        testFrame.setSize(200, 100);
        testFrame.setVisible(true);
    }

    public TaskIndicator(Component parent) {
        super(AwtUtil.getFrame(parent), "", true);
        this.parent = parent;

        JPanel p = new JPanel(new GridBagLayout());
        label = new JLabel(" ", SwingConstants.CENTER);
        JButton cancelButton = new JButton("Cancel");

        p.add(new JLabel(" "), AwtUtil.getConstraints(0, 0, 1, 1, 1, 1, true, true));
        p.add(label, AwtUtil.getConstraints(1, 1, 1, 1, 1, 1, true, true));
        p.add(new JLabel(" "), AwtUtil.getConstraints(2, 2, 1, 1, 1, 1, true, true));
        p.add(cancelButton, AwtUtil.getConstraints(0, 3, 1, 0, 3, 1, false, false));

        cancelButton.addActionListener(this);

        getContentPane().add(p);
    }

    public void invokeTask(@SuppressWarnings("hiding") Runnable task, String taskLabel) {
        label.setText(taskLabel);
        pack();
        setSize(getPreferredSize());
        AwtUtil.setWindowPosition(this, parent);

        this.task = task;
        taskThread = new Thread(this);
        taskThread.start();
        setVisible(true);
        this.task = null;
        this.taskThread = null;
    }

    @Override
    public void run() {
        // wait until the window becomes visible...
        while (!isVisible()) {
            try {
                Thread.sleep(500);
            } catch (Throwable e) {
            }
        }

        try {
            task.run();
        } catch (Throwable e) {
            System.err.println("Exception running task: " + e);
            e.printStackTrace(System.err);
        } finally {
            taskFinished();
        }
    }

    private synchronized void taskFinished() {
        task = null;
        taskThread = null;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                setVisible(false);
                dispose();
            }
        });
    }

    private void cancelPressed() {
        // interrupt the thread, the thread will then call setVisible...
        try {
            System.err.println("cancel process...");
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    setVisible(false);
                    dispose();
                }
            });

            taskThread.interrupt();

        } catch (Exception e) {
            System.err.println("Exception interrupting task: " + e);
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        cancelPressed();
    }

}

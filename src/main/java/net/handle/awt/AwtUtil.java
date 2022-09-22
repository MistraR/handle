/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.awt;

import java.awt.*;
import javax.swing.JLabel;

public class AwtUtil {

    public final static JLabel PadLabel = new JLabel(" test ");

    private static GridBagConstraints c = new GridBagConstraints();
    private final static Insets DEFAULT_INSETS = c.insets;
    private final static int DEFAULT_ANCHOR = c.anchor;
    private final static int DEFAULT_IPADX = c.ipadx;
    private final static int DEFAULT_IPADY = c.ipady;

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

    //add weightX/Y, gridH/W
    public static GridBagConstraints getConstraints(int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, boolean fillHorizontal, boolean fillVertical) {

        return getConstraints(gridx, gridy, weightx, weighty, gridwidth, gridheight, DEFAULT_INSETS, DEFAULT_ANCHOR, fillHorizontal, fillVertical);

    }

    //add inset
    public static GridBagConstraints getConstraints(int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, Insets insets, boolean fillHorizontal, boolean fillVertical) {

        return getConstraints(gridx, gridy, weightx, weighty, gridwidth, gridheight, insets, DEFAULT_ANCHOR, fillHorizontal, fillVertical);
    }

    //add inset and anchor
    public static GridBagConstraints getConstraints(int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, Insets insets, int align, boolean fillHorizontal, boolean fillVertical) {

        return getConstraints(gridx, gridy, weightx, weighty, gridwidth, gridheight, DEFAULT_IPADX, DEFAULT_IPADY, insets, align, fillHorizontal, fillVertical);
    }

    //set full features
    public static GridBagConstraints getConstraints(int gridx, int gridy, double weightx, double weighty, int gridwidth, int gridheight, int ipadx, int ipady, Insets insets, int align, boolean fillHorizontal, boolean fillVertical) {
        GridBagConstraints gc = getDefaultGC();
        gc.gridx = gridx;
        gc.gridy = gridy;
        gc.weightx = weightx;
        gc.weighty = weighty;
        gc.gridwidth = gridwidth;
        gc.gridheight = gridheight;
        gc.ipadx = ipadx;
        gc.ipady = ipady;
        gc.insets = insets;
        gc.anchor = align;

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

    public static Frame getFrame(Component comp) {
        while (comp != null && !(comp instanceof Frame))
            comp = comp.getParent();
        return (Frame) comp;
    }

    public static final int WINDOW_BOTTOM_RIGHT = 0;
    public static final int WINDOW_BOTTOM_LEFT = 1;
    public static final int WINDOW_TOP_LEFT = 2;
    public static final int WINDOW_TOP_RIGHT = 3;
    public static final int WINDOW_CENTER = 4;

    public static final void setWindowPosition(Window win, int position) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        if (tk == null) {
            win.setLocation(20, 20);
            return;
        }
        Dimension ssz = tk.getScreenSize();
        Dimension sz = win.getSize();
        switch (position) {
        case WINDOW_BOTTOM_RIGHT:
            win.setLocation(ssz.width - sz.width, ssz.height - sz.height);
            break;
        case WINDOW_BOTTOM_LEFT:
            win.setLocation(0, ssz.height - sz.height);
            break;
        case WINDOW_TOP_RIGHT:
            win.setLocation(ssz.width - sz.width, 0);
            break;
        case WINDOW_TOP_LEFT:
            win.setLocation(0, 0);
            break;
        case WINDOW_CENTER:
        default:
            win.setLocation(ssz.width / 2 - sz.width / 2, ssz.height / 2 - sz.height / 2);
            break;
        }
    }

    public static final void setWindowPosition(Window child, Component parent) {
        Component parentComp = getFrame(parent);
        if (parentComp == null) parentComp = parent;

        Point pLoc;
        Dimension pSz;
        if (parentComp == null) {
            pLoc = new Point(0, 0);
            pSz = Toolkit.getDefaultToolkit().getScreenSize();
        } else {
            pLoc = parent.getLocationOnScreen();
            pSz = parent.getSize();
        }
        Dimension cSz = child.getSize();
        try {
            child.setLocation(pLoc.x + pSz.width / 2 - cSz.width / 2, pLoc.y + pSz.height / 2 - cSz.height / 2);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

    }
}

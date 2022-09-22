/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
         http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.cnri.guiutil;

import java.awt.GridBagConstraints;

/**
 * Utility class for easily creating GridBagConstraints objects for use in
 * laying out user interfaces.  To lay out components, add them to a panel
 * like so:  p.add(someComponent, GridC.getc().xy(x,y).fillboth());
 * Method calls can be chained since each parameter setting method returns
 * the same GridC object upon which it operates.  Since GridC is a subclass
 * of GridBagConstraints, it can be used as the parameter to the add() method
 * if the LayoutManager is set to GridBagLayout.  This allows GUIs to be
 * created programmatically with very short but flexible code.
 */
public class GridC extends GridBagConstraints {
    private static GridC staticRef = new GridC();

    /** Reset and return the singleton GridC instance */
    public static final GridC getc() {
        return staticRef.reset();
    }

    /** Reset and return the singleton GridC instance, with x and y initialized to
     *  the given values.  This is a shortcut to GridC.getc().xy(x,y) */
    public static final GridC getc(int x, int y) {
        return staticRef.reset().xy(x, y);
    }

    /** Reset all of the constraints to the default value and return ourself. */
    public GridC reset() {
        anchor = CENTER;
        fill = NONE;
        gridheight = 1;
        gridwidth = 1;
        gridx = RELATIVE;
        gridy = RELATIVE;
        insets.top = 0;
        insets.left = 0;
        insets.bottom = 0;
        insets.right = 0;
        ipadx = 0;
        ipady = 0;
        weightx = 0;
        weighty = 0;
        return this;
    }

    /** Set our gridx value and return self */
    public GridC x(int x) {
        this.gridx = x;
        return this;
    }

    /** Set our gridx value and return self */
    public GridC y(int y) {
        this.gridy = y;
        return this;
    }

    /** Set both the gridx and gridy values and return self */
    public GridC xy(int x, int y) {
        this.gridx = x;
        this.gridy = y;
        return this;
    }

    /** Set our weightx value and return self */
    public GridC wx(float xWeight) {
        this.weightx = xWeight;
        return this;
    }

    /** Set our weighty value and return self */
    public GridC wy(float yWeight) {
        this.weighty = yWeight;
        return this;
    }

    /** Set our weightx and weighty value and return self */
    public GridC wxy(float xWeight, float yWeight) {
        this.weightx = xWeight;
        this.weighty = yWeight;
        return this;
    }

    /** Set the gridwidth: number of columns to span. */
    public GridC colspan(int numColumns) {
        this.gridwidth = numColumns;
        return this;
    }

    /** Set the gridheight: number of rows to span. */
    public GridC rowspan(int numRows) {
        this.gridheight = numRows;
        return this;
    }

    /** Set the fill type to horizontal */
    public GridC fillx() {
        this.fill = HORIZONTAL;
        return this;
    }

    /** Set the fill type to vertical */
    public GridC filly() {
        this.fill = VERTICAL;
        return this;
    }

    /** Set the fill type to both horizontal and vertical */
    public GridC fillboth() {
        this.fill = BOTH;
        return this;
    }

    /** Set the fill type to no filling (the default) */
    public GridC fillnone() {
        this.fill = NONE;
        return this;
    }

    /** Set the insets */
    public GridC insets(int top, int left, int bottom, int right) {
        insets.top = top;
        insets.left = left;
        insets.bottom = bottom;
        insets.right = right;
        return this;
    }

    /** Anchor the component to the east */
    public GridC east() {
        this.anchor = EAST;
        return this;
    }

    /** Anchor the component to the west */
    public GridC west() {
        this.anchor = WEST;
        return this;
    }

    /** Anchor the component to the north */
    public GridC north() {
        this.anchor = NORTH;
        return this;
    }

    /** Anchor the component to the south */
    public GridC south() {
        this.anchor = SOUTH;
        return this;
    }

    /** Anchor the component to the northeast */
    public GridC northEast() {
        this.anchor = NORTHEAST;
        return this;
    }

    /** Anchor the component to the northwest */
    public GridC northWest() {
        this.anchor = NORTHWEST;
        return this;
    }

    /** Anchor the component to the southeast */
    public GridC southEast() {
        this.anchor = SOUTHEAST;
        return this;
    }

    /** Anchor the component to the southwest */
    public GridC southWest() {
        this.anchor = SOUTHWEST;
        return this;
    }

    /** Anchor the component to the center (the default) */
    public GridC center() {
        this.anchor = CENTER;
        return this;
    }

    /** Set the internal padding along the X axis */
    public GridC padx(int xPadding) {
        this.ipadx = xPadding;
        return this;
    }

    /** Set the internal padding along the Y axis */
    public GridC pady(int yPadding) {
        this.ipady = yPadding;
        return this;
    }

    /** Shortcut for the settings associated with a label in a standard dialog */
    public GridC label() {
        return east().insets(2, 0, 2, 2);
    }

    /** Shortcut for the constraints associated with a field in a standard dialog */
    public GridC field() {
        return wx(1).fillx().insets(2, 2, 2, 0);
    }

}

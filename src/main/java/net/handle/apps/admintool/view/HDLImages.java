/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import java.util.HashMap;
import java.awt.*;
import javax.swing.*;

public class HDLImages {

    public static final String DOWN_TRIANGLE = "/net/handle/apps/admintool/view/resources/down_triangle.png";

    private final HashMap<String, Image> images = new HashMap<>();
    private final HashMap<String, Icon> icons = new HashMap<>();

    private static final HDLImages singleton = new HDLImages();

    /** Return a single instance of an HDLImages object */
    public static HDLImages singleton() {
        return singleton;
    }

    HDLImages() {
        // preload the images...
        getImage(DOWN_TRIANGLE);
    }

    /** Get an icon with the given path as a resource.  This uses a cache to
     * avoid reloading icons numerous times. */
    public synchronized Icon getIcon(String imgPath) {
        Icon icon = icons.get(imgPath);
        if (icon != null) return icon;

        Image img = getImage(imgPath);
        if (img == null) return null;

        ImageIcon imgIcon = new ImageIcon(img);
        icon = imgIcon;
        while (imgIcon.getImageLoadStatus() == MediaTracker.LOADING) {
            try {
                Thread.yield();
            } catch (Exception e) {
            }
        }
        icons.put(imgPath, icon);
        return icon;
    }

    /** Get an image with the given path as a resource.  This uses a cache to
     * avoid reloading images numerous times. */
    public synchronized Image getImage(String imgPath) {
        Image img = images.get(imgPath);
        if (img != null) return img;

        try {
            java.net.URL url = getClass().getResource(imgPath);
            if (url == null) return null;

            img = Toolkit.getDefaultToolkit().getImage(url);
            if (img == null) return null;

            images.put(imgPath, img);

            // wait for the image to load and cache the icon
            try {
                ImageIcon imgIcon = new ImageIcon(img);
                while (imgIcon.getImageLoadStatus() == MediaTracker.LOADING) {
                    try {
                        Thread.yield();
                    } catch (Exception e) {
                    }
                }
                icons.put(imgPath, imgIcon);
            } catch (Exception e) {
                System.err.println("Error (pre)loading image: " + e + " image: " + imgPath);
            }

            return img;
        } catch (Exception e) {
            System.err.println("Error loading image: '" + imgPath + "' error=" + e);
        }
        return null;
    }

}

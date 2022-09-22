/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.io.File;

import net.cnri.util.StreamTable;

public class HDLToolConfig {
    public static StreamTable table;
    static File configFile;

    static {
        load();
    }

    public static void load() {
        // load config
        table = new StreamTable();
        File userDir;
        String userDirName = System.getProperty("user.home");
        if (userDirName == null) {
            // if the user has no "home" directory, use the current directory..
            userDir = new File(System.getProperty("user.dir", File.separator));
        } else {
            userDir = new File(userDirName);
        }
        // create a file object for our config dir based on the "home" directory
        File configDir = new File(userDir, ".handle");
        configDir.mkdirs();
        configFile = new File(configDir, "hdltool.ini");
        try {
            table.readFromFile(configFile);
        } catch (Exception e) {
        }
    }

    public static void save() {
        try {
            table.writeToFile(configFile);
        } catch (Exception e) {
            System.err.println("Error saving settings: " + e);
        }
    }
}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view.resources;

import java.util.*;
import java.io.*;

import net.cnri.util.StreamTable;

public class Resources extends ResourceBundle {
    private static final String DEFAULT_DICT = "/net/handle/apps/admintool/view/resources/english.dict";

    private final StreamTable resourceTable;
    private StreamTable backupResourceTable;

    /** Default constructor that loads the English dictionary.
      Subclasses for other locales should call the Resources(String)
      constructor. */
    public Resources() {
        this(DEFAULT_DICT);
    }

    public Resources(String resourceName) {
        resourceTable = new StreamTable();
        try {
            Reader rdr = new InputStreamReader(getClass().getResourceAsStream(resourceName), "UTF8");
            resourceTable.readFrom(rdr);
        } catch (Exception e) {
            System.err.println("Error reading resources: " + e);
            e.printStackTrace(System.err);
        }

        if (DEFAULT_DICT.equals(resourceName)) {
            backupResourceTable = resourceTable;
        } else {
            backupResourceTable = new StreamTable();
            try {
                Reader rdr = new InputStreamReader(getClass().getResourceAsStream(DEFAULT_DICT), "UTF8");
                backupResourceTable.readFrom(rdr);
            } catch (Exception e) {
                System.err.println("Error reading backup (english) resources: " + e);
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public Enumeration<String> getKeys() {
        return resourceTable.keys();
    }

    @Override
    protected Object handleGetObject(String key) throws MissingResourceException {
        Object o = resourceTable.get(key);
        if (o == null) o = backupResourceTable.get(key);
        if (o == null) return "??" + key + "??";
        return o;
    }

}

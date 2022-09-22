/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.hdllib.*;
import net.handle.jdb.*;
import java.io.*;
import java.util.*;

/**
 * This class defines an object that splits the handles.jdb file from
 * one server into handles.jdb files for several servers.  It does this
 * by scanning all the handles in the original handles.jdb file and
 * computing the hash for each one to determine the server to which it
 * belongs.  Because this uses the hash result, the order that the
 * destination server directories are provided on the command-line are
 * significant.
 */
public class SplitServer {
    private final DBHash destDBs[];
    private final DBHash origDB;
    private final int numDBs;

    public static void main(String argv[]) throws Exception {
        if (argv.length < 3) {
            System.err.println("usage: java net.handle.apps.tools.SplitServer <sourcefile> <destdir1> <destdir2> ...");
            return;
        }

        SplitServer splitServer = new SplitServer(argv);
        splitServer.doit();
        splitServer.cleanup();
    }

    SplitServer(String argv[]) throws Exception {
        File origFile = new File(argv[0]);

        if (!origFile.canRead()) {
            throw new Exception("Unable to read database file: " + origFile);
        }

        System.err.println("loading source DB...");
        origDB = new DBHash(origFile, 5000, 1000);
        numDBs = argv.length - 1;
        destDBs = new DBHash[numDBs];
        for (int i = 0; i < destDBs.length; i++) {
            System.err.println("loading dest db: " + argv[i + 1]);
            destDBs[i] = new DBHash(new File(argv[i + 1], "handles.jdb"), 5000, 1000);
        }
    }

    final void doit() throws Exception {
        // clear all of the destination records
        for (int i = 0; i < destDBs.length; i++) {
            destDBs[i].deleteAllRecords();
        }

        Enumeration<?> enumeration = origDB.getEnumerator();
        byte record[][] = null;
        while (enumeration.hasMoreElements()) {
            record = (byte[][]) enumeration.nextElement();
            destDBs[SiteInfo.determineServerNum(record[0], SiteInfo.HASH_TYPE_BY_ALL, numDBs)].setValue(record[0], record[1]);
        }
    }

    void cleanup() throws Exception {
        origDB.close();

        for (int i = 0; i < destDBs.length; i++) {
            destDBs[i].close();
        }
    }

}

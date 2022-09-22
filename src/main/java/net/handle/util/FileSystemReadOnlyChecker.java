/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.util;

import java.io.File;
import java.io.IOException;

public class FileSystemReadOnlyChecker {

    private static final String TEST_FILE_NAME = ".is_file_system_read_only_test_file";

    public static synchronized boolean isReadOnly(File dir) {
        File testFile = new File(dir, TEST_FILE_NAME);
        try {
            testFile.createNewFile();
            testFile.delete();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}

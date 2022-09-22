/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.io.*;

public class FileOpt {
    /**
     * Function: save byte buffer to local file
     * @param buffer is the byte buffer
     * @param file is the local file
     **/
    public static void bytesToFile(byte[] buffer, File file) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(buffer);
            out.close();
        } catch (Exception e) {
            e.printStackTrace(System.err);
            if (out != null) try {
                out.close();
            } catch (Exception e1) {
            }
        }
    }

    /**
     * Function: load file to byte buffer
     * @param file is the local file
     * @return byte buffer
     **/
    public static byte[] fileToBytes(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            int len = (int) file.length();
            byte[] buffer = new byte[len];
            int r = 0;
            int n = 0;
            while (n < len && (r = in.read(buffer, n, len - n)) >= 0)
                n += r;
            in.close();
            return buffer;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            if (in != null) try {
                in.close();
            } catch (Exception e1) {
                return null;
            }
            return null;
        }
    }

    /**
     *Function: get the parent dirctory of the input file
     *@param f is input file
     **/
    public static File getParent(File f) {
        String dirname = f.getParent();
        if (dirname == null) if (f.isAbsolute()) return new File(File.separator);
        else return new File(System.getProperty("user.dir"));
        return new File(dirname);
    }

    /**
     *Function: check the given dir have reserved files or not
     *@param dir -- checked dir
     *@param ds -- reserved file name list
     *@return if have all reserved files return true, else, false
     **/
    public static boolean checkDir(File dir, String[] ds) {
        if (dir.isDirectory()) {
            String[] fs = dir.list();

            int cnt = 0;
            for (int i = 0; i < fs.length; i++) {
                for (int j = 0; j < ds.length; j++)
                    if (fs[i].equals(ds[j])) {
                        cnt++;
                        break;
                    }
                if (cnt == ds.length) return true;
            }
        }
        return false;
    }

}

/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.io.*;
import java.util.zip.*;

import net.handle.hdllib.Util;

/************************************************************************
 * Class to Zip files
 ************************************************************************/

public class ZipFile {

    /**
     *@param dir -- compressing directory
     *@param infiles -- compressing input file names
     *@param outName -- compressed output file name
     *@return if zip successfully, return ture, else, false
     **/
    public static boolean ZIP(File dir, String[] infiles, String outName) {
        File file = new File(outName);
        if (file.exists() || !(FileOpt.getParent(file).canWrite())) {
            System.err.println("zip error: Can not write file: " + outName);
            return false;
        }

        try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(file))) {
            zout.setMethod(ZipOutputStream.STORED);
            File f1;
            for (int i = 0; i < infiles.length; i++) {
                try {
                    f1 = new File(dir, infiles[i]);
                    if (!f1.exists() || !f1.isFile() || !f1.canRead()) return false;

                    //create zip entry
                    CRC32 crc32 = new CRC32();
                    try (FileInputStream in = new FileInputStream(f1)) {
                        byte[] bf = Util.getBytesFromInputStream(in);
                        crc32.update(bf);
                    }
                    ZipEntry zent = new ZipEntry(infiles[i]);
                    zent.setTime(file.lastModified());
                    zent.setSize(f1.length());
                    zent.setCrc(crc32.getValue());
                    zout.putNextEntry(zent);

                    //add zip entry and associated data
                    try (FileInputStream in = new FileInputStream(f1)) {
                        int n;
                        byte[] buffer = new byte[1024];
                        while ((n = in.read(buffer)) > -1)
                            zout.write(buffer, 0, n);
                    }
                    zout.closeEntry();

                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return false;
                }
            }
            zout.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return false;
        }
    }

    /**
     *@param dir -- unzip output dir
     *@param zipfile -- unzip input file
     *@return if unzip successfully, return true, else false
     **/
    public static boolean UNZIP(File dir, String zipfile) {
        File file = new File(zipfile);
        if (!file.exists() || !file.canRead()) {
            System.err.println("unzip error: Can not read Zip file:" + zipfile);
            return false;
        }

        ZipInputStream zin = null;
        try {
            zin = new ZipInputStream(new FileInputStream(file));
            FileOutputStream fout = null;
            File f2;
            while (true) {
                try {
                    ZipEntry zent = zin.getNextEntry();
                    if (zent == null) break;
                    String fname = zent.getName();
                    f2 = new File(dir, fname);
                    if (f2.exists()) {
                        System.err.println("unzip error: Can not write Zip file: " + f2.getName());
                        return false;
                    }
                    fout = new FileOutputStream(f2);
                    System.err.println(fname);
                    //            if(zent ==null) break;
                    byte[] buffer = new byte[1024];
                    int n;
                    while ((n = zin.read(buffer)) > -1)
                        fout.write(buffer, 0, n);
                    fout.close();
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    if (fout != null) try {
                        fout.close();
                    } catch (Exception e1) {
                    }
                    return false;
                }
            }
            zin.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            if (zin != null) try {
                zin.close();
            } catch (Exception e1) {
                return false;
            }
            return false;
        }
    }

    /**
     *@param files -- unzip output files
     *@param zipfile -- unzip input file
     *@return if unzip successfully, return true, else false
     **/
    public static boolean unzipToDir(String zipfile, File[] files) {
        if (!zipfile.endsWith(".zip")) return false;
        files[0] = null;
        String unzipName = new String(zipfile);
        int ind = unzipName.indexOf(".zip");
        unzipName = unzipName.substring(0, ind);
        System.err.println("unzip message: Unzip " + zipfile + " to directory " + unzipName + "...");
        File f1 = new File(unzipName);
        if (f1.exists()) {
            System.err.println("unzip error: " + unzipName + " already existed");
            return false;
        }
        if (FileOpt.getParent(f1).canWrite()) {
            f1.mkdir();
            try {
                ZipFile.UNZIP(f1, zipfile);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                return false;
            }
            files[0] = f1;
            return true;
        }
        return false;
    }
}

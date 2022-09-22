/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.util.Hashtable;
import java.util.Map;

import net.cnri.util.Template;
import net.cnri.util.TemplateException;
import java.io.*;

import javax.servlet.ServletContext;

public class HTMLFile {
    private final ServletContext context;

    String fileName, dir;
    File file;
    private long lastModified;
    private final Hashtable<Object,Object> dict = new Hashtable<>();
    String page;

    boolean isResource; // whether to use getResource() or normal File io
    boolean isServletResource;

    public HTMLFile(String dir, String fileName, ServletContext context) throws IOException {
        this.context = context;
        this.fileName = fileName;
        if (dir.startsWith("servlet:")) {
            isServletResource = true;
            this.dir = dir.substring(8);
            file = null;
        } else if (dir.startsWith("res:")) {
            isResource = true;
            this.dir = dir.substring(4);
            file = null;
        } else {
            file = new File(fileName);
            if (file.isAbsolute()) {
                this.dir = "";
            } else {
                this.dir = dir;
                file = new File(new File(dir), fileName);
            }
        }
        loadFile();
    }

    private String getPath() {
        return dir + "/" + fileName;
    }

    private void loadFile() throws IOException {
        InputStream in;
        if (isServletResource) {
            in = context.getResourceAsStream(getPath());
        } else if (isResource) {
            in = getClass().getResourceAsStream(getPath());
        } else {
            lastModified = file.lastModified();
            System.err.println("loading file: " + file.getCanonicalPath());
            in = new FileInputStream(file);
        }
        char b[] = new char[1024];
        int len;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuffer sb = new StringBuffer();
            while (true) {
                len = reader.read(b);
                if (len == -1) break;
                sb.append(b, 0, len);
            }
            page = sb.toString();
        }
    }

    public void setValue(String key, String val) {
        dict.put(key, val);
    }

    public void reset() {
        dict.clear();
    }

    public void output(OutputStream out) throws IOException {
        if (!isResource && !isServletResource && lastModified != file.lastModified()) {
            // reload
            System.err.println("Reloading " + fileName);
            loadFile();
        }
        try {
            out.write(Template.subDictIntoString(page, dict).getBytes("UTF-8"));
        } catch (TemplateException e) {
            throw new IOException("Error templatizing page.", e);
        }
    }

    public void output(OutputStream out, Map<?,?> dictParam) throws IOException {
        if (!isResource && !isServletResource && lastModified != file.lastModified()) {
            // reload
            System.err.println("Reloading " + fileName);
            loadFile();
        }
        try {
            out.write(Template.subDictIntoString(page, dictParam).getBytes("UTF-8"));
        } catch (TemplateException e) {
            throw new IOException("Error templatizing page.", e);
        }
    }
}

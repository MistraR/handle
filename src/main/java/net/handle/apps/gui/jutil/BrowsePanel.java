/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import java.awt.event.*;
import java.io.*;
import javax.swing.*;

/*******************************************************************************\
 * Class to provide input open/save file path GUI
 *
 *******************************************************************************/
public class BrowsePanel extends JPanel implements ActionListener {
    protected JTextField fileField;
    protected MyButton browseButton;
    protected static String pathStr = "";
    protected String[] exts = null;
    protected boolean saveFlag;

    /**
     *@param title --label display on the browser left side;
     *@param path --file may be under the path
     *@param filename --file may be named
     *@param exts --file filters, files' extention string array,
     *@param saveFlag -- true if save option, false if open option
     **/

    public BrowsePanel(String title, String path, String filename, String[] exts, boolean saveFlag) {
        // super(new GridBagLayout());
        super();
        this.exts = exts;
        this.saveFlag = saveFlag;

        fileField = new JTextField("", 25);
        File f = new File(path);
        if (f.exists()) pathStr = f.getAbsolutePath();
        fileField.setText(pathStr + filename);
        fileField.setToolTipText("Set the path and name to save information");
        browseButton = new MyButton("Browse", " find the path to save file");
        browseButton.addActionListener(this);

        add(new JLabel(title, SwingConstants.RIGHT));
        add(fileField);
        add(browseButton);

    }

    public BrowsePanel(String title, File path, String filename, String[] exts, boolean saveFlag) {

        // super(new GridBagLayout());
        super();
        this.exts = exts;
        this.saveFlag = saveFlag;

        fileField = new JTextField("", 25);
        if (path != null) pathStr = path.getAbsolutePath();
        fileField.setText(pathStr + filename);
        fileField.setToolTipText("Set the path and name to save information");
        browseButton = new MyButton("Browse", " find the path to save file");
        browseButton.addActionListener(this);

        add(new JLabel(title, SwingConstants.RIGHT));
        add(fileField);
        add(browseButton);

    }

    public BrowsePanel() {
        this("filepath:", pathStr, "", null, false);
    }

    public BrowsePanel(String title, boolean flag) {
        this(title, pathStr, "", null, flag);
    }

    public BrowsePanel(String[] exts, boolean flag) {
        this("filepath:", pathStr, "", exts, flag);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        String choice = ae.getActionCommand();

        if (choice.equals("Browse")) browseDir();
        else System.err.println("Error input");
    }

    public void setBrowseEnabled(boolean enable) {
        fileField.setEditable(enable);
        browseButton.setEnabled(enable);
        if (!enable) fileField.setText("");
        this.validate();
    }

    public boolean getReadFile(File[] files) {
        String fstr = fileField.getText().trim();
        files[0] = null;
        try {
            File file = new File(fstr);

            if (!file.exists() || !file.canRead()) {
                warn("File can not read, reset");
                return false;
            }

            files[0] = file;
            pathStr = FileOpt.getParent(file).getAbsolutePath();
            return true;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            warn("File can not read, reset");
            return false;
        }
    }

    public boolean getWriteFile(File[] files) {
        String fstr = fileField.getText().trim();
        File file = new File(fstr);
        files[0] = null;
        if (file.exists()) {
            if (file.isDirectory()) {
                warn("File is Directory, cannot write, reset");
                return false;
            }
            if (file.isFile()) {
                if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "File " + fstr + " already exists. Do you want to overwrite the file?", "Overwrite:", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
                    files[0] = file;
                    pathStr = FileOpt.getParent(file).getAbsolutePath();
                    return true;
                } else {
                    InfoPanel.message("Please specify another file name.");
                    return false;
                }
            } else return false;
        }

        if (FileOpt.getParent(file).canWrite()) {
            files[0] = file;
            return true;
        } else {
            warn("Parent directory does not allow to create new file, reset");
            return false;
        }
    }

    public boolean getWriteDir(File[] files) {
        String fstr = fileField.getText().trim();
        File file = new File(fstr);
        files[0] = null;

        if (file.exists()) {
            if (file.isFile()) {
                warn("It is not a directory, reset");
                return false;
            }
            if (file.isDirectory()) {
                if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(this, "Overwrite the directory or not ?", "Overwrite:", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)) {
                    files[0] = file;
                    pathStr = FileOpt.getParent(file).getAbsolutePath();
                    return true;
                } else {
                    warn("Directory cannot overwrite, reset");
                    return false;
                }
            } else return false;
        }

        if (FileOpt.getParent(file).canWrite()) {
            file.mkdir();
            files[0] = file;
            pathStr = FileOpt.getParent(file).getAbsolutePath();
            return true;
        } else {
            warn("Parent directory does not allow to create new directory, reset");
            return false;
        }
    }

    public void setPath(String pathname) {
        fileField.setText(pathname);
    }

    public String getPath() {
        return fileField.getText().trim();
    }

    public void browseDir() {
        JFileChooser fc = new JFileChooser(pathStr);
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        javax.swing.filechooser.FileFilter filter;
        if (exts != null) {
            filter = new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    if (f.isDirectory()) {
                        for (int i = 0; i < exts.length; i++)
                            if (exts[i].equals(".")) return true;
                    } else {
                        for (int i = 0; i < exts.length; i++)
                            if (f.getName().toLowerCase().endsWith(exts[i])) return true;
                    }
                    return false;
                }

                @Override
                public String getDescription() {
                    return null;
                }

            };

            fc.setFileFilter(filter);
        }

        File file = null;
        try {
            int option = fc.showOpenDialog(this);

            if (option == JFileChooser.APPROVE_OPTION) {
                file = fc.getSelectedFile();
                fileField.setText(file.getAbsolutePath());
                if (file.isFile()) {
                    pathStr = file.getParent();
                } else {
                    pathStr = file.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     *set browser button enable
     **/
    @Override
    public void setEnabled(boolean flag) {
        fileField.setEditable(flag);
        browseButton.setEnabled(flag);
    }

    protected void warn(String mesg) {
        JOptionPane.showMessageDialog(this, "Unexpected Error" + mesg, "Warning", JOptionPane.WARNING_MESSAGE);
    }
}

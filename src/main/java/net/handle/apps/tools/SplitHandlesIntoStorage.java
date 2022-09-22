/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.cnri.util.StreamTable;
import net.handle.server.*;
import net.handle.hdllib.*;
import java.io.*;
import java.util.*;

/**
 * SplitHandlesIntoStorage is a utility program that reads handles and
 * sorts them into a set of databases that make up a handle service site.
 * The database into which each handle is stored depends on the slot
 * to which the handle is hashed.
 */
public class SplitHandlesIntoStorage {
    public static final String ENCODING = "UTF8";
    public static final String AUTH_STR = "AUTHENTICATE";
    public static final String SECKEY_STR = "SECKEY";
    public static final String PUBKEY_STR = "PUBKEY";
    public static final String CREATE_STR = "CREATE";
    public static final String DELETE_STR = "DELETE";
    public static final String ADD_STR = "ADD";
    public static final String MODIFY_STR = "MODIFY";
    public static final String REMOVE_STR = "REMOVE";
    public static final String HOME_STR = "HOME";
    public static final String UNHOME_STR = "UNHOME";
    public static final String SEPA_STR = " ";
    public static final String NEW_LINE = "\n";
    public static final String ADMIN_STR = "ADMIN";
    public static final String FILE_STR = "FILE";
    public static final String LIST_STR = "LIST";

    private HandleStorage destDBs[];
    private BufferedReader batchReader;
    private final PrintStream log = System.err;
    private int numDBs;
    private int lineNum = 0;

    public static void main(String argv[]) throws Exception {
        if (argv.length < 3) {
            System.err.println("usage: java net.handle.apps.tools.SplitHandlesIntoStorage <sourcefile> <destdir1> <destdir2> ...");
            System.err.println("If <sourcefile> is '-' then the handles are read from STDIN");
            return;
        }

        SplitHandlesIntoStorage splitServer = new SplitHandlesIntoStorage();
        try {
            splitServer.init(argv);
            splitServer.doit();
        } finally {
            splitServer.cleanup();
        }
    }

    public SplitHandlesIntoStorage() {
    }

    public void init(String argv[]) throws Exception {
        String origFileStr = argv[0];
        InputStream in = origFileStr.equals("-") ? System.in : new FileInputStream(origFileStr);
        batchReader = new BufferedReader(new InputStreamReader(in, "UTF8"));

        numDBs = argv.length - 1;
        destDBs = new HandleStorage[numDBs];
        for (int i = 0; i < destDBs.length; i++) {
            String destDirStr = argv[i] + 1;
            log.println("loading dest db: " + destDirStr);
            File destDir = new File(destDirStr);
            StreamTable config = new StreamTable();
            try {
                config.readFromFile(new File(destDir, "config.dct"));
            } catch (Exception e) {
                log.println("Error reading config.dct file from directory: " + destDir.getAbsolutePath());
                throw e;
            }
            if (config.containsKey("server_config")) {
                destDBs[i] = HandleStorageFactory.getStorage(destDir, config, true);
            } else {
                throw new Exception("Invalid config file in directory: " + destDir.getAbsolutePath());
            }
        }
    }

    final void doit() throws Exception {
        String line;
        String command;
        byte handle[];
        while (true) {
            line = batchReader.readLine();
            lineNum++;
            if (line == null) break;
            if (line.trim().length() <= 0) continue; // skip blank lines

            int spaceIdx = line.indexOf(' ');
            if (spaceIdx < 0) {
                throw new Exception("Invalid batch command line: '" + line + "'");
            }

            command = line.substring(0, spaceIdx);
            handle = Util.encodeString(line.substring(spaceIdx + 1).trim());

            if (command.equalsIgnoreCase("create")) {
                HandleValue values[] = readHandleValueArray();
                int serverNum = SiteInfo.determineServerNum(handle, SiteInfo.HASH_TYPE_BY_ALL, destDBs.length);
                destDBs[serverNum].createHandle(handle, values);
            }
        }
    }

    void cleanup() {
        for (int i = 0; destDBs != null && i < destDBs.length; i++) {
            try {
                if (destDBs[i] != null) destDBs[i].shutdown();
            } catch (Throwable t) {
                log.println("Error shutting down server " + i + " (starting with zero): " + t);
            }
        }
    }

    /*
     * <handle_values> ::= <handle_value>* <blank_line>
     * <handle_value> ::= <index> <sepa> <value_type> <sepa> <value_ttl> <sepa>
     *                    <value_permissions> <sepa> <value_data>
     * <value_type> ::= <string>
     * <value_ttl> ::= <integer>
     * <value_permissions> ::= <admin_read> <admin_write> <public_read> <public_write>
     * <value_data> ::= ['ADMIN' <sepa> <admin_record>] | ['UTF8' <sepa> <string>] |
     *                  ['LIST' <sepa> <reference_list>] | ['FILE' <sepa> <file_path>]
     * <file_path> ::= <string>
     * <admin_record> ::= <index> ':' <admin_permissions> ':' <handle>
     * <admin_permissions> ::= <add_handle> <delete_handle> <add_na_handle> <delete_na_handle>
     *                         <modify_value> <remove_value> <add_value> <read_value>
     *                         <modify_admin> <remove_admin> <add_admin> <list_handles>
     * <reference_list> ::= <reference>*
     * <reference> ::= <index> ':' <handle> ';'
     */
    private HandleValue[] readHandleValueArray() {
        Vector<HandleValue> vt = new Vector<>();
        try {
            while (true) {
                //read line
                String line = batchReader.readLine();
                if (line == null) break;
                line.trim();
                if (line.length() <= 0) break;

                //process line
                HandleValue hv = readHandleValue(line);
                if (hv == null) return null;
                else vt.addElement(hv);
            }

            if (vt.size() < 1) return null;
            HandleValue[] values = new HandleValue[vt.size()];
            for (int i = 0; i < vt.size(); i++)
                values[i] = vt.elementAt(i);

            return values;
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: " + e.getMessage());
            return null;
        }
    }

    private HandleValue readHandleValue(String line) {
        try {
            StringTokenizer st = new StringTokenizer(line, SEPA_STR);
            HandleValue hv = new HandleValue();

            //read index
            try {
                hv.setIndex(Integer.parseInt(st.nextToken().trim()));
            } catch (Exception e) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value index string");
                return null;
            }
            //read type
            hv.setType(Util.encodeString(st.nextToken().trim()));

            //read ttl
            try {
                hv.setTTL(Integer.parseInt(st.nextToken().trim()));
            } catch (Exception e) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value ttl string");
                return null;
            }

            // read permission
            String substr = st.nextToken().trim();
            if (substr.length() < 4) {
                log.println("==>INVALID[" + lineNum + "]: error in handle value permission string");
                return null;
            } else {
                hv.setAdminCanRead(substr.charAt(0) == '1');
                hv.setAdminCanWrite(substr.charAt(1) == '1');
                hv.setAnyoneCanRead(substr.charAt(2) == '1');
                hv.setAnyoneCanWrite(substr.charAt(3) == '1');
            }

            // read handle value data
            substr = st.nextToken().trim().toUpperCase();

            if (substr.equals(ENCODING)) { // UTF8

                hv.setData(Util.encodeString(st.nextToken(NEW_LINE).trim()));

            } else if (substr.equals(ADMIN_STR)) { //ADMIN
                AdminRecord record = new AdminRecord();
                //read admin_index
                try {
                    record.adminIdIndex = Integer.parseInt(st.nextToken(":").trim());
                } catch (Exception e) {
                    log.println("==>INVALID[" + lineNum + "]: error in admin index string");
                    return null;
                }
                //read admin_permissions
                substr = st.nextToken(":").trim();
                if (substr.length() != 12) {
                    log.println("==>INVALID[" + lineNum + "]: error in admin permission string");
                    return null;
                }
                for (int i = 0; i < substr.length(); i++)
                    if (substr.charAt(i) == '1') record.perms[i] = true;
                    else record.perms[i] = false;
                //read admin_handle
                substr = st.nextToken(NEW_LINE);
                record.adminId = Util.encodeString(substr.substring(1).trim());
                hv.setData(Encoder.encodeAdminRecord(record));

            } else if (substr.equals(FILE_STR)) { //FILE
                String filename = st.nextToken(NEW_LINE).trim();
                File file = new File(filename);
                if (!file.exists() || !file.canRead()) {
                    log.println("==>INVALID[" + lineNum + "]: error public key file: " + filename);
                    return null;
                }

                InputStream in = null;
                byte[] rawKey = new byte[(int) file.length()];
                in = new FileInputStream(file);
                int n = 0;
                int r = 0;
                while (n < rawKey.length && (r = in.read(rawKey, n, rawKey.length - n)) > 0)
                    n += r;
                in.close();
                hv.setData(rawKey);
            } else if (substr.equals(LIST_STR)) { //LIST
                int index;
                String handleStr;
                Vector<ValueReference> vt = new Vector<>();

                substr = st.nextToken(NEW_LINE).trim();
                StringTokenizer stt = new StringTokenizer(substr, ";");
                while (stt.hasMoreTokens()) {
                    try {
                        index = Integer.parseInt(stt.nextToken(":").trim());
                    } catch (Exception e) {
                        log.println("==>INVALID[" + lineNum + "]: error in admin index string");
                        return null;
                    }

                    handleStr = stt.nextToken(";");
                    handleStr = handleStr.substring(1).trim();
                    if (handleStr == null || handleStr.length() <= 0) {
                        log.println("==>INVALID[" + lineNum + "]: error admin handle string");
                        return null;
                    }

                    vt.addElement(new ValueReference(Util.encodeString(handleStr), index));
                }

                if (vt.size() < 1) return null;
                ValueReference[] refs = new ValueReference[vt.size()];
                for (int j = 0; j < refs.length; j++)
                    refs[j] = vt.elementAt(j);
                hv.setData(Encoder.encodeValueReferenceList(refs));
            } else { //OTHER
                log.println("==>INVALID[" + lineNum + "]: error in handle data type string: '" + substr + "'");
                return null;
            }

            return hv;
        } catch (Exception e) {
            log.println("==>INVALID[" + lineNum + "]: " + e.getMessage());
            return null;
        }
    }

}

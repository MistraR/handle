/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;

import java.io.Closeable;
import java.sql.*;
import java.util.*;

/*************************************************************
 * Class that provides a storage mechanism for handle records
 * using an SQL database that is accessed via JDBC
 *************************************************************/
public class SQLHandleStorage implements HandleStorage {
    private static final String SQL_URL = "sql_url";
    private static final String SQL_LOGIN = "sql_login";
    private static final String SQL_PASSWD = "sql_passwd";
    private static final String SQL_DRIVER_CLASS = "sql_driver";
    private static final String SQL_READ_ONLY = "sql_read_only";

    private static final long CONN_LIFE_TIME = 6 * 60 * 60 * 1000; // 6 hours
    private static final long MAX_OPS_PER_CONN = 50000; // max # of queries before reconnect

    private String databaseURL;
    private String username;
    private String passwd;
    private boolean readOnly = false;
    private boolean compensateForOracleJDBCBug = false;
    private boolean storeHandleAsString = false;
    private boolean storeNaAsString = false;
    private boolean storeHandleValueTypeAsString = false;
    private boolean traceSql = false;

    private Connection sqlConnection = null;
    private long lastConnectTime = 0;
    private long numOperations = 0;

    private PreparedStatement haveNAStatement = null;
    private PreparedStatement addNAStatement = null;
    private PreparedStatement delNAStatement = null;
    private PreparedStatement createHandleStatement = null;
    private PreparedStatement getHandleStatement = null;
    private PreparedStatement handleExistsStatement = null;
    private PreparedStatement deleteHandleStatement = null;
    private PreparedStatement modifyValueStatement = null;

    private String HAVE_NA_STMT = "select count(*) from nas where na = ?";
    private String DEL_NA_STMT = "delete from nas where na = ?";
    private String ADD_NA_STMT = "insert into nas ( na ) values ( ? )";
    private String SCAN_HANDLES_STMT = "select distinct handle from handles order by handle";
    private String SCAN_HANDLES_FROM_STMT = "select distinct handle from handles where handle >= ? order by handle";
    private String SCAN_BYPREFIX_STMT = "select distinct handle from handles where handle like ?";
    private String SCAN_NAS_STMT = "select distinct na from nas order by na";
    private String SCAN_NAS_FROM_STMT = "select distinct na from nas where na >= ? order by na";
    private String DELETE_ALL_HDLS_STMT = "delete from handles";
    private String DELETE_ALL_NAS_STMT = "delete from nas";
    private String CREATE_HDL_STMT = "insert into handles ( handle, idx, type, data, ttl_type, ttl, " + "timestamp, refs, admin_read, admin_write, pub_read, pub_write) values " + "( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private String GET_HDL_STMT = "select idx, type, data, ttl_type, ttl, timestamp, refs, admin_read, " + "admin_write, pub_read, pub_write from handles where handle = ?";
    private String HDL_EXISTS_STMT = "select count(*) from handles where handle = ?";
    private String DELETE_HDL_STMT = "delete from handles where handle = ?";
    private String MOD_VALUE_STMT = "update handles set type = ?, data = ?, ttl_type = ?, ttl = ?, " + "timestamp = ?, refs = ?, admin_read = ?, admin_write = ?, pub_read = ?, " + "pub_write = ? where handle = ? and idx = ?";

    private static final String CFG_HAVE_NA_STMT = "have_na_stmt";
    private static final String CFG_DEL_NA_STMT = "del_na_stmt";
    private static final String CFG_ADD_NA_STMT = "add_na_stmt";
    private static final String CFG_SCAN_HANDLES_STMT = "scan_handles_stmt";
    private static final String CFG_SCAN_HANDLES_FROM_STMT = "scan_handles_from_stmt";
    private static final String CFG_SCAN_BYPREFIX_STMT = "scan_by_prefix_stmt";
    private static final String CFG_SCAN_NAS_STMT = "scan_nas_stmt";
    private static final String CFG_SCAN_NAS_FROM_STMT = "scan_nas_from_stmt";
    private static final String CFG_DELETE_ALL_HDLS_STMT = "delete_all_handles_stmt";
    private static final String CFG_DELETE_ALL_NAS_STMT = "delete_all_nas_stmt";
    private static final String CFG_CREATE_HDL_STMT = "create_handle_stmt";
    private static final String CFG_GET_HDL_STMT = "get_handle_stmt";
    private static final String CFG_HDL_EXISTS_STMT = "handle_exists_stmt";
    private static final String CFG_DELETE_HDL_STMT = "delete_handle_stmt";
    private static final String CFG_MOD_VALUE_STMT = "modify_value_stmt";
    private static final String CFG_FIX_ORACLE_BUG = "compensate_for_oracle_jdbc_bug";
    private static final String CFG_STORE_HANDLE_AS_STRING = "store_handle_as_string";
    private static final String CFG_STORE_NA_AS_STRING = "store_na_as_string";
    private static final String CFG_STORE_HANDLE_VALUE_TYPE_AS_STRING = "store_handle_value_type_as_string";
    private static final String CFG_TRACE_SQL = "trace_sql";

    public SQLHandleStorage() {
    }

    /** Initialize the SQL storage object with the given settings. */
    @Override
    public void init(StreamTable config) throws Exception {
        // load the SQL driver, if configured...
        if (config.containsKey(SQL_DRIVER_CLASS)) {
            Class.forName(String.valueOf(config.get(SQL_DRIVER_CLASS)));
        }

        // get the database URL and other connection parameters
        this.databaseURL = (String) config.get(SQL_URL);
        this.username = (String) config.get(SQL_LOGIN);
        this.passwd = (String) config.get(SQL_PASSWD);

        // is this back-end operating in read-only mode
        this.readOnly = config.getBoolean(SQL_READ_ONLY, false);

        // compensate for behavior in Oracle JDBC driver that returns
        // a different value for getBytes() than was inserted using setBytes()
        this.compensateForOracleJDBCBug = config.getBoolean(CFG_FIX_ORACLE_BUG, false);

        this.storeHandleAsString = config.getBoolean(CFG_STORE_HANDLE_AS_STRING, false);
        this.storeNaAsString = config.getBoolean(CFG_STORE_NA_AS_STRING, false);
        this.storeHandleValueTypeAsString = config.getBoolean(CFG_STORE_HANDLE_VALUE_TYPE_AS_STRING, false);

        this.traceSql = config.getBoolean(CFG_TRACE_SQL, false);

        if (traceSql) {
            System.err.println("SQL URL: " + databaseURL + ", username " + username);
            StringBuilder sb = new StringBuilder("SQL config options: ");
            if (readOnly) sb.append("readOnly ");
            if (compensateForOracleJDBCBug) sb.append("compensateForOracleJDBCBug ");
            if (storeHandleAsString) sb.append("storeHandleAsString ");
            if (storeNaAsString) sb.append("storeNaAsString ");
            if (storeHandleValueTypeAsString) sb.append("storeHandleValueTypeAsString ");
            System.err.println(sb);
        }

        // allow the config file to override any of the SQL statements
        HAVE_NA_STMT = config.getStr(CFG_HAVE_NA_STMT, HAVE_NA_STMT);
        DEL_NA_STMT = config.getStr(CFG_DEL_NA_STMT, DEL_NA_STMT);
        ADD_NA_STMT = config.getStr(CFG_ADD_NA_STMT, ADD_NA_STMT);
        SCAN_HANDLES_STMT = config.getStr(CFG_SCAN_HANDLES_STMT, SCAN_HANDLES_STMT);
        SCAN_HANDLES_FROM_STMT = config.getStr(CFG_SCAN_HANDLES_FROM_STMT, SCAN_HANDLES_FROM_STMT);
        SCAN_BYPREFIX_STMT = config.getStr(CFG_SCAN_BYPREFIX_STMT, SCAN_BYPREFIX_STMT);
        SCAN_NAS_STMT = config.getStr(CFG_SCAN_NAS_STMT, SCAN_NAS_STMT);
        SCAN_NAS_FROM_STMT = config.getStr(CFG_SCAN_NAS_FROM_STMT, SCAN_NAS_FROM_STMT);
        DELETE_ALL_HDLS_STMT = config.getStr(CFG_DELETE_ALL_HDLS_STMT, DELETE_ALL_HDLS_STMT);
        DELETE_ALL_NAS_STMT = config.getStr(CFG_DELETE_ALL_NAS_STMT, DELETE_ALL_NAS_STMT);
        CREATE_HDL_STMT = config.getStr(CFG_CREATE_HDL_STMT, CREATE_HDL_STMT);
        GET_HDL_STMT = config.getStr(CFG_GET_HDL_STMT, GET_HDL_STMT);
        HDL_EXISTS_STMT = config.getStr(CFG_HDL_EXISTS_STMT, HDL_EXISTS_STMT);
        DELETE_HDL_STMT = config.getStr(CFG_DELETE_HDL_STMT, DELETE_HDL_STMT);
        MOD_VALUE_STMT = config.getStr(CFG_MOD_VALUE_STMT, MOD_VALUE_STMT);

        ensureOpenSingletonConnection();
    }

    private synchronized void ensureOpenSingletonConnection() throws HandleException {
        long now = System.currentTimeMillis();

        // if the current connection has seen enough action, close it and start a new one
        if (sqlConnection != null && (lastConnectTime < now - CONN_LIFE_TIME || numOperations > MAX_OPS_PER_CONN)) {
            try {
                closePreparedStatements();
                sqlConnection.close();
                sqlConnection = null;
            } catch (Exception e) {
                System.err.println("Error resetting old connection: " + e);
                e.printStackTrace(System.err);
            }
        }

        // if we already have an open connection, use it
        try {
            if (sqlConnection != null && !sqlConnection.isClosed()) {
                return; // sqlConnection;
            }
        } catch (SQLException e) {
            System.err.println(e);
            e.printStackTrace(System.err);
        }

        // at this point, there is no valid open connection

        // if there is a connection, try to clean it up a little
        if (sqlConnection != null) {
            try {
                closePreparedStatements();
                sqlConnection.close();
            } catch (Exception e) {
                System.err.println("Error cleaning up SQL connection: " + e);
                e.printStackTrace(System.err);
            }
            sqlConnection = null;
        }

        try {
            // get a new connection and prepare the statements..
            sqlConnection = getNewConnection();

            lastConnectTime = now;
            numOperations = 0;

            haveNAStatement = sqlConnection.prepareStatement(HAVE_NA_STMT);
            delNAStatement = sqlConnection.prepareStatement(DEL_NA_STMT);
            addNAStatement = sqlConnection.prepareStatement(ADD_NA_STMT);
            createHandleStatement = sqlConnection.prepareStatement(CREATE_HDL_STMT);
            getHandleStatement = sqlConnection.prepareStatement(GET_HDL_STMT);
            handleExistsStatement = sqlConnection.prepareStatement(HDL_EXISTS_STMT);
            deleteHandleStatement = sqlConnection.prepareStatement(DELETE_HDL_STMT);
            modifyValueStatement = sqlConnection.prepareStatement(MOD_VALUE_STMT);
        } catch (SQLException e) {
            SQLException curr = e;
            while (curr != null) {
                System.err.println("Got SQL Exception " + curr);
                curr.printStackTrace();
                curr = curr.getNextException();
            }
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error connecting", e);
        } catch (Exception e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unable to setup sql connection", e);
        }
        return; // sqlConnection;
    }

    /*********************************************************************
     * Close the database and clean up
     *********************************************************************/
    @Override
    public synchronized void shutdown() {
        closeSingletonConnection();
    }

    private synchronized void closeSingletonConnection() {
        if (sqlConnection != null) {
            try {
                closePreparedStatements();
                sqlConnection.close();
            } catch (Throwable t) {
            }
            sqlConnection = null;
        }
    }

    private synchronized void closePreparedStatements() {
        closeQuietly(haveNAStatement);
        closeQuietly(delNAStatement);
        closeQuietly(addNAStatement);
        closeQuietly(createHandleStatement);
        closeQuietly(getHandleStatement);
        closeQuietly(handleExistsStatement);
        closeQuietly(deleteHandleStatement);
        closeQuietly(modifyValueStatement);
    }

    private void closeQuietly(PreparedStatement p) {
        if (p != null) try { p.close(); } catch (Exception e) { }
    }

    private Connection getNewConnection() throws SQLException {
        return DriverManager.getConnection(databaseURL, username, passwd);
    }

    /*********************************************************************
     * Returns true if this server is responsible for the given prefix.
     *********************************************************************/
    @Override
    public synchronized boolean haveNA(byte authHandle[]) throws HandleException {
        ensureOpenSingletonConnection();
        ResultSet results = null;
        try {
            authHandle = Util.upperCase(authHandle);
            setNa(haveNAStatement, 1, authHandle);
            if (traceSql) {
                System.err.println("SQL: " + HAVE_NA_STMT + " " + Util.decodeString(authHandle));
            }
            results = haveNAStatement.executeQuery();
            numOperations++;
            return results.next() && results.getInt(1) > 0;
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error accessing NA data", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) { }
        }
    }

    /*********************************************************************
     * Sets a flag indicating whether or not this server is responsible
     * for the given prefix
     *********************************************************************/
    @Override
    public synchronized void setHaveNA(byte authHandle[], boolean flag) throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }

        ensureOpenSingletonConnection();

        boolean currentlyHaveIt = haveNA(authHandle);
        if (currentlyHaveIt == flag) {
            return;
        }

        try {
            authHandle = Util.upperCase(authHandle);
            if (currentlyHaveIt) { // we already have it but need to remove it
                setNa(delNAStatement, 1, authHandle);
                if (traceSql) {
                    System.err.println("SQL: " + DEL_NA_STMT + " " + Util.decodeString(authHandle));
                }
                delNAStatement.executeUpdate();
            } else { // we need to add the NA to the database
                setNa(addNAStatement, 1, authHandle);
                if (traceSql) {
                    System.err.println("SQL: " + ADD_NA_STMT + " " + Util.decodeString(authHandle));
                }
                addNAStatement.executeUpdate();
            }
            numOperations++;
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error accessing NA data", e);
        }
    }

    private synchronized boolean handleExists(byte handle[]) throws HandleException {
        ensureOpenSingletonConnection();
        ResultSet results = null;
        try {
            setHandle(handleExistsStatement, 1, handle);
            if (traceSql) {
                System.err.println("SQL: " + HDL_EXISTS_STMT + " " + Util.decodeString(handle));
            }
            results = handleExistsStatement.executeQuery();
            return results.next() && results.getInt(1) > 0;
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error checking for existing handle", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) {}
        }
    }

    @Override
    public boolean exists(byte[] handle) throws HandleException {
        return handleExists(handle);
    }

    // special encoding used only for references
    private static final String encodeString(String str) {
        int len = str.length();
        StringBuffer sb = new StringBuffer(len + 4);
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch >= 0x7f || ch < 0x20 || ch == '%') {
                sb.append('%');
                sb.append(HEX_VALUES[(ch >> 12) & 0xf]);
                sb.append(HEX_VALUES[(ch >> 8) & 0xf]);
                sb.append(HEX_VALUES[(ch >> 4) & 0xf]);
                sb.append(HEX_VALUES[ch & 0xf]);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static final char HEX_VALUES[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

    private static final char decodeChar(char ch1, char ch2, char ch3, char ch4) {
        int ich1 = (ch1 >= 'a') ? (ch1 - 'a') + 10 : ((ch1 >= 'A') ? (ch1 - 'A') + 10 : (ch1 - '0'));
        int ich2 = (ch2 >= 'a') ? (ch2 - 'a') + 10 : ((ch2 >= 'A') ? (ch2 - 'A') + 10 : (ch2 - '0'));
        int ich3 = (ch3 >= 'a') ? (ch3 - 'a') + 10 : ((ch3 >= 'A') ? (ch3 - 'A') + 10 : (ch3 - '0'));
        int ich4 = (ch4 >= 'a') ? (ch4 - 'a') + 10 : ((ch4 >= 'A') ? (ch4 - 'A') + 10 : (ch4 - '0'));
        return (char) ((ich1 << 12) | (ich2 << 8) | (ich3 << 4) | (ich4));
    }

    private static final String decodeString(String str) {
        int len = str.length();
        StringBuffer sb = new StringBuffer(len);
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == '%' && i < len - 4) {
                char encCh1 = str.charAt(++i);
                char encCh2 = str.charAt(++i);
                char encCh3 = str.charAt(++i);
                char encCh4 = str.charAt(++i);
                sb.append(decodeChar(encCh1, encCh2, encCh3, encCh4));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /*********************************************************************
     * Creates the specified handle in the "database" with the specified
     * initial values
     *********************************************************************/
    @Override
    public synchronized void createHandle(byte handle[], HandleValue values[]) throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }

        ensureOpenSingletonConnection();

        String handleStr = Util.decodeString(handle);

        // if the handle already exists, throw an exception
        if (handleExists(handle)) {
            throw new HandleException(HandleException.HANDLE_ALREADY_EXISTS, handleStr);
        }

        if (values == null) {
            throw new HandleException(HandleException.INVALID_VALUE);
        }

        try {
            try {
                sqlConnection.setAutoCommit(false);
                performCreation(handle, values);
                sqlConnection.commit();
            } catch (Exception sqlExc) {
                sqlConnection.rollback();
                throw sqlExc;
            } finally {
                sqlConnection.setAutoCommit(true);
            }
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error creating handle", e);
        } finally {
            numOperations++;
        }
    }

    private void performCreation(byte[] handle, HandleValue[] values) throws SQLException {
        for (HandleValue val : values) {
            // handle, index, type, data, ttl_type, ttl, timestamp, references,
            // admin_read, admin_write, pub_read, pub_write

            setHandle(createHandleStatement, 1, handle);
            createHandleStatement.setInt(2, val.getIndex());
            if (storeHandleValueTypeAsString) {
                createHandleStatement.setString(3, val.getTypeAsString());
            } else {
                createHandleStatement.setBytes(3, val.getType());
            }
            createHandleStatement.setBytes(4, val.getData());
            createHandleStatement.setByte(5, val.getTTLType());
            createHandleStatement.setInt(6, val.getTTL());
            createHandleStatement.setInt(7, val.getTimestamp());
            StringBuffer sb = new StringBuffer();
            ValueReference refs[] = val.getReferences();
            for (int rv = 0; refs != null && rv < refs.length; rv++) {
                if (rv != 0) {
                    sb.append('\t');
                }
                sb.append(refs[rv].index);
                sb.append(':');
                sb.append(encodeWhitespace(Util.decodeString(refs[rv].handle)));
            }
            createHandleStatement.setString(8, encodeString(sb.toString()));

            createHandleStatement.setBoolean(9, val.getAdminCanRead());
            createHandleStatement.setBoolean(10, val.getAdminCanWrite());
            createHandleStatement.setBoolean(11, val.getAnyoneCanRead());
            createHandleStatement.setBoolean(12, val.getAnyoneCanWrite());
            if (traceSql) {
                System.err.println("SQL: " + CREATE_HDL_STMT + " " + Util.decodeString(handle) + "," + val.getIndex() + "," + val.getTypeAsString() + ",...," + val.getTTLType() + "," + val.getTTL() + "," + val.getTimestamp() + ","
                    + encodeString(sb.toString()) + "," + val.getAdminCanRead() + "," + val.getAdminCanWrite() + "," + val.getAnyoneCanRead() + "," + val.getAnyoneCanWrite());
            }
            createHandleStatement.executeUpdate();
        }
    }

    /* Encode a string for storage... basically remove all tabs so that it
     * doesn't screw up our tab-delimited format */
    public static final String encodeWhitespace(String str) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            switch (ch) {
            case '\t':
                sb.append("\\t");
                break;
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            default:
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static final String decodeWhitespace(String str) {
        int len = str.length();
        int currPos = 0;
        StringBuilder sb = new StringBuilder(str.length());
        while (currPos < len) {
            char ch = str.charAt(currPos);
            if (ch == '\\') {
                currPos++;
                if (currPos >= len) {
                    sb.append(ch);
                    break;
                }
                ch = str.charAt(currPos);
                if (ch == 'n') sb.append('\n');
                else if (ch == 't') sb.append('\t');
                else if (ch == 'r') sb.append('\r');
                else sb.append(ch);
            } else {
                sb.append(ch);
            }
            currPos++;
        }
        return sb.toString();
    }

    /*********************************************************************
     * Delete the specified handle in the database.
     *********************************************************************/
    @Override
    public synchronized boolean deleteHandle(byte handle[]) throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }

        ensureOpenSingletonConnection();

        boolean deleted;
        try {
            deleted = performDeletion(handle);
            numOperations++;
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error deleting handle");
        }
        return deleted;
    }

    private boolean performDeletion(byte[] handle) throws SQLException {
        boolean deleted;
        setHandle(deleteHandleStatement, 1, handle);
        if (traceSql) {
            System.err.println("SQL: " + DELETE_HDL_STMT + " " + Util.decodeString(handle));
        }
        deleted = deleteHandleStatement.executeUpdate() > 0;
        return deleted;
    }

    /*********************************************************************
     * Return the pre-packaged values of the given handle that are either
     * in the indexList or the typeList. This method should return any
     * values of type ALIAS or REDIRECT, even if they were not requested.
     *********************************************************************/
    @Override
    public synchronized byte[][] getRawHandleValues(byte handle[], int indexList[], byte typeList[][]) throws HandleException {
        ensureOpenSingletonConnection();

        ResultSet results = null;
        try {
            setHandle(getHandleStatement, 1, handle);
            if (traceSql) {
                System.err.println("SQL: " + GET_HDL_STMT + " " + Util.decodeString(handle));
            }
            results = getHandleStatement.executeQuery();

            boolean allValues = ((typeList == null || typeList.length == 0) && (indexList == null || indexList.length == 0));

            List<HandleValue> values = new ArrayList<>();

            boolean handleExists = false;
            while (results.next()) {
                handleExists = true;
                HandleValue value = new HandleValue();

                value.setIndex(results.getInt(1));
                if (storeHandleValueTypeAsString) {
                    value.setType(Util.encodeString(results.getString(2)));
                } else {
                    value.setType(getBytesFromResults(results, 2));
                }

                if (allValues) {
                } else if (!Util.isParentTypeInArray(typeList, value.getType()) && !Util.isInArray(indexList, value.getIndex())) {
                    continue;
                }

                value.setData(getBytesFromResults(results, 3));
                value.setTTLType(results.getByte(4));
                value.setTTL(results.getInt(5));
                value.setTimestamp(results.getInt(6));
                String referencesStr = getDecodedStringFromResults(results, 7);

                // parse references...
                String references[];
                if (referencesStr == null) references = new String[0];
                else references = referencesStr.split("\t");
                if (referencesStr != null && referencesStr.length() > 0 && references.length > 0) {
                    ValueReference valReferences[] = new ValueReference[references.length];
                    for (int i = 0; i < references.length; i++) {
                        valReferences[i] = new ValueReference();
                        int colIdx = references[i].indexOf(':');
                        try {
                            valReferences[i].index = Integer.parseInt(references[i].substring(0, colIdx));
                        } catch (Exception t) {
                        }
                        valReferences[i].handle = Util.encodeString(decodeWhitespace(references[i].substring(colIdx + 1)));
                    }
                    value.setReferences(valReferences);
                }

                value.setAdminCanRead(results.getBoolean(8));
                value.setAdminCanWrite(results.getBoolean(9));
                value.setAnyoneCanRead(results.getBoolean(10));
                value.setAnyoneCanWrite(results.getBoolean(11));
                values.add(value);
            }

            numOperations++;

            if (!handleExists) {
                return null;
            }

            byte rawValues[][] = new byte[values.size()][];
            for (int i = 0; i < rawValues.length; i++) {
                HandleValue value = values.get(i);
                rawValues[i] = new byte[Encoder.calcStorageSize(value)];
                Encoder.encodeHandleValue(rawValues[i], 0, value);
            }

            return rawValues;

        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error retrieving handle");
        } finally {
            if (results != null) try { results.close(); } catch (SQLException e) { }
        }
    }

    /*********************************************************************
     * Replace the current values for the given handle with new values.
     *********************************************************************/
    @Override
    public synchronized void updateValue(byte handle[], HandleValue values[]) throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }
        if (!handleExists(handle)) {
            throw new HandleException(HandleException.HANDLE_DOES_NOT_EXIST);
        }
        createOrUpdateRecord(handle, values);
    }

    @Override
    public synchronized void createOrUpdateRecord(byte handle[], HandleValue values[]) throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }
        ensureOpenSingletonConnection();
        try {
            try {
                sqlConnection.setAutoCommit(false);
                performDeletion(handle);
                performCreation(handle, values);
                sqlConnection.commit();
            } catch (Exception sqlExc) {
                sqlConnection.rollback();
                throw sqlExc;
            } finally {
                sqlConnection.setAutoCommit(true);
            }
        } catch (Exception e) {
            // force a reconnect because something is likely wrong with
            // the SQL connection
            closeSingletonConnection();
            throw new HandleException(HandleException.INTERNAL_ERROR, "Error updating handle values", e);
        } finally {
            numOperations++;
        }
    }

    @Override
    public boolean supportsDumpResumption() {
        return true;
    }

    @Override
    public void scanHandlesFrom(byte[] startingPoint, boolean inclusive, ScanCallback callback) throws HandleException {
        Connection connection = null;
        PreparedStatement scanStatement = null;
        ResultSet results = null;
        try {
            connection = getNewConnection();
            scanStatement = connection.prepareStatement(SCAN_HANDLES_FROM_STMT, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            setHandle(scanStatement, 1, startingPoint);
            boolean isMySql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (isMySql) {
                // set to streaming
                scanStatement.setFetchSize(Integer.MIN_VALUE);
            }
            if (traceSql) {
                System.err.println("SQL: " + SCAN_HANDLES_FROM_STMT + " " + Util.decodeString(startingPoint));
            }
            results = scanStatement.executeQuery();
            boolean hasNext = true;
            if (!inclusive) {
                hasNext = results.next();
            }
            if (hasNext) {
                while (results.next()) {
                    byte b[] = getHandleBytesFromResults(results, 1);
                    callback.scanHandle(b);
                }
            }
        } catch (SQLException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) {}
            if (scanStatement != null) try { scanStatement.close(); } catch (Exception e) {}
            if (connection != null) try { connection.close(); } catch (Exception e) {}
        }
    }

    @Override
    public void scanNAsFrom(byte[] startingPoint, boolean inclusive, ScanCallback callback) throws HandleException {
        Connection connection = null;
        PreparedStatement scanStatement = null;
        ResultSet results = null;
        try {
            connection = getNewConnection();
            scanStatement = connection.prepareStatement(SCAN_NAS_FROM_STMT, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            setNa(scanStatement, 1, Util.encodeString(Util.decodeString(startingPoint) + "_%"));
            boolean isMySql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (isMySql) {
                // set to streaming
                scanStatement.setFetchSize(Integer.MIN_VALUE);
            }
            if (traceSql) {
                System.err.println("SQL: " + SCAN_NAS_FROM_STMT + " " + Util.decodeString(startingPoint) + "_%");
            }
            results = scanStatement.executeQuery();
            boolean hasNext = true;
            if (!inclusive) {
                hasNext = results.next();
            }
            if (hasNext) {
                while (results.next()) {
                    byte b[] = getNaBytesFromResults(results, 1);
                    callback.scanHandle(b);
                }
            }
        } catch (SQLException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) {}
            if (scanStatement != null) try { scanStatement.close(); } catch (Exception e) {}
            if (connection != null) try { connection.close(); } catch (Exception e) {}
        }
    }

    /*********************************************************************
     * Scan the database, calling a method in the specified callback for
     * every handle in the database.
     *********************************************************************/
    @Override
    public void scanHandles(ScanCallback callback) throws HandleException {
        Connection connection = null;
        PreparedStatement scanStatement = null;
        ResultSet results = null;
        try {
            connection = getNewConnection();
            scanStatement = connection.prepareStatement(SCAN_HANDLES_STMT, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            boolean isMySql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (isMySql) {
                // set to streaming
                scanStatement.setFetchSize(Integer.MIN_VALUE);
            }
            if (traceSql) {
                System.err.println("SQL: " + SCAN_HANDLES_STMT);
            }
            results = scanStatement.executeQuery();
            while (results.next()) {
                byte b[] = getHandleBytesFromResults(results, 1);
                callback.scanHandle(b);
            }
        } catch (SQLException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) {}
            if (scanStatement != null) try { scanStatement.close(); } catch (Exception e) {}
            if (connection != null) try { connection.close(); } catch (Exception e) {}
        }
    }

    /*********************************************************************
     * Scan the NA database, calling a method in the specified callback for
     * every prefix handle in the database.
     *********************************************************************/
    @Override
    public void scanNAs(ScanCallback callback) throws HandleException {
        Connection connection = null;
        PreparedStatement scanStatement = null;
        ResultSet results = null;
        try {
            connection = getNewConnection();
            scanStatement = connection.prepareStatement(SCAN_NAS_STMT, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            boolean isMySql = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
            if (isMySql) {
                // set to streaming
                scanStatement.setFetchSize(Integer.MIN_VALUE);
            }
            if (traceSql) {
                System.err.println("SQL: " + SCAN_NAS_STMT);
            }
            results = scanStatement.executeQuery();
            while (results.next()) {
                byte b[] = getNaBytesFromResults(results, 1);
                callback.scanHandle(b);
            }
        } catch (SQLException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
        } finally {
            if (results != null) try { results.close(); } catch (Exception e) {}
            if (scanStatement != null) try { scanStatement.close(); } catch (Exception e) {}
            if (connection != null) try { connection.close(); } catch (Exception e) {}
        }
    }

    /*********************************************************************
     * Scan the database for handles with the given prefix
     * and return an Enumeration of byte arrays with each byte array
     * being a handle. <i>naHdl</i> is the prefix handle
     * for the prefix that you want to list the handles for.
     *********************************************************************/
    @Override
    public Enumeration<byte[]> getHandlesForNA(byte naHdl[]) throws HandleException {
        if (!haveNA(naHdl)) {
            throw new HandleException(HandleException.INVALID_VALUE, "The requested prefix doesn't live here");
        }
        boolean isZeroNA = Util.startsWithCI(naHdl, Common.NA_HANDLE_PREFIX);
        if (isZeroNA) {
            naHdl = Util.getSuffixPart(naHdl);
        }
        return new ListHdlsEnum(naHdl);
    }

    /*********************************************************************
     * Remove all of the records from the database.
     ********************************************************************/
    @Override
    public void deleteAllRecords() throws HandleException {
        if (readOnly) {
            throw new HandleException(HandleException.STORAGE_RDONLY, "Server is read-only");
        }
        Connection connection = null;
        Statement statement = null;
        try {
            connection = getNewConnection();
            statement = connection.createStatement();
            if (traceSql) {
                System.err.println("SQL: " + DELETE_ALL_HDLS_STMT);
            }
            statement.executeUpdate(DELETE_ALL_HDLS_STMT);
            if (traceSql) {
                System.err.println("SQL: " + DELETE_ALL_NAS_STMT);
            }
            statement.executeUpdate(DELETE_ALL_NAS_STMT);
        } catch (SQLException e) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
        } finally {
            if (statement != null) try { statement.close(); } catch (Exception e) {}
            if (connection != null) try { connection.close(); } catch (Exception e) {}
        }
    }

    @Override
    public void checkpointDatabase() throws HandleException {
        throw new HandleException(HandleException.SERVER_ERROR, "Checkpoint not supported in this storage type");
    }

    /**
     * Helpers to compensate for some SQL providers' tendency to return null for empty strings
     **/
    private final String getDecodedStringFromResults(ResultSet results, int i) throws SQLException {
        String s = results.getString(i);
        return s == null ? "" : decodeString(s);
    }

    private void setHandle(PreparedStatement statement, int i, byte[] x) throws SQLException {
        if (storeHandleAsString) {
            statement.setString(i, Util.decodeString(x));
        } else {
            statement.setBytes(i, x);
        }
    }

    private void setNa(PreparedStatement statement, int i, byte[] x) throws SQLException {
        if (storeNaAsString) {
            statement.setString(i, Util.decodeString(x));
        } else {
            statement.setBytes(i, x);
        }
    }

    private final byte[] getBytesFromResults(ResultSet results, int i) throws SQLException {
        if (compensateForOracleJDBCBug) {
            String s = results.getString(i);
            if (s == null) {
                return new byte[0];
            }
            return Util.encodeHexString(s);
        }
        byte b[] = results.getBytes(i);
        return (b == null) ? new byte[0] : b;
    }

    private final byte[] getHandleBytesFromResults(ResultSet results, int i) throws SQLException {
        if (storeHandleAsString) {
            String s = results.getString(i);
            if (s == null) {
                return new byte[0];
            }
            return Util.encodeString(s);
        } else {
            return getBytesFromResults(results, i);
        }
    }

    private final byte[] getNaBytesFromResults(ResultSet results, int i) throws SQLException {
        if (storeNaAsString) {
            String s = results.getString(i);
            if (s == null) {
                return new byte[0];
            }
            return Util.encodeString(s);
        } else {
            return getBytesFromResults(results, i);
        }
    }

    private class ListHdlsEnum implements Enumeration<byte[]>, Closeable {
        private final Connection connection;
        private final byte[] prefix;
        private final PreparedStatement scanStatement;
        private final ResultSet results;
        private byte[] nextVal = null;
        private boolean isClosed = false;
        private final boolean listingDerivedPrefixes;

        ListHdlsEnum(byte[] prefix) throws HandleException {
            this.prefix = prefix;
            this.listingDerivedPrefixes = Util.startsWithCI(prefix, Common.NA_HANDLE_PREFIX);
            try {
                connection = getNewConnection();
                scanStatement = connection.prepareStatement(SCAN_BYPREFIX_STMT);
                String suffix = listingDerivedPrefixes ? ".%" : "/%";
                setHandle(scanStatement, 1, Util.encodeString(Util.decodeString(prefix) + suffix));
                if (traceSql) {
                    System.err.println("SQL: " + SCAN_BYPREFIX_STMT + " " + (Util.decodeString(prefix) + suffix));
                }
                results = scanStatement.executeQuery();
            } catch (SQLException e) {
                close();
                throw new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e);
            }
            getNextValue();
        }

        @Override
        public void close() {
            isClosed = true;
            if (results != null) try { results.close(); } catch (Exception e) { }
            if (scanStatement != null) try { scanStatement.close(); } catch (Exception e) { }
            if (connection != null) try { connection.close(); } catch (Exception e) { }
        }

        @Override
        public boolean hasMoreElements() {
            return nextVal != null;
        }

        @Override
        public byte[] nextElement() {
            byte[] returnVal = nextVal;
            if (returnVal != null) {
                getNextValue();
            }
            return returnVal;
        }

        private void getNextValue() {
            nextVal = null;
            if (isClosed) {
                return;
            }
            try {
                if (results.next()) {
                    byte[] candNextVal = getHandleBytesFromResults(results, 1);
                    if (listingDerivedPrefixes ? candNextVal[prefix.length] == (byte) '.' : candNextVal[prefix.length] == (byte) '/') {
                        nextVal = candNextVal;
                    } else {
                        getNextValue();
                    }
                } else {
                    // no more values... close it up..
                    close();
                }
            } catch (Exception e) {
                close();
                throw new RuntimeException(new HandleException(HandleException.INTERNAL_ERROR, "SQL Error", e));
            }
        }
    }

}

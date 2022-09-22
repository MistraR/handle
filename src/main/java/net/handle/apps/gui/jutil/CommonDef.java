/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jutil;

import net.handle.hdllib.*;

public class CommonDef {

    public final static String ROOT_HANDLE = Util.decodeString(Common.ROOT_HANDLE);
    public final static int DPORT = 2641;
    //File
    public final static String HELP_DIR = "/net/handle/apps/gui/help/en_en";
    public final static String INDEX_FILE = "intro.html";
    public final static String HELP_CREATE_HANDLE = "createhandle.html";
    public final static String HELP_MODIFY_HANDLE = "modifyhandle.html";
    public final static String HELP_REMOVE_HANDLE = "removehandle.html";
    public final static String HELP_QUERY_HANDLE = "queryhandle.html";
    public final static String HELP_HOME_NA = "home_na.html";
    public final static String HELP_UNHOME_NA = "unhome_na.html";
    public final static String HELP_BATCH_HANDLE = "batchhandle.html";
    public final static String HELP_LIST_HANDLES = "listhandle.html";
    public final static String HELP_BACKUP = "backup.html";
    public final static String HELP_SESSION_SETUP = "sessionsetup.html";
    public final static String HELP_GEN_KEY_PAIRS = "gen_keypairs.html";

    public final static String FILE_PUBKEY = "pubkey.bin";
    public final static String FILE_PRIVKEY = "privkey.bin";
    public final static String FILE_REPL_PUBKEY = "replpub.bin";
    public final static String FILE_REPL_PRIVKEY = "replpriv.bin";
    public final static String FILE_SITE = "siteinfo.bin";
    public final static String FILE_DB = "handles.jdb";
    public final static String FILE_NA = "nas.jdb";

    //handle value data type
    public final static String[] DATA_TYPE_STR = { Util.decodeString(Common.STD_TYPE_HSADMIN), Util.decodeString(Common.STD_TYPE_HSSITE), Util.decodeString(Common.STD_TYPE_HSALIAS), Util.decodeString(Common.STD_TYPE_HSVALLIST),
            Util.decodeString(Common.STD_TYPE_HSSECKEY), Util.decodeString(Common.STD_TYPE_HSPUBKEY), Util.decodeString(Common.STD_TYPE_HSSERV), Util.decodeString(Common.STD_TYPE_EMAIL), Util.decodeString(Common.STD_TYPE_URL),
            //Util.decodeString(Common.STD_TYPE_URN),
            //Util.decodeString(Common.STD_TYPE_HOSTNAME)

    };

    //Site, Server, Interface Inforamtion
    public final static String[] INTERFACE_PROTOCOL_STR = { "hdl_udp", "hdl_tcp", "hdl_http", "hdl_https" };

    public final static byte[] INTERFACE_PROTOCOL_TYPE = { Interface.SP_HDL_UDP, Interface.SP_HDL_TCP, Interface.SP_HDL_HTTP, Interface.SP_HDL_HTTPS };

    public final static String[] INTERFACE_ADMIN_STR = { "OUT_OF_SERVICE", "ADMIN", "QUERY", "ADMIN_AND_QUERY" };
    public final static byte[] INTERFACE_ADMIN_TYPE = { Interface.ST_OUT_OF_SERVICE, Interface.ST_ADMIN, Interface.ST_QUERY, Interface.ST_ADMIN_AND_QUERY };
    //handle value header
    public final static String[] HANDLE_VALUE_HEADER = { "Index", "Type", "Data", "Permission", "ttlType", "ttlValue", "Timestamp", "ValueRefs" };
}

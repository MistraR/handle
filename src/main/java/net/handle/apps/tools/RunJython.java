/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.hdllib.*;
import net.handle.apps.admintool.view.AuthWindow;
import org.python.util.PythonInterpreter;
import org.python.util.InteractiveConsole;
import java.io.*;
import java.util.*;

/**
 * This is a simple utility to invoke an operation on an object from the command-line.
 */
public class RunJython {

    private static final void printUsageAndExit() {
        System.err.println("usage: hdl-jython [-a | -authgui | -authpriv:<id>:<index>:<privkeyfile>:<passphrase>] [<scriptfile> ...]");
        System.err.println(" if <scriptfile> is omitted then hdl-jython will read jython commands from stdin.");
        System.err.println(" The 'resolver' variable is initialize to an instance of net.handle.hdllib.Resolver");
        System.err.println("   -h    prints this help message");
        System.err.println("   -v    turns on verbose resolution (tracing)");
        System.err.println(" The following arguments will set the 'auth' variable to an instance of AuthenticationInfo");
        System.err.println("   -auth   display GUI to acquire authentication interactively");
        System.err.println("   -authpriv:id:privkeyfile:passphrase  Use private key authentication using the ");
        System.err.println("           given handle, key index, private key file, and private key passphrase");
        System.exit(1);
    }

    public static void main(String argv[]) throws Exception {
        AuthenticationInfo auth = null;
        ArrayList<String> additionalArgs = new ArrayList<>();
        String filename = null;
        boolean verbose = false;

        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg == null || arg.trim().length() <= 0) continue;
            if (arg.equals("-h")) {
                printUsageAndExit();
            } else if (arg.equals("-v")) {
                verbose = true;
            } else if (auth == null && arg.equals("-authgui")) {
                AuthWindow authWin = new AuthWindow(null, "jython");
                authWin.setVisible(true);
                if (authWin.wasCanceled()) {
                    System.exit(0);
                }
                auth = authWin.getAuthentication();
                authWin.dispose();
            } else if (auth == null && arg.startsWith("-authpriv:")) {
                // load the authentication info from a private key file
                String tokens[] = arg.substring("-authpriv:".length()).split(":");
                if (tokens.length < 3 || tokens.length > 3) {
                    printUsageAndExit();
                    return;
                }
                String authID = tokens[0];
                int keyIndex = Integer.parseInt(tokens[1].trim());
                String privKeyFile = tokens[2];
                String passphrase = tokens.length > 3 ? tokens[3] : null;

                try {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();
                    byte buf[] = new byte[1024];
                    FileInputStream fin = new FileInputStream(privKeyFile);
                    try {
                        int r = 0;
                        while ((r = fin.read(buf)) >= 0) {
                            bout.write(buf, 0, r);
                        }
                    } finally {
                        fin.close();
                    }
                    buf = bout.toByteArray();
                    buf = Util.decrypt(buf, passphrase == null ? null : Util.encodeString(passphrase));
                    auth = new PublicKeyAuthenticationInfo(Util.encodeString(authID), keyIndex, Util.getPrivateKeyFromBytes(buf, 0));
                } catch (Exception e) {
                    System.err.println("Error building authentication: " + e);
                    e.printStackTrace(System.err);
                    printUsageAndExit();
                    return;
                }
            } else if (filename == null) {
                filename = arg;
            } else {
                additionalArgs.add(arg);
            }
        }

        Properties preProperties = System.getProperties();
        Properties postProperties = new Properties();
        postProperties.put("python.cachedir", System.getProperty("user.home", ".") + "/.hdl_jythoncache");

        PythonInterpreter.initialize(preProperties, postProperties, additionalArgs.toArray(new String[0]));

        PythonInterpreter python = filename == null ? new InteractiveConsole() : new PythonInterpreter();
        python.setErr(System.err);
        python.setOut(System.out);

        Resolver resolver = new Resolver();
        resolver.getResolver().traceMessages = verbose;

        // if authentication has been provided, automatically turn on support for
        // authenticated sessions
        if (auth != null) {
            ClientSessionTracker tracker = new ClientSessionTracker();
            tracker.setSessionSetupInfo(new SessionSetupInfo());
            resolver.getResolver().setSessionTracker(tracker);
        }

        python.set("resolver", resolver);
        if (auth != null) python.set("auth", auth);

        if (filename == null) { // process commands from stdin
            InteractiveConsole console = (InteractiveConsole) python;
            String welcome = "Welcome to hdl-jython console. Initialized variable(s): resolver" + (auth == null ? "" : " and auth");
            console.interact(welcome);
        } else {
            python.execfile(filename);
        }
    }

}

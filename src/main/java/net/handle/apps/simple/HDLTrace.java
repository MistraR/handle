/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import net.handle.hdllib.*;

/**
 * A simple command-line handle resolver.
 */

public class HDLTrace {
    private final HandleResolver resolver;

    public HDLTrace(String handle) {

        // First create a HandleResolver object.  This object can be used not
        // only for resolution, but for create/delete/modify operations as well.
        resolver = new HandleResolver();

        // Setting the traceMessages flag to true will cause the resolver to
        // print debugging messages to stdout.  These are useful for watching
        // which servers packets are sent to in the process of resolving a handle.
        resolver.traceMessages = true;

        try {
            // We can now use the HandleResolver object to perform handle operations.
            // To resolve a handle, first create a ResolutionRequest object.  The
            // handle to resolve is the first argument, and must be UTF8 encoded in a
            // byte[].
            //
            // The second argument is a byte[][] of handle value types to resolve.
            // If you wanted to only resolve "URL" values in the handle you could
            // use something like { "URL".getBytes() }; instead of null
            //
            // The third argument is a int[] of indices to retrieve.  If you only
            // wanted the handle value with index 300, you could pass the value
            // { 300 }; instead of null.
            //
            // The fourth argument is for a PublicKeyAuthenticationInfo or
            // PrivateKeyAuthenticationInfo object.  This is necessary to resolve
            // handle values without public read permissions.  Otherwise, just
            // leave null.
            ResolutionRequest req = new ResolutionRequest(handle.getBytes("UTF8"), null, null, null);

            // Finally, call HandleResolver's processRequest method.  This will
            // perform the resolution.  The result is returned as either a
            // ResolutionResponse or ErrorResponse object.  It is important to note
            // that a failed resolution will not throw an exception, only return
            // a ErrorResponse.
            AbstractResponse response = resolver.processRequest(req);

            // The responseCode value for a response indicates the status of the
            // request.  A successful resolution will always return RC_SUCCESS.
            // Failed resolutions could return one of several response codes,
            // including RC_ERROR, RC_SERVER_TOO_BUSY, and RC_HANDLE_NOT_FOUND.
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                System.out.println("\nGot Response: \n" + response);
            } else {
                System.out.println("\nGot Error: \n" + response);
            }
        } catch (Throwable t) {
            // Should only reach here if there is an internal error
            System.err.println("\nError: " + t);
            t.printStackTrace(System.err);
        }
    }

    public static void main(String argv[]) {
        if (argv.length < 1) {
            System.err.println("usage: java net.handle.apps.simple.HDLTrace <handle>");
            System.exit(-1);
        }
        @SuppressWarnings("unused")
        HDLTrace m = new HDLTrace(argv[0]);
    }

}

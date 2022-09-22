/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import net.handle.hdllib.*;
import java.io.*;
import java.security.*;

/**
 * Simple tool for handle creation.
 * Uses public key authentication.
 **/
public class HDLCreate {

    public static void main(String argv[]) {
        if (argv.length != 4) {
            System.err.println("usage: java net.handle.apps.simple.HDLCreate <auth handle> <auth index> <privkey> <handle>");
            System.exit(-1);
        }

        // First we need to read the private key in from disk
        byte[] key = null;
        try {
            File f = new File(argv[2]);
            try (FileInputStream fs = new FileInputStream(f)) {
                key = Util.getBytesFromInputStream(fs);
            }
        } catch (Throwable t) {
            System.err.println("Cannot read private key " + argv[2] + ": " + t);
            System.exit(-1);
        }

        // A HandleResolver object is used not just for resolution, but for
        // all handle operations(including create)
        HandleResolver resolver = new HandleResolver();

        // Check to see if the private key is encrypted.  If so, read in the
        // user's passphrase and decrypt.  Finally, convert the byte[]
        // representation of the private key into a PrivateKey object.
        PrivateKey privkey = null;
        byte secKey[] = null;
        try {
            if (Util.requiresSecretKey(key)) {
                secKey = Util.getPassphrase("passphrase: ");
            }
            key = Util.decrypt(key, secKey);
            privkey = Util.getPrivateKeyFromBytes(key, 0);
        } catch (Throwable t) {
            System.err.println("Can't load private key in " + argv[2] + ": " + t);
            System.exit(-1);
        }

        try {
            // Create a PublicKeyAuthenticationInfo object to pass to HandleResolver.
            // This is constructed with the admin handle, index, and PrivateKey as
            // arguments.
            PublicKeyAuthenticationInfo auth = new PublicKeyAuthenticationInfo(argv[0].getBytes("UTF8"), Integer.valueOf(argv[1]).intValue(), privkey);

            // We don't want to create a handle without an admin value-- otherwise
            // we would be locked out.  Give ourselves all permissions, even
            // ones that only apply for NA handles.
            AdminRecord admin = new AdminRecord(argv[0].getBytes("UTF8"), Integer.valueOf(argv[1]).intValue(), true, true, true, true, true, true, true, true, true, true, true, true);

            // All handle values need a timestamp, so get the current time in
            // seconds since the epoch
            int timestamp = (int) (System.currentTimeMillis() / 1000);

            // Now build the HandleValue object for our admin value.  The first
            // argument is the value's index, 100 in this case.  The second
            // argument is the value's type.  The third argument holds the value's
            // data.  Since this is binary data, not textual data like a URL, we have
            // to encode it first.
            //
            // The other arguments can usually just be copied verbatim from here.
            // The fourth argument indicates whether the time to live for the
            // value is absolute or relative.  The fifth argument is the time to
            // live, 86400 seconds(24 hours) in this case.  The sixth argument is
            // the timestamp we created earlier.  The seventh argument is a
            // ValueReference array.  You will almost always want to leave this
            // null; read the RFC's for more information.  The last four arguments
            // are the permissions for the value: admin read, admin write, public
            // read, and public write.
            //
            // whew!
            HandleValue[] val = { new HandleValue(100, "HS_ADMIN".getBytes("UTF8"), Encoder.encodeAdminRecord(admin), HandleValue.TTL_TYPE_RELATIVE, 86400, timestamp, null, true, true, true, false) };

            // Now we can build our CreateHandleRequest object.  As its first
            // parameter it takes the handle we are going to create.  The second
            // argument is the array of initial values the handle should have.
            // The final argument is the authentication object that should be
            // used to gain permission to perform the creation.
            CreateHandleRequest req = new CreateHandleRequest(argv[3].getBytes("UTF8"), val, auth);

            // Setting this flag lets us watch the request as it is processed.
            resolver.traceMessages = true;

            // Finally, we are ready to send the message.  We do this by calling
            // the processRequest method of the resolver object with the request
            // object as an argument.  The result is returned as either a
            // GenericResponse or ErrorResponse object.  It is important to note that
            // a failed resolution will not throw an exception, only return a
            // ErrorResponse.
            AbstractResponse response = resolver.processRequest(req);

            // The responseCode value for a response indicates the status of
            // the request.  A successful resolution will always return
            // RC_SUCCESS.  Failed resolutions could return one of several
            // response codes, including RC_ERROR, RC_INVALID_ADMIN, and
            // RC_INSUFFICIENT_PERMISSIONS.
            if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                System.out.println("\nGot Response: \n" + response);
            } else {
                System.out.println("\nGot Error: \n" + response);
            }
        } catch (Throwable t) {
            System.err.println("\nError: " + t);
        }
    }

}

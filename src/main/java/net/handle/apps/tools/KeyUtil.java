/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.hdllib.*;
import java.security.*;
import java.io.*;

public class KeyUtil {

    private static final void printUsage() {
        System.err.println("usage: net.handle.apps.tools.KeyUtil <privatekeyfile>");
    }

    public static void main(String argv[]) throws Exception {
        if (argv.length < 1) {
            printUsage();
            return;
        }

        PrivateKey privateKey = null;
        File privateKeyFile = null;
        try {

            // read the private key from the private key file...
            privateKeyFile = new File(argv[0]);
            if (!privateKeyFile.exists() || !privateKeyFile.canRead()) {
                System.err.println("Missing or inaccessible private key file: " + privateKeyFile.getAbsolutePath());
                return;
            }
            byte encKeyBytes[] = new byte[(int) privateKeyFile.length()];
            FileInputStream in = new FileInputStream(privateKeyFile);
            try {
                int n = 0;
                int r;
                while (n < encKeyBytes.length && (r = in.read(encKeyBytes, n, encKeyBytes.length - n)) >= 0) {
                    n += r;
                }
            } finally {
                in.close();
            }

            byte keyBytes[] = null;
            byte secKey[] = null;
            if (Util.requiresSecretKey(encKeyBytes)) { // ask for a secret key to decrypt the private key
                // get the passphrase and decrypt the server's private key
                secKey = Util.getPassphrase("Enter the passphrase for this private key: ");
            }

            keyBytes = Util.decrypt(encKeyBytes, secKey);
            for (int i = 0; secKey != null && i < secKey.length; i++) { // clear the secret key
                secKey[i] = (byte) 0;
            }
            privateKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
            for (int i = 0; i < keyBytes.length; i++) keyBytes[i] = (byte) 0;

        } catch (Exception e) {
            System.err.println("Unable to read private key: " + e);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.println("\nChoose an operation:");
            System.out.println("  1: Encrypt private key with passphrase");
            System.out.println("  2: Save private key without encryption");
            System.out.println("  3: Exit");
            System.out.println("  --------------------------------------------------------------------------");
            System.out.println("  6: Encrypt private key using encryption compatible with version 6 software");
            System.out.println("  7: Encrypt private key using encryption compatible with version 7 software");
            System.out.println("  (only needed if you will use this key with old versions of the software)");
            System.out.flush();
            String line = in.readLine();
            if (line == null) break;
            line = line.trim();
            if (line.equals("1")) {
                encryptKey(privateKey, privateKeyFile, Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
            } else if (line.equals("2")) {
                encryptKey(privateKey, privateKeyFile, Common.ENCRYPT_NONE);
            } else if (line.equals("6")) {
                @SuppressWarnings("deprecation")
                int ancientEncryption = Common.ENCRYPT_DES_ECB_PKCS5;
                encryptKey(privateKey, privateKeyFile, ancientEncryption);
            } else if (line.equals("7")) {
                encryptKey(privateKey, privateKeyFile, Common.ENCRYPT_DES_CBC_PKCS5);
            } else if (line.equals("3")) {
                System.exit(0);
            } else {
                System.out.println("Huh?  Please enter 1, 2, 3, 6, or 7");
            }
        }
    }

    private static void encryptKey(PrivateKey privateKey, File privateKeyFile, int encryptionType) throws Exception {
        byte keyBytes[] = Util.getBytesFromPrivateKey(privateKey);

        // encrypt the private key bytes
        byte encKeyBytes[] = null;
        if (encryptionType == Common.ENCRYPT_NONE) {
            encKeyBytes = Util.encrypt(keyBytes, null, encryptionType);
        } else {
            byte secKey[] = null;
            while (true) {
                // read the passphrase and use it to encrypt the private key
                secKey = Util.getPassphrase("Please enter a new private key passphrase: ");

                byte secKey2[] = Util.getPassphrase("Please re-enter the private key passphrase: ");
                if (!Util.equals(secKey, secKey2)) {
                    System.out.println("Passphrases do not match!  Try again.\n");
                } else {
                    break;
                }
            }

            encKeyBytes = Util.encrypt(keyBytes, secKey, encryptionType);
        }

        // save the private key to a file...
        FileOutputStream out = new FileOutputStream(privateKeyFile);
        out.write(encKeyBytes);
        out.close();
        System.out.println("Private key saved to file: " + privateKeyFile);
    }

}

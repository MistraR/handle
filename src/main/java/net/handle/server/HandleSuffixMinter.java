/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.security.SecureRandom;

import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.Util;

public class HandleSuffixMinter {
    private static final SecureRandom random = new SecureRandom();

    private final HandleStorage storage;
    private final boolean caseSensitive;

    public HandleSuffixMinter(HandleStorage storage, boolean caseSensitive) {
        this.storage = storage;
        this.caseSensitive = caseSensitive;
    }

    /**
     * Given an initial portion of a handle this method returns that string plus a random string.
     * Steps are taken to ensure that the resulting handle does not yet exist in storage.
     *
     * Note that handleInitialPortion is not a prefix. It is everything that should come before the random string.
     * e.g. if your prefix is "12345" and you want to have handles of the form "12345/<some random string>" the handleInitialPortion
     * needs to include the slash like this "12345/"
     *
     * You can also include additional chars after the slash like this:
     * "12345/xyz-" and this will result in minted handles of the form "12345/xyz-<some random string>"
     */
    public String mintNextSuffix(String handleInitialPortion) throws HandleException {
        String handle = null;
        boolean foundUniqueSuffix = false;
        while (!foundUniqueSuffix) {
            String handleEndPortion = generateRandomString();
            handle = handleInitialPortion + handleEndPortion;
            foundUniqueSuffix = isHandleUnique(handle);
        }
        return handle;
    }

    private String generateRandomString() {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        randomBytes[6] &= 0x0F;
        randomBytes[6] |= 0x40;
        randomBytes[8] &= 0x3F;
        randomBytes[8] |= 0x80;
        return bytesToHex(randomBytes);
    }

    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[36];
        int i = 0;
        for (int j = 0; j < bytes.length; j++) {
            if (j == 4 || j == 6 || j == 8 || j == 10) hexChars[i++] = '-';
            int v = bytes[j] & 0xFF;
            hexChars[i++] = hexArray[v >>> 4];
            hexChars[i++] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private boolean isHandleUnique(String handle) throws HandleException {
        byte[] handleBytes = Util.encodeString(handle);
        handleBytes = caseSensitive ? handleBytes : Util.upperCase(handleBytes);
        try {
            if (storage.getRawHandleValues(handleBytes, null, null) == null) {
                return true;
            } else {
                return false;
            }
        } catch (HandleException e) {
            if (e.getCode() == HandleException.HANDLE_DOES_NOT_EXIST) return true;
            throw e;
        }
    }
}

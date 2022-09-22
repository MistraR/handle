/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.api;

import net.handle.hdllib.HandleException;

public final class HSAdapterFactory {

    /**
     *
     * @return HSAdapter with no administrative priveleges.
     */
    public static HSAdapter newInstance() {
        return new GenericHSAdapter();
    }

    /**
     *
     * @param adminHandle
     *            The administrative Handle of the user.
     * @param keyIndex
     *            The index at which the public key is present in the
     *            administrative Handle.
     * @param privateKey
     *            The byte array of the private key that matches the public key.
     * @param cipher
     *            The byte array of the cipher used to encrypt the keys. Use
     *            null for unencrypted keys.
     * @return HSAdapter with administrative priveleges based on the private key
     *         provided.
     * @throws HandleException
     *             Thrown when the authentication information is invalid.
     */
    public static HSAdapter newInstance(final String adminHandle, final int keyIndex, final byte[] privateKey, final byte[] cipher) throws HandleException {
        return new GenericHSAdapter(adminHandle, keyIndex, privateKey, cipher);
    }

    /**
     *
     * @param adminHandle
     *            The administrative Handle of the user.
     * @param keyIndex
     *            The index at which the public key is present in the
     *            administrative Handle.
     * @param secretKey
     *            The byte array of the secret key.
     * @return HSAdapter with administrative priveleges based on the secret key
     *         provided.
     * @throws HandleException
     *             Thrown when the authentication information is invalid.
     */
    public static HSAdapter newInstance(final String adminHandle, final int keyIndex, final byte[] secretKey) throws HandleException {
        return new GenericHSAdapter(adminHandle, keyIndex, secretKey);
    }

}

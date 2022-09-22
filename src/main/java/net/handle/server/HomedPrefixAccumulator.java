/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleStorage;
import net.handle.hdllib.ScanCallback;

/**
 * Used to get a collection of all NAs homes on a given HandleStorage.
 * As HandleStorage does not have a method for retrieving all NAs
 * this class provides that functionality by making use of the
 * HandleStorage.scanNAs method.
 */
public class HomedPrefixAccumulator implements ScanCallback {

    private final HandleStorage storage;
    private List<byte[]> prefixes;

    public HomedPrefixAccumulator(HandleStorage storage) {
        this.storage = storage;
    }

    public List<byte[]> getHomedPrefixes() throws HandleException {
        prefixes = new ArrayList<>();
        storage.scanNAs(this);
        return prefixes;
    }

    @Override
    public void scanHandle(byte[] handle) throws HandleException {
        prefixes.add(handle);
    }

}

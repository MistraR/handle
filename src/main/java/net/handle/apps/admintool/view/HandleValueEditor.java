/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;

public interface HandleValueEditor {

    /**
     * Saves the handle value data in the given HandleValue.  Returns false
     * if there was an error and the operation was canceled.
     */
    public boolean saveValueData(HandleValue value);

    /**
     * Sets the handle value data from the given HandleValue.
     */
    public void loadValueData(HandleValue value);

}

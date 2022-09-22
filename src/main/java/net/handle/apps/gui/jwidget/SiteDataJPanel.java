/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.gui.jwidget;

import net.handle.awt.*;
import net.handle.hdllib.*;

public class SiteDataJPanel extends GenDataJPanel {
    protected SiteInfoJPanel sitePanel;

    /**
     *@param moreFlag to enable the More Button to work
     **/
    public SiteDataJPanel(boolean moreFlag, boolean editFlag) {
        this(moreFlag, editFlag, 1);
    }

    public SiteDataJPanel(boolean moreFlag, boolean editFlag, int index) {
        super(moreFlag, editFlag, String.valueOf(index));
        sitePanel = new SiteAdmInfoJPanel(editFlag);
        panel.add(sitePanel, AwtUtil.getConstraints(0, 0, 0f, 0f, 1, 1, true, true));

        handlevalue.setType(Common.STD_TYPE_HSSITE);
    }

    @Override
    public byte[] getValueData() {
        SiteInfo site = sitePanel.getSiteInfo();
        if (site != null) return (Encoder.encodeSiteInfoRecord(site));
        else {
            return Common.EMPTY_BYTE_ARRAY;
        }
    }

    @Override
    public void setValueData(byte[] data) {
        if (data == Common.EMPTY_BYTE_ARRAY) {
            return;
        }

        SiteInfo site = new SiteInfo();
        try {
            Encoder.decodeSiteInfoRecord(data, 0, site);
        } catch (HandleException e) {
        }
        sitePanel.setSiteInfo(site);
    }

    class SiteAdmInfoJPanel extends SiteInfoJPanel {

        SiteAdmInfoJPanel(boolean editFlag) {
            super(editFlag);
            this.add(setPanel, AwtUtil.getConstraints(0, 0, 0f, 0f, 1, 1, true, true));
            this.add(serverList, AwtUtil.getConstraints(0, 1, 0f, 0f, 1, 10, true, true));
            this.add(attriList, AwtUtil.getConstraints(0, 11, 0f, 0f, 1, 10, true, true));
            this.add(savePanel, AwtUtil.getConstraints(0, 21, 0f, 0f, 1, 1, true, true));
        }
    }
}

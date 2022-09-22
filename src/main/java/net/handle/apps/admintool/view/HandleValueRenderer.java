/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.admintool.view;

import net.handle.hdllib.*;
import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Hashtable;

public class HandleValueRenderer extends Component implements ListCellRenderer<HandleValue> {
    private static Color bgColors[] = new Color[] { new Color(-1), new Color(-1442307) };

    private final Hashtable<HandleValue, String> cache = new Hashtable<>();

    private HandleValue value = null;
    private boolean isSelected = false;
    private FontMetrics fm = null;
    private int maxDescent;
    private int w = 0;
    private int h = 0;
    private int index = 0;

    private final DateTimeFormatter dateTimeFormat;

    public HandleValueRenderer() {
        dateTimeFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss zzz").withZone(ZoneId.systemDefault());
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(100, 40);
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        this.w = w;
        this.h = h;
    }

    public String getTTLInWords(int ttl, byte ttlType) {
        int tmp;
        int tmp2;
        switch (ttlType) {
        case HandleValue.TTL_TYPE_RELATIVE:
            StringBuffer sb = new StringBuffer(20);
            boolean isNegative = ttl < 0;
            if (isNegative) ttl = -ttl;
            if (ttl >= 86400) { // >= one day
                tmp = ttl % 86400; // remainder
                tmp2 = (ttl - tmp) / 86400; // number of days;
                sb.append(' ');
                sb.append(tmp2);
                sb.append(tmp2 > 1 ? " days" : " day");
                ttl = tmp;
            }
            if (ttl >= 3600) { // >= one hour
                tmp = ttl % 3600; // remainder
                tmp2 = (ttl - tmp) / 3600; // number of hours
                sb.append(' ');
                sb.append(tmp2);
                sb.append(tmp2 > 1 ? " hours" : " hour");
                ttl = tmp;
            }
            if (ttl >= 60) { // >= one minute
                tmp = ttl % 60; // remainder
                tmp2 = (ttl - tmp) / 60; // number of minutes
                sb.append(' ');
                sb.append(tmp2);
                sb.append(tmp2 > 1 ? " minutes" : " minute");
                ttl = tmp;
            }
            if (ttl > 0 || sb.length() <= 0) {
                sb.append(' ');
                sb.append(ttl);
                sb.append(ttl == 1 ? " second" : " seconds");
            }
            if (isNegative) {
                sb.append(" ago");
                sb.insert(0, "Expires");
            } else {
                sb.insert(0, "Expires in");
            }
            return sb.toString();
        case HandleValue.TTL_TYPE_ABSOLUTE:
            return "Expires at " + dateTimeFormat.format(Instant.ofEpochMilli(1000l * ttl));
        default:
            return "Unknown TTL type!";
        }
    }

    /** Return a text representation of the given handle value. */
    public String getTextValue() {
        String cachedVal = cache.get(value);
        if (cachedVal != null) return cachedVal;

        StringBuffer val = new StringBuffer(30);
        byte type[] = value.getType();
        String typeStr = null;
        if (type != null) {
            typeStr = Util.decodeString(type);
            val.append(typeStr);
            val.append(": ");
        }

        byte data[] = value.getData();
        if (typeStr == null) {
            val.append(value.getDataAsString());
        } else if (typeStr.equalsIgnoreCase("URL") || typeStr.startsWith("URL.") || typeStr.equalsIgnoreCase("EMAIL") || typeStr.startsWith("EMAIL.") || typeStr.equalsIgnoreCase("HS_ALIAS") || typeStr.startsWith("HS_ALIAS.")
            || typeStr.equalsIgnoreCase("HS_SERV") || typeStr.startsWith("HS_SERV.") ||
            //              typeStr.equalsIgnoreCase("INET_HOST") ||
            //              typeStr.startsWith("INET_HOST.") ||
            //              typeStr.equalsIgnoreCase("URN") ||
            //              typeStr.startsWith("URN.") ||
            typeStr.equalsIgnoreCase("DESC") || typeStr.startsWith("DESC.") || typeStr.equalsIgnoreCase("HS_SECKEY") || typeStr.startsWith("HS_SECKEY.")) {
            val.append(Util.decodeString(data));
        } else if (typeStr.equalsIgnoreCase("HS_SITE") || typeStr.startsWith("HS_SITE.") || typeStr.equalsIgnoreCase("HS_NA_DELEGATE")) {
            SiteInfo siteInfo = new SiteInfo();
            try {
                Encoder.decodeSiteInfoRecord(data, 0, siteInfo);
                val.append(siteInfo);
            } catch (Exception e) {
                val.append("*** BAD DATA ***");
            }
        } else if (typeStr.equalsIgnoreCase("HS_ADMIN") || typeStr.startsWith("HS_ADMIN.")) {
            AdminRecord adminInfo = new AdminRecord();
            try {
                Encoder.decodeAdminRecord(data, 0, adminInfo);
                val.append(adminInfo);
            } catch (Exception e) {
                val.append("*** BAD DATA ***");
            }
        } else if (typeStr.equalsIgnoreCase("HS_DSAPUBKEY") || typeStr.startsWith("HS_DSAPUBKEY.") || typeStr.equalsIgnoreCase("HS_PUBKEY") || typeStr.startsWith("HS_PUBKEY.")) {
            java.security.PublicKey key = null;
            try {
                key = Util.getPublicKeyFromBytes(data, 0);
                val.append("alg=");
                val.append(key.getAlgorithm());
                val.append("; format=");
                val.append(key.getFormat());
            } catch (Exception e) {
                val.append("*** BAD DATA: ***");
            }
        } else if (typeStr.equalsIgnoreCase("HS_VLIST") || typeStr.startsWith("HS_VLIST.")) {
            ValueReference refs[] = null;
            try {
                refs = Encoder.decodeValueReferenceList(data, 0);
                for (int i = 0; refs != null && i < refs.length; i++) {
                    if (i != 0) val.append(", ");
                    val.append(String.valueOf(refs[i]));
                }
            } catch (Exception e) {
                val.append("*** BAD DATA ***");
            }

        } else {
            val.append(value.getDataAsString());
        }

        String returnStr = val.toString();
        cache.put(value, returnStr);
        return returnStr;
    }

    public String getAccessString() {
        StringBuffer sb = new StringBuffer("admin:-- public:--");
        if (value.getAdminCanRead()) sb.setCharAt(6, 'r');
        if (value.getAdminCanWrite()) sb.setCharAt(7, 'w');
        if (value.getAnyoneCanRead()) sb.setCharAt(16, 'r');
        if (value.getAnyoneCanWrite()) sb.setCharAt(17, 'w');
        return sb.toString();
    }

    @Override
    public void paint(Graphics g) {
        if (fm == null) {
            fm = g.getFontMetrics();
            maxDescent = fm.getMaxDescent();
        }
        Color bg = Color.white;
        if (isSelected) bg = Color.lightGray;
        else bg = bgColors[index % bgColors.length];

        g.setColor(bg);
        g.fillRect(0, 0, w, h);

        g.setColor(Color.lightGray);
        g.drawLine(0, h - 1, w, h - 1);

        int x = 5;
        String tmp = getTextValue();
        g.setColor(Color.black);
        g.drawString(tmp, x, h / 2 - maxDescent - 1);

        g.setColor(Color.gray);
        x = 15;

        int timeStamp = value.getTimestamp();
        if (timeStamp <= 0) tmp = "No timestamp";
        else tmp = "Last Modified: " + dateTimeFormat.format(Instant.ofEpochMilli(1000l * timeStamp));
        g.drawString(tmp, x, h - maxDescent - 1);
        x += fm.stringWidth(tmp);
        x += 15;

        tmp = getTTLInWords(value.getTTL(), value.getTTLType());
        g.drawString(tmp, x, h - maxDescent - 1);
        x += fm.stringWidth(tmp);
        x += 15;

        tmp = getAccessString();
        g.drawString(tmp, x, h - maxDescent - 1);
        x += fm.stringWidth(tmp);
        x += 15;

        tmp = "index: " + value.getIndex();
        g.drawString(tmp, x, h - maxDescent - 1);
        x += fm.stringWidth(tmp);
        x += 15;
    }

    @Override
    @SuppressWarnings("hiding")
    public Component getListCellRendererComponent(JList<? extends HandleValue> list, HandleValue val, int index, boolean isSelected, boolean isFocused) {
        this.index = index;
        this.value = val;
        this.isSelected = isSelected;
        return this;
    }
}

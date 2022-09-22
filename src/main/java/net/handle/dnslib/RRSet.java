/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

public class RRSet implements Iterable<ResourceRecord> {
    private ArrayList<ResourceRecord> rrs = new ArrayList<>();

    public RRSet(ResourceRecord rr) {
        rrs.add(rr);
    }

    protected RRSet(RRSet rrSet) {
        this.rrs = rrSet.rrs;
    }

    public void add(ResourceRecord rr) {
        rrs.add(rr);
    }

    public Question getKey() {
        return rrs.get(0).getKey();
    }

    public int getType() {
        return rrs.get(0).getType();
    }

    public int getTTL() {
        int min = Integer.MAX_VALUE;
        for (ResourceRecord rr : rrs) {
            if (rr.getTTL() <= 0) min = 0;
            else if (rr.getTTL() < min) min = rr.getTTL();
        }
        return min;
    }

    public void fixTTL() {
        int ttl = getTTL();
        for (ResourceRecord rr : rrs) {
            rr.setTTL(ttl);
        }
    }

    @Override
    public Iterator<ResourceRecord> iterator() {
        return rrs.iterator();
    }

    public int size() {
        return rrs.size();
    }

    public void shuffle() {
        Collections.shuffle(rrs);
    }
}

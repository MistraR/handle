/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MultiRRSet {
    protected Map<Question, RRSet> map;

    public MultiRRSet() {
        map = new HashMap<>();
    }

    public void add(ResourceRecord rr) {
        if (rr == null) return;
        Question key = rr.getKey();
        RRSet rrs = map.get(key);
        if (rrs == null) map.put(key, new RRSet(rr));
        else rrs.add(rr);
    }

    public void add(RRSet rrs) {
        map.put(rrs.getKey(), rrs);
    }

    public void add(MultiRRSet rrss) {
        for (RRSet rrs : rrss.values()) {
            add(rrs);
        }
    }

    public RRSet get(Question q) {
        return map.get(q);
    }

    public void clear() {
        map.clear();
    }

    public Set<Question> keySet() {
        return map.keySet();
    }

    public Collection<RRSet> values() {
        return map.values();
    }

    public int size() {
        return map.size();
    }
}

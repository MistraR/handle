/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.tools;

import net.handle.apps.batch.BatchUtil;
import net.handle.hdllib.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class CheckHandleAtSites {

    public static void main(String[] argv) throws Exception {
        if (argv.length < 1) {
            System.err.println("usage: hdl-java net.handle.apps.tools.CheckHandleAtSites <handle>");
            return;
        }

        HandleResolver resolver = new HandleResolver();
        String handle = argv[0];
        System.out.println("Checking handle: " + handle);
        SiteInfo[] sites = getSiteList(resolver, handle);
        Map<SiteInfo, ResolutionResult> results = resolveHandleAtSites(resolver, handle, sites);
        processResults(results);
    }

    private static void processResults(Map<SiteInfo, ResolutionResult> results) {
        SiteInfo primarySite = getPrimarySite(results);
        ResolutionResult primary = results.get(primarySite);
        Arrays.sort(primary.values, Comparator.comparingInt(HandleValue::getIndex));
        Map<SiteInfo, HandleValue[]> mismatches = new HashMap<>();
        Map<SiteInfo, String> errors = new HashMap<>();
        for (Map.Entry<SiteInfo, ResolutionResult> result : results.entrySet()) {
            if (primarySite.equals(result.getKey())) continue;
            HandleValue[] values = result.getValue().values;
            if (values == null) {
                errors.put(result.getKey(), result.getValue().error);
            } else {
                Arrays.sort(values, Comparator.comparingInt(HandleValue::getIndex));
                if (!Arrays.equals(primary.values, values)) {
                    HandleValue[] diff = Arrays.stream(values)
                            .filter(v -> !v.equals(BatchUtil.getHandleValueAtIndex(primary.values, v.getIndex())))
                            .toArray(HandleValue[]::new);
                    mismatches.put(result.getKey(), diff);
                }
            }
        }

        if (errors.size() > 0) {
            System.out.println(errors.size() + " errors resolving handle:");
            for (Map.Entry<SiteInfo, String> error : errors.entrySet()) {
                System.out.println("Error at site: " + error.getKey());
                System.out.println("\t" + error.getValue());
            }
        }

        if (mismatches.size() > 0) {
            System.out.println("\n" + mismatches.size() + " mismatches found");
            System.out.println("Info at primary/first site: " + primarySite);
            printHandleValues(primary.values);
            for (Map.Entry<SiteInfo, HandleValue[]> mismatch : mismatches.entrySet()) {
                System.out.println("\nMismatched info at site: " + mismatch.getKey());
                printHandleValues(mismatch.getValue());
            }
        }

        if (mismatches.size() == 0 && errors.size() == 0){
            System.out.println("Handle is the same on all " + results.size() + " sites:");
            printHandleValues(primary.values);
        }
    }

    private static SiteInfo getPrimarySite(Map<SiteInfo, ResolutionResult> results) {
        SiteInfo[] sitesSortedPrimariesFirst = results.keySet().toArray(new SiteInfo[0]);
        Arrays.sort(sitesSortedPrimariesFirst, (o1, o2) -> {
            if (o1.isPrimary && !o2.isPrimary) return -1;
            if (o2.isPrimary && !o1.isPrimary) return 1;
            return 0;
        });
        return sitesSortedPrimariesFirst[0];
    }

    private static Map<SiteInfo, ResolutionResult> resolveHandleAtSites(HandleResolver resolver, String handle, SiteInfo[] sites) {
        Map<SiteInfo, ResolutionResult> results = new HashMap<>();
        for (SiteInfo site : sites) {
            ResolutionResult result = new ResolutionResult();
            try {
                System.out.print("Resolving at site: " + site + "...");
                result.values = BatchUtil.resolveHandleFromSite(handle, resolver, null, site);
                System.out.println("done");
            } catch (HandleException e) {
                if (e.getCode() == HandleException.INTERNAL_ERROR && "Error(100): HANDLE NOT FOUND".equals(e.getMessage())) {
                    System.out.println("ERROR: Handle not found");
                    result.error = "Handle not found";
                } else if (e.getCode() == HandleException.CANNOT_CONNECT_TO_SERVER) {
                    System.out.println("ERROR: Could not connect to server");
                    result.error = "Could not connect to server";
                } else {
                    System.out.println("ERROR: Something went wrong. " + e.getMessage());
                    result.error = "Something went wrong. " + e.getMessage();
                }
            }
            results.put(site, result);
        }
        System.out.println("Done resolving handles.\n");
        return results;
    }

    private static SiteInfo[] getSiteList(HandleResolver resolver, String handle) throws HandleException {
        System.out.print("Generating site list...");
        String prefix = Util.getZeroNAHandle(handle);
        SiteInfo[] sites = resolver.findLocalSitesForNA(Util.encodeString(prefix));
        if (sites == null || sites.length == 0) {
            throw new HandleException(HandleException.SERVICE_NOT_FOUND, "No sites available for handle: " + handle);
        }
        System.out.println("Found " + sites.length + " sites.");
        return sites;
    }

    private static void printHandleValues(HandleValue[] values) {
        if (values.length == 0) {
            System.out.println("  No values found");
            return;
        }
        for (HandleValue value : values) {
            System.out.println(value.toDetailedString());
        }
    }

    private static class ResolutionResult {
        public HandleValue[] values;
        public String error;
    }
}

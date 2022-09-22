/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import java.util.ArrayList;
import java.util.List;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;

public class BatchHandleProcessor {

    private final List<String> handles;
    private final HandleResolver resolver;
    private final AuthenticationInfo authInfoForProcessing;
    private final AuthenticationInfo authInfoForResolution;
    private final SiteInfo site;
    private final List<String> errorHandles;
    private boolean skipResolution;

    public BatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolutionAndProcessing, SiteInfo site) {
        this(handles, resolver, authInfoForResolutionAndProcessing, authInfoForResolutionAndProcessing, site);
    }

    public BatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolution, AuthenticationInfo authInfoForProcessing, SiteInfo site) {
        this.handles = handles;
        this.resolver = resolver;
        this.authInfoForResolution = authInfoForResolution;
        this.authInfoForProcessing = authInfoForProcessing;
        this.site = site;
        errorHandles = new ArrayList<>();
    }

    public BatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolution, AuthenticationInfo authInfoForProcessing, SiteInfo site, boolean skipResolution) {
        this.handles = handles;
        this.resolver = resolver;
        this.authInfoForResolution = authInfoForResolution;
        this.authInfoForProcessing = authInfoForProcessing;
        this.site = site;
        errorHandles = new ArrayList<>();
        this.skipResolution = skipResolution;
    }

    public void process(HandleRecordOperationInterface recordProcessor, HandleRecordFilter processFilter) {
        int count = 0;
        for (String handle : handles) {
            try {
                if ("".equals(handle)) {
                    continue;
                }
                HandleValue[] values = null;
                if (!skipResolution) values = BatchUtil.resolveHandle(handle, resolver, authInfoForResolution);
                if (processFilter != null) {
                    boolean skip = !processFilter.accept(values);
                    if (skip) {
                        continue;
                    }
                }
                recordProcessor.process(handle, values, resolver, authInfoForProcessing, site);
            } catch (HandleException e) {
                System.err.println("Exception processing " + handle);
                e.printStackTrace();
                errorHandles.add(handle);
            }
            System.out.println(count + ": " + handle);
            count++;
        }
    }

    public void process(HandleRecordOperationInterface recordProcessor) {
        process(recordProcessor, null);
    }

    public List<String> getErrorHandles() {
        return errorHandles;
    }

    public List<String> filter(HandleRecordFilter filter) throws HandleException {
        List<String> result = new ArrayList<>();
        int count = 0;
        for (String handle : handles) {
            HandleValue[] values = resolver.resolveHandle(handle);
            if (filter.accept(values)) {
                result.add(handle);
            }
            System.out.println(count++);
        }
        return result;
    }

}

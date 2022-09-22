/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.batch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.SiteInfo;

public class ParallelBatchHandleProcessor {

    public static enum ResolutionType {
        SPECIFIC_SITE, GLOBAL, NONE
    }

    private final List<String> handles;
    private final HandleResolver resolver;
    private final AuthenticationInfo authInfoForResolution;
    private final AuthenticationInfo authInfoForProcessing;
    private final SiteInfo site;
    private final List<String> errorHandles;
    private final List<Exception> exceptions;
    private final AtomicInteger atomicCount;
    private final ExecutorService executor;
    //private boolean resolveAtSite;
    private boolean verbose = true;

    ResolutionType resolutionType = ResolutionType.GLOBAL;

    public ParallelBatchHandleProcessor(boolean verbose, List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolutionAndProcessing, SiteInfo site, int numThreads) {
        this(handles, resolver, authInfoForResolutionAndProcessing, site, numThreads);
        this.verbose = verbose;
    }

    public ParallelBatchHandleProcessor(boolean verbose, List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolutionAndProcessing, SiteInfo site, int numThreads, boolean resolveAtSite) {
        this(handles, resolver, authInfoForResolutionAndProcessing, site, numThreads, resolveAtSite);
        this.verbose = verbose;
    }

    public ParallelBatchHandleProcessor(boolean verbose, List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolution, AuthenticationInfo authInfoForProcessing, SiteInfo site, int numThreads,
        boolean resolveAtSite) {
        this(handles, resolver, authInfoForResolution, authInfoForProcessing, site, numThreads, resolveAtSite);
        this.verbose = verbose;
    }

    public ParallelBatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolutionAndProcessing, SiteInfo site, int numThreads) {
        this(handles, resolver, authInfoForResolutionAndProcessing, site, numThreads, false);
    }

    public ParallelBatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolutionAndProcessing, SiteInfo site, int numThreads, boolean resolveAtSite) {
        this(handles, resolver, authInfoForResolutionAndProcessing, authInfoForResolutionAndProcessing, site, numThreads, resolveAtSite);
    }

    public ParallelBatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolution, AuthenticationInfo authInfoForProcessing, SiteInfo site, int numThreads, boolean resolveAtSite) {
        this(handles, resolver, authInfoForResolution, authInfoForProcessing, site, numThreads, resolveAtSite ? ResolutionType.SPECIFIC_SITE : ResolutionType.GLOBAL);
    }

    public ParallelBatchHandleProcessor(List<String> handles, HandleResolver resolver, AuthenticationInfo authInfoForResolution, AuthenticationInfo authInfoForProcessing, SiteInfo site, int numThreads, ResolutionType resolutionType) {
        this.handles = handles;
        this.resolver = resolver;
        this.authInfoForResolution = authInfoForResolution;
        this.authInfoForProcessing = authInfoForProcessing;
        this.site = site;
        errorHandles = Collections.synchronizedList(new ArrayList<String>());
        exceptions = Collections.synchronizedList(new ArrayList<Exception>());
        executor = Executors.newFixedThreadPool(numThreads);
        atomicCount = new AtomicInteger(0);
        this.resolutionType = resolutionType;
    }

    public void process(HandleRecordOperationInterface recordProcessor) {
        for (String handle : handles) {
            if ("".equals(handle)) {
                continue;
            }
            SingleHandleTask task = new SingleHandleTask(handle, recordProcessor);
            executor.execute(task);
        }
        try {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.out.println("error shutting down");
        }
    }

    public void process(HandleRecordOperationInterface recordProcessor, HandleRecordFilter processFilter) {
        for (String handle : handles) {
            if ("".equals(handle)) {
                continue;
            }
            SingleHandleTask task = new SingleHandleTask(handle, recordProcessor, processFilter);
            executor.execute(task);
        }
        try {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            System.out.println("error shutting down");
        }
    }

    public List<String> getErrorHandles() {
        return errorHandles;
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

    public class SingleHandleTask implements Runnable {

        private final String handle;
        private final HandleRecordOperationInterface recordProcessor;
        private HandleRecordFilter processFilter;

        public SingleHandleTask(String handle, HandleRecordOperationInterface recordProcessor) {
            this.handle = handle;
            this.recordProcessor = recordProcessor;
        }

        public SingleHandleTask(String handle, HandleRecordOperationInterface recordProcessor, HandleRecordFilter processFilter) {
            this.handle = handle;
            this.recordProcessor = recordProcessor;
            this.processFilter = processFilter;
        }

        @Override
        public void run() {
            try {
                int count = atomicCount.getAndIncrement();
                if (verbose) System.out.println(count + ": " + handle);
                HandleValue[] values;
                if (resolutionType == ResolutionType.SPECIFIC_SITE) {
                    values = BatchUtil.resolveHandleFromSite(handle, resolver, authInfoForResolution, site);
                } else if (resolutionType == ResolutionType.GLOBAL) {
                    values = BatchUtil.resolveHandle(handle, resolver, authInfoForResolution);
                } else {
                    values = null;
                }
                if (processFilter != null && !processFilter.accept(values)) return;
                recordProcessor.process(handle, values, resolver, authInfoForProcessing, site);
            } catch (HandleException e) {
                System.err.println("Exception processing " + handle);
                e.printStackTrace();
                exceptions.add(e);
                errorHandles.add(handle);
            }
        }
    }

}

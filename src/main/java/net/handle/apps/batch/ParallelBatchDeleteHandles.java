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

import net.handle.hdllib.AbstractRequest;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.SiteInfo;
import net.handle.hdllib.Util;

public class ParallelBatchDeleteHandles {

    private final List<String> handlesToDelete;
    private final SiteInfo site;
    private final AuthenticationInfo authInfo;
    private final HandleResolver resolver;
    private final List<String> errorHandles;
    private final List<Exception> exceptions;
    private final AtomicInteger atomicCount;
    private final ExecutorService executor;

    public ParallelBatchDeleteHandles(List<String> handlesToDelete, SiteInfo site, AuthenticationInfo authInfo, HandleResolver resolver, int numThreads) {
        this.handlesToDelete = handlesToDelete;
        this.site = site;
        this.authInfo = authInfo;
        this.resolver = resolver;
        errorHandles = Collections.synchronizedList(new ArrayList<String>());
        exceptions = Collections.synchronizedList(new ArrayList<Exception>());
        executor = Executors.newFixedThreadPool(numThreads);
        atomicCount = new AtomicInteger(0);
    }

    public void deleteHandles() {
        for (String handle : handlesToDelete) {
            if ("".equals(handle)) {
                continue;
            }
            DeleteHandleTask task = new DeleteHandleTask(handle, resolver, authInfo);
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

    public class DeleteHandleTask implements Runnable {

        private final String handle;
        @SuppressWarnings("hiding")
        private final HandleResolver resolver;
        @SuppressWarnings("hiding")
        private final AuthenticationInfo authInfo;

        public DeleteHandleTask(String handle, HandleResolver resolver, AuthenticationInfo authInfo) {
            this.handle = handle;
            this.resolver = resolver;
            this.authInfo = authInfo;
        }

        @Override
        public void run() {
            try {
                int count = atomicCount.getAndIncrement();
                System.out.println(count + ": " + handle);

                byte[] handleBytes = Util.encodeString(handle);
                AbstractRequest deleteHandleRequest = new DeleteHandleRequest(handleBytes, authInfo);
                AbstractResponse response = resolver.sendRequestToSite(deleteHandleRequest, site);
                BatchUtil.throwIfNotSuccess(response);
            } catch (HandleException e) {
                e.printStackTrace();
                exceptions.add(e);
                errorHandles.add(handle);
            }
        }
    }
}

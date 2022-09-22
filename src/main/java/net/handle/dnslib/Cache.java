/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.dnslib;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.handle.util.LRUCacheTable;

public class Cache {
    public static final int MAXIMUM_TTL = 86400;

    private final QueryResolver cacheRunner;
    private final ExecutorService prefetcher;
    private final LRUCacheTable<Key, Value> lrutable;

    public Cache(QueryResolver cacheRunner, int numPrefetcherThreads, int cacheSize) {
        this.cacheRunner = cacheRunner;
        this.prefetcher = new ThreadPoolExecutor(numPrefetcherThreads, numPrefetcherThreads, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>());
        this.lrutable = new LRUCacheTable<>(cacheSize);
    }

    public void shutdown() {
        this.prefetcher.shutdownNow();
    }

    public void put(Message query, Message response) {
        if (response.getTTL() <= 0) return;
        try {
            // precompute the datagram
            response.getDatagram(65535);
        } catch (IOException e) {
        }
        lrutable.put(new Key(query), new Value(response));
    }

    public Message get(final Message query) {
        Key key = new Key(query);
        Value response = lrutable.get(key);
        if (response == null) return null;

        long now = System.currentTimeMillis();
        int ttl = Math.min(response.response.getTTL(), MAXIMUM_TTL);
        long expires = response.timestamp + 1000L * ttl;
        if (expires <= now) {
            lrutable.remove(key);
            return null;
        }
        if (expires - response.timestamp < 2 * (now - response.timestamp)) {
            try {
                prefetcher.execute(() -> cacheRunner.resolve(query));
            } catch (RejectedExecutionException e) {
            }
        }

        Message res = Message.copy(response.response);
        res.id = query.id;
        res.recursionDesired = query.recursionDesired;
        return res;
    }

    private static class Key {
        final Question question;
        final int ednsVersion;

        public Key(Message query) {
            if (query == null || query.getQuestion() == null) throw new NullPointerException();
            this.question = query.getQuestion();
            this.ednsVersion = query.getEDNSVersion();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Key)) return false;
            Key key = (Key) obj;
            return question.equals(key.question) && ednsVersion == key.ednsVersion;
        }

        @Override
        public int hashCode() {
            return 31 * question.hashCode() + ednsVersion;
        }
    }

    private static class Value {
        final Message response;
        final long timestamp;

        public Value(Message response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static interface QueryResolver {
        void resolve(Message query);
    }
}

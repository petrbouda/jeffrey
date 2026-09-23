/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.shared.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public abstract class Schedulers {

    private static class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingUncaughtExceptionHandler.class);

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            LOG.error("Uncaught exception: thread={} message={}", t.getName(), e.getMessage(), e);
        }
    }

    private static final ExecutorService PARALLEL =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), platformThreadfactory("parallel"));

    private static final ExecutorService BULK_PARALLEL =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), platformThreadfactory("bulk-parallel"));

    private static final ExecutorService VIRTUAL =
            Executors.newThreadPerTaskExecutor(virtualThreadfactory("virtual"));

    private static final ScheduledExecutorService SINGLE_SCHEDULED =
            Executors.newSingleThreadScheduledExecutor(platformThreadfactory("single-scheduled"));

    private static final ExecutorService STREAMING =
            Executors.newThreadPerTaskExecutor(virtualThreadfactory("streaming"));

    /**
     * Number of threads flushing batches into the database. Connection pools serving the writers
     * should offer at least this many connections, otherwise flush tasks block on the pool.
     */
    public static final int DB_WRITER_THREADS = 20;

    private static final ExecutorService DB_WRITER =
            Executors.newFixedThreadPool(DB_WRITER_THREADS, platformThreadfactory("db-writer"));

    /**
     * Pool for interactive, latency-sensitive parallel work (e.g. flamegraph and timeseries
     * generation triggered by user requests). Bulk/batch workloads must use
     * {@link #sharedBulkParallel()} so that a large import cannot queue ahead of interactive requests.
     */
    public static ExecutorService sharedParallel() {
        return PARALLEL;
    }

    /**
     * Pool for bulk, throughput-oriented parallel work (e.g. chunk parsing during profile
     * initialization). Separated from {@link #sharedParallel()} so long-running batch jobs
     * do not starve interactive requests.
     */
    public static ExecutorService sharedBulkParallel() {
        return BULK_PARALLEL;
    }

    public static ExecutorService sharedVirtual() {
        return VIRTUAL;
    }

    public static ScheduledExecutorService sharedSingleScheduled() {
        return SINGLE_SCHEDULED;
    }

    public static ExecutorService streamingExecutor() {
        return STREAMING;
    }

    public static ExecutorService sharedDbWriter() {
        return DB_WRITER;
    }

    public static ThreadFactory platformThreadfactory(String prefix) {
        return Thread.ofPlatform()
                .daemon(true)
                .name(prefix)
                .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
                .factory();
    }

    public static ThreadFactory virtualThreadfactory(String prefix) {
        return Thread.ofVirtual()
                .name(prefix)
                .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
                .factory();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PARALLEL.close();
            BULK_PARALLEL.close();
            VIRTUAL.close();
            SINGLE_SCHEDULED.close();
            STREAMING.close();
            DB_WRITER.close();
        }));
    }
}

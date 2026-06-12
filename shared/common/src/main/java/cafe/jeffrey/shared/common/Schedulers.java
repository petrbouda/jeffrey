/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.shared.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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

    private static final ExecutorService SINGLE =
            Executors.newSingleThreadExecutor(platformThreadfactory("single"));

    private static final ExecutorService VIRTUAL =
            Executors.newThreadPerTaskExecutor(virtualThreadfactory("virtual"));

    private static final ScheduledExecutorService SINGLE_SCHEDULED =
            Executors.newSingleThreadScheduledExecutor(platformThreadfactory("single-scheduled"));

    private static final ExecutorService STREAMING =
            Executors.newThreadPerTaskExecutor(virtualThreadfactory("streaming"));

    private static final int DB_WRITER_MIN_THREADS = 2;

    /**
     * Hard cap on concurrent DB-writer threads. Connection pools serving the writers must offer
     * at least this many connections so that no flush task blocks on pool acquisition.
     */
    public static final int DB_WRITER_MAX_THREADS = 20;

    // Cached-style pool: 2 core threads always alive, grows to DB_WRITER_MAX_THREADS on demand,
    // idle threads above the core shrink after 60 s. SynchronousQueue forces immediate hand-off
    // to a thread (no buffering), so the pool grows eagerly instead of filling a queue first.
    // CallerRunsPolicy provides natural back-pressure: if all 20 threads are occupied the
    // submitting parser thread runs the flush itself, slowing ingestion to match write speed.
    private static final ExecutorService DB_WRITER =
            new ThreadPoolExecutor(
                    DB_WRITER_MIN_THREADS,
                    DB_WRITER_MAX_THREADS,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    platformThreadfactory("db-writer"),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

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

    public static ExecutorService sharedSingle() {
        return SINGLE;
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
            SINGLE.close();
            VIRTUAL.close();
            SINGLE_SCHEDULED.close();
            STREAMING.close();
            DB_WRITER.close();
        }));
    }
}

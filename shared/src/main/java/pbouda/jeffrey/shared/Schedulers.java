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

package pbouda.jeffrey.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public abstract class Schedulers {

    private static class LoggingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
        private static final Logger LOG = LoggerFactory.getLogger(LoggingUncaughtExceptionHandler.class);

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            LOG.error("Uncaught exception: thread={} message={}", t.getName(), e.getMessage(), e);
        }
    }

    private static final ExecutorService PARALLEL = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            platformThreadfactory("parallel"));

    private static final ExecutorService SINGLE = Executors.newSingleThreadExecutor(platformThreadfactory("single"));

    private static final ExecutorService VIRTUAL = Executors.newThreadPerTaskExecutor(virtualThreadfactory());

    public static ExecutorService sharedParallel() {
        return PARALLEL;
    }

    public static ExecutorService sharedSingle() {
        return SINGLE;
    }

    public static ExecutorService sharedVirtual() {
        return VIRTUAL;
    }

    public static ThreadFactory platformThreadfactory(String prefix) {
        return Thread.ofPlatform()
                .daemon(true)
                .name(prefix)
                .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
                .factory();
    }

    public static ThreadFactory virtualThreadfactory() {
        return Thread.ofVirtual()
                .uncaughtExceptionHandler(new LoggingUncaughtExceptionHandler())
                .factory();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PARALLEL.close();
            SINGLE.close();
            VIRTUAL.close();
        }));
    }
}

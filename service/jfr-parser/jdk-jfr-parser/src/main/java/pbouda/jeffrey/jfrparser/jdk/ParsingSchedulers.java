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

package pbouda.jeffrey.jfrparser.jdk;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ParsingSchedulers {

    private static final int MAX_THREADS = 25;

    private static final ExecutorService CACHED_EXECUTORS =
            new ThreadPoolExecutor(0, MAX_THREADS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    public static ExecutorService cached() {
        return CACHED_EXECUTORS;
    }

    public static ThreadFactory factory(String prefix) {
        return new NamedThreadFactory(prefix);
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            return thread;
        }
    }
}

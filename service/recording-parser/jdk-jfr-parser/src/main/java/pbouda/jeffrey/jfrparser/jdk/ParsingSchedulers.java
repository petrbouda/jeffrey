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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public abstract class ParsingSchedulers {

    private static final int MAX_THREADS = 25;

    private static final ExecutorService CACHED_EXECUTORS =
            new ThreadPoolExecutor(0, MAX_THREADS, 60L, TimeUnit.SECONDS, new SynchronousQueue<>());

    public static ExecutorService cached() {
        return CACHED_EXECUTORS;
    }

    public static ThreadFactory factory(String prefix) {
        return Thread.ofPlatform().name(prefix).daemon().factory();
    }
}

/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.repository.parser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides ExecutorService instances for parallel JFR repository parsing.
 */
public final class ParsingSchedulers {

    private static final ExecutorService CACHED_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r);
        thread.setName("jfr-repo-parser-" + thread.getId());
        thread.setDaemon(true);
        return thread;
    });

    private ParsingSchedulers() {
    }

    /**
     * Returns a cached thread pool executor for parallel parsing operations.
     * Threads are daemon threads and named with "jfr-repo-parser-" prefix.
     *
     * @return shared cached executor service
     */
    public static ExecutorService cached() {
        return CACHED_EXECUTOR;
    }
}

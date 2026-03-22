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

package pbouda.jeffrey.profile.heapdump;

import org.netbeans.lib.profiler.heap.Heap;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for loading Java heap dumps.
 * Implementations may cache heaps for performance.
 */
public interface HeapLoader {

    /**
     * Load a heap dump from the specified path.
     *
     * @param heapDumpPath path to the heap dump file (.hprof or .hprof.gz)
     * @return the loaded Heap, or empty if loading fails
     */
    Optional<Heap> load(Path heapDumpPath);

    /**
     * Unload a previously loaded heap from cache (if applicable).
     *
     * @param heapDumpPath path to the heap dump file
     */
    void unload(Path heapDumpPath);

    /**
     * Check if a heap is currently loaded/cached for the given path.
     *
     * @param heapDumpPath path to the heap dump file
     * @return true if the heap is loaded
     */
    boolean isLoaded(Path heapDumpPath);
}

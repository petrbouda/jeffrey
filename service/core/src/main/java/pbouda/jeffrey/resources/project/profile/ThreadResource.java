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

package pbouda.jeffrey.resources.project.profile;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import pbouda.jeffrey.manager.ThreadManager;
import pbouda.jeffrey.manager.ThreadManager.AllocatingThread;
import pbouda.jeffrey.manager.ThreadManager.ThreadStats;
import pbouda.jeffrey.profile.thread.ThreadRoot;

import java.util.List;

public class ThreadResource {

    /**
     * The statistics of the threads.
     *
     * @param count the thread count
     * @param graphPoints the graph data points
     * @param threads the threads that are allocating memory
     */
    public record ThreadStatistics(ThreadStats count, long[][] graphPoints, List<AllocatingThread> threads) {
    }

    private final ThreadManager threadManager;

    public ThreadResource(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    @GET
    public ThreadRoot list() {
        return threadManager.threadRows();
    }

    @GET
    @Path("/statistics")
    public ThreadStatistics threadStatistics() {
        ThreadStats count = threadManager.threadStatistics();
        long[][] graphData = threadManager.activeGraphPoints();
        List<AllocatingThread> threads = threadManager.threadsAllocatingMemory();
        return new ThreadStatistics(count, graphData, threads);
    }
}

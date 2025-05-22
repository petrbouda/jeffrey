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
import pbouda.jeffrey.manager.model.AllocatingThread;
import pbouda.jeffrey.manager.model.ThreadStats;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class ThreadResource {

    /**
     * Different thread counts for gauge visualization on UI.
     *
     * @param accumulated the total number of threads created since the JVM started
     * @param peak        the peak number of threads created since the JVM started
     * @param maxActive   the max of active threads
     * @param maxDaemon   the max of daemon threads
     */
    public record ThreadStatsResponse(long accumulated, long peak, long maxActive, long maxDaemon) {
    }

    /**
     * The statistics of the threads.
     *
     * @param statistics the thread statistics
     * @param serie      the graph data points
     * @param allocators the threads that are allocating memory
     */
    public record ThreadStatistics(
            ThreadStatsResponse statistics, SingleSerie serie, List<AllocatingThread> allocators) {
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
        ThreadStats threadStats = threadManager.threadStatistics();
        List<AllocatingThread> threads = threadManager.threadsAllocatingMemory();

        return new ThreadStatistics(
                toThreadStatsResponse(threadStats),
                threadStats.serie(),
                threads);
    }

    private static ThreadStatsResponse toThreadStatsResponse(ThreadStats stats) {
        return new ThreadStatsResponse(stats.accumulated(), stats.peak(), stats.maxActive(), stats.maxDaemon());
    }
}

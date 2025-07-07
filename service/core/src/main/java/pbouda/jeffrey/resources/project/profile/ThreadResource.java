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
import pbouda.jeffrey.common.model.Type;
import pbouda.jeffrey.manager.ThreadManager;
import pbouda.jeffrey.manager.model.thread.AllocatingThread;
import pbouda.jeffrey.manager.model.thread.ThreadCpuLoads;
import pbouda.jeffrey.manager.model.thread.ThreadStats;
import pbouda.jeffrey.manager.model.thread.ThreadWithCpuLoad;
import pbouda.jeffrey.profile.thread.ThreadRoot;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

public class ThreadResource {

    private static final int TOP_ALLOCATING_THREADS = 20;
    private static final int TOP_CPU_LOADS = 10;

    /**
     * The statistics of the threads.
     *
     * @param statistics    the thread statistics
     * @param allocators    the threads that are allocating memory
     * @param userCpuLoad   the threads with user CPU load
     * @param systemCpuLoad the threads with system CPU load
     */
    public record ThreadStatistics(
            ThreadStats statistics,
            List<AllocatingThread> allocators,
            List<ThreadWithCpuLoad> userCpuLoad,
            List<ThreadWithCpuLoad> systemCpuLoad,
            Type allocationType) {
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
        List<AllocatingThread> threads = threadManager.threadsAllocatingMemory(TOP_ALLOCATING_THREADS);
        ThreadCpuLoads cpuLoads = threadManager.threadCpuLoads(TOP_CPU_LOADS);
        Type allocationType = threadManager.resolveAllocationType();
        return new ThreadStatistics(threadStats, threads, cpuLoads.user(), cpuLoads.system(), allocationType);
    }

    @GET
    @Path("/timeseries")
    public SingleSerie activeThreadsSerie() {
        return threadManager.activeThreadsSerie();
    }
}

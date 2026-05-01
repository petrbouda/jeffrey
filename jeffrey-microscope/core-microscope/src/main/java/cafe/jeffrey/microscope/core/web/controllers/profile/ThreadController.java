/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.ThreadManager;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/thread")
public class ThreadController {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadController.class);
    private static final int TOP_ALLOCATING_THREADS = 20;
    private static final int TOP_CPU_LOADS = 10;

    public record ThreadStatistics(
            ThreadStats statistics,
            List<AllocatingThread> allocators,
            List<ThreadWithCpuLoad> userCpuLoad,
            List<ThreadWithCpuLoad> systemCpuLoad,
            Type allocationType) {
    }

    private final ProfileManagerResolver resolver;

    public ThreadController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public ThreadRoot list(@PathVariable("profileId") String profileId) {
        var result = mgr(profileId).threadRows();
        LOG.debug("Listed threads: profileId={}", profileId);
        return result;
    }

    @GetMapping("/statistics")
    public ThreadStatistics threadStatistics(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching thread statistics");
        ThreadManager threadManager = mgr(profileId);
        ThreadStats threadStats = threadManager.threadStatistics();
        List<AllocatingThread> threads = threadManager.threadsAllocatingMemory(TOP_ALLOCATING_THREADS);
        ThreadCpuLoads cpuLoads = threadManager.threadCpuLoads(TOP_CPU_LOADS);
        Type allocationType = threadManager.resolveAllocationType();
        return new ThreadStatistics(threadStats, threads, cpuLoads.user(), cpuLoads.system(), allocationType);
    }

    @GetMapping("/timeseries")
    public SingleSerie activeThreadsSerie(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching active threads timeseries");
        return mgr(profileId).activeThreadsSerie();
    }

    private ThreadManager mgr(String profileId) {
        return resolver.resolve(profileId).threadManager();
    }
}

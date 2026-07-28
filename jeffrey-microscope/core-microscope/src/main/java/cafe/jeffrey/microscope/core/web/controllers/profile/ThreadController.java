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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.thread.ThreadManager;
import cafe.jeffrey.profile.manager.model.thread.ReservedStackActivation;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.thread.ThreadEventDetail;
import cafe.jeffrey.profile.thread.ThreadEventsQuery;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadSort;
import cafe.jeffrey.profile.thread.ThreadState;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/thread")
public class ThreadController {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadController.class);
    private static final int TOP_ALLOCATING_THREADS = 20;
    private static final int TOP_CPU_LOADS = 10;

    /**
     * A tooltip renders one event's fields and takes the rest from the band's own count, so there is
     * no reason to let a caller pull a whole band's worth of events back.
     */
    private static final int MAX_BAND_EVENTS = 20;

    /**
     * The ceiling on one page of threads. Each row still carries a lane's worth of bands, so an
     * unbounded page would put us back where we started.
     */
    private static final int MAX_THREAD_PAGE = 250;

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

    /**
     * One page of the timeline, a lane per group of identically named threads. A recording can hold
     * thousands of threads and most of them are pool workers, so the lanes come busiest-first and the
     * page comes back for more. Sorting and filtering happen here rather than in the browser, because
     * a client that holds one page can only sort and filter that page.
     */
    @GetMapping
    public ThreadGroupPage list(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "sort", defaultValue = "EVENT_COUNT") ThreadSort sort,
            @RequestParam(value = "nameFilter", required = false) String nameFilter,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        ThreadPageQuery query = new ThreadPageQuery(sort, nameFilter, offset, Math.min(limit, MAX_THREAD_PAGE));

        ThreadGroupPage page = mgr(profileId).threadGroups(query);
        LOG.debug("Listed thread groups: profileId={} sort={} offset={} returned={} matched={} threads={}",
                profileId, sort, offset, page.groups().size(), page.matchedGroups(), page.totalThreads());
        return page;
    }

    /**
     * The threads behind one collapsed lane. Opening a pool of 351 workers pages through them the
     * same way the timeline itself pages through lanes.
     */
    @GetMapping("/members")
    public ThreadPage members(
            @PathVariable("profileId") String profileId,
            @RequestParam("group") String group,
            @RequestParam(value = "sort", defaultValue = "EVENT_COUNT") ThreadSort sort,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {

        ThreadMembersQuery query =
                new ThreadMembersQuery(group, sort, offset, Math.min(limit, MAX_THREAD_PAGE));

        ThreadPage page = mgr(profileId).threadGroupMembers(query);
        LOG.debug("Listed members of a thread group: profileId={} group={} offset={} returned={} matched={}",
                profileId, group, offset, page.rows().size(), page.matchedCount());
        return page;
    }

    /**
     * The events behind one band of the timeline. The timeline itself ships rectangles and event
     * counts only, so the fields a tooltip renders are read here, one hovered band at a time.
     */
    @GetMapping("/events")
    public List<ThreadEventDetail> threadEvents(
            @PathVariable("profileId") String profileId,
            @RequestParam("osId") long osId,
            @RequestParam("javaId") long javaId,
            @RequestParam("state") ThreadState state,
            @RequestParam("from") long fromNanos,
            @RequestParam("to") long toNanos,
            @RequestParam(value = "limit", defaultValue = "1") int limit) {

        ThreadEventsQuery query = new ThreadEventsQuery(
                new ThreadInfo(osId, javaId, null),
                state,
                Duration.ofNanos(fromNanos),
                Duration.ofNanos(toNanos),
                Math.min(limit, MAX_BAND_EVENTS));

        LOG.debug("Fetching events of a timeline band: state={} from={} to={}", state, fromNanos, toNanos);
        return mgr(profileId).threadEvents(query);
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

    @GetMapping("/dumps")
    public ThreadDumpAnalysis threadDumpAnalysis(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching thread-dump analysis");
        return mgr(profileId).threadDumpAnalysis();
    }

    @GetMapping("/dumps/{index}")
    public ParsedDump threadDump(
            @PathVariable("profileId") String profileId,
            @PathVariable("index") int index) {
        LOG.debug("Fetching thread dump: index={}", index);
        return mgr(profileId).threadDump(index);
    }

    @GetMapping("/reserved-stack")
    public List<ReservedStackActivation> reservedStack(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching reserved-stack activations");
        return mgr(profileId).reservedStackActivations();
    }

    private ThreadManager mgr(String profileId) {
        return resolver.resolve(profileId).threadManager();
    }
}

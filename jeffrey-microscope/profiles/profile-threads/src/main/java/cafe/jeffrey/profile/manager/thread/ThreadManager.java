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

package cafe.jeffrey.profile.manager.thread;

import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.manager.model.thread.ReservedStackActivation;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.thread.ThreadEventDetail;
import cafe.jeffrey.profile.thread.ThreadEventsQuery;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.timeseries.SingleSerie;

import java.util.List;
import java.util.function.Function;

public interface ThreadManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ThreadManager> {
    }

    ThreadStats threadStatistics();

    SingleSerie activeThreadsSerie();

    List<AllocatingThread> threadsAllocatingMemory(int limit);

    Type resolveAllocationType();

    ThreadCpuLoads threadCpuLoads(int limit);

    /**
     * The whole timeline. Used to warm the profile's cache after an import — the page itself asks
     * for {@link #threadGroups(ThreadPageQuery)} instead, so that a recording with a thousand threads
     * does not have to cross the wire in one piece.
     */
    ThreadRoot threadRows();

    /**
     * One ordered slice of the timeline, a lane per group of identically named threads. Reads from
     * the same cached timeline {@link #threadRows()} builds, so paging costs nothing after the first
     * request.
     */
    ThreadGroupPage threadGroups(ThreadPageQuery query);

    /**
     * The threads behind one collapsed lane, a page at a time. Opening a pool of 351 workers must not
     * put 351 lanes on the page any more than the ungrouped timeline could.
     */
    ThreadPage threadGroupMembers(ThreadMembersQuery query);

    /**
     * Every thread behind one collapsed lane, unpaged — what anything acting on the lane as a whole
     * has to be scoped to. Unlike {@link #threadGroupMembers(ThreadMembersQuery)} this merges no
     * bands, so resolving a 351-thread pool costs a grouping pass and nothing more.
     */
    List<ThreadInfo> threadGroupThreads(String groupKey);

    /**
     * The events behind one band of {@link #threadRows()}, for the tooltip that opens when the
     * pointer settles on it. The timeline itself ships only rectangles and event counts, so the
     * fields are read here — one band at a time — instead of for every event in the recording.
     */
    List<ThreadEventDetail> threadEvents(ThreadEventsQuery query);

    /**
     * Cross-dump analysis of all {@code jdk.ThreadDump} occurrences (state timeline, top frames,
     * deadlocks, lock contention, stuck threads, heatmap). Excludes per-thread stacks — fetch those
     * per dump via {@link #threadDump(int)}.
     */
    ThreadDumpAnalysis threadDumpAnalysis();

    /**
     * The fully parsed thread dump at {@code index} (its threads + stacks + raw text), for the dump
     * viewer. Returns an empty dump when the index is out of range.
     */
    ParsedDump threadDump(int index);

    /**
     * Reserved-stack activations ({@code jdk.ReservedStackActivation}) in time order — stack-overflow
     * near-misses in {@code @ReservedStackAccess} methods. Empty in the common (healthy) case.
     */
    List<ReservedStackActivation> reservedStackActivations();
}

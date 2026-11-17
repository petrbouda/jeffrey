/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.manager.thread;

import cafe.jeffrey.microscope.model.ThreadInfo;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.profile.manager.model.thread.ReservedStackActivation;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.thread.ThreadEventsQuery;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.profile.thread.ThreadWindowEvents;
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
     * What one category of a lane was doing during a hovered slice of time, for the tooltip that
     * opens when the pointer settles. The timeline itself ships only rectangles, so both the count
     * and the field values are read here — one window at a time — instead of for every event in the
     * recording.
     *
     * <p>Scoped to the window rather than to the band under the pointer on purpose: a band merges
     * every run of activity too dense to draw apart, so on a busy lane it covers the whole recording
     * and would answer the same thing wherever the pointer is.
     */
    ThreadWindowEvents threadEvents(ThreadEventsQuery query);

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

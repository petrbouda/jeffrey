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

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.shared.common.model.EventSummary;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.ThreadInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.profile.manager.thread.builder.CPULoadBuilder;
import cafe.jeffrey.profile.manager.thread.builder.ThreadTimeseriesBuilder;
import cafe.jeffrey.profile.manager.model.thread.ReservedStackActivation;
import cafe.jeffrey.profile.manager.model.thread.ReservedStackActivationBuilder;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadStats;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump;
import cafe.jeffrey.profile.manager.model.thread.dump.RawDump;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalyzer;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpBuilder;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpParser;
import cafe.jeffrey.profile.thread.ThreadBands;
import cafe.jeffrey.profile.thread.ThreadEventsQuery;
import cafe.jeffrey.profile.thread.ThreadGroup;
import cafe.jeffrey.profile.thread.ThreadGroupMembers;
import cafe.jeffrey.profile.thread.ThreadGroupPage;
import cafe.jeffrey.profile.thread.ThreadGrouping;
import cafe.jeffrey.profile.thread.ThreadInfoProvider;
import cafe.jeffrey.profile.thread.ThreadMembersQuery;
import cafe.jeffrey.profile.thread.ThreadPage;
import cafe.jeffrey.profile.thread.ThreadPageQuery;
import cafe.jeffrey.profile.thread.ThreadRoot;
import cafe.jeffrey.profile.thread.ThreadRow;
import cafe.jeffrey.profile.thread.ThreadWindowEvents;
import cafe.jeffrey.profile.thread.ThreadWindowEventsBuilder;
import cafe.jeffrey.provider.profile.api.EventQueryConfigurer;
import cafe.jeffrey.provider.profile.api.ProfileEventRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventStreamRepository;
import cafe.jeffrey.provider.profile.api.ProfileEventTypeRepository;
import cafe.jeffrey.provider.profile.api.AllocatingThread;
import cafe.jeffrey.timeseries.SingleSerie;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public class ThreadManagerImpl implements ThreadManager {

    private static final int MAX_THREAD_DUMPS = 200;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MIN_WINDOW_MILLIS = 1L;

    private final ProfileInfo profileInfo;
    private final ProfileEventRepository eventRepository;
    private final ProfileEventStreamRepository eventStreamRepository;
    private final ProfileEventTypeRepository eventTypeRepository;
    private final ThreadInfoProvider threadInfoProvider;

    public ThreadManagerImpl(
            ProfileInfo profileInfo,
            ProfileEventRepository eventRepository,
            ProfileEventStreamRepository eventStreamRepository,
            ProfileEventTypeRepository eventTypeRepository,
            ThreadInfoProvider threadInfoProvider) {

        this.profileInfo = profileInfo;
        this.eventRepository = eventRepository;
        this.eventStreamRepository = eventStreamRepository;
        this.eventTypeRepository = eventTypeRepository;
        this.threadInfoProvider = threadInfoProvider;
    }

    @Override
    public ThreadStats threadStatistics() {
        Optional<ObjectNode> latestOpt = eventRepository.latestJsonFields(Type.JAVA_THREAD_STATISTICS);
        if (latestOpt.isEmpty()) {
            return null;
        }

        ObjectNode jsonNodes = latestOpt.get();
        long currAccumulated = jsonNodes.get("accumulatedCount").asLong();
        long currPeak = jsonNodes.get("peakCount").asLong();

        List<EventSummary> summaries = eventTypeRepository.eventSummaries(
                List.of(Type.THREAD_SLEEP, Type.THREAD_PARK, Type.JAVA_MONITOR_ENTER));

        long sleepSamples = filterEventSamples(summaries, Type.THREAD_SLEEP);
        long parkSamples = filterEventSamples(summaries, Type.THREAD_PARK);
        long monitorSamples = filterEventSamples(summaries, Type.JAVA_MONITOR_ENTER);

        return new ThreadStats(
                currAccumulated, currPeak, sleepSamples, parkSamples, monitorSamples);
    }

    private long filterEventSamples(List<EventSummary> summaries, Type type) {
        return summaries.stream()
                .filter(summary -> Type.fromCode(summary.name()).equals(type))
                .findFirst()
                .map(EventSummary::samples)
                .orElse(0L);
    }

    @Override
    public SingleSerie activeThreadsSerie() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.JAVA_THREAD_STATISTICS)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new ThreadTimeseriesBuilder(new RelativeTimeRange(profileInfo.profilingStartEnd())));
    }

    @Override
    public List<AllocatingThread> threadsAllocatingMemory(int limit) {
        return eventRepository.allocatingThreads(limit);
    }

    @Override
    public Type resolveAllocationType() {
        List<EventSummary> summaries = eventTypeRepository.eventSummaries(
                List.of(Type.OBJECT_ALLOCATION_IN_NEW_TLAB, Type.OBJECT_ALLOCATION_SAMPLE));

        return summaries.stream()
                .max(Comparator.comparing(EventSummary::samples))
                .map(summary -> Type.fromCode(summary.name()))
                .orElse(null);
    }

    @Override
    public ThreadCpuLoads threadCpuLoads(int limit) {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_CPU_LOAD)
                .withThreads()
                .withJsonFields();

        ThreadCpuLoads result = eventStreamRepository.genericStreaming(configurer, new CPULoadBuilder(limit));

        return new ThreadCpuLoads(result.user(), result.system());
    }

    @Override
    public ThreadRoot threadRows() {
        return threadInfoProvider.get();
    }

    @Override
    public ThreadGroupPage threadGroups(ThreadPageQuery query) {
        ThreadRoot root = threadInfoProvider.get();
        List<ThreadGroupMembers> groups = grouping().group(root.rows());

        List<ThreadGroupMembers> matched = query.hasNameFilter()
                ? groups.stream().filter(matchesName(query.nameFilter())).toList()
                : groups;

        // Only the lanes actually returned are merged: building a union walks every member's bands,
        // while ordering and filtering need no more than the totals each group already knows.
        List<ThreadGroup> page = matched.stream()
                .sorted(query.sort().groupComparator())
                .skip(query.offset())
                .limit(query.limit())
                .map(grouping()::merge)
                .toList();

        return new ThreadGroupPage(
                root.common(), page, query.offset(), matched.size(), groups.size(), root.rows().size());
    }

    @Override
    public ThreadPage threadGroupMembers(ThreadMembersQuery query) {
        ThreadRoot root = threadInfoProvider.get();
        List<ThreadRow> members = membersOf(root.rows(), query.groupKey());

        List<ThreadRow> page = members.stream()
                .sorted(query.sort().comparator())
                .skip(query.offset())
                .limit(query.limit())
                .toList();

        return new ThreadPage(root.common(), page, query.offset(), members.size(), root.rows().size());
    }

    @Override
    public List<ThreadInfo> threadGroupThreads(String groupKey) {
        return membersOf(threadInfoProvider.get().rows(), groupKey).stream()
                .map(ThreadRow::threadInfo)
                .toList();
    }

    /**
     * Every thread in a group, in the order the timeline built them. Unlike a page of members this
     * merges no bands, so resolving a 351-thread pool for a filter costs a grouping pass and nothing
     * more.
     */
    private List<ThreadRow> membersOf(List<ThreadRow> rows, String groupKey) {
        return grouping().group(rows).stream()
                .filter(group -> group.key().equals(groupKey))
                .findFirst()
                .map(ThreadGroupMembers::members)
                .orElse(List.of());
    }

    private ThreadGrouping grouping() {
        return new ThreadGrouping(ThreadBands.forRecording(profileInfo.duration()));
    }

    /**
     * A group matches when its own name does, or when any thread inside it does — otherwise a search
     * for one worker of a pool would come back empty just because the lane is named after the pool.
     */
    private static Predicate<ThreadGroupMembers> matchesName(String nameFilter) {
        String needle = nameFilter.toLowerCase(Locale.ROOT);
        return group -> group.key().toLowerCase(Locale.ROOT).contains(needle)
                || group.members().stream().anyMatch(row -> {
                    String name = row.threadInfo().name();
                    return name != null && name.toLowerCase(Locale.ROOT).contains(needle);
                });
    }

    @Override
    public ThreadWindowEvents threadEvents(ThreadEventsQuery query) {
        RelativeTimeRange window = storageWindow(query);

        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(query.state().eventType())
                .withSpecifiedThreads(query.threads())
                .withTimeRange(window)
                .withJsonFields();

        return eventStreamRepository.genericStreaming(
                configurer, new ThreadWindowEventsBuilder(window, query.limit()));
    }

    /**
     * Snaps the hovered window onto the millisecond grid the events are stored at.
     *
     * <p>The start rounds down and the end rounds up, so a window narrower than a millisecond still
     * selects the millisecond it falls in rather than nothing at all. The query's upper bound is
     * exclusive, which is what lets the windows of two neighbouring pixels tile without counting an
     * event under both of them.
     */
    private static RelativeTimeRange storageWindow(ThreadEventsQuery query) {
        long fromMillis = Math.floorDiv(query.from().toNanos(), NANOS_PER_MILLI);
        long toMillis = Math.ceilDiv(query.to().toNanos(), NANOS_PER_MILLI);
        if (toMillis <= fromMillis) {
            toMillis = fromMillis + MIN_WINDOW_MILLIS;
        }
        return new RelativeTimeRange(Duration.ofMillis(fromMillis), Duration.ofMillis(toMillis));
    }

    @Override
    public ThreadDumpAnalysis threadDumpAnalysis() {
        RelativeTimeRange timeRange = new RelativeTimeRange(profileInfo.profilingStartEnd());
        return ThreadDumpAnalyzer.analyze(rawDumps(), timeRange);
    }

    @Override
    public ParsedDump threadDump(int index) {
        List<RawDump> dumps = rawDumps();
        if (index < 0 || index >= dumps.size()) {
            return new ParsedDump(0, List.of(), List.of(), "");
        }
        RawDump raw = dumps.get(index);
        return ThreadDumpParser.parse(raw.timeOffsetMillis(), raw.text());
    }

    @Override
    public List<ReservedStackActivation> reservedStackActivations() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.RESERVED_STACK_ACTIVATION)
                .withJsonFields()
                .orderedByTime();

        return eventStreamRepository.genericStreaming(configurer, new ReservedStackActivationBuilder());
    }

    private List<RawDump> rawDumps() {
        EventQueryConfigurer configurer = new EventQueryConfigurer()
                .withEventType(Type.THREAD_DUMP)
                .withJsonFields()
                .orderedByTime();
        return eventStreamRepository.genericStreaming(configurer, new ThreadDumpBuilder(MAX_THREAD_DUMPS));
    }
}

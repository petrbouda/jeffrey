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

package pbouda.jeffrey.profile.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.jfrparser.api.type.JfrThread;
import pbouda.jeffrey.jfrparser.db.QueryBuilder;
import pbouda.jeffrey.jfrparser.db.RecordQuery;
import pbouda.jeffrey.persistence.profile.EventsReadRepository;

import java.time.Duration;
import java.util.*;

public class DbBasedThreadProvider implements ThreadInfoProvider {

    private static final Duration OFFSET_UNKNOWN = Duration.ofSeconds(-1);

    private static final Logger LOG = LoggerFactory.getLogger(DbBasedThreadProvider.class);

    private final static List<Type> TYPES = List.of(
            Type.THREAD_START,
            Type.THREAD_END,
            Type.THREAD_PARK,
            Type.THREAD_SLEEP,
            Type.JAVA_MONITOR_ENTER,
            Type.JAVA_MONITOR_WAIT,
            Type.SOCKET_READ,
            Type.SOCKET_WRITE,
            Type.FILE_READ,
            Type.FILE_WRITE);

    // Metadata to correctly create and format Tooltip
    private static final ThreadMetadata METADATA = new ThreadMetadata(
            new EventMetadata("Thread's Lifespan", ThreadState.STARTED,
                    List.of()),
            new EventMetadata("Java Thread Park", ThreadState.PARKED,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Parked On", "Class"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Until", "jdk.jfr.Timestamp"))),
            new EventMetadata("Java Monitor Blocked", ThreadState.BLOCKED,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Monitor Class", "Class"),
                            field("Previous Owner", "Thread"))),
            new EventMetadata("Java Monitor Wait", ThreadState.WAITING,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Monitor Class", "Class"),
                            field("Notifier Thread", "Thread"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Timed Out", "Boolean"))),
            new EventMetadata("Thread Sleep", ThreadState.SLEEP,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Sleep Time", "jdk.jfr.Timespan"))),
            new EventMetadata("Socket Read", ThreadState.SOCKET_READ,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Remote Host"),
                            field("Remote Addr"),
                            field("Remote Port"),
                            field("Timeout", "jdk.jfr.Timespan"),
                            field("Read Bytes", "jdk.jfr.DataAmount"),
                            field("End of Stream", "Boolean"))),
            new EventMetadata("Socket Write", ThreadState.SOCKET_WRITE,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Remote Host"),
                            field("Remote Addr"),
                            field("Remote Port"),
                            field("Read Written", "jdk.jfr.DataAmount"))),
            new EventMetadata("File Read", ThreadState.FILE_READ,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Path"),
                            field("Read Bytes", "jdk.jfr.DataAmount"),
                            field("End of File", "Boolean"))),
            new EventMetadata("File Write", ThreadState.FILE_WRITE,
                    List.of(field("Duration", "jdk.jfr.Timespan"),
                            field("Path"),
                            field("Read Written", "jdk.jfr.DataAmount"))
            )
    );

    private final ProfileInfo profileInfo;
    private final EventsReadRepository eventsReadRepository;

    private static ThreadField field(String value, String type) {
        return new ThreadField(value, type);
    }

    private static ThreadField field(String value) {
        return new ThreadField(value, null);
    }

    public DbBasedThreadProvider(EventsReadRepository eventsReadRepository, ProfileInfo profileInfo) {
        this.eventsReadRepository = eventsReadRepository;
        this.profileInfo = profileInfo;
    }

    @Override
    public ThreadRoot get() {
        RecordQuery recordQuery = QueryBuilder.events(TYPES)
                .withJsonFields()
                .withEventTypeInfo()
                .withThreads()
                .build();

        ThreadsRecordBuilder builder = new ThreadsRecordBuilder();
        eventsReadRepository.streamRecords(recordQuery)
                .forEach(builder::onRecord);
        List<ThreadRecord> records = builder.build();

        boolean containsWallClock = eventsReadRepository.containsEventType(Type.WALL_CLOCK_SAMPLE);
        ThreadCommon common = new ThreadCommon(profileInfo.duration().toNanos(), containsWallClock, METADATA);
        return new ThreadRoot(common, toThreadRows(records));
    }

    private List<ThreadRow> toThreadRows(List<ThreadRecord> combined) {
        Map<Long, List<ThreadRecord>> byJavaId = new HashMap<>();
        Map<Long, List<ThreadRecord>> byOsId = new HashMap<>();
        for (ThreadRecord threadRecord : combined) {
            JfrThread threadInfo = threadRecord.threadInfo();

            long javaId = threadInfo.javaThreadId();
            if (javaId != -1) {
                byJavaId.computeIfAbsent(threadInfo.javaThreadId(), id -> new ArrayList<>())
                        .add(threadRecord);
                continue;
            }

            long osId = threadInfo.osThreadId();
            if (osId != -1) {
                byOsId.computeIfAbsent(threadInfo.osThreadId(), id -> new ArrayList<>())
                        .add(threadRecord);
                continue;
            }

            LOG.error("Thread ID is not available!: {}", threadInfo);
        }

        return merge(byJavaId, byOsId);
    }


    private List<ThreadRow> merge(Map<Long, List<ThreadRecord>> first, Map<Long, List<ThreadRecord>> second) {
        List<ThreadRow> threadRows = new ArrayList<>();
        first.values().forEach(events -> threadRows.add(toThreadRow(events)));
        second.values().forEach(events -> threadRows.add(toThreadRow(events)));
        return threadRows;
    }

    private ThreadRow toThreadRow(List<ThreadRecord> events) {
        events.sort(Comparator.comparing(ThreadRecord::start));

        List<ThreadPeriod> active = new ArrayList<>();
        List<ThreadPeriod> parked = new ArrayList<>();
        List<ThreadPeriod> blocked = new ArrayList<>();
        List<ThreadPeriod> waiting = new ArrayList<>();
        List<ThreadPeriod> sleep = new ArrayList<>();
        List<ThreadPeriod> socketRead = new ArrayList<>();
        List<ThreadPeriod> socketWrite = new ArrayList<>();
        List<ThreadPeriod> fileRead = new ArrayList<>();
        List<ThreadPeriod> fileWrite = new ArrayList<>();

        Duration currentStartOffset = Duration.ZERO;
        Duration latestReportedOffset = Duration.ZERO;
        for (ThreadRecord event : events) {
            switch (event.state()) {
                case STARTED -> {
                    if (currentStartOffset != OFFSET_UNKNOWN && currentStartOffset != Duration.ZERO) {
                        LOG.warn("2 Thread Start in a row! Ignore the event: thread_info:{}", event.threadInfo());
                        continue;
                    }
                    currentStartOffset = Duration.between(profileInfo.startedAt(), event.start());
                }
                case ENDED -> {
                    Duration endOffset = Duration.between(profileInfo.startedAt(), event.start());
                    if (currentStartOffset == OFFSET_UNKNOWN) {
                        LOG.warn("2 Thread End in a row!: thread_info:{}", event.threadInfo());
                        active.add(new ThreadPeriod(latestReportedOffset, endOffset));
                    } else {
                        active.add(new ThreadPeriod(currentStartOffset, endOffset));
                        currentStartOffset = OFFSET_UNKNOWN;
                    }
                    latestReportedOffset = endOffset;
                }
                case PARKED -> parked.add(createEvent(event));
                case BLOCKED -> blocked.add(createEvent(event));
                case WAITING -> waiting.add(createEvent(event));
                case SLEEP -> sleep.add(createEvent(event));
                case SOCKET_READ -> socketRead.add(createEvent(event));
                case SOCKET_WRITE -> socketWrite.add(createEvent(event));
                case FILE_READ -> fileRead.add(createEvent(event));
                case FILE_WRITE -> fileWrite.add(createEvent(event));
            }
        }

        if (currentStartOffset != OFFSET_UNKNOWN) {
            Duration endOffset = Duration.between(profileInfo.startedAt(), profileInfo.finishedAt());
            active.add(new ThreadPeriod(currentStartOffset, endOffset));
        }

        ThreadRecord first = events.getFirst();

        // Calculate all events happened for this thread
        long eventsCount = parked.size()
                + blocked.size()
                + waiting.size()
                + sleep.size()
                + socketRead.size()
                + socketWrite.size();

        // Calculate the total duration of all lifespan events (total time of the thread being active)
        long totalDuration = active.stream()
                .mapToLong(ThreadPeriod::width)
                .reduce(0, Long::sum);

        return new ThreadRow(
                totalDuration,
                eventsCount,
                first.threadInfo(),
                active,
                parked,
                blocked,
                waiting,
                sleep,
                socketRead,
                socketWrite,
                fileRead,
                fileWrite);
    }

    private ThreadPeriod createEvent(ThreadRecord event) {
        return new ThreadPeriod(
                Duration.between(profileInfo.startedAt(), event.start()).toNanos(),
                Math.max(event.duration().toNanos(), 1),
                event.values());
    }
}

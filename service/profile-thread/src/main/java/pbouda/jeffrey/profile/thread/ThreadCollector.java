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
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.ThreadInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class ThreadCollector implements Collector<List<ThreadRecord>, List<ThreadRow>> {

    private static final Duration OFFSET_UNKNOWN = Duration.ofSeconds(-1);

    private static final Logger LOG = LoggerFactory.getLogger(ThreadCollector.class);

    private final Instant recordingStart;
    private final Instant recordingEnd;

    public ThreadCollector(Instant recordingStart, Instant recordingEnd) {
        this.recordingStart = recordingStart;
        this.recordingEnd = recordingEnd;
    }

    @Override
    public Supplier<List<ThreadRecord>> empty() {
        return List::of;
    }

    @Override
    public List<ThreadRecord> combiner(List<ThreadRecord> partial1, List<ThreadRecord> partial2) {
        partial1.addAll(partial2);
        return partial1;
    }

    @Override
    public List<ThreadRow> finisher(List<ThreadRecord> combined) {
        Map<Long, List<ThreadRecord>> byJavaId = new HashMap<>();
        Map<Long, List<ThreadRecord>> byOsId = new HashMap<>();
        for (ThreadRecord threadRecord : combined) {
            ThreadInfo threadInfo = threadRecord.threadInfo();

            long javaId = threadInfo.javaId();
            if (javaId != -1) {
                byJavaId.computeIfAbsent(threadInfo.javaId(), id -> new ArrayList<>())
                        .add(threadRecord);
                continue;
            }

            long osId = threadInfo.osId();
            if (osId != -1) {
                byOsId.computeIfAbsent(threadInfo.osId(), id -> new ArrayList<>())
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

        Duration currentStartOffset = Duration.ZERO;
        Duration latestReportedOffset = Duration.ZERO;
        for (ThreadRecord event : events) {
            switch (event.state()) {
                case STARTED -> {
                    if (currentStartOffset != OFFSET_UNKNOWN && currentStartOffset != Duration.ZERO) {
                        LOG.warn("2 Thread Start in a row! Ignore the event: thread_info:{}", event.threadInfo());
                        continue;
                    }
                    currentStartOffset = Duration.between(recordingStart, event.start());
                }
                case ENDED -> {
                    Duration endOffset = Duration.between(recordingStart, event.start());
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
            }
        }

        if (currentStartOffset != OFFSET_UNKNOWN) {
            Duration endOffset = Duration.between(recordingStart, recordingEnd);
            active.add(new ThreadPeriod(currentStartOffset, endOffset));
        }

        ThreadRecord first = events.getFirst();
        return new ThreadRow(first.threadInfo(), active, parked, blocked, waiting, sleep, socketRead, socketWrite);
    }

    private ThreadPeriod createEvent(ThreadRecord event) {
        return new ThreadPeriod(
                Duration.between(recordingStart, event.start()).toNanos(),
                Math.max(event.duration().toNanos(), 1),
                event.values());
    }
}

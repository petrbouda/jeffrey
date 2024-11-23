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

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class ThreadCollector implements Collector<List<ThreadRecord>, List<ThreadRow>> {

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


        List<ThreadRow> merged = merge(byJavaId, byOsId);
        return merged;
    }


    private List<ThreadRow> merge(Map<Long, List<ThreadRecord>> first, Map<Long, List<ThreadRecord>> second) {
        List<ThreadRow> threadRows = new ArrayList<>();
        first.values().forEach(events -> threadRows.add(toThreadRow(events)));
        second.values().forEach(events -> threadRows.add(toThreadRow(events)));
        return threadRows;
    }

    private ThreadRow toThreadRow(List<ThreadRecord> events) {
        events.sort(Comparator.comparing(ThreadRecord::start));

        List<ThreadLifespan> active = new ArrayList<>();
        List<ThreadEvent> parked = new ArrayList<>();
        List<ThreadEvent> blocked = new ArrayList<>();
        List<ThreadEvent> waiting = new ArrayList<>();

        long currentStartOffset = 0;
        long latestReportedOffset = 0;
        for (ThreadRecord event : events) {
            switch (event.state()) {
                case STARTED -> {
                    if (currentStartOffset != -1 && currentStartOffset != 0) {
                        LOG.warn("2 Thread Start in a row! Ignore the event: thread_info:{}", event.threadInfo());
                        continue;
                    }
                    currentStartOffset = calculateDiff(event.start(), recordingStart);
                }
                case ENDED -> {
                    long endOffset = calculateDiff(event.start(), recordingStart);
                    if (currentStartOffset == -1) {
                        LOG.warn("2 Thread End in a row!: thread_info:{}", event.threadInfo());
                        active.add(new ThreadLifespan(latestReportedOffset, endOffset, "Missing ThreadStart Event"));
                    } else {
                        active.add(new ThreadLifespan(currentStartOffset, endOffset));
                        currentStartOffset = -1;
                    }
                    latestReportedOffset = endOffset;
                }
                case PARKED -> {
                    parked.add(createEvent(event));
                }
                case BLOCKED -> {
                    blocked.add(createEvent(event));
                }
                case WAITING -> {
                    waiting.add(createEvent(event));
                }
            }
        }

        if (currentStartOffset != -1) {
            long endOffset = calculateDiff(recordingEnd, recordingStart);
            active.add(new ThreadLifespan(currentStartOffset, endOffset));
        }

        return new ThreadRow(events.getFirst().threadInfo(), active, parked, blocked, waiting);
    }

    private static long calculateDiff(Instant first, Instant second) {
        return first.toEpochMilli() - second.toEpochMilli();
    }

    private ThreadEvent createEvent(ThreadRecord event) {
        return new ThreadEvent(
                calculateDiff(event.start(), recordingStart),
                Math.max(event.duration().toMillis(), 1),
                event.state());
    }
}

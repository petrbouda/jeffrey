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

package cafe.jeffrey.profile.manager.model.thread.dump;

import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ParsedThread;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ThreadLock;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.DeadlockEntry;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.DumpDescriptor;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.FrameStat;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.Header;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.Heatmap;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.LockContention;
import cafe.jeffrey.profile.manager.model.thread.dump.ThreadDumpAnalysis.StuckThread;
import cafe.jeffrey.shared.common.model.time.RelativeTimeRange;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;
import cafe.jeffrey.timeseries.TimeseriesUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates a recording's {@code jdk.ThreadDump} occurrences into a {@link ThreadDumpAnalysis}: state
 * timeline, top frames, deadlocks, lock contention, stuck threads and a per-thread state heatmap.
 */
public final class ThreadDumpAnalyzer {

    private static final int MAX_TOP_FRAMES = 100;
    private static final int MAX_LOCK_CONTENTION = 50;
    private static final int MAX_HEATMAP_ROWS = 200;
    private static final int MIN_STUCK_DUMPS = 3;
    private static final int STUCK_SIGNATURE_FRAMES = 5;
    private static final long CARRY_FORWARD_MARK = 0L;

    private ThreadDumpAnalyzer() {
    }

    public static ThreadDumpAnalysis analyze(List<RawDump> rawDumps, RelativeTimeRange timeRange) {
        List<ParsedDump> dumps = new ArrayList<>(rawDumps.size());
        for (RawDump raw : rawDumps) {
            dumps.add(ThreadDumpParser.parse(raw.timeOffsetMillis(), raw.text()));
        }

        List<DumpDescriptor> descriptors = descriptors(dumps);
        TimeseriesData stateTimeline = stateTimeline(dumps, timeRange);
        List<FrameStat> topFrames = topFrames(dumps);
        List<DeadlockEntry> deadlocks = deadlocks(dumps);
        List<LockContention> lockContention = lockContention(dumps);
        List<StuckThread> stuckThreads = stuckThreads(dumps);
        Heatmap heatmap = heatmap(dumps, stuckThreads);
        Header header = header(dumps, deadlocks.size(), stuckThreads.size());

        return new ThreadDumpAnalysis(
                header, descriptors, stateTimeline, topFrames, deadlocks, lockContention, stuckThreads, heatmap);
    }

    private static List<DumpDescriptor> descriptors(List<ParsedDump> dumps) {
        List<DumpDescriptor> result = new ArrayList<>(dumps.size());
        for (int i = 0; i < dumps.size(); i++) {
            ParsedDump dump = dumps.get(i);
            result.add(new DumpDescriptor(i, dump.timeOffsetMillis(), dump.threads().size(), dump.deadlocks().size()));
        }
        return result;
    }

    private static TimeseriesData stateTimeline(List<ParsedDump> dumps, RelativeTimeRange timeRange) {
        Map<ThreadState, LongLongHashMap> seriesByState = new LinkedHashMap<>();
        for (ThreadState state : ThreadState.values()) {
            seriesByState.put(state, TimeseriesUtils.initWithZeros(timeRange));
        }

        for (ParsedDump dump : dumps) {
            long second = dump.timeOffsetMillis() / 1000;
            Map<ThreadState, Long> counts = new HashMap<>();
            for (ParsedThread thread : dump.threads()) {
                counts.merge(thread.state(), 1L, Long::sum);
            }
            counts.forEach((state, count) -> seriesByState.get(state).put(second, count));
        }

        List<SingleSerie> series = new ArrayList<>();
        for (Map.Entry<ThreadState, LongLongHashMap> entry : seriesByState.entrySet()) {
            if (!hasNonZero(entry.getValue())) {
                continue;
            }
            SingleSerie serie = TimeseriesUtils.buildSerie(entry.getKey().name(), entry.getValue());
            TimeseriesUtils.remapTimeseriesBySteps(serie, CARRY_FORWARD_MARK);
            series.add(serie);
        }
        return new TimeseriesData(series);
    }

    private static boolean hasNonZero(LongLongHashMap series) {
        return series.values().anySatisfy(value -> value != 0);
    }

    private static List<FrameStat> topFrames(List<ParsedDump> dumps) {
        Map<String, long[]> occurrences = new HashMap<>();
        Map<String, Set<String>> distinctThreads = new HashMap<>();
        for (ParsedDump dump : dumps) {
            for (ParsedThread thread : dump.threads()) {
                String top = thread.topFrame();
                if (top == null) {
                    continue;
                }
                occurrences.computeIfAbsent(top, key -> new long[1])[0]++;
                distinctThreads.computeIfAbsent(top, key -> new HashSet<>()).add(thread.name());
            }
        }
        return occurrences.entrySet().stream()
                .map(entry -> new FrameStat(
                        entry.getKey(), entry.getValue()[0], distinctThreads.get(entry.getKey()).size()))
                .sorted(Comparator.comparingLong(FrameStat::occurrences).reversed())
                .limit(MAX_TOP_FRAMES)
                .toList();
    }

    private static List<DeadlockEntry> deadlocks(List<ParsedDump> dumps) {
        List<DeadlockEntry> result = new ArrayList<>();
        for (int i = 0; i < dumps.size(); i++) {
            ParsedDump dump = dumps.get(i);
            for (ParsedDump.Deadlock deadlock : dump.deadlocks()) {
                result.add(new DeadlockEntry(
                        i, dump.timeOffsetMillis(), deadlock.description(), deadlock.involvedThreads()));
            }
        }
        return result;
    }

    private static List<LockContention> lockContention(List<ParsedDump> dumps) {
        ParsedDump worst = null;
        int worstWaiters = 0;
        for (ParsedDump dump : dumps) {
            int waiters = countWaiters(dump);
            if (waiters > worstWaiters) {
                worstWaiters = waiters;
                worst = dump;
            }
        }
        if (worst == null) {
            return List.of();
        }

        Map<String, int[]> waiterCounts = new HashMap<>();
        Map<String, String> monitorClasses = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
        for (ParsedThread thread : worst.threads()) {
            for (ThreadLock lock : thread.locks()) {
                if (lock.monitorId() == null) {
                    continue;
                }
                if (lock.monitorClass() != null) {
                    monitorClasses.putIfAbsent(lock.monitorId(), lock.monitorClass());
                }
                if (lock.kind() == ThreadLock.Kind.LOCKED) {
                    owners.putIfAbsent(lock.monitorId(), thread.name());
                } else if (lock.kind() == ThreadLock.Kind.WAITING_TO_LOCK
                        || lock.kind() == ThreadLock.Kind.PARKING_TO_WAIT) {
                    waiterCounts.computeIfAbsent(lock.monitorId(), key -> new int[1])[0]++;
                }
            }
        }

        return waiterCounts.entrySet().stream()
                .map(entry -> new LockContention(
                        entry.getKey(),
                        monitorClasses.get(entry.getKey()),
                        entry.getValue()[0],
                        owners.get(entry.getKey())))
                .sorted(Comparator.comparingInt(LockContention::waiterCount).reversed())
                .limit(MAX_LOCK_CONTENTION)
                .toList();
    }

    private static int countWaiters(ParsedDump dump) {
        int waiters = 0;
        for (ParsedThread thread : dump.threads()) {
            for (ThreadLock lock : thread.locks()) {
                if (lock.kind() == ThreadLock.Kind.WAITING_TO_LOCK
                        || lock.kind() == ThreadLock.Kind.PARKING_TO_WAIT) {
                    waiters++;
                }
            }
        }
        return waiters;
    }

    private static List<StuckThread> stuckThreads(List<ParsedDump> dumps) {
        List<Map<String, ParsedThread>> byName = byName(dumps);
        Set<String> names = new java.util.LinkedHashSet<>();
        byName.forEach(map -> names.addAll(map.keySet()));

        List<StuckThread> result = new ArrayList<>();
        for (String name : names) {
            int bestLen = 0;
            int bestStart = -1;
            int bestEnd = -1;
            int curLen = 0;
            int curStart = -1;
            int prevIdx = -2;
            String curSignature = null;
            for (int i = 0; i < dumps.size(); i++) {
                ParsedThread thread = byName.get(i).get(name);
                if (thread == null || thread.topFrame() == null) {
                    curLen = 0;
                    curSignature = null;
                    prevIdx = -2;
                    continue;
                }
                String signature = signature(thread);
                if (curSignature != null && curSignature.equals(signature) && i == prevIdx + 1) {
                    curLen++;
                } else {
                    curLen = 1;
                    curStart = i;
                    curSignature = signature;
                }
                prevIdx = i;
                if (curLen > bestLen) {
                    bestLen = curLen;
                    bestStart = curStart;
                    bestEnd = i;
                }
            }
            if (bestLen >= MIN_STUCK_DUMPS) {
                ParsedThread last = byName.get(bestEnd).get(name);
                long stuckFor = dumps.get(bestEnd).timeOffsetMillis() - dumps.get(bestStart).timeOffsetMillis();
                result.add(new StuckThread(name, last.state(), last.topFrame(), bestLen, stuckFor));
            }
        }
        result.sort(Comparator.comparingInt(StuckThread::consecutiveDumps).reversed());
        return result;
    }

    private static String signature(ParsedThread thread) {
        List<String> frames = thread.frames();
        return String.join("\n", frames.subList(0, Math.min(STUCK_SIGNATURE_FRAMES, frames.size())));
    }

    private static List<Map<String, ParsedThread>> byName(List<ParsedDump> dumps) {
        List<Map<String, ParsedThread>> byName = new ArrayList<>(dumps.size());
        for (ParsedDump dump : dumps) {
            Map<String, ParsedThread> map = new HashMap<>();
            for (ParsedThread thread : dump.threads()) {
                map.putIfAbsent(thread.name(), thread);
            }
            byName.add(map);
        }
        return byName;
    }

    private static Heatmap heatmap(List<ParsedDump> dumps, List<StuckThread> stuckThreads) {
        List<Map<String, ParsedThread>> byName = byName(dumps);
        Map<String, Integer> presence = new LinkedHashMap<>();
        Set<String> priority = new java.util.LinkedHashSet<>();
        for (Map<String, ParsedThread> map : byName) {
            for (ParsedThread thread : map.values()) {
                presence.merge(thread.name(), 1, Integer::sum);
                if (thread.state() == ThreadState.BLOCKED) {
                    priority.add(thread.name());
                }
            }
        }
        stuckThreads.forEach(stuck -> priority.add(stuck.name()));

        List<String> rowNames = new ArrayList<>(priority);
        presence.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .filter(name -> !priority.contains(name))
                .forEach(rowNames::add);
        if (rowNames.size() > MAX_HEATMAP_ROWS) {
            rowNames = rowNames.subList(0, MAX_HEATMAP_ROWS);
        }

        List<Long> offsets = dumps.stream().map(ParsedDump::timeOffsetMillis).toList();
        List<Heatmap.Row> rows = new ArrayList<>(rowNames.size());
        for (String name : rowNames) {
            List<ThreadState> states = new ArrayList<>(dumps.size());
            for (Map<String, ParsedThread> map : byName) {
                ParsedThread thread = map.get(name);
                states.add(thread == null ? null : thread.state());
            }
            rows.add(new Heatmap.Row(name, states));
        }
        return new Heatmap(offsets, rows);
    }

    private static Header header(List<ParsedDump> dumps, int deadlockCount, int stuckCount) {
        int peakThreads = dumps.stream().mapToInt(dump -> dump.threads().size()).max().orElse(0);
        long firstOffset = dumps.isEmpty() ? 0 : dumps.getFirst().timeOffsetMillis();
        long lastOffset = dumps.isEmpty() ? 0 : dumps.getLast().timeOffsetMillis();
        return new Header(dumps.size(), peakThreads, deadlockCount, stuckCount, firstOffset, lastOffset);
    }
}

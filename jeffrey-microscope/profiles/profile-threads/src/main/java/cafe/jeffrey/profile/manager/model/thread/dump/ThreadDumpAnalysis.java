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

import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;

/**
 * Cross-dump analysis of all {@code jdk.ThreadDump} occurrences in a recording. Heavy per-thread stacks
 * are excluded here (fetched lazily per dump via {@link ParsedDump}); this is the compact overview.
 *
 * @param header        headline counters
 * @param dumps         per-dump descriptors (for the dump selector), no stacks
 * @param stateTimeline thread counts per state across dumps, one carried-forward series per state
 * @param topFrames     the stack frames threads sit at most often, across all dumps
 * @param deadlocks     JVM-reported deadlocks, with the dump they appeared in
 * @param lockContention most-contended monitors in the worst dump
 * @param stuckThreads  threads whose stack stayed identical across consecutive dumps
 * @param heatmap       per-thread state across the dump sequence (capped rows)
 */
public record ThreadDumpAnalysis(
        Header header,
        List<DumpDescriptor> dumps,
        TimeseriesData stateTimeline,
        List<FrameStat> topFrames,
        List<DeadlockEntry> deadlocks,
        List<LockContention> lockContention,
        List<StuckThread> stuckThreads,
        Heatmap heatmap) {

    public record Header(
            int dumpCount,
            int peakThreadCount,
            int deadlockCount,
            int stuckThreadCount,
            long firstOffsetMillis,
            long lastOffsetMillis) {
    }

    public record DumpDescriptor(int index, long timeOffsetMillis, int threadCount, int deadlockCount) {
    }

    public record FrameStat(String frame, long occurrences, int distinctThreads) {
    }

    public record DeadlockEntry(int dumpIndex, long timeOffsetMillis, String description, List<String> involvedThreads) {
    }

    public record LockContention(String monitorId, String monitorClass, int waiterCount, String owner) {
    }

    public record StuckThread(
            String name,
            ThreadState state,
            String topFrame,
            int consecutiveDumps,
            long stuckForMillis) {
    }

    /**
     * @param dumpOffsets the dump time offsets (columns)
     * @param rows        per-thread state across the dump sequence; a {@code null} cell means the thread
     *                    was absent from that dump
     */
    public record Heatmap(List<Long> dumpOffsets, List<Row> rows) {
        public record Row(String threadName, List<ThreadState> states) {
        }
    }
}

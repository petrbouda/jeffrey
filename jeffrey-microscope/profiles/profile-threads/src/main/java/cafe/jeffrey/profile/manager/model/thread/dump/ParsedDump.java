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

import java.util.List;

/**
 * One parsed thread dump ({@code jdk.ThreadDump.result}): its threads, any JVM-reported deadlocks, and
 * the original raw text (kept so the UI can always fall back to the verbatim dump).
 *
 * @param timeOffsetMillis offset of the dump from the recording start
 * @param threads          parsed per-thread entries
 * @param deadlocks        JVM-reported Java-level deadlocks found in the dump
 * @param rawText          the original dump text
 */
public record ParsedDump(
        long timeOffsetMillis,
        List<ParsedThread> threads,
        List<Deadlock> deadlocks,
        String rawText) {

    /**
     * A single thread within a dump.
     *
     * @param name   thread name
     * @param group  pool/group name (numeric/worker suffix stripped from {@code name})
     * @param state  parsed thread state
     * @param frames stack frames, top first (the text after {@code "at "})
     * @param locks  monitor lock operations recorded for the thread
     */
    public record ParsedThread(
            String name,
            String group,
            ThreadState state,
            List<String> frames,
            List<ThreadLock> locks) {

        public String topFrame() {
            return frames.isEmpty() ? null : frames.getFirst();
        }
    }

    /**
     * A monitor lock operation on a thread's stack.
     */
    public record ThreadLock(Kind kind, String monitorId, String monitorClass) {
        public enum Kind {
            LOCKED,
            WAITING_TO_LOCK,
            WAITING_ON,
            PARKING_TO_WAIT
        }
    }

    /**
     * A JVM-reported Java-level deadlock.
     *
     * @param description     the deadlock section text
     * @param involvedThreads names of the threads in the deadlock cycle
     */
    public record Deadlock(String description, List<String> involvedThreads) {
    }
}

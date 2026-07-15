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

import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.Deadlock;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ParsedThread;
import cafe.jeffrey.profile.manager.model.thread.dump.ParsedDump.ThreadLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the {@code jdk.ThreadDump.result} text (a {@code jstack}-style dump) into structured threads,
 * lock operations and JVM-reported deadlocks.
 * <p>
 * The format is not contractual, so parsing is heuristic and degrades gracefully: a line is a thread
 * header only when it starts with a quoted name <em>and</em> carries thread metadata ({@code tid=} /
 * {@code nid=} / {@code prio=} / {@code #<id>}) — which distinguishes real headers from the quoted names
 * inside the deadlock summary. Anything unrecognized is ignored, and the caller always retains the raw
 * text.
 */
public final class ThreadDumpParser {

    private static final String STATE_MARKER = "java.lang.Thread.State:";
    private static final String FRAME_PREFIX = "at ";
    private static final String DEADLOCK_MARKER = "Java-level deadlock";
    private static final String DEADLOCK_STACKS_BOUNDARY = "Java stack information for the threads listed above";

    private static final Pattern HEADER_METADATA = Pattern.compile("tid=|nid=|prio=|\" #\\d+");
    private static final Pattern MONITOR_ID = Pattern.compile("<(0x[0-9a-fA-F]+)>");
    private static final Pattern MONITOR_CLASS = Pattern.compile("\\(a ([^)]+)\\)");
    private static final Pattern QUOTED_NAME = Pattern.compile("\"([^\"]+)\"");

    private ThreadDumpParser() {
    }

    public static ParsedDump parse(long timeOffsetMillis, String text) {
        if (text == null || text.isBlank()) {
            return new ParsedDump(timeOffsetMillis, List.of(), List.of(), text == null ? "" : text);
        }

        String[] lines = text.split("\n", -1);
        List<ParsedThread> threads = parseThreads(lines);
        List<Deadlock> deadlocks = parseDeadlocks(lines);
        return new ParsedDump(timeOffsetMillis, threads, deadlocks, text);
    }

    private static List<ParsedThread> parseThreads(String[] lines) {
        List<ParsedThread> threads = new ArrayList<>();
        ThreadAccumulator current = null;
        for (String line : lines) {
            if (isThreadHeader(line)) {
                if (current != null) {
                    threads.add(current.toThread());
                }
                current = new ThreadAccumulator(threadName(line));
            } else if (current != null) {
                current.consume(line);
            }
        }
        if (current != null) {
            threads.add(current.toThread());
        }
        return threads;
    }

    private static boolean isThreadHeader(String line) {
        return !line.isEmpty() && line.charAt(0) == '"' && HEADER_METADATA.matcher(line).find();
    }

    private static String threadName(String headerLine) {
        int close = headerLine.indexOf('"', 1);
        return close > 0 ? headerLine.substring(1, close) : headerLine;
    }

    private static List<Deadlock> parseDeadlocks(String[] lines) {
        List<Deadlock> deadlocks = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains(DEADLOCK_MARKER)) {
                continue;
            }
            StringBuilder description = new StringBuilder();
            Set<String> involved = new java.util.LinkedHashSet<>();
            int j = i;
            for (; j < lines.length && !lines[j].contains(DEADLOCK_STACKS_BOUNDARY); j++) {
                description.append(lines[j]).append('\n');
                Matcher nameMatcher = QUOTED_NAME.matcher(lines[j]);
                while (nameMatcher.find()) {
                    involved.add(nameMatcher.group(1));
                }
            }
            deadlocks.add(new Deadlock(description.toString().strip(), List.copyOf(involved)));
            i = j;
        }
        return deadlocks;
    }

    /**
     * Strips a trailing pool index from a thread name so worker threads collapse into one group, e.g.
     * {@code http-nio-8080-exec-7} → {@code http-nio-8080-exec} and {@code ForkJoinPool-1-worker-3} →
     * {@code ForkJoinPool-1-worker}.
     */
    static String poolGroup(String name) {
        String group = name.replaceAll("[-#]?\\d+$", "");
        group = group.replaceAll("[-#]$", "");
        return group.isBlank() ? name : group;
    }

    private static final class ThreadAccumulator {
        private final String name;
        private ThreadState state = ThreadState.UNKNOWN;
        private final List<String> frames = new ArrayList<>();
        private final List<ThreadLock> locks = new ArrayList<>();

        private ThreadAccumulator(String name) {
            this.name = name;
        }

        private void consume(String line) {
            String trimmed = line.strip();
            int stateAt = line.indexOf(STATE_MARKER);
            if (stateAt >= 0) {
                state = ThreadState.fromLabel(line.substring(stateAt + STATE_MARKER.length()));
            } else if (trimmed.startsWith(FRAME_PREFIX)) {
                frames.add(trimmed.substring(FRAME_PREFIX.length()).strip());
            } else if (trimmed.startsWith("- ")) {
                ThreadLock lock = parseLock(trimmed);
                if (lock != null) {
                    locks.add(lock);
                }
            }
        }

        private ParsedThread toThread() {
            return new ParsedThread(name, poolGroup(name), state, List.copyOf(frames), List.copyOf(locks));
        }
    }

    private static ThreadLock parseLock(String trimmed) {
        ThreadLock.Kind kind = lockKind(trimmed);
        if (kind == null) {
            return null;
        }
        Matcher idMatcher = MONITOR_ID.matcher(trimmed);
        String monitorId = idMatcher.find() ? idMatcher.group(1) : null;
        Matcher classMatcher = MONITOR_CLASS.matcher(trimmed);
        String monitorClass = classMatcher.find() ? classMatcher.group(1) : null;
        return new ThreadLock(kind, monitorId, monitorClass);
    }

    private static ThreadLock.Kind lockKind(String trimmed) {
        if (trimmed.startsWith("- locked")) {
            return ThreadLock.Kind.LOCKED;
        }
        if (trimmed.startsWith("- waiting to lock")) {
            return ThreadLock.Kind.WAITING_TO_LOCK;
        }
        if (trimmed.startsWith("- parking to wait for")) {
            return ThreadLock.Kind.PARKING_TO_WAIT;
        }
        if (trimmed.startsWith("- waiting on")) {
            return ThreadLock.Kind.WAITING_ON;
        }
        return null;
    }
}

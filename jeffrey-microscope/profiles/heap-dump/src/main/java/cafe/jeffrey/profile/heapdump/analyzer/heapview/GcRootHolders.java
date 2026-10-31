/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.OptionalInt;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableInt;
import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableLong;

/**
 * What keeps a GC root alive: the kind of root it is and, for a root that sits on a thread's stack
 * (a Java frame's local, a JNI local, a native-stack or thread-block reference), the thread and the
 * frame holding it. "Held by a Java frame" alone does not say which code to read; the frame does.
 *
 * <p>The frame comes from the root's {@code thread_serial} and {@code frame_index} through the
 * thread root's explicit stack-trace serial; the thread's name from the thread object rooted under the same serial.
 */
public final class GcRootHolders {

    /**
     * The root's kind, with its thread and frame where the dump records them.
     *
     * @param rootType   the kind of root, as {@link HprofTag.Sub#rootKindName(int)} names it
     * @param threadName the name of the thread the root belongs to; null for a root on no thread
     * @param stackFrame the frame holding it, {@code class.method(File.java:line)}; null for a root
     *                   that is not a frame's local, or when the dump has no stack trace for it
     */
    public record Holder(String rootType, String threadName, String stackFrame) {

        public Holder {
            if (rootType == null || rootType.isBlank()) {
                throw new IllegalArgumentException("rootType must not be blank");
            }
        }
    }

    /** The selected root and the thread root that identifies its holding stack trace. */
    private static final String HOLDER_SQL = """
            SELECT g.root_kind, g.thread_serial, g.frame_index, t.instance_id, t.file_offset
            FROM gc_root g
            LEFT JOIN gc_root t ON t.thread_serial = g.thread_serial AND t.root_kind = %d
            WHERE g.instance_id = ?
            ORDER BY g.root_kind, t.instance_id
            LIMIT 1
            """.formatted(HprofTag.Sub.ROOT_THREAD_OBJECT);

    private static final String FRAME_SQL = """
            SELECT sf.class_name, sf.method_name, sf.source_file, sf.line_number
            FROM stack_trace_frame stf
            JOIN stack_frame sf ON sf.frame_id = stf.frame_id
            WHERE stf.thread_serial = ? AND stf.frame_index = ? AND stf.trace_serial = ?
            """;

    /** Without raw root bytes, only an unambiguous recorded trace is safe to name. */
    private static final String UNIQUE_TRACE_SQL = """
            SELECT min(trace_serial) FROM stack_trace_frame
            WHERE thread_serial = ?
            HAVING count(DISTINCT trace_serial) = 1
            """;

    /** HPROF's line-number sentinel for a native method. */
    private static final int LINE_NUMBER_NATIVE = -3;

    private static final String NATIVE_METHOD = "Native Method";

    private static final String UNKNOWN_SOURCE = "Unknown Source";

    private GcRootHolders() {
    }

    /** The holder of {@code instanceId}, or empty when it is not a GC root. */
    public static Optional<Holder> of(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(HOLDER_SQL)) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String rootType = HprofTag.Sub.rootKindName(rs.getInt(1));
                Integer threadSerial = nullableInt(rs, 2);
                Integer frameIndex = nullableInt(rs, 3);
                Long threadId = nullableLong(rs, 4);
                Long threadRootOffset = nullableLong(rs, 5);
                String frame = frameOf(view, threadSerial, frameIndex, threadRootOffset);
                String threadName = threadId == null ? null : ThreadAnalyzer.nameOf(view, threadId).orElse(null);
                return Optional.of(new Holder(rootType, threadName, frame));
            }
        }
    }

    private static String frameOf(HeapView view, Integer threadSerial, Integer frameIndex, Long threadRootOffset)
            throws SQLException {
        if (threadSerial == null || frameIndex == null || frameIndex < 0) {
            return null;
        }
        OptionalInt trace = threadRootOffset == null ? OptionalInt.empty() : view.threadRootTraceSerial(threadRootOffset);
        if (trace.isEmpty()) {
            trace = uniqueTrace(view, threadSerial);
        }
        if (trace.isEmpty()) {
            return null;
        }
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(FRAME_SQL)) {
            stmt.setInt(1, threadSerial);
            stmt.setInt(2, frameIndex);
            stmt.setInt(3, trace.getAsInt());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? frame(rs.getString(1), rs.getString(2), rs.getString(3), nullableInt(rs, 4)) : null;
            }
        }
    }

    private static OptionalInt uniqueTrace(HeapView view, int threadSerial) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(UNIQUE_TRACE_SQL)) {
            stmt.setInt(1, threadSerial);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty();
            }
        }
    }

    /** {@code class.method(File.java:line)} as a stack trace prints it, or null without a frame. */
    private static String frame(String className, String methodName, String sourceFile, Integer line) {
        if (className == null || methodName == null) {
            return null;
        }
        String location;
        if (line != null && line == LINE_NUMBER_NATIVE) {
            location = NATIVE_METHOD;
        } else if (sourceFile == null) {
            location = UNKNOWN_SOURCE;
        } else if (line != null && line > 0) {
            location = sourceFile + ":" + line;
        } else {
            location = sourceFile;
        }
        return className + "." + methodName + "(" + location + ")";
    }
}

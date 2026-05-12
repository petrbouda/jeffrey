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
package cafe.jeffrey.profile.heapdump.analyzer.heapview;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.HeapThreadState;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.ThreadAnalyzer}.
 *
 * Discovers thread objects via {@code gc_root} entries with
 * {@code root_kind = ROOT_THREAD_OBJECT} and decodes each one's instance fields.
 * For the {@code name} field (a {@code String} reference) the
 * {@link JavaStringDecoder} is used so the output matches what the JVM would
 * have surfaced.
 *
 * Stack frames are handled separately by {@link ThreadStackAnalyzer}, which
 * joins {@code gc_root} (for the thread's {@code thread_serial}) with
 * {@code stack_trace_frame} + {@code stack_frame}. {@link HeapThreadInfo}
 * intentionally doesn't carry frames so the lightweight thread list can be
 * pre-computed up front without pulling every frame for every thread.
 *
 * {@code retainedSize} is null until callers run
 * {@link cafe.jeffrey.profile.heapdump.parser.DominatorTreeBuilder}. When the
 * dominator tree is present, retained size is populated from the index.
 */
public final class ThreadAnalyzer {

    private ThreadAnalyzer() {
    }

    /**
     * Pulls every ROOT_THREAD_OBJECT row joined to its top-of-stack frame, frame
     * count, and locals stats in a single SQL pass. LEFT JOINs cover threads
     * with no STACK_TRACE record (which we still want to surface — just with
     * null stack stats).
     */
    private static final String THREAD_BULK_SQL = """
            WITH frame_counts AS (
                SELECT thread_serial, COUNT(*) AS frame_count
                FROM stack_trace_frame
                GROUP BY thread_serial
            ),
            top_frame AS (
                SELECT stf.thread_serial, sf.class_name, sf.method_name, sf.line_number
                FROM stack_trace_frame stf
                JOIN stack_frame sf ON sf.frame_id = stf.frame_id
                WHERE stf.frame_index = 0
            ),
            local_stats AS (
                SELECT gr.thread_serial,
                       COUNT(*)                AS locals_count,
                       SUM(i.shallow_size)     AS locals_bytes
                FROM gc_root gr
                JOIN instance i ON i.instance_id = gr.instance_id
                WHERE gr.root_kind = """ + HprofTag.Sub.ROOT_JAVA_FRAME + """
                GROUP BY gr.thread_serial
            )
            SELECT  gr.instance_id,
                    gr.thread_serial,
                    fc.frame_count,
                    tf.class_name  AS top_class,
                    tf.method_name AS top_method,
                    tf.line_number AS top_line,
                    ls.locals_count,
                    ls.locals_bytes
            FROM gc_root gr
            LEFT JOIN frame_counts fc ON fc.thread_serial = gr.thread_serial
            LEFT JOIN top_frame    tf ON tf.thread_serial = gr.thread_serial
            LEFT JOIN local_stats  ls ON ls.thread_serial = gr.thread_serial
            WHERE gr.root_kind = """ + HprofTag.Sub.ROOT_THREAD_OBJECT;

    public static List<HeapThreadInfo> analyze(HeapView view) throws SQLException {
        boolean haveRetained = view.hasDominatorTree();
        List<HeapThreadInfo> out = new ArrayList<>();

        try (Statement stmt = view.connection().createStatement();
             ResultSet rs = stmt.executeQuery(THREAD_BULK_SQL)) {
            while (rs.next()) {
                long threadInstanceId = rs.getLong(1);
                Integer frameCount = nullableInt(rs, 3);
                String topClass = rs.getString(4);
                String topMethod = rs.getString(5);
                Integer topLine = nullableInt(rs, 6);
                Integer localsCount = nullableInt(rs, 7);
                Long localsBytes = nullableLong(rs, 8);
                HeapThreadState state = deriveState(topClass, topMethod, topLine);
                Optional<HeapThreadInfo> info = decodeThread(
                        view, threadInstanceId, haveRetained,
                        frameCount, localsCount, localsBytes, state);
                info.ifPresent(out::add);
            }
        }
        return out;
    }

    // Fully qualified Class.method names of JDK blocking primitives, grouped
    // by the HeapThreadState they imply on the top of stack. Compared with
    // string equality below; kept as constants so the list is easy to extend
    // if new top-frame patterns appear (newer LockSupport entry points,
    // Loom carrier-thread frames, …).
    private static final String UNSAFE_PARK = "jdk.internal.misc.Unsafe.park";
    private static final String UNSAFE_PARK_LEGACY = "sun.misc.Unsafe.park";
    private static final String OBJECT_WAIT = "java.lang.Object.wait";
    private static final String OBJECT_WAIT0 = "java.lang.Object.wait0";
    private static final String THREAD_SLEEP = "java.lang.Thread.sleep";
    private static final String THREAD_SLEEP0 = "java.lang.Thread.sleep0";
    private static final String THREAD_SLEEP_NANOS = "java.lang.Thread.sleepNanos";

    // HPROF line-number sentinel for "native frame".
    private static final int LINE_NUMBER_NATIVE = -3;

    /**
     * Heuristic thread state from the top frame's class + method. HPROF doesn't
     * record the JVM's actual thread status, but the top frame is the most
     * reliable signal for "what is this thread waiting on?". Returns
     * {@code null} when no stack data is available.
     */
    private static HeapThreadState deriveState(String topClass, String topMethod, Integer topLine) {
        if (topClass == null || topMethod == null) {
            return null;
        }
        String fq = topClass + "." + topMethod;
        if (UNSAFE_PARK.equals(fq) || UNSAFE_PARK_LEGACY.equals(fq)) {
            return HeapThreadState.PARKED;
        }
        if (OBJECT_WAIT.equals(fq) || OBJECT_WAIT0.equals(fq)) {
            return HeapThreadState.WAITING;
        }
        if (THREAD_SLEEP.equals(fq) || THREAD_SLEEP0.equals(fq) || THREAD_SLEEP_NANOS.equals(fq)) {
            return HeapThreadState.SLEEPING;
        }
        if (topLine != null && topLine == LINE_NUMBER_NATIVE) {
            return HeapThreadState.NATIVE;
        }
        return HeapThreadState.RUNNABLE;
    }

    private static Optional<HeapThreadInfo> decodeThread(
            HeapView view, long instanceId, boolean haveRetained,
            Integer frameCount, Integer localsCount, Long localsBytes, HeapThreadState state) throws SQLException {
        List<InstanceFieldValue> fields;
        try {
            fields = view.readInstanceFields(instanceId);
        } catch (IllegalStateException noHprof) {
            return Optional.empty();
        }
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        String name = "<unknown>";
        boolean daemon = false;
        int priority = 0;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case "name" -> {
                    if (f.value() instanceof Long stringRef && stringRef != 0L) {
                        Optional<JavaStringDecoder.Decoded> decoded =
                                JavaStringDecoder.decode(view, stringRef);
                        if (decoded.isPresent()) {
                            name = decoded.get().content();
                        }
                    }
                }
                case "daemon" -> {
                    if (f.value() instanceof Boolean b) {
                        daemon = b;
                    }
                }
                case "priority" -> {
                    if (f.value() instanceof Integer i) {
                        priority = i;
                    }
                }
                default -> {
                    // ignore other Thread fields (group, threadStatus, target, etc.)
                }
            }
        }

        Long retained = haveRetained ? probeRetainedSize(view, instanceId) : null;
        return Optional.of(new HeapThreadInfo(
                instanceId, name, daemon, priority, retained,
                frameCount, localsCount, localsBytes, state));
    }

    private static Long probeRetainedSize(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }
}

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.HeapThreadInfo;
import cafe.jeffrey.profile.heapdump.model.HeapThreadState;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.view.InstanceColumns;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JdkFieldNames;

import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableInt;
import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableLong;

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
 * {@link cafe.jeffrey.profile.heapdump.persistence.DominatorTreeBuilder}. When the
 * dominator tree is present, retained size is populated from the index.
 */
public final class ThreadAnalyzer {

    private static final String UNKNOWN_NAME = "<unknown>";

    private ThreadAnalyzer() {
    }

    /**
     * Pulls every ROOT_THREAD_OBJECT row joined to its top-of-stack frame, frame
     * count, and locals stats in a single SQL pass. LEFT JOINs cover threads
     * with no STACK_TRACE record (which we still want to surface — just with
     * null stack stats). The thread's own instance row and retained size come
     * along, so decoding a thread costs no lookup of its own: three per thread
     * was what this analysis spent, and DuckDB 2.0's parser makes each one dearer.
     * A thread object with no instance row has no fields to decode and is left out.
     * Ordered by object id, so two threads a caller ranks equal keep one order.
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
            SELECT  fc.frame_count,
                    tf.class_name  AS top_class,
                    tf.method_name AS top_method,
                    tf.line_number AS top_line,
                    ls.locals_count,
                    ls.locals_bytes,
                    ret.bytes      AS retained_bytes,
                    """ + InstanceColumns.of("i") + "\n" + """
            FROM gc_root gr
            JOIN instance i ON i.instance_id = gr.instance_id
            LEFT JOIN retained_size ret ON ret.instance_id = gr.instance_id
            LEFT JOIN frame_counts fc ON fc.thread_serial = gr.thread_serial
            LEFT JOIN top_frame    tf ON tf.thread_serial = gr.thread_serial
            LEFT JOIN local_stats  ls ON ls.thread_serial = gr.thread_serial
            WHERE gr.root_kind = """ + HprofTag.Sub.ROOT_THREAD_OBJECT + " ORDER BY gr.instance_id";

    private static final int FIRST_INSTANCE_COLUMN = 8;

    public static List<HeapThreadInfo> analyze(HeapView view) throws SQLException {
        boolean haveRetained = view.hasDominatorTree();

        record ThreadRow(InstanceRow instance, List<InstanceFieldValue> fields, Long retained,
                         Integer frameCount, Integer localsCount, Long localsBytes, HeapThreadState state) {
        }
        List<ThreadRow> threads = new ArrayList<>();
        try (Statement stmt = view.databaseClient().connection().createStatement();
             ResultSet rs = stmt.executeQuery(THREAD_BULK_SQL)) {
            while (rs.next()) {
                Integer frameCount = nullableInt(rs, 1);
                String topClass = rs.getString(2);
                String topMethod = rs.getString(3);
                Integer topLine = nullableInt(rs, 4);
                Integer localsCount = nullableInt(rs, 5);
                Long localsBytes = nullableLong(rs, 6);
                Long retained = haveRetained ? nullableLong(rs, 7) : null;
                InstanceRow instance = InstanceColumns.read(rs, FIRST_INSTANCE_COLUMN);
                List<InstanceFieldValue> fields;
                try {
                    fields = view.readInstanceFields(instance);
                } catch (IllegalStateException noHprof) {
                    return List.of();
                }
                if (!fields.isEmpty()) {
                    threads.add(new ThreadRow(instance, fields, retained, frameCount, localsCount, localsBytes,
                            deriveState(topClass, topMethod, topLine)));
                }
            }
        }

        List<Long> nameRefs = new ArrayList<>();
        for (ThreadRow thread : threads) {
            Long ref = nameRef(thread.fields());
            if (ref != null) {
                nameRefs.add(ref);
            }
        }
        Map<Long, String> names = view.findStringContents(nameRefs);

        List<HeapThreadInfo> out = new ArrayList<>(threads.size());
        for (ThreadRow thread : threads) {
            out.add(decodeThread(view, thread.instance().instanceId(), thread.fields(), names, thread.retained(),
                    thread.frameCount(), thread.localsCount(), thread.localsBytes(), thread.state()));
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

    /** The String the thread's {@code name} field points at, or null when it has none. */
    private static Long nameRef(List<InstanceFieldValue> fields) {
        for (InstanceFieldValue f : fields) {
            if (JdkFieldNames.THREAD_NAME.equals(f.name()) && f.value() instanceof Long stringRef && stringRef != 0L) {
                return stringRef;
            }
        }
        return null;
    }

    /**
     * Builds the thread from its decoded fields. Its name comes from {@code names}, the contents read
     * for every thread at once; a name too long to have been stored at index time is decoded from
     * the dump on its own.
     */
    private static HeapThreadInfo decodeThread(
            HeapView view, long instanceId, List<InstanceFieldValue> fields, Map<Long, String> names, Long retained,
            Integer frameCount, Integer localsCount, Long localsBytes, HeapThreadState state) throws SQLException {
        String name = UNKNOWN_NAME;
        boolean daemon = false;
        int priority = 0;
        for (InstanceFieldValue f : fields) {
            switch (f.name()) {
                case JdkFieldNames.THREAD_NAME -> {
                    if (f.value() instanceof Long stringRef && stringRef != 0L) {
                        String stored = names.get(stringRef);
                        if (stored != null) {
                            name = stored;
                        } else {
                            Optional<JavaStringDecoder.Decoded> decoded =
                                    JavaStringDecoder.decode(view, stringRef);
                            if (decoded.isPresent()) {
                                name = decoded.get().content();
                            }
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

        return new HeapThreadInfo(
                instanceId, name, daemon, priority, retained,
                frameCount, localsCount, localsBytes, state);
    }

}

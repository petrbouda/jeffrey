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
import java.util.ArrayList;
import java.util.List;
import cafe.jeffrey.profile.heapdump.model.StackFrameLocal;
import cafe.jeffrey.profile.heapdump.model.ThreadStackFrame;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;

/**
 * Assembles the per-thread call stack for the Threads page's "Stack" expansion.
 *
 * <p>Given a thread instance id (the object id of the {@code java.lang.Thread}
 * the user clicked), this analyzer:
 *
 * <ol>
 *   <li>Resolves the thread's {@code thread_serial} from the {@code gc_root}
 *       row tagged {@code ROOT_THREAD_OBJECT}.</li>
 *   <li>Reads the ordered frame list from {@code stack_trace_frame} joined
 *       with {@code stack_frame} (method name / source file / line number)
 *       and LEFT-joined with {@code class} on {@code class_serial} for the
 *       containing class name.</li>
 *   <li>For each frame, pulls locals from {@code gc_root} rows tagged
 *       {@code ROOT_JAVA_FRAME} that carry the same {@code thread_serial}
 *       and {@code frame_index}, joined with {@code instance} + {@code class}
 *       for class name and shallow size.</li>
 * </ol>
 *
 * <p>HPROF does not record local-variable names — {@link StackFrameLocal#fieldName()}
 * is therefore always empty for these locals, matching the legacy renderer's
 * {@code · : ClassName (size)} format.
 *
 * <p>Empty list is returned when the thread is unknown, has no stack trace,
 * or the parser found no STACK_TRACE / STACK_FRAME records (older indexes
 * built before stack-frame support was added — re-index to fix).
 */
public final class ThreadStackAnalyzer {

    private static final String THREAD_SERIAL_SQL =
            "SELECT thread_serial FROM gc_root "
                    + "WHERE instance_id = ? AND root_kind = " + HprofTag.Sub.ROOT_THREAD_OBJECT
                    + " LIMIT 1";

    private static final String FRAMES_SQL = """
            SELECT stf.frame_index,
                   sf.method_name,
                   sf.source_file,
                   sf.line_number,
                   sf.class_name
            FROM stack_trace_frame stf
            JOIN stack_frame sf ON sf.frame_id = stf.frame_id
            WHERE stf.thread_serial = ?
            ORDER BY stf.frame_index
            """;

    private static final String LOCALS_SQL =
            "SELECT gr.instance_id, c.name AS class_name, i.shallow_size "
                    + "FROM gc_root gr "
                    + "JOIN instance i        ON i.instance_id = gr.instance_id "
                    + "LEFT JOIN class c      ON c.class_id    = i.class_id "
                    + "WHERE gr.root_kind     = " + HprofTag.Sub.ROOT_JAVA_FRAME + " "
                    + "  AND gr.thread_serial = ? "
                    + "  AND gr.frame_index   = ? "
                    + "ORDER BY gr.instance_id";

    private ThreadStackAnalyzer() {
    }

    public static List<ThreadStackFrame> getStack(HeapView view, long threadObjectId) throws SQLException {
        Integer threadSerial = lookupThreadSerial(view, threadObjectId);
        if (threadSerial == null) {
            return List.of();
        }

        List<FrameRow> frameRows = pullFrames(view, threadSerial);
        if (frameRows.isEmpty()) {
            return List.of();
        }

        List<ThreadStackFrame> out = new ArrayList<>(frameRows.size());
        try (PreparedStatement localsStmt = view.databaseClient().connection().prepareStatement(LOCALS_SQL)) {
            for (FrameRow f : frameRows) {
                List<StackFrameLocal> locals = pullLocals(localsStmt, threadSerial, f.frameIndex);
                out.add(new ThreadStackFrame(
                        f.className == null ? "<unknown>" : f.className,
                        f.methodName,
                        f.sourceFile,
                        f.lineNumber,
                        locals));
            }
        }
        return out;
    }

    private static Integer lookupThreadSerial(HeapView view, long threadObjectId) throws SQLException {
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(THREAD_SERIAL_SQL)) {
            stmt.setLong(1, threadObjectId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int serial = rs.getInt(1);
                return rs.wasNull() ? null : serial;
            }
        }
    }

    private static List<FrameRow> pullFrames(HeapView view, int threadSerial) throws SQLException {
        List<FrameRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(FRAMES_SQL)) {
            stmt.setInt(1, threadSerial);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new FrameRow(
                            rs.getInt(1),
                            rs.getString(2),
                            rs.getString(3),
                            rs.getInt(4),
                            rs.getString(5)));
                }
            }
        }
        return rows;
    }

    private static List<StackFrameLocal> pullLocals(
            PreparedStatement stmt, int threadSerial, int frameIndex) throws SQLException {
        stmt.setInt(1, threadSerial);
        stmt.setInt(2, frameIndex);
        try (ResultSet rs = stmt.executeQuery()) {
            List<StackFrameLocal> locals = new ArrayList<>();
            while (rs.next()) {
                long objectId = rs.getLong(1);
                String className = rs.getString(2);
                long shallowSize = rs.getLong(3);
                locals.add(new StackFrameLocal(
                        objectId,
                        className == null ? "<unknown>" : className,
                        "",
                        shallowSize));
            }
            return locals;
        }
    }

    private record FrameRow(
            int frameIndex, String methodName, String sourceFile, int lineNumber, String className) {
    }
}

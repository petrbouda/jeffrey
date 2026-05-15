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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.profile.heapdump.persistence.HeapDumpDatabaseClient;
import cafe.jeffrey.profile.heapdump.persistence.HeapDumpStatement;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2 — persists buffered STACK_FRAME records into {@code stack_frame}
 * and STACK_TRACE records into {@code stack_trace_frame}. Runs immediately
 * after the top-level walk so {@code top.stringPool} is complete and
 * method / signature / source-file ids can be resolved inline. Frames whose
 * method-name id is missing from the pool get an {@code "<unresolved-...>"}
 * placeholder; source-file id 0 maps to NULL.
 */
public final class HprofStackTraceWriter {

    private HprofStackTraceWriter() {
    }

    public static void write(HeapDumpDatabaseClient client, TopLevelData top) {
        if (!top.stackFrames.isEmpty()) {
            Map<Integer, String> classNameBySerial = buildClassNameBySerial(top);
            client.withAppender(HeapDumpStatement.APPEND_STACK_FRAME, "stack_frame", app -> {
                long rows = 0;
                for (HprofRecord.StackFrame sf : top.stackFrames) {
                    app.beginRow();
                    app.append(sf.stackFrameId());
                    String className = classNameBySerial.getOrDefault(sf.classSerial(),
                            "<unresolved-class-serial:" + sf.classSerial() + ">");
                    app.append(className);
                    app.append(resolveString(top, sf.methodNameStringId(), "<unresolved-method>"));
                    app.append(resolveString(top, sf.methodSignatureStringId(), ""));
                    String sourceFile = sf.sourceFileNameStringId() == 0L
                            ? null
                            : resolveString(top, sf.sourceFileNameStringId(), null);
                    if (sourceFile == null) {
                        app.appendNull();
                    } else {
                        app.append(sourceFile);
                    }
                    app.append(sf.lineNumber());
                    app.endRow();
                    rows++;
                }
                return rows;
            });
        }
        if (!top.stackTraces.isEmpty()) {
            client.withAppender(HeapDumpStatement.APPEND_STACK_TRACE_FRAME, "stack_trace_frame", app -> {
                long rows = 0;
                for (HprofRecord.StackTrace st : top.stackTraces) {
                    long[] frameIds = st.frameIds();
                    for (int idx = 0; idx < frameIds.length; idx++) {
                        app.beginRow();
                        app.append(st.traceSerial());
                        app.append(st.threadSerial());
                        app.append(idx);
                        app.append(frameIds[idx]);
                        app.endRow();
                        rows++;
                    }
                }
                return rows;
            });
        }
    }

    private static String resolveString(TopLevelData top, long stringId, String fallback) {
        byte[] bytes = top.stringPool.get(stringId);
        if (bytes != null) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return fallback;
    }

    /**
     * Maps each {@code classSerial} (from LOAD_CLASS) to the user-facing class
     * name. STACK_FRAME records reference classes by serial, and we want a
     * resolved name even when the matching CLASS_DUMP is absent — common for
     * framework classes that appear on stacks but are never instantiated.
     */
    private static Map<Integer, String> buildClassNameBySerial(TopLevelData top) {
        Map<Integer, String> out = new HashMap<>(top.loadClassByClassId.size());
        for (HprofRecord.LoadClass lc : top.loadClassByClassId.values()) {
            String raw = resolveString(top, lc.nameStringId(),
                    "<unresolved-class-name:0x" + Long.toHexString(lc.nameStringId()) + ">");
            out.put(lc.classSerial(), ClassNameFormatter.userFacing(raw));
        }
        return out;
    }
}

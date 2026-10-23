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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceField;
import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.HprofTag;
import cafe.jeffrey.profile.heapdump.view.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JavaClassRow;

/**
 * HeapView-backed equivalent of
 * {@link cafe.jeffrey.profile.heapdump.analyzer.InstanceDetailAnalyzer}.
 *
 * Renders a single instance's full field list with typed values plus the
 * referenced class for OBJECT fields. Used by the UI's "show this object"
 * view.
 *
 * Limitations vs the existing NetBeans-backed analyzer:
 * <ul>
 *   <li>Static fields list is empty — capturing them requires also
 *       persisting class statics to the index, deferred to a follow-up.</li>
 *   <li>{@code displayValue} is a basic stringification; the existing path
 *       has special rendering for collections and arrays.</li>
 *   <li>{@code stringValue} is populated via JavaStringDecoder when the
 *       target is a {@code java.lang.String}, otherwise null.</li>
 * </ul>
 */
public final class InstanceDetailAnalyzer {

    private static final int STRING_PREVIEW_MAX_CHARS = 200;

    private static final String UNKNOWN_CLASS = "<unknown>";

    /**
     * The class of every referenced instance, asked of {@code instance} alone with the ids as a
     * literal list, which DuckDB answers from the index on {@code instance_id}; the names then come
     * from the view's class cache. Showing an object used to cost a query per reference field --
     * which DuckDB 2.0's dearer statement preparation made the bulk of this view -- and joining
     * {@code class} here instead turns the lookup into a scan.
     */
    private static final String REFERENCED_CLASS_IDS_SQL =
            "SELECT instance_id, class_id FROM instance WHERE class_id IS NOT NULL AND instance_id IN (%s)";

    private static final String PLACEHOLDER = "?";

    private InstanceDetailAnalyzer() {
    }

    public static Optional<InstanceDetail> analyze(HeapView view, long objectId) throws SQLException {
        InstanceRow inst = view.findInstanceById(objectId).orElse(null);
        if (inst == null) {
            return Optional.empty();
        }
        String className = inst.classId() != null
                ? view.findClassById(inst.classId()).map(JavaClassRow::name).orElse(UNKNOWN_CLASS)
                : kindClassName(inst);

        List<InstanceField> fields = decodeFields(view, inst);
        Long retained = view.hasDominatorTree() ? view.findRetainedSize(objectId).orElse(null) : null;

        String stringValue = String.class.getName().equals(className)
                ? resolveStringContent(view, objectId)
                : null;
        // Only Strings get a human-readable value here. For any other ref
        // type, leave displayValue null — the heap dump can't recover an
        // instance's runtime toString() output, so anything we synthesize
        // (e.g. "Foo@hex") would just duplicate the class name + object id
        // already shown elsewhere in the panel.
        String displayValue = stringValue != null
                ? "\"" + truncate(stringValue) + "\""
                : null;

        return Optional.of(new InstanceDetail(
                objectId,
                className,
                displayValue,
                stringValue,
                displayValue,
                inst.shallowSize(),
                retained,
                fields,
                List.of()));
    }

    /**
     * Pulls the decoded {@code String.value} contents for a {@code java.lang.String}
     * instance, preferring the indexer-materialised {@code string_content} table
     * (single PK lookup, cached) and only falling back to mmap-decoded HPROF
     * payload bytes when the content row is missing or the String exceeded the
     * indexer's content cap.
     */
    private static String resolveStringContent(HeapView view, long objectId) throws SQLException {
        Optional<String> cached = view.findStringContent(objectId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return JavaStringDecoder.decode(view, objectId)
                .map(JavaStringDecoder.Decoded::content)
                .orElse(null);
    }

    private static List<InstanceField> decodeFields(HeapView view, InstanceRow instance) throws SQLException {
        List<InstanceFieldValue> raw;
        try {
            raw = view.readInstanceFields(instance);
        } catch (IllegalStateException noHprof) {
            return List.of();
        }
        List<Long> refs = new ArrayList<>();
        for (InstanceFieldValue f : raw) {
            if (f.basicType() == HprofTag.BasicType.OBJECT && f.value() instanceof Long ref && ref != 0L) {
                refs.add(ref);
            }
        }
        Map<Long, String> refClassNames = resolveClassNamesByInstance(view, refs);

        List<InstanceField> out = new ArrayList<>(raw.size());
        for (InstanceFieldValue f : raw) {
            String typeName = typeName(f.basicType());
            boolean primitive = f.basicType() != HprofTag.BasicType.OBJECT;
            String value;
            Long refId = null;
            String refClassName = null;
            if (primitive) {
                value = f.value() == null ? "null" : f.value().toString();
            } else {
                long ref = f.value() instanceof Long l ? l : 0L;
                if (ref == 0L) {
                    value = "null";
                } else {
                    refId = ref;
                    refClassName = refClassNames.get(ref);
                    // Leave value null for non-null refs — the refClassName +
                    // refId pair (delivered alongside) is the real data; any
                    // "Foo@hex" string here would just be a synthetic copy of
                    // information already on the row.
                    value = null;
                }
            }
            out.add(new InstanceField(f.name(), typeName, value, primitive, refId, refClassName));
        }
        return out;
    }

    private static Map<Long, String> resolveClassNamesByInstance(HeapView view, List<Long> instanceIds)
            throws SQLException {
        if (instanceIds.isEmpty()) {
            return Map.of();
        }
        String sql = REFERENCED_CLASS_IDS_SQL.formatted(
                String.join(", ", Collections.nCopies(instanceIds.size(), PLACEHOLDER)));
        Map<Long, Long> classIds = new HashMap<>();
        try (PreparedStatement stmt = view.databaseClient().connection().prepareStatement(sql)) {
            for (int i = 0; i < instanceIds.size(); i++) {
                stmt.setLong(i + 1, instanceIds.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    classIds.put(rs.getLong(1), rs.getLong(2));
                }
            }
        }
        Map<Long, String> out = new HashMap<>();
        for (Map.Entry<Long, Long> entry : classIds.entrySet()) {
            Optional<JavaClassRow> cls = view.findClassById(entry.getValue());
            if (cls.isPresent()) {
                out.put(entry.getKey(), cls.get().name());
            }
        }
        return out;
    }

    private static String kindClassName(InstanceRow inst) {
        return switch (inst.kind()) {
            case PRIMITIVE_ARRAY -> primitiveArrayName(inst.primitiveType());
            case OBJECT_ARRAY -> "Object[]";
            case INSTANCE -> UNKNOWN_CLASS;
        };
    }

    private static String primitiveArrayName(Integer primitiveType) {
        if (primitiveType == null) {
            return "<primitive[]>";
        }
        return switch (primitiveType) {
            case HprofTag.BasicType.BOOLEAN -> "boolean[]";
            case HprofTag.BasicType.BYTE -> "byte[]";
            case HprofTag.BasicType.CHAR -> "char[]";
            case HprofTag.BasicType.SHORT -> "short[]";
            case HprofTag.BasicType.INT -> "int[]";
            case HprofTag.BasicType.LONG -> "long[]";
            case HprofTag.BasicType.FLOAT -> "float[]";
            case HprofTag.BasicType.DOUBLE -> "double[]";
            default -> "<primitive[]>";
        };
    }

    private static String typeName(int basicType) {
        return switch (basicType) {
            case HprofTag.BasicType.OBJECT -> "Object";
            case HprofTag.BasicType.BOOLEAN -> "boolean";
            case HprofTag.BasicType.BYTE -> "byte";
            case HprofTag.BasicType.CHAR -> "char";
            case HprofTag.BasicType.SHORT -> "short";
            case HprofTag.BasicType.INT -> "int";
            case HprofTag.BasicType.LONG -> "long";
            case HprofTag.BasicType.FLOAT -> "float";
            case HprofTag.BasicType.DOUBLE -> "double";
            default -> "<unknown>";
        };
    }

    private static String truncate(String s) {
        return StringTruncate.to(s, STRING_PREVIEW_MAX_CHARS);
    }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import cafe.jeffrey.profile.heapdump.model.InstanceDetail;
import cafe.jeffrey.profile.heapdump.model.InstanceField;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.HprofTag;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

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

    private static final String JAVA_LANG_STRING = "java.lang.String";

    private InstanceDetailAnalyzer() {
    }

    public static Optional<InstanceDetail> analyze(HeapView view, long objectId) throws SQLException {
        InstanceRow inst = view.findInstanceById(objectId).orElse(null);
        if (inst == null) {
            return Optional.empty();
        }
        String className = inst.classId() != null
                ? view.findClassById(inst.classId()).map(JavaClassRow::name).orElse("<unknown>")
                : kindClassName(inst);

        List<InstanceField> fields = decodeFields(view, objectId);
        Long retained = view.hasDominatorTree() ? probeRetained(view, objectId) : null;

        String stringValue = JAVA_LANG_STRING.equals(className)
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

    private static List<InstanceField> decodeFields(HeapView view, long instanceId) throws SQLException {
        List<InstanceFieldValue> raw;
        try {
            raw = view.readInstanceFields(instanceId);
        } catch (IllegalStateException noHprof) {
            return List.of();
        }
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
                    refClassName = resolveClassNameByInstance(view, ref);
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

    private static String resolveClassNameByInstance(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT c.name FROM instance i JOIN class c ON i.class_id = c.class_id "
                        + "WHERE i.instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static Long probeRetained(HeapView view, long instanceId) throws SQLException {
        try (PreparedStatement stmt = view.connection().prepareStatement(
                "SELECT bytes FROM retained_size WHERE instance_id = ?")) {
            stmt.setLong(1, instanceId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private static String kindClassName(InstanceRow inst) {
        return switch (inst.kind()) {
            case PRIMITIVE_ARRAY -> primitiveArrayName(inst.primitiveType());
            case OBJECT_ARRAY -> "Object[]";
            case INSTANCE -> "<unknown>";
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
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}

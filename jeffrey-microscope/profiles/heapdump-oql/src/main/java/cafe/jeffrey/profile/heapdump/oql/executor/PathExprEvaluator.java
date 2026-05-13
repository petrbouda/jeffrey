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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.parser.DumpMetadata;
import cafe.jeffrey.profile.heapdump.parser.HeapView;
import cafe.jeffrey.profile.heapdump.parser.InstanceFieldValue;
import cafe.jeffrey.profile.heapdump.parser.InstanceRow;
import cafe.jeffrey.profile.heapdump.parser.JavaClassRow;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Walks a {@link cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr.PathExpr}
 * chain over the heap-dump index. Each segment narrows the value: a
 * {@link PathSegment.Field} looks up an instance field, a
 * {@link PathSegment.AttrField} resolves a bean attribute, and a
 * {@link PathSegment.Index} reads an array element from the {@code .hprof}
 * payload.
 *
 * <p>Per SQL semantics, encountering a null/zero object reference mid-path
 * short-circuits to {@code null} for the remainder of the chain.
 */
final class PathExprEvaluator {

    /** HPROF basic-type tags (mirrored from {@code HprofTag.BasicType}). */
    private static final int BT_OBJECT = 2;
    private static final int BT_BOOLEAN = 4;
    private static final int BT_CHAR = 5;
    private static final int BT_FLOAT = 6;
    private static final int BT_DOUBLE = 7;
    private static final int BT_BYTE = 8;
    private static final int BT_SHORT = 9;
    private static final int BT_INT = 10;
    private static final int BT_LONG = 11;

    private final HeapView view;
    private final int idSize;

    PathExprEvaluator(HeapView view) throws SQLException {
        this.view = view;
        DumpMetadata md = safeMetadata(view);
        this.idSize = md == null ? 8 : md.idSize();
    }

    /** Callback to evaluate an index expression. Provided by the outer evaluator. */
    interface IndexEvaluator {
        Object eval(cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr indexExpr) throws SQLException;
    }

    Object walk(Object current, List<PathSegment> segments, IndexEvaluator indexEval) throws SQLException {
        for (PathSegment seg : segments) {
            if (current == null) {
                return null;
            }
            current = step(current, seg, indexEval);
        }
        return current;
    }

    private Object step(Object current, PathSegment seg, IndexEvaluator indexEval) throws SQLException {
        if (current instanceof JavaClassRow clazz && seg instanceof PathSegment.Field f) {
            // classof(o).name pattern — Plan C path that wasn't already handled by SQL.
            if ("name".equals(f.name())) {
                return clazz.name();
            }
        }
        InstanceRow inst = asInstance(current);
        if (inst == null) {
            return null;
        }
        return switch (seg) {
            case PathSegment.Field f -> fieldValue(inst, f.name());
            case PathSegment.AttrField af -> attrValue(inst, af.name());
            case PathSegment.Index ix -> {
                Object idxVal = indexEval.eval(ix.index());
                if (!(idxVal instanceof Number n)) {
                    yield null;
                }
                yield readArrayElement(inst, n.intValue());
            }
        };
    }

    private Object fieldValue(InstanceRow inst, String name) throws SQLException {
        // Special-case `.length` on an array kind — read array_length from the
        // instance row directly without touching .hprof bytes.
        if ("length".equals(name) && inst.kind() != InstanceRow.Kind.INSTANCE) {
            return inst.arrayLength();
        }
        if (inst.kind() != InstanceRow.Kind.INSTANCE) {
            return null;
        }
        List<InstanceFieldValue> fields = view.readInstanceFields(inst.instanceId());
        for (InstanceFieldValue f : fields) {
            if (name.equals(f.name())) {
                return f.value();
            }
        }
        return null;
    }

    private Object attrValue(InstanceRow inst, String name) throws SQLException {
        return switch (name) {
            case "objectId" -> inst.instanceId();
            case "objectAddress" -> String.format("0x%x", inst.instanceId());
            case "usedHeapSize" -> (long) inst.shallowSize();
            case "retainedHeapSize" -> view.retainedSize(inst.instanceId());
            case "displayName" -> {
                String className = inst.classId() == null
                        ? "(unknown)"
                        : view.findClassById(inst.classId()).map(JavaClassRow::name).orElse("(unknown)");
                yield className + "@" + Long.toHexString(inst.instanceId());
            }
            case "clazz" -> inst.classId() == null
                    ? null
                    : view.findClassById(inst.classId()).orElse(null);
            default -> throw new IllegalArgumentException("Unknown attribute: @" + name);
        };
    }

    private Object readArrayElement(InstanceRow inst, int index) throws SQLException {
        if (inst.kind() == InstanceRow.Kind.INSTANCE) {
            return null;
        }
        Integer arrLen = inst.arrayLength();
        if (arrLen == null || index < 0 || index >= arrLen) {
            return null;
        }
        if (inst.kind() == InstanceRow.Kind.OBJECT_ARRAY) {
            // Object array — element bytes are id-sized references.
            byte[] bytes = view.readInstanceContentBytes(inst);
            int offset = index * idSize;
            if (offset + idSize > bytes.length) {
                return null;
            }
            long id = readLong(bytes, offset, idSize);
            return id == 0L ? null : id;
        }
        // Primitive array — decode by primitive type.
        Integer pt = inst.primitiveType();
        if (pt == null) {
            return null;
        }
        byte[] bytes = view.readPrimitiveArrayBytes(inst.instanceId());
        return decodePrimitive(bytes, pt, index);
    }

    private InstanceRow asInstance(Object v) throws SQLException {
        if (v instanceof InstanceRow r) {
            return r;
        }
        if (v instanceof Long ref && ref != 0L) {
            return view.findInstanceById(ref).orElse(null);
        }
        return null;
    }

    private static Object decodePrimitive(byte[] bytes, int basicType, int index) {
        int size = switch (basicType) {
            case BT_BOOLEAN, BT_BYTE -> 1;
            case BT_CHAR, BT_SHORT -> 2;
            case BT_FLOAT, BT_INT -> 4;
            case BT_DOUBLE, BT_LONG -> 8;
            default -> -1;
        };
        if (size < 0) {
            return null;
        }
        int offset = index * size;
        if (offset + size > bytes.length) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes, offset, size).order(ByteOrder.BIG_ENDIAN);
        return switch (basicType) {
            case BT_BOOLEAN -> buf.get() != 0;
            case BT_BYTE -> buf.get();
            case BT_CHAR -> buf.getChar();
            case BT_SHORT -> buf.getShort();
            case BT_FLOAT -> buf.getFloat();
            case BT_INT -> buf.getInt();
            case BT_DOUBLE -> buf.getDouble();
            case BT_LONG -> buf.getLong();
            case BT_OBJECT -> buf.getLong();
            default -> null;
        };
    }

    private static long readLong(byte[] bytes, int offset, int size) {
        long v = 0;
        for (int i = 0; i < size; i++) {
            v = (v << 8) | (bytes[offset + i] & 0xffL);
        }
        return v;
    }

    private static DumpMetadata safeMetadata(HeapView view) {
        try {
            return view.metadata();
        } catch (SQLException | RuntimeException ignored) {
            return null;
        }
    }
}

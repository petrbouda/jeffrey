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
package cafe.jeffrey.profile.heapdump.oql.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * Catalogue of OQL string functions handled by the SQL emitter — predicates,
 * accessors, length, and the projection-side {@code toString(s)}. Each entry
 * carries the OQL surface name, the kind of operation it produces, its arity,
 * and (where applicable) the DuckDB primitive used in the emitted SQL.
 *
 * <p>Centralising these here keeps the SqlEmitter dispatch table out of
 * string literals and makes adding a new string function a one-line change.
 */
enum StringOp {

    // ---- Predicates: return BOOLEAN, take (binding, literal) ---------
    STARTS_WITH("startsWith", Kind.PREDICATE, 2, "starts_with"),
    ENDS_WITH("endsWith", Kind.PREDICATE, 2, "ends_with"),
    CONTAINS("contains", Kind.PREDICATE, 2, "contains"),
    MATCHES_REGEX("matchesRegex", Kind.PREDICATE, 2, "regexp_matches"),
    /** Case-sensitive equality. Special-cased in the emitter (uses {@code =}, not a function). */
    EQUALS_STRING("equalsString", Kind.EQUALITY, 2, null),
    /** Case-insensitive equality. Special-cased in the emitter (wraps both sides in {@code lower(...)}). */
    EQUALS_IGNORE_CASE("equalsIgnoreCase", Kind.EQUALITY, 2, null),
    /** Empty-string test. Special-cased: emits {@code sc.content = ''}. */
    IS_EMPTY_STRING("isEmptyString", Kind.PREDICATE_UNARY, 1, null),

    // ---- Accessors: return a value derived from the content -----------
    /** Reads the {@code content_length} column directly — works even for Strings beyond the content cap. */
    STRING_LENGTH("stringLength", Kind.LENGTH, 1, null),
    LOWER("lower", Kind.ACCESSOR_UNARY, 1, "lower"),
    UPPER("upper", Kind.ACCESSOR_UNARY, 1, "upper"),
    TRIM("trim", Kind.ACCESSOR_UNARY, 1, "trim"),
    /** Special-cased: 0-based OQL start translated to DuckDB's 1-based; optional end → length. */
    SUBSTRING("substring", Kind.SUBSTRING, 2, null),
    /** {@code instr(content, x) - 1}; returns -1 when not found, matching OQL semantics. */
    INDEX_OF("indexOf", Kind.INDEX_OF, 2, "instr"),
    /** No direct DuckDB pushdown — surfaces a clear error and falls back to Plan C. */
    LAST_INDEX_OF("lastIndexOf", Kind.UNSUPPORTED_PUSHDOWN, 2, null),

    // ---- Projection-side: return content as-is ------------------------
    TO_STRING("toString", Kind.TO_STRING, 1, null);

    enum Kind {
        /** Binary predicate that maps to a single DuckDB function with the same shape. */
        PREDICATE,
        /** Unary predicate (e.g. {@code isEmptyString(s)}). */
        PREDICATE_UNARY,
        /** {@code a = b} (case-sensitive) or {@code lower(a) = lower(b)} (case-insensitive). */
        EQUALITY,
        /** Reads the {@code content_length} column directly. */
        LENGTH,
        /** {@code func(content)}; passes literal arg through unchanged when binding isn't a String. */
        ACCESSOR_UNARY,
        /** DuckDB's 1-based {@code substring}; offsets adjusted from OQL's 0-based form. */
        SUBSTRING,
        /** {@code instr() - 1} → 0-based position or -1. */
        INDEX_OF,
        /** Surface error and force Plan C. */
        UNSUPPORTED_PUSHDOWN,
        /** Projection-side: {@code toString(s)} on a String binding yields its content. */
        TO_STRING
    }

    private static final Map<String, StringOp> BY_NAME = build();

    private static Map<String, StringOp> build() {
        Map<String, StringOp> m = new HashMap<>();
        for (StringOp op : values()) {
            m.put(op.oqlName, op);
        }
        return m;
    }

    /** Returns the {@link StringOp} that corresponds to the given OQL function name, or {@code null}. */
    static StringOp forOqlName(String oqlName) {
        return BY_NAME.get(oqlName);
    }

    private final String oqlName;
    private final Kind kind;
    private final int arity;
    private final String duckDbFunc;

    StringOp(String oqlName, Kind kind, int arity, String duckDbFunc) {
        this.oqlName = oqlName;
        this.kind = kind;
        this.arity = arity;
        this.duckDbFunc = duckDbFunc;
    }

    String oqlName() {
        return oqlName;
    }

    Kind kind() {
        return kind;
    }

    int arity() {
        return arity;
    }

    /** Non-null only for ops that map cleanly to a single DuckDB function with the same shape. */
    String duckDbFunc() {
        return duckDbFunc;
    }
}

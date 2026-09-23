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
package cafe.jeffrey.profile.heapdump.parser;

import cafe.jeffrey.shared.persistence.DuckDBAppenders;

import org.duckdb.DuckDBAppender;

import java.sql.SQLException;

/**
 * Static appender helpers shared across the HPROF index-build phases:
 * NULL-aware id/int appends, and synthetic class-id allocation for primitive
 * array types (which HPROF doesn't emit class records for).
 */
public final class HprofAppenderUtils {

    /**
     * HPROF doesn't emit CLASS_DUMP records for primitive-array types — only the
     * BasicType byte on each PRIMITIVE_ARRAY_DUMP. We synthesize one class row
     * per primitive type so primitive arrays show up in the histogram and in
     * every other class-keyed view. Synthetic ids are deeply negative so they
     * cannot collide with real HPROF object ids (which are non-negative).
     */
    private static final long PRIM_ARRAY_CLASS_ID_BASE = -1_000_000_000L;

    private HprofAppenderUtils() {
    }

    /** HPROF id 0 means "no reference"; map to NULL in the index for clean SQL semantics. */
    public static void appendNullableId(DuckDBAppender app, long id) throws SQLException {
        if (id == 0L) {
            app.appendNull();
        } else {
            app.append(id);
        }
    }

    public static void appendNullableInt(DuckDBAppender app, Integer value) throws SQLException {
        DuckDBAppenders.nullableAppend(app, value);
    }

    /** Sentinel -1 used by the parser for "absent" thread/frame fields. */
    public static void appendNullableInt(DuckDBAppender app, int value) throws SQLException {
        if (value < 0) {
            app.appendNull();
        } else {
            app.append(value);
        }
    }

    public static long primArrayClassId(int elementType) {
        return PRIM_ARRAY_CLASS_ID_BASE - elementType;
    }

    public static String primArrayName(int elementType) {
        return switch (elementType) {
            case 4 -> "boolean[]";
            case 5 -> "char[]";
            case 6 -> "float[]";
            case 7 -> "double[]";
            case 8 -> "byte[]";
            case 9 -> "short[]";
            case 10 -> "int[]";
            case 11 -> "long[]";
            default -> null;
        };
    }
}

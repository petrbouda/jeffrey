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
package cafe.jeffrey.profile.heapdump.view;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableInt;
import static cafe.jeffrey.profile.heapdump.view.JdbcNullable.nullableLong;

/**
 * The {@code instance} columns an {@link InstanceRow} is read from, in the order {@link #read}
 * expects them. A query that answers whole rows -- a page of a class's instances, the threads, a
 * graph walk -- selects these beside its own columns, so the caller holds the row and never looks
 * the instance up again, one query per row.
 */
public final class InstanceColumns {

    private static final List<String> COLUMNS = List.of(
            "instance_id", "class_id", "file_offset", "record_kind", "shallow_size", "array_length", "primitive_type");

    /** How many columns {@link #read} consumes. */
    public static final int COUNT = COLUMNS.size();

    private InstanceColumns() {
    }

    /** The columns, unqualified, for a query over {@code instance} alone. */
    public static String unqualified() {
        return String.join(", ", COLUMNS);
    }

    /** The columns qualified by a table alias, for a query that joins {@code instance} as {@code alias}. */
    public static String of(String alias) {
        return COLUMNS.stream()
                .map(column -> alias + "." + column)
                .collect(Collectors.joining(", "));
    }

    /** The row whose columns start at {@code firstColumn} (1-based, as JDBC counts). */
    public static InstanceRow read(ResultSet rs, int firstColumn) throws SQLException {
        return new InstanceRow(
                rs.getLong(firstColumn),
                nullableLong(rs, firstColumn + 1),
                rs.getLong(firstColumn + 2),
                InstanceRow.Kind.fromOrdinal(rs.getInt(firstColumn + 3)),
                rs.getInt(firstColumn + 4),
                nullableInt(rs, firstColumn + 5),
                nullableInt(rs, firstColumn + 6));
    }
}

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

import java.util.List;

/**
 * Hints from the compiler to the result mapper telling it which output
 * columns carry distinguished meaning (instance id, class name, sizes).
 * Indexes are 0-based positions in the SELECT list.
 *
 * <p>A {@code -1} means "this row does not carry that distinguished column".
 * The result mapper renders rows accordingly: a row missing the instance-id
 * column does not get per-row drill-in actions in the UI.
 */
public record ResultShape(
        int objectIdColumn,
        int classNameColumn,
        int shallowSizeColumn,
        int retainedSizeColumn,
        List<String> columnNames) {

    public ResultShape {
        columnNames = columnNames == null ? List.of() : List.copyOf(columnNames);
    }

    public static ResultShape empty() {
        return new ResultShape(-1, -1, -1, -1, List.of());
    }
}

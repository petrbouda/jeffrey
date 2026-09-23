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

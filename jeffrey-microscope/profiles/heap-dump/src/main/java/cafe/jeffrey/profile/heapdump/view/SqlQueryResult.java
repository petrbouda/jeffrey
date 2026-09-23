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

import java.util.List;

/**
 * What a read-only SQL query against the heap-dump index returned.
 * <p>
 * Values arrive already rendered as text. The index holds object ids, sizes and class names, and a
 * reader wants to see them rather than to compute with them; carrying typed objects would only push
 * the same formatting decision outwards to every caller.
 *
 * @param columns the column labels, in the order the query selected them
 * @param rows    the rows, each as many values as there are columns; a SQL NULL is {@code null}
 * @param capped  whether the query had more rows to give and was stopped at the cap. Distinguishing
 *                this from a short answer is the whole point: a truncated result that looks complete
 *                is read as the whole story
 */
public record SqlQueryResult(List<String> columns, List<List<String>> rows, boolean capped) {

    public SqlQueryResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}

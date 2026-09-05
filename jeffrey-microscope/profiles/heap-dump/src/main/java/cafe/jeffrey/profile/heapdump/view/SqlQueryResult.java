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

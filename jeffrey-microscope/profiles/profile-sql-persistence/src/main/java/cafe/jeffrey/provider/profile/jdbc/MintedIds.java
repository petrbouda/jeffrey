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

package cafe.jeffrey.provider.profile.jdbc;

import java.util.List;

/**
 * An id the derivation mints for something the recording gave none — a blocking wait promoted into a
 * span, a throw charged to a span, a notification — computed in SQL from what the event is: the columns
 * that identify it, and its occurrence among the events those columns cannot tell apart.
 *
 * <p>Never the row's {@code rowid}: rows are numbered as the parallel parse and the clustering leave
 * them, so the same event got another id in another analysis of the same recording. The occurrence
 * keeps two byte-identical events (a re-imported chunk) two ids, numbered in an order their content
 * decides. The digest is MD5 folded into {@code [1, 2^63-1]} — never 0, the wire encoding for "absent",
 * never negative — for the reason {@link TextKeys} gives: DuckDB's {@code hash()} collides on
 * near-identical input.
 */
final class MintedIds {

    private static final String ID_OF = "1 + CAST(mod(md5_number(CONCAT_WS('|', %s, ROW_NUMBER() OVER ("
            + "PARTITION BY %s ORDER BY %s))), CAST(9223372036854775806 AS UHUGEINT)) AS BIGINT)";

    private static final String SEPARATOR = ", ";

    private MintedIds() {
    }

    /**
     * The SQL expression for the id of an event identified by {@code identity}, its occurrences among
     * events of the same identity numbered in {@code tieBreak} order. A window function: it belongs in
     * a SELECT list.
     *
     * @param identity the columns that identify the event
     * @param tieBreak what orders events of the same identity — their content, so the order is the
     *                 recording's and not the rows'
     */
    static String of(List<String> identity, List<String> tieBreak) {
        String columns = String.join(SEPARATOR, identity);
        return ID_OF.formatted(columns, columns, String.join(SEPARATOR, tieBreak));
    }
}

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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.model.ThreadInfo;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

/**
 * Splits a set of threads into the two id columns the events query can match them on, and binds them
 * as list params so they render as native DuckDB list literals — the same shape
 * {@code SpanIntervalParams} uses.
 *
 * <p>Threads are matched by Java id where they have one. A thread that never got one reports
 * {@code -1}, and every such thread would collide on that value, so those are matched on their OS id
 * instead. A lane standing for a group of threads can hold both kinds, which is why this is a split
 * rather than a choice.
 *
 * <p>The predicate and the parameters are derived from the same two methods so the clause spliced
 * into the SQL can never reference a list that was not bound.
 */
final class ThreadIdParams {

    static final String JAVA_THREAD_IDS = "java_thread_ids";
    static final String OS_THREAD_IDS = "os_thread_ids";

    /**
     * The value {@link ThreadInfo} carries when a thread id is not available.
     */
    private static final long UNKNOWN_THREAD_ID = -1;

    private ThreadIdParams() {
    }

    static List<Long> javaIds(List<ThreadInfo> threads) {
        return threads.stream()
                .map(ThreadInfo::javaId)
                .filter(id -> id != UNKNOWN_THREAD_ID)
                .distinct()
                .toList();
    }

    static List<Long> osIds(List<ThreadInfo> threads) {
        return threads.stream()
                .filter(thread -> thread.javaId() == UNKNOWN_THREAD_ID)
                .map(ThreadInfo::osId)
                .distinct()
                .toList();
    }

    static void apply(MapSqlParameterSource params, List<ThreadInfo> threads) {
        params.addValue(JAVA_THREAD_IDS, nullIfEmpty(javaIds(threads)))
                .addValue(OS_THREAD_IDS, nullIfEmpty(osIds(threads)));
    }

    /**
     * An empty list has no valid rendering as a list literal, and the clause that would have read it
     * is not spliced in when it is empty, so the binding is left null.
     */
    private static List<Long> nullIfEmpty(List<Long> ids) {
        return ids.isEmpty() ? null : ids;
    }
}

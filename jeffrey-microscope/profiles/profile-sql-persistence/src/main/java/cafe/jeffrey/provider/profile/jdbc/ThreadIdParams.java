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

import cafe.jeffrey.microscope.model.ThreadInfo;
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

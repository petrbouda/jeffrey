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

package cafe.jeffrey.provider.profile.api;

import java.util.List;

/**
 * Reads async-profiler {@code profiler.Span} events from a profile.
 * <p>
 * Backed by a DuckDB implementation that reads real {@code profiler.Span} events from the
 * profile database. The single primitive {@link #listSpans()} returns raw spans; all aggregation
 * (by-tag stats) happens above this layer in the span manager.
 */
public interface SpanRepository {

    /**
     * @return all spans in the profile, ordered by start time ascending
     */
    List<SpanRecord> listSpans();

    /**
     * Returns all events (any type, except {@code profiler.Span} itself) that ran on the given thread
     * within the time window, ordered by start time — for the span events drill-down. The thread is
     * matched by its identity hash ({@code thread_hash}) rather than OS id, so it resolves correctly
     * for virtual threads (which have no OS id).
     *
     * @param threadHash      identity hash of the span's thread
     * @param fromEpochMillis window start (inclusive), absolute UTC epoch millis
     * @param toEpochMillis   window end (inclusive), absolute UTC epoch millis
     * @return the matching events, ordered by start time ascending
     */
    List<ThreadWindowEventRecord> eventsForThread(long threadHash, long fromEpochMillis, long toEpochMillis);
}

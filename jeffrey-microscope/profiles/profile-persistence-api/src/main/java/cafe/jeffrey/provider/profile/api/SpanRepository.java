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
    List<SpanEventRecord> eventsForThread(long threadHash, long fromEpochMillis, long toEpochMillis);
}

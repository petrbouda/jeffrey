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

import cafe.jeffrey.provider.profile.api.MethodTraceWeightRepository;
import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_EVENTS;

/**
 * Turns each method trace's inclusive duration into an exclusive weight.
 *
 * <p>Written against {@code events_raw} rather than the {@code events} view because it writes: the
 * view splices a pooled field back into the JSON and is not updatable. Nothing here reads
 * {@code fields}, so the view buys nothing anyway.
 */
public class JdbcMethodTraceWeightRepository implements MethodTraceWeightRepository {

    /**
     * Charges every traced call only for the time no traced call inside it already accounts for.
     *
     * <p>Each event is paired with the <b>innermost</b> traced call that contains it on the same
     * thread — latest start, earliest end — which for the well-nested intervals a call stack
     * produces is its direct traced parent. Direct children are disjoint by that construction, so
     * their durations add up to exactly the stretch of the parent they cover, and no interval
     * merging is needed to avoid counting an overlap twice.
     *
     * <p>Two candidates can hold the identical interval, either because a method really did return
     * within the same microsecond it was entered or because two traced frames of a recursive call
     * did. Containment alone would then let each claim the other as its parent and the pass would
     * have to invent a tie-break under the covers. Instead the row id breaks the tie in the join
     * itself — an equal interval may only be a parent if its row id is lower — which is a strict
     * order and therefore cannot close a cycle. Ordering by descending row id then picks the nearest
     * such candidate, so a run of identical intervals nests in a chain and their times do not
     * multiply.
     *
     * <p>Containment compares microseconds, the resolution a timestamp column carries, against
     * nanosecond durations. A call can therefore appear to outlast a parent it is genuinely inside
     * by less than a microsecond, which is what the floor at zero is for: a negative self time is
     * rounding, never a measurement.
     */
    //language=SQL
    private static final String DERIVE_SELF_WEIGHTS = """
            UPDATE events_raw
            SET weight = GREATEST(0, duration - nested.covered_ns)
            FROM (
                WITH traced_calls AS (
                    SELECT
                        rowid                                          AS event_row,
                        thread_hash                                    AS thread_hash,
                        EPOCH_US(start_timestamp)                      AS from_us,
                        EPOCH_US(start_timestamp) + duration // 1000   AS to_us,
                        duration                                       AS duration
                    FROM events_raw
                    WHERE event_type = :event_type
                      AND duration IS NOT NULL
                ),
                direct_parent AS (
                    SELECT
                        p.event_row AS parent_row,
                        c.duration  AS child_duration
                    FROM traced_calls c
                    JOIN traced_calls p
                      ON p.thread_hash = c.thread_hash
                     AND p.from_us <= c.from_us
                     AND p.to_us >= c.to_us
                     AND (p.from_us < c.from_us
                          OR p.to_us > c.to_us
                          OR p.event_row < c.event_row)
                    QUALIFY ROW_NUMBER() OVER (
                        PARTITION BY c.event_row
                        ORDER BY p.from_us DESC, p.to_us ASC, p.event_row DESC) = 1
                )
                SELECT parent_row, SUM(child_duration) AS covered_ns
                FROM direct_parent
                GROUP BY parent_row
            ) nested
            WHERE events_raw.rowid = nested.parent_row
            """;

    /**
     * Counted over a subquery that stops at the first hit, so it answers from one row group rather
     * than counting every traced call in the recording. Wrapped in a count because the caller reads
     * a single number back, and a bare {@code LIMIT 1} returns no row at all when there is nothing
     * to find.
     */
    //language=SQL
    private static final String METHOD_TRACES_EXIST = """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM events_raw WHERE event_type = :event_type LIMIT 1
            )
            """;

    private static final String EVENT_TYPE_PARAM = "event_type";

    private final DatabaseClient databaseClient;

    public JdbcMethodTraceWeightRepository(DatabaseClientProvider clientProvider) {
        this.databaseClient = clientProvider.provide(PROFILE_EVENTS);
    }

    @Override
    public int deriveSelfWeights() {
        return databaseClient.update(
                StatementLabel.DERIVE_METHOD_TRACE_WEIGHTS, DERIVE_SELF_WEIGHTS, eventTypeParam());
    }

    @Override
    public boolean hasMethodTraces() {
        return databaseClient.queryExists(
                StatementLabel.METHOD_TRACES_EXIST, METHOD_TRACES_EXIST, eventTypeParam());
    }

    private static MapSqlParameterSource eventTypeParam() {
        return new MapSqlParameterSource().addValue(EVENT_TYPE_PARAM, EventTypeName.METHOD_TRACE);
    }
}

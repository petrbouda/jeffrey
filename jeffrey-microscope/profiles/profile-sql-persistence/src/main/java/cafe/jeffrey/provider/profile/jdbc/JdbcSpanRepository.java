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

import cafe.jeffrey.provider.profile.api.ThreadWindowEventRecord;
import cafe.jeffrey.provider.profile.api.SpanRecord;
import cafe.jeffrey.provider.profile.api.SpanRepository;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static cafe.jeffrey.shared.persistence.GroupLabel.PROFILE_EVENTS;

/**
 * Reads async-profiler {@code profiler.Span} events straight from the per-profile {@code events}
 * table. Spans are parsed there generically like any other JFR event; this repository just selects
 * them, resolves the thread via {@code thread_hash}, and exposes the tag from the JSON fields. The
 * span↔event association is reconstructed by thread identity ({@code thread_hash}) + time overlap —
 * {@code thread_hash} rather than {@code os_id} so it works for virtual threads too (a virtual
 * thread has no OS id).
 */
public class JdbcSpanRepository implements SpanRepository {

    /** The async-profiler event this feature is built on, and the one the drill-down leaves out. */
    private static final String SPAN_EVENT_TYPE = "profiler.Span";

    //language=SQL
    private static final String LIST_SPANS = """
            SELECT
                e.start_timestamp_from_beginning          AS start_ms,
                EPOCH_MS(e.start_timestamp)               AS start_epoch_ms,
                e.duration                                AS duration_ns,
                e.thread_hash                             AS thread_hash,
                COALESCE(t.os_id, 0)                      AS os_id,
                COALESCE(t.java_id, 0)                    AS java_id,
                t.name                                    AS thread_name,
                COALESCE(t.is_virtual, FALSE)             AS is_virtual,
                json_extract_string(e.fields, '$.tag')    AS tag
            FROM events e
            LEFT JOIN threads t ON e.thread_hash = t.thread_hash
            WHERE e.event_type = :event_type
            ORDER BY e.start_timestamp
            """;

    /** What ran on the span's thread while it was open, minus the span events themselves. */
    private static final String EVENTS_FOR_THREAD =
            ThreadWindowEvents.excluding("e.event_type <> :span_event_type");

    private final DatabaseClient databaseClient;

    public JdbcSpanRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(PROFILE_EVENTS);
    }

    @Override
    public List<SpanRecord> listSpans() {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("event_type", SPAN_EVENT_TYPE);

        return databaseClient.query(
                StatementLabel.LIST_SPANS,
                LIST_SPANS,
                params,
                (rs, _) -> new SpanRecord(
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_ms"),
                        rs.getLong("duration_ns"),
                        rs.getLong("thread_hash"),
                        rs.getLong("os_id"),
                        rs.getLong("java_id"),
                        rs.getString("thread_name"),
                        rs.getBoolean("is_virtual"),
                        rs.getString("tag")));
    }

    @Override
    public List<ThreadWindowEventRecord> eventsForThread(
            long threadHash, long fromEpochMillis, long toEpochMillis) {

        MapSqlParameterSource params = ThreadWindowEvents
                .params(threadHash, fromEpochMillis, toEpochMillis)
                .addValue("span_event_type", SPAN_EVENT_TYPE);

        return databaseClient.query(
                StatementLabel.SPAN_EVENTS,
                EVENTS_FOR_THREAD,
                params,
                ThreadWindowEvents.mapper());
    }
}

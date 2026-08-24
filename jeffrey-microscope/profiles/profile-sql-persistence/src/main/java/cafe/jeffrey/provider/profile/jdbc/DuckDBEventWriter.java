/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.provider.profile.api.*;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import static cafe.jeffrey.provider.profile.jdbc.DuckDBAppenderUtils.nullableAppend;

public class DuckDBEventWriter extends DuckDBBatchingWriter<Event> {

    /**
     * Zero point of the relative event timeline ({@code start_timestamp_from_beginning}).
     * It is the profiling start of the recording, matching Java's {@code RelativeTimeRange}.
     */
    private final long profilingStartedAtMillis;

    /** The physical table behind the {@code events} view — the appender writes past the view. */
    private static final String EVENTS_TABLE = "events_raw";

    public DuckDBEventWriter(
            Executor executor,
            DataSource dataSource,
            int batchSize,
            Instant profilingStartedAt,
            BatchFlushLimit flushLimit) {

        super(executor, EVENTS_TABLE, dataSource, batchSize, StatementLabel.INSERT_EVENTS, flushLimit);
        Objects.requireNonNull(profilingStartedAt, "profilingStartedAt must be provided to compute relative event timestamps");
        this.profilingStartedAtMillis = profilingStartedAt.toEpochMilli();
    }

    @Override
    public void execute(DuckDBConnection connection, List<Event> batch) throws Exception {
        try (DuckDBAppender appender = connection.createAppender(EVENTS_TABLE)) {
            for (Event event : batch) {
                appender.beginRow();
                // event_type - VARCHAR
                appender.append(event.eventType());
                // start_timestamp - TIMESTAMP_MS NOT NULL
                appender.append(event.startTimestamp().atOffset(ZoneOffset.UTC));
                // start_timestamp_from_beginning - BIGINT (millis since profiling start)
                appender.append(event.startTimestamp().toEpochMilli() - profilingStartedAtMillis);
                // duration - BIGINT (nullable)
                nullableAppend(appender, event.duration());
                // samples - BIGINT NOT NULL
                appender.append(event.samples());
                // weight - BIGINT (nullable)
                nullableAppend(appender, event.weight());
                // weight_entity - VARCHAR (nullable)
                nullableAppend(appender, event.weightEntity());
                // stack_hash - BIGINT (nullable) - maps from stacktraceId
                nullableAppend(appender, event.stacktraceId());
                // thread_hash - BIGINT (nullable) - hash value
                nullableAppend(appender, event.threadId());
                // fields - JSON (nullable), already written as text by the parser
                nullableAppend(appender, event.fields());
                // pooled_field - VARCHAR (nullable) - key of the field lifted out of `fields`
                Event.PooledField pooled = event.pooledField();
                nullableAppend(appender, pooled != null ? pooled.field() : null);
                // pooled_text_hash - BIGINT (nullable) - reference to field_texts.text_hash
                nullableAppend(appender, pooled != null ? pooled.textHash() : null);
                appender.endRow();
            }
        }
    }
}

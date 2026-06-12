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

import cafe.jeffrey.provider.profile.api.DatabaseWriter;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.shared.persistence.StatementLabel;

import org.apache.arrow.c.ArrowArrayStream;
import org.apache.arrow.c.Data;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BigIntVector;
import org.apache.arrow.vector.TimeStampMicroTZVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Columnar writer for the {@code events} table. Instead of crossing JNI once per appended
 * value (as the row-based {@code DuckDBAppender} does), the whole batch is accumulated into
 * Arrow vectors, exported through the Arrow C Data Interface, registered on the DuckDB
 * connection ({@link DuckDBConnection#registerArrowStream}) and inserted with a single bulk
 * {@code INSERT INTO events SELECT ... FROM <arrow-stream>} statement. On an events-like
 * schema this measured ~3x faster than the appender path (1.0M vs 0.31M rows/s on 4M rows).
 *
 * <p>The dominant Java-side cost of ingestion — serializing the JSON {@code fields} document —
 * is paid on the caller (parser) thread in {@link #insert(Event)}: events are converted into
 * {@link PreparedEvent}s carrying the pre-serialized JSON string, so the serialization runs in
 * parallel across parser threads and overlaps with parsing by construction. The flush threads
 * only fill Arrow vectors from ready values.
 *
 * <p>Flushes may run concurrently on a shared executor: each flush operates on its own pooled
 * connection, its own {@link VectorSchemaRoot} and a process-wide unique stream name, and the
 * shared {@link RootAllocator} is thread-safe.
 */
public class DuckDBArrowEventWriter implements DatabaseWriter<Event> {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBArrowEventWriter.class);

    private static final String EVENTS_TABLE = "events";

    private static final String COLUMN_EVENT_TYPE = "event_type";
    private static final String COLUMN_START_TIMESTAMP = "start_timestamp";
    private static final String COLUMN_START_TIMESTAMP_FROM_BEGINNING = "start_timestamp_from_beginning";
    private static final String COLUMN_DURATION = "duration";
    private static final String COLUMN_SAMPLES = "samples";
    private static final String COLUMN_WEIGHT = "weight";
    private static final String COLUMN_WEIGHT_ENTITY = "weight_entity";
    private static final String COLUMN_STACKTRACE_HASH = "stacktrace_hash";
    private static final String COLUMN_THREAD_HASH = "thread_hash";
    private static final String COLUMN_FIELDS = "fields";

    private static final String UTC_TIMEZONE = "UTC";

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;

    /**
     * The {@code fields} Arrow column is exported as VARCHAR and must be cast to the
     * JSON-typed {@code fields} column of the {@code events} table.
     */
    private static final String INSERT_FROM_ARROW_SQL_TEMPLATE = """
            INSERT INTO events (event_type, start_timestamp, start_timestamp_from_beginning, \
            duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, fields) \
            SELECT event_type, start_timestamp, start_timestamp_from_beginning, \
            duration, samples, weight, weight_entity, stacktrace_hash, thread_hash, CAST(fields AS JSON) \
            FROM "%s\"""";

    private static final String ARROW_STREAM_NAME_PREFIX = "arrow_events_batch_";

    /**
     * Registered Arrow streams live for the rest of the connection's lifetime (connections are
     * pooled) and flushes of the same writer may run concurrently on a shared executor, so every
     * exported batch needs a process-wide unique name to avoid collisions.
     */
    private static final AtomicLong STREAM_NAME_SEQUENCE = new AtomicLong();

    private static final Schema EVENTS_SCHEMA = new Schema(List.of(
            Field.notNullable(COLUMN_EVENT_TYPE, new ArrowType.Utf8()),
            Field.notNullable(COLUMN_START_TIMESTAMP, new ArrowType.Timestamp(TimeUnit.MICROSECOND, UTC_TIMEZONE)),
            Field.notNullable(COLUMN_START_TIMESTAMP_FROM_BEGINNING, new ArrowType.Int(Long.SIZE, true)),
            Field.nullable(COLUMN_DURATION, new ArrowType.Int(Long.SIZE, true)),
            Field.notNullable(COLUMN_SAMPLES, new ArrowType.Int(Long.SIZE, true)),
            Field.nullable(COLUMN_WEIGHT, new ArrowType.Int(Long.SIZE, true)),
            Field.nullable(COLUMN_WEIGHT_ENTITY, new ArrowType.Utf8()),
            Field.nullable(COLUMN_STACKTRACE_HASH, new ArrowType.Int(Long.SIZE, true)),
            Field.nullable(COLUMN_THREAD_HASH, new ArrowType.Int(Long.SIZE, true)),
            Field.nullable(COLUMN_FIELDS, new ArrowType.Utf8())));

    /**
     * An {@link Event} with the caller-thread work already done: the JSON {@code fields}
     * document serialized to a string and the timestamp converted to epoch micros.
     */
    private record PreparedEvent(
            String eventType,
            long startTimestampMicros,
            long startTimestampFromBeginningMillis,
            Long duration,
            long samples,
            Long weight,
            String weightEntity,
            Long stacktraceHash,
            Long threadHash,
            String fieldsJson) {
    }

    private final BufferAllocator allocator;
    private final DuckDBBatchingWriter<PreparedEvent> batchingWriter;

    /**
     * Zero point of the relative event timeline ({@code start_timestamp_from_beginning}).
     * It is the profiling start of the recording, matching Java's {@code RelativeTimeRange}.
     */
    private final long profilingStartedAtMillis;

    public DuckDBArrowEventWriter(Executor executor, DataSource dataSource, int batchSize, Instant profilingStartedAt) {
        // Fail at construction with an actionable message instead of failing mid-parse
        // when the Arrow runtime (add-opens, native library) is unusable.
        ArrowRuntimeSupport.ensureAvailable();
        Objects.requireNonNull(profilingStartedAt, "profilingStartedAt must be provided to compute relative event timestamps");
        this.profilingStartedAtMillis = profilingStartedAt.toEpochMilli();
        this.allocator = new RootAllocator();
        this.batchingWriter = new DuckDBBatchingWriter<>(
                executor, EVENTS_TABLE, dataSource, batchSize, StatementLabel.INSERT_EVENTS) {

            @Override
            protected void execute(DuckDBConnection connection, List<PreparedEvent> batch) throws Exception {
                insertThroughArrowStream(connection, batch);
            }
        };
    }

    @Override
    public void insert(Event event) {
        batchingWriter.insert(prepare(event));
    }

    @Override
    public void insertBatch(List<Event> events) {
        List<PreparedEvent> preparedEvents = new ArrayList<>(events.size());
        for (Event event : events) {
            preparedEvents.add(prepare(event));
        }
        batchingWriter.insertBatch(preparedEvents);
    }

    @Override
    public void close() {
        // Flushes the remaining batch and awaits all pending flushes,
        // so all per-batch roots are already released when the allocator closes.
        batchingWriter.close();
        try {
            allocator.close();
        } catch (IllegalStateException e) {
            // The allocator detected leaked buffers from a failed flush. Failed batches are
            // already logged by the batching writer — don't let the leak check abort
            // the profile initialization on top of it.
            LOG.error("Arrow allocator detected leaked memory after event ingestion", e);
        }
    }

    private PreparedEvent prepare(Event event) {
        return new PreparedEvent(
                event.eventType(),
                toEpochMicros(event.startTimestamp()),
                event.startTimestamp().toEpochMilli() - profilingStartedAtMillis,
                event.duration(),
                event.samples(),
                event.weight(),
                event.weightEntity(),
                event.stacktraceId(),
                event.threadId(),
                event.fields() != null ? event.fields().toString() : null);
    }

    private void insertThroughArrowStream(DuckDBConnection connection, List<PreparedEvent> batch) throws Exception {
        String streamName = ARROW_STREAM_NAME_PREFIX + STREAM_NAME_SEQUENCE.incrementAndGet();
        try (VectorSchemaRoot root = VectorSchemaRoot.create(EVENTS_SCHEMA, allocator)) {
            fillVectors(root, batch);
            try (SingleBatchArrowReader reader = new SingleBatchArrowReader(allocator, root);
                 ArrowArrayStream arrowStream = ArrowArrayStream.allocateNew(allocator)) {

                Data.exportArrayStream(allocator, reader, arrowStream);
                connection.registerArrowStream(streamName, arrowStream);
                try (Statement statement = connection.createStatement()) {
                    statement.execute(INSERT_FROM_ARROW_SQL_TEMPLATE.formatted(streamName));
                }
            }
        }
    }

    private static void fillVectors(VectorSchemaRoot root, List<PreparedEvent> batch) {
        VarCharVector eventType = (VarCharVector) root.getVector(COLUMN_EVENT_TYPE);
        TimeStampMicroTZVector startTimestamp = (TimeStampMicroTZVector) root.getVector(COLUMN_START_TIMESTAMP);
        BigIntVector startTimestampFromBeginning =
                (BigIntVector) root.getVector(COLUMN_START_TIMESTAMP_FROM_BEGINNING);
        BigIntVector duration = (BigIntVector) root.getVector(COLUMN_DURATION);
        BigIntVector samples = (BigIntVector) root.getVector(COLUMN_SAMPLES);
        BigIntVector weight = (BigIntVector) root.getVector(COLUMN_WEIGHT);
        VarCharVector weightEntity = (VarCharVector) root.getVector(COLUMN_WEIGHT_ENTITY);
        BigIntVector stacktraceHash = (BigIntVector) root.getVector(COLUMN_STACKTRACE_HASH);
        BigIntVector threadHash = (BigIntVector) root.getVector(COLUMN_THREAD_HASH);
        VarCharVector fields = (VarCharVector) root.getVector(COLUMN_FIELDS);

        for (int i = 0; i < batch.size(); i++) {
            PreparedEvent event = batch.get(i);
            eventType.setSafe(i, event.eventType().getBytes(StandardCharsets.UTF_8));
            startTimestamp.setSafe(i, event.startTimestampMicros());
            startTimestampFromBeginning.setSafe(i, event.startTimestampFromBeginningMillis());
            setNullableBigInt(duration, i, event.duration());
            samples.setSafe(i, event.samples());
            setNullableBigInt(weight, i, event.weight());
            setNullableVarChar(weightEntity, i, event.weightEntity());
            setNullableBigInt(stacktraceHash, i, event.stacktraceHash());
            setNullableBigInt(threadHash, i, event.threadHash());
            setNullableVarChar(fields, i, event.fieldsJson());
        }
        root.setRowCount(batch.size());
    }

    private static void setNullableBigInt(BigIntVector vector, int index, Long value) {
        if (value != null) {
            vector.setSafe(index, value);
        } else {
            vector.setNull(index);
        }
    }

    private static void setNullableVarChar(VarCharVector vector, int index, String value) {
        if (value != null) {
            vector.setSafe(index, value.getBytes(StandardCharsets.UTF_8));
        } else {
            vector.setNull(index);
        }
    }

    private static long toEpochMicros(Instant instant) {
        return Math.multiplyExact(instant.getEpochSecond(), MICROS_PER_SECOND)
                + instant.getNano() / NANOS_PER_MICRO;
    }
}

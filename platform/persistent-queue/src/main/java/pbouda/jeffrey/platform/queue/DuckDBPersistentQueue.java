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

package pbouda.jeffrey.platform.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * DuckDB-backed implementation of {@link PersistentQueue}. Uses a shared pair of tables
 * ({@code persistent_queue_events} and {@code persistent_queue_consumers}) partitioned
 * by {@code queue_name} and {@code scope_id} columns.
 *
 * <p>This implementation is scope-independent: the {@code scopeId} is passed as a
 * parameter to each operation rather than being fixed at construction time. This allows
 * a single instance to serve multiple logical partitions.
 *
 * <p>Offsets are generated using a DuckDB sequence ({@code persistent_queue_seq}),
 * guaranteeing monotonically increasing values across all queue partitions.
 *
 * @param <T> the type of the event payload
 */
public class DuckDBPersistentQueue<T> implements PersistentQueue<T> {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBPersistentQueue.class);

    //language=SQL
    private static final String INSERT_EVENT = """
            INSERT INTO persistent_queue_events (queue_name, scope_id, dedup_key, payload, created_at)
            VALUES (:queue_name, :scope_id, :dedup_key, :payload, :created_at)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_EVENTS_FROM_OFFSET = """
            SELECT * FROM persistent_queue_events
            WHERE queue_name = :queue_name AND scope_id = :scope_id AND offset_id > :from_offset
            ORDER BY offset_id""";

    //language=SQL
    private static final String SELECT_ALL_EVENTS = """
            SELECT * FROM persistent_queue_events
            WHERE queue_name = :queue_name AND scope_id = :scope_id
            ORDER BY created_at DESC""";

    //language=SQL
    private static final String INSERT_CONSUMER = """
            INSERT INTO persistent_queue_consumers (consumer_id, queue_name, scope_id, last_offset, created_at)
            VALUES (:consumer_id, :queue_name, :scope_id, 0, :created_at)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String UPDATE_CONSUMER_OFFSET = """
            UPDATE persistent_queue_consumers
            SET last_offset = :last_offset, last_execution_at = :last_execution_at
            WHERE consumer_id = :consumer_id AND queue_name = :queue_name AND scope_id = :scope_id""";

    //language=SQL
    private static final String SELECT_CONSUMER = """
            SELECT * FROM persistent_queue_consumers
            WHERE consumer_id = :consumer_id AND queue_name = :queue_name AND scope_id = :scope_id""";

    private final String queueName;
    private final EventSerializer<T> serializer;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public DuckDBPersistentQueue(
            DatabaseClientProvider databaseClientProvider,
            String queueName,
            EventSerializer<T> serializer,
            Clock clock) {

        this.queueName = queueName;
        this.serializer = serializer;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PERSISTENT_QUEUE);
        this.clock = clock;
    }

    @Override
    public void append(String scopeId, T event) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId)
                .addValue("dedup_key", serializer.dedupKey(event))
                .addValue("payload", serializer.serialize(event))
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.QUEUE_APPEND, INSERT_EVENT, params);
    }

    @Override
    public void appendBatch(String scopeId, List<T> events) {
        if (events.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] paramSources = new MapSqlParameterSource[events.size()];
        for (int i = 0; i < events.size(); i++) {
            T event = events.get(i);
            paramSources[i] = new MapSqlParameterSource()
                    .addValue("queue_name", queueName)
                    .addValue("scope_id", scopeId)
                    .addValue("dedup_key", serializer.dedupKey(event))
                    .addValue("payload", serializer.serialize(event))
                    .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));
        }

        long result = databaseClient.batchInsert(StatementLabel.QUEUE_APPEND_BATCH, INSERT_EVENT, paramSources);
        long skipped = events.size() - result;
        if (skipped > 0) {
            LOG.debug("Batch appended queue events with duplicates skipped: queue_name={} scope_id={} inserted={} skipped={}",
                    queueName, scopeId, result, skipped);
        } else {
            LOG.debug("Batch appended queue events: queue_name={} scope_id={} count={}",
                    queueName, scopeId, events.size());
        }
    }

    @Override
    public List<QueueEntry<T>> poll(String scopeId, String consumerId) {
        long lastOffset = getOrCreateConsumerOffset(scopeId, consumerId);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId)
                .addValue("from_offset", lastOffset);

        List<QueueEntry<T>> entries = databaseClient.query(
                StatementLabel.QUEUE_POLL,
                SELECT_EVENTS_FROM_OFFSET,
                params,
                queueEntryMapper());

        LOG.debug("Polled queue events: queue_name={} scope_id={} consumer_id={} count={}",
                queueName, scopeId, consumerId, entries.size());

        return entries;
    }

    @Override
    public void acknowledge(String scopeId, String consumerId, long offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId)
                .addValue("last_offset", offset)
                .addValue("last_execution_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.QUEUE_ACKNOWLEDGE, UPDATE_CONSUMER_OFFSET, params);
    }

    @Override
    public List<QueueEntry<T>> findAll(String scopeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId);

        return databaseClient.query(
                StatementLabel.QUEUE_FIND_ALL,
                SELECT_ALL_EVENTS,
                params,
                queueEntryMapper());
    }

    private long getOrCreateConsumerOffset(String scopeId, String consumerId) {
        // Ensure consumer exists
        MapSqlParameterSource insertParams = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId)
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.QUEUE_REGISTER_CONSUMER, INSERT_CONSUMER, insertParams);

        // Read consumer offset
        MapSqlParameterSource selectParams = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("queue_name", queueName)
                .addValue("scope_id", scopeId);

        Optional<Long> offset = databaseClient.querySingle(
                StatementLabel.QUEUE_FIND_CONSUMER,
                SELECT_CONSUMER,
                selectParams,
                (rs, _) -> rs.getLong("last_offset"));

        return offset.orElse(0L);
    }

    private RowMapper<QueueEntry<T>> queueEntryMapper() {
        return (rs, _) -> {
            long offsetId = rs.getLong("offset_id");
            String payload = rs.getString("payload");
            Instant createdAt = rs.getTimestamp("created_at").toInstant();
            return new QueueEntry<>(offsetId, serializer.deserialize(payload), createdAt);
        };
    }
}

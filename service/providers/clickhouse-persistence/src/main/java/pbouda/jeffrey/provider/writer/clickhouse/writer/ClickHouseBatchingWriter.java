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

package pbouda.jeffrey.provider.writer.clickhouse.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.api.DatabaseWriter;
import pbouda.jeffrey.provider.writer.clickhouse.ClickHouseClient;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public abstract class ClickHouseBatchingWriter<T, O> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ClickHouseBatchingWriter.class);

    private final String tableName;
    private final ClickHouseClient databaseClient;
    private final int batchSize;
    private final StatementLabel statementLabel;

    private final List<T> batch = new ArrayList<>();

    public ClickHouseBatchingWriter(
            String tableName,
            ClickHouseClient databaseClient,
            int batchSize,
            StatementLabel statementLabel) {

        this.tableName = tableName;
        this.databaseClient = databaseClient;
        this.batchSize = batchSize;
        this.statementLabel = statementLabel;
    }

    @Override
    public void start() {
    }

    @Override
    public void insert(T event) {
        batch.add(event);
        if (batch.size() >= batchSize) {
            sendBatch(batch);
        }
    }

    @Override
    public void insertBatch(List<T> events) {
        batch.addAll(events);
        if (batch.size() >= batchSize) {
            sendBatch(batch);
        }
    }

    protected abstract O entityMapper(T entity);

    private void sendBatch(List<T> batch) {
        if (batch.isEmpty()) {
            LOG.info("Batch of items is empty: type={}", tableName);
            return;
        }

        long start = System.nanoTime();

        List<O> mappedBatch = batch.stream()
                .map(this::entityMapper)
                .toList();

        try {
            // TODO: Use Completable Future to let the request running and keep preparing the next one
            databaseClient.batchInsert(tableName, mappedBatch)
                    .get();
        } catch (Exception e) {
            LOG.error("Failed to insert batch of items: type={} size={}",
                    tableName, batch.size(), e);
        }

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Batch of items has been flushed: type={} size={} elapsed_ms={}",
                tableName, batch.size(), millis);

        this.batch.clear();
    }

    @Override
    public void close() {
        sendBatch(batch);
    }
}

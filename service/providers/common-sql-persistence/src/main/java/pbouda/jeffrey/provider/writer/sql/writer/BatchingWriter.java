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

package pbouda.jeffrey.provider.writer.sql.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public abstract class BatchingWriter<T> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(BatchingWriter.class);

    private final DatabaseClient databaseClient;
    private final int batchSize;
    private final Class<T> clazz;
    private final String insertQuery;
    private final StatementLabel statementLabel;

    private final List<T> batch = new ArrayList<>();

    public BatchingWriter(
            Class<T> clazz,
            DatabaseClient databaseClient,
            String insertQuery,
            int batchSize,
            StatementLabel statementLabel) {

        this.databaseClient = databaseClient;
        this.batchSize = batchSize;
        this.clazz = clazz;
        this.insertQuery = insertQuery;
        this.statementLabel = statementLabel;
    }

    @Override
    public void start() {
    }

    public void insert(T event) {
        if (batch.size() >= batchSize) {
            sendBatch(batch);
        }
        batch.add(event);
    }

    protected abstract SqlParameterSource queryMapper(T entity);

    private void sendBatch(List<T> batch) {
        if (batch.isEmpty()) {
            LOG.info("Batch of items is empty: type={}", clazz.getSimpleName());
            return;
        }

        long start = System.nanoTime();

        SqlParameterSource[] values = new SqlParameterSource[batch.size()];
        for (int i = 0; i < batch.size(); i++) {
            values[i] = queryMapper(batch.get(i));
        }

        try {
            databaseClient.batchInsert(statementLabel, insertQuery, values);
        } catch (Exception e) {
            LOG.error("Failed to insert batch of items: type={} size={}", clazz.getSimpleName(), batch.size(), e);
        }

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Batch of items has been flushed: type={} size={} elapsed_ms={}",
                clazz.getSimpleName(), batch.size(), millis);

        this.batch.clear();
    }

    @Override
    public void close() {
        sendBatch(batch);
    }
}

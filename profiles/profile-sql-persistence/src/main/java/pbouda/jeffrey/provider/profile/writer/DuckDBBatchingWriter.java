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

package pbouda.jeffrey.provider.profile.writer;

import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.provider.profile.DatabaseWriter;
import pbouda.jeffrey.shared.persistence.DataSourceUtils;
import pbouda.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class DuckDBBatchingWriter<T> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBBatchingWriter.class);

    private final Executor executor;
    private final String tableName;
    private final DataSource dataSource;
    private final int batchSize;
    private final StatementLabel statementLabel;

    private final List<T> batch = new ArrayList<>();

    public DuckDBBatchingWriter(
            Executor executor,
            String tableName,
            DataSource dataSource,
            int batchSize,
            StatementLabel statementLabel) {

        this.executor = executor;
        this.tableName = tableName;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.statementLabel = statementLabel;
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

    protected abstract void execute(DuckDBConnection connection, List<T> events) throws Exception;

    private void sendBatch(List<T> batch) {
        if (batch.isEmpty()) {
            LOG.info("Batch of items is empty: type={}", tableName);
            return;
        }

        List<T> copiedBatch = List.copyOf(batch);
        executor.execute(() -> {
            long start = System.nanoTime();

            try (Connection conn = dataSource.getConnection()) {
                DuckDBConnection duckDBConnection = DataSourceUtils.unwrapConnection(conn, DuckDBConnection.class);
                execute(duckDBConnection, copiedBatch);
            } catch (Exception e) {
                LOG.error("Failed to insert batch of items: type={} size={}",
                        tableName, copiedBatch.size(), e);
            }

            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            LOG.info("Batch of items has been flushed: type={} size={} elapsed_ms={}",
                    tableName, copiedBatch.size(), millis);
        });

        this.batch.clear();
    }

    @Override
    public void close() {
        sendBatch(batch);
    }
}

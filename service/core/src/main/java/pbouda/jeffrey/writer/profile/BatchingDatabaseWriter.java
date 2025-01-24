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

package pbouda.jeffrey.writer.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.repository.SQLiteBatchingClient;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;

public abstract class BatchingDatabaseWriter<T> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(BatchingDatabaseWriter.class);

    private final int batchSize;
    private final SQLiteBatchingClient client;
    private final String className;

    private int batchCounter = 0;

    public BatchingDatabaseWriter(Class<T> clazz, DataSource dataSource, int batchSize, String insertQuery) {
        this.className = clazz.getSimpleName();
        this.client = new SQLiteBatchingClient(dataSource, insertQuery);
        this.batchSize = batchSize;
    }

    @Override
    public void start() {
        this.client.disableCheckpointingInWriteAheadLog();
    }

    abstract void mapper(PreparedStatement statement, T entity) throws SQLException;

    @Override
    public void insert(T entity) {
        try {
            mapper(this.client.statement(), entity);

            this.client.addBatch();
            batchCounter++;

            if (batchCounter >= batchSize) {
                flushCurrentBatch();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot add a new event into the batch", e);
        }
    }

    private void flushCurrentBatch() {
        long start = System.nanoTime();
        this.client.executeAndClearBatch();
        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Batch of items has been flushed: type={} size={} elapsed_ms={}", className, batchCounter, millis);

        batchCounter = 0;
    }

    @Override
    public void close() {
        flushCurrentBatch();
        this.client.flushDatabaseWriteAheadLog();
        this.client.close();
    }

    protected static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }

    protected static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value != null) {
            ps.setString(index, value);
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }

    protected static void setNullableJson(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value != null) {
            ps.setString(index, Json.toString(value));
        } else {
            ps.setNull(index, Types.VARCHAR);
        }
    }
}

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

package pbouda.jeffrey.provider.writer.sqlite.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public abstract class BatchingWriter<T> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(BatchingWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final Class<T> clazz;
    private final String insertQuery;

    private final List<T> batch = new ArrayList<>();

    public BatchingWriter(Class<T> clazz, JdbcTemplate jdbcTemplate, String insertQuery, int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
        this.clazz = clazz;
        this.insertQuery = insertQuery;
    }

    @Override
    public void start() {
    }

    public void insert(T event) {
        if (batch.size() >= batchSize) {
            sendBatch(batch);
        } else {
            batch.add(event);
        }
    }

    protected abstract Object[] queryMapper(T entity);

    private void sendBatch(List<T> batch) {
        if (batch.isEmpty()) {
            LOG.info("Batch of items is empty: type={}", clazz.getSimpleName());
            return;
        }

        long start = System.nanoTime();

        List<Object[]> values = new ArrayList<>();
        for (T e : batch) {
            values.add(queryMapper(e));
        }
        this.jdbcTemplate.batchUpdate(insertQuery, values);

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

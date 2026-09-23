/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.provider.profile.api.*;

import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.provider.profile.api.DatabaseWriter;
import cafe.jeffrey.shared.persistence.DataSourceUtils;
import cafe.jeffrey.shared.persistence.StatementLabel;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DuckDBBatchingWriter<T> implements DatabaseWriter<T> {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBBatchingWriter.class);

    private final Executor executor;
    private final String tableName;
    private final DataSource dataSource;
    private final int batchSize;
    private final StatementLabel statementLabel;
    private final BatchFlushLimit flushLimit;

    private final List<T> batch = new ArrayList<>();
    private final List<CompletableFuture<Void>> pendingBatches = new ArrayList<>();

    // First failure of an async batch insert. Once set, no further batches are submitted and the
    // failure is rethrown at the synchronization point (close) — otherwise events would be lost silently.
    private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

    public DuckDBBatchingWriter(
            Executor executor,
            String tableName,
            DataSource dataSource,
            int batchSize,
            StatementLabel statementLabel,
            BatchFlushLimit flushLimit) {

        this.executor = executor;
        this.tableName = tableName;
        this.dataSource = dataSource;
        this.batchSize = batchSize;
        this.statementLabel = statementLabel;
        this.flushLimit = flushLimit;
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
            LOG.debug("Batch of items is empty: type={}", tableName);
            return;
        }

        if (firstFailure.get() != null) {
            // A previous batch already failed — the whole write is doomed, don't submit more work.
            this.batch.clear();
            return;
        }

        List<T> copiedBatch = List.copyOf(batch);

        // Blocks the parsing thread once this profile already has as many batches in flight as the
        // writer pool can work on. The slot is given back below, on the writer pool.
        flushLimit.acquire();

        CompletableFuture<Void> future;
        try {
            future = CompletableFuture.runAsync(() -> {
                long start = System.nanoTime();

                try (Connection conn = dataSource.getConnection()) {
                    DuckDBConnection duckDBConnection = DataSourceUtils.unwrapConnection(conn, DuckDBConnection.class);
                    execute(duckDBConnection, copiedBatch);
                } catch (Exception e) {
                    LOG.error("Failed to insert batch of items: type={} size={}",
                            tableName, copiedBatch.size(), e);
                    firstFailure.compareAndSet(null, e);
                    return;
                } finally {
                    flushLimit.release();
                }

                long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
                LOG.debug("Batch of items has been flushed: type={} size={} elapsed_ms={}",
                        tableName, copiedBatch.size(), millis);
            }, executor);
        } catch (RuntimeException e) {
            // The task never ran, so nothing else will hand the slot back.
            flushLimit.release();
            throw e;
        }

        pendingBatches.removeIf(CompletableFuture::isDone);
        pendingBatches.add(future);
        this.batch.clear();
    }

    @Override
    public void close() {
        sendBatch(batch);
        awaitPendingBatches();

        Throwable failure = firstFailure.get();
        if (failure != null) {
            throw new IllegalStateException(
                    "Failed to insert batch of items: type=" + tableName, failure);
        }
    }

    private void awaitPendingBatches() {
        if (pendingBatches.isEmpty()) {
            return;
        }

        try {
            CompletableFuture.allOf(pendingBatches.toArray(CompletableFuture[]::new)).join();
        } catch (Exception e) {
            LOG.error("Failed to await pending batches: type={}", tableName, e);
            firstFailure.compareAndSet(null, e);
        }
        pendingBatches.clear();
    }
}

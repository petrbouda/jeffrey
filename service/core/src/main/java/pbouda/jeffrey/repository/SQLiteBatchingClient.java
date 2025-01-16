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

package pbouda.jeffrey.repository;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

public class SQLiteBatchingClient implements AutoCloseable {

    private static final Duration SLEEP_TIME = Duration.ofMillis(10);
    private static final Duration MAX_SLEEP_TIME = Duration.ofSeconds(10);
    private static final long MAX_RETRIES = MAX_SLEEP_TIME.dividedBy(SLEEP_TIME);

    private final Connection connection;
    private final PreparedStatement statement;

    public interface RunnableWithException {
        void run() throws SQLException;
    }

    public interface SupplierWithException<T> {
        T get() throws SQLException;
    }

    public SQLiteBatchingClient(DataSource dataSource, String preparedQuery) {
        this.connection = retrySupplier(dataSource::getConnection);
        this.statement = retrySupplier(() -> connection.prepareStatement(preparedQuery));
    }

    public PreparedStatement statement() {
        return statement;
    }

    public void addBatch() {
        retry(statement::addBatch);
    }

    public void executeAndClearBatch() {
        retry(statement::executeBatch);

        try {
            statement.clearBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot clear a batch", e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            retry(connection::close);
        }
    }

    /**
     * <a href="https://www.sqlite.org/pragma.html#pragma_wal_autocheckpoint">PRAGMA WAL auto-checkpoint</a>
     * </br>
     * Disables auto-checkpoint for write-ahead log. The checkpoints are triggered when N-number of pages are written
     * into the WAL file. The default value is 1000 pages. The checkpoint is a process that writes the data from the
     * WAL file into the main database file. This disables the automatic checkpointing, and we need to ensure to call
     * checkpoint manually: {@link #flushDatabaseWriteAheadLog()}.
     */
    public void disableCheckpointingInWriteAheadLog() {
        try (Statement stmt = connection.createStatement()) {
            retry(() -> stmt.execute("PRAGMA wal_autocheckpoint=-1;"));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot disable WAL checkpoint", e);
        }
    }

    /**
     * <a href="https://www.sqlite.org/pragma.html#pragma_wal_checkpoint">PRAGMA WAL checkpoint</a>
     * </br>
     * Triggers a checkpoint in the write-ahead log. The checkpoint writes the data from the WAL file into the main
     * database file.
     */
    public void flushDatabaseWriteAheadLog() {
        try (Statement stmt = connection.createStatement()) {
            retry(() -> stmt.execute("PRAGMA wal_checkpoint(FULL)"));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot disable WAL checkpoint", e);
        }
    }

    private static void retry(RunnableWithException executable) {
        retrySupplier(() -> {
            executable.run();
            return null;
        });
    }

    private static <T> T retrySupplier(SupplierWithException<T> supplier) {
        int retries = 0;
        while (true) {
            try {
                return supplier.get();
            } catch (SQLException ex) {
                if (ex instanceof SQLiteException sqlEx
                        && sqlEx.getResultCode().code == SQLiteErrorCode.SQLITE_BUSY.code) {
                    if (++retries > MAX_RETRIES) {
                        throw new RuntimeException("JDBC operation failed (max attempts failed)", ex);
                    }

                    waitForNextAttempt();
                } else {
                    throw new RuntimeException("Cannot execute the JDBC", ex);
                }
            }
        }
    }

    private static void waitForNextAttempt() {
        try {
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
            throw new RuntimeException("Cannot sleep the thread", e);
        }
    }
}

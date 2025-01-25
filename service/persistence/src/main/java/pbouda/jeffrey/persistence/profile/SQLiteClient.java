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

package pbouda.jeffrey.persistence.profile;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;

public class SQLiteClient implements AutoCloseable {

    private static final Duration SLEEP_TIME = Duration.ofMillis(10);
    private static final Duration MAX_SLEEP_TIME = Duration.ofSeconds(10);
    private static final long MAX_RETRIES = MAX_SLEEP_TIME.dividedBy(SLEEP_TIME);

    final Connection connection;
    final PreparedStatement statement;

    public interface RunnableWithException {
        void run() throws Exception;
    }

    public interface SupplierWithException<T> {
        T get() throws Exception;
    }

    public SQLiteClient(DataSource dataSource, String preparedQuery) {
        this.connection = retrySupplier(dataSource::getConnection);
        this.statement = retrySupplier(() -> connection.prepareStatement(preparedQuery));
    }

    public PreparedStatement statement() {
        return statement;
    }

    public void select(ResultSetConsumer consumer) {
        retry(() -> {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                consumer.accept(resultSet);
            }
        });
    }

    @Override
    public void close() {
        if (connection != null) {
            retry(connection::close);
        }
    }

    static void retry(RunnableWithException executable) {
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
            } catch (Exception ex) {
                throw new RuntimeException("Cannot execute the operation", ex);
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

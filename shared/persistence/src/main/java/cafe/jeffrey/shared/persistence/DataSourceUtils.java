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

package cafe.jeffrey.shared.persistence;

import javax.sql.DataSource;
import java.sql.Connection;

public abstract class DataSourceUtils {

    /**
     * Close the datasource if it's a closeable type. Useful if the underlying datasource is pool-based
     * and needs to be released (e.g. Hikari).
     *
     * @param dataSource datasource that needs to be released/closed
     */
    public static void close(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new RuntimeException("Cannot release data source to the database", e);
            }
        }
    }

    /**
     * Close the given connection.
     *
     * @param connection the connection to close
     */
    public static void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                throw new RuntimeException("Cannot close connection to the database", e);
            }
        }
    }

    /**
     * Get a connection from the given datasource.
     *
     * @param dataSource the datasource to get the connection from
     */
    public static Connection connection(DataSource dataSource) {
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            throw new RuntimeException("Cannot get connection from the database", e);
        }
    }

    /**
     * Unwrap the given connection to the given class.
     *
     * @param connection the connection to unwrap
     * @param clazz      the class to unwrap to
     * @param <T>        the type of the class
     * @return the unwrapped connection
     */
    public static <T> T unwrapConnection(Connection connection, Class<T> clazz) {
        try {
            if (connection.isWrapperFor(clazz)) {
                return connection.unwrap(clazz);
            } else {
                throw new IllegalArgumentException(
                        "Connection is not a wrapper: expected=" + clazz.getName() + " actual=" + connection.getClass().getName());
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Cannot unwrap connection: expected=" + clazz.getName() + " actual=" + connection.getClass().getName(), e);
        }
    }
}

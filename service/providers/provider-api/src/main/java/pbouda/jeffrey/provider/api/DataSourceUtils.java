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

package pbouda.jeffrey.provider.api;

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
     * Unwrap the given DataSource to the specified class.
     *
     * @param dataSource the DataSource to unwrap
     * @param clazz      the class to unwrap to
     * @param <T>        the type of the class
     * @return the unwrapped DataSource
     * @throws IllegalArgumentException if the DataSource cannot be unwrapped to the specified class
     */
    public static <T> T connection(DataSource dataSource, Class<T> clazz) {
        try {
            Connection connection = dataSource.getConnection();
            try {
                if (connection.isWrapperFor(clazz)) {
                    return connection.unwrap(clazz);
                } else {
                    throw new IllegalArgumentException("Data source is not a wrapper: expected=" + clazz.getName() + " actual=" + connection.getClass().getName());
                }
            } catch (Exception e) {
                throw new RuntimeException("Cannot unwrap data source: expected=" + clazz.getName() + " actual=" + connection.getClass().getName(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Cannot get connection from data source", e);
        }
    }
}

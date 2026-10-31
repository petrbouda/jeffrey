/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * A prepared statement's ORDER BY, read through the DuckDB JDBC driver, comes back out of order once
 * the result passes one row group while preserve_insertion_order is off. No Jeffrey code: a fresh
 * in-memory database, one table of shuffled keys, one ordered read per configuration.
 *
 * <p>Run: {@code java --enable-native-access=ALL-UNNAMED -cp <duckdb_jdbc jar> build/scripts/DuckDbOrderRepro.java}.
 * Kept beside the build scripts so a driver upgrade can be checked against it: while any line reads more
 * than 0, the heap index and dominator builds must keep placing rows by an index column (backend-java.md).
 */
public final class DuckDbOrderRepro {

    private static final int ROWS = 1_000_000;

    private static final String CREATE = "CREATE TABLE t AS SELECT i AS k FROM range(" + ROWS + ") r(i) ORDER BY random()";

    private static final String ORDERED = "SELECT k FROM t ORDER BY k";

    private DuckDbOrderRepro() {
    }

    public static void main(String[] args) throws SQLException {
        System.out.println("DuckDB " + driverVersion() + ", Java " + Runtime.version());
        report("preserve_insertion_order = false, prepared", false, false, 0, true);
        report("preserve_insertion_order = false + jdbc_stream_results, prepared", false, true, 0, true);
        report("preserve_insertion_order = false, plain Statement", false, false, 0, false);
        report("preserve_insertion_order = false, threads = 1, prepared", false, false, 1, true);
        report("default (preserve_insertion_order = true), prepared", true, false, 0, true);
    }

    private static void report(String label, boolean preserveOrder, boolean streamResults, int threads, boolean prepared)
            throws SQLException {
        Properties properties = new Properties();
        if (streamResults) {
            properties.setProperty("jdbc_stream_results", "true");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:", properties);
             Statement setup = connection.createStatement()) {
            setup.execute("SET preserve_insertion_order = " + preserveOrder);
            if (threads > 0) {
                setup.execute("SET threads = " + threads);
            }
            setup.execute(CREATE);
            long outOfOrder = prepared ? countPrepared(connection) : countPlain(setup);
            System.out.printf("%-70s rows out of order: %d of %d%n", label, outOfOrder, ROWS);
        }
    }

    private static long countPrepared(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ORDERED);
             ResultSet rows = statement.executeQuery()) {
            return outOfOrder(rows);
        }
    }

    private static long countPlain(Statement statement) throws SQLException {
        try (ResultSet rows = statement.executeQuery(ORDERED)) {
            return outOfOrder(rows);
        }
    }

    /** Rows whose key is smaller than the one before it: zero for a correctly ordered result. */
    private static long outOfOrder(ResultSet rows) throws SQLException {
        long previous = Long.MIN_VALUE;
        long count = 0;
        while (rows.next()) {
            long key = rows.getLong(1);
            if (key < previous) {
                count++;
            }
            previous = key;
        }
        return count;
    }

    private static String driverVersion() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("SELECT library_version, source_id FROM pragma_version()")) {
            version.next();
            return version.getString(1) + " (" + version.getString(2) + ")";
        }
    }
}

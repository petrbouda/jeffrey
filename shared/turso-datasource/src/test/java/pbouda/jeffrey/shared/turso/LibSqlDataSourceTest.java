/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.shared.turso;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the libsql (Turso) DataSource implementation using file-based databases.
 *
 * <p>Tests verify the full JDBC lifecycle: DataSource → Connection → PreparedStatement → ResultSet,
 * including CRUD operations, transactions, concurrent writes (MVCC), and type handling.
 *
 * <p>Requires the native libsql library to be available (bundled in resources or on system path).
 */
class LibSqlDataSourceTest {

    @TempDir
    Path tempDir;

    private LibSqlDataSource dataSource;

    @BeforeEach
    void setUp() {
        Path dbPath = tempDir.resolve("test.db");
        dataSource = new LibSqlDataSource(dbPath.toString(), false);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Nested
    class DataSourceLifecycle {

        @Test
        void createsFileBasedDatabase() {
            assertNotNull(dataSource);
            assertNotNull(dataSource.getDatabase());
            assertEquals(tempDir.resolve("test.db").toString(), dataSource.getPath());
        }

        @Test
        void providesWorkingConnection() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                assertNotNull(conn);
                assertFalse(conn.isClosed());
                assertTrue(conn.isValid(1));
            }
        }

        @Test
        void connectionClosedAfterClose() throws SQLException {
            Connection conn = dataSource.getConnection();
            assertFalse(conn.isClosed());
            conn.close();
            assertTrue(conn.isClosed());
        }

        @Test
        void multipleConnectionsToSameDatabase() throws SQLException {
            try (Connection conn1 = dataSource.getConnection();
                 Connection conn2 = dataSource.getConnection()) {
                assertNotNull(conn1);
                assertNotNull(conn2);
                assertNotSame(conn1, conn2);
            }
        }

        @Test
        void databasePersistsAcrossDataSourceInstances() throws SQLException {
            String dbPath = tempDir.resolve("persistent.db").toString();

            // Create and populate with first DataSource
            try (LibSqlDataSource ds1 = new LibSqlDataSource(dbPath, false)) {
                try (Connection conn = ds1.getConnection()) {
                    ((LibSqlConnection) conn).executeBatch(
                            "CREATE TABLE test_persist (id INTEGER PRIMARY KEY, value TEXT);"
                                    + "INSERT INTO test_persist VALUES (1, 'hello');");
                }
            }

            // Re-open and verify data persists
            try (LibSqlDataSource ds2 = new LibSqlDataSource(dbPath, false)) {
                try (Connection conn = ds2.getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT value FROM test_persist WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("hello", rs.getString(1));
                        assertFalse(rs.next());
                    }
                }
            }
        }
    }

    @Nested
    class DDLOperations {

        @Test
        void createsTable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL, price REAL)");

                // Verify table exists by inserting and querying
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO items (id, name, price) VALUES (?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "Widget");
                    ps.setDouble(3, 9.99);
                    assertEquals(1, ps.executeUpdate());
                }
            }
        }

        @Test
        void createsTableWithIndexes() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE indexed_items (id INTEGER PRIMARY KEY, category TEXT, name TEXT);"
                                + "CREATE INDEX idx_category ON indexed_items(category);"
                                + "CREATE INDEX idx_name ON indexed_items(name);");

                // Verify by inserting data
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO indexed_items VALUES (?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "electronics");
                    ps.setString(3, "Phone");
                    assertEquals(1, ps.executeUpdate());
                }
            }
        }
    }

    @Nested
    class CrudOperations {

        @BeforeEach
        void createTable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE users ("
                                + "id INTEGER PRIMARY KEY,"
                                + "name TEXT NOT NULL,"
                                + "email TEXT,"
                                + "age INTEGER,"
                                + "score REAL,"
                                + "active INTEGER NOT NULL DEFAULT 1,"
                                + "created_at INTEGER NOT NULL"
                                + ")");
            }
        }

        @Test
        void insertAndSelectSingleRow() throws SQLException {
            long createdAt = 1710000000000000L; // epoch micros

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement insert = conn.prepareStatement(
                         "INSERT INTO users (id, name, email, age, score, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "Alice");
                insert.setString(3, "alice@example.com");
                insert.setInt(4, 30);
                insert.setDouble(5, 95.5);
                insert.setBoolean(6, true);
                insert.setLong(7, createdAt);
                assertEquals(1, insert.executeUpdate());
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement select = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                select.setInt(1, 1);
                try (ResultSet rs = select.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt("id"));
                    assertEquals("Alice", rs.getString("name"));
                    assertEquals("alice@example.com", rs.getString("email"));
                    assertEquals(30, rs.getInt("age"));
                    assertEquals(95.5, rs.getDouble("score"), 0.001);
                    assertTrue(rs.getBoolean("active"));
                    assertEquals(createdAt, rs.getLong("created_at"));
                    assertFalse(rs.next());
                }
            }
        }

        @Test
        void insertMultipleRowsAndSelectAll() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                for (int i = 1; i <= 5; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO users (id, name, email, age, score, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        ps.setInt(1, i);
                        ps.setString(2, "User" + i);
                        ps.setString(3, "user" + i + "@test.com");
                        ps.setInt(4, 20 + i);
                        ps.setDouble(5, 80.0 + i);
                        ps.setBoolean(6, i % 2 == 0);
                        ps.setLong(7, 1710000000000000L + i * 1000000L);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement select = conn.prepareStatement(
                        "SELECT COUNT(*) as cnt FROM users")) {
                    try (ResultSet rs = select.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(5, rs.getInt("cnt"));
                    }
                }
            }
        }

        @Test
        void updateRow() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                // Insert
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (id, name, email, age, score, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "Bob");
                    ps.setString(3, "bob@test.com");
                    ps.setInt(4, 25);
                    ps.setDouble(5, 70.0);
                    ps.setBoolean(6, true);
                    ps.setLong(7, 1710000000000000L);
                    ps.executeUpdate();
                }

                // Update
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET name = ?, score = ? WHERE id = ?")) {
                    ps.setString(1, "Robert");
                    ps.setDouble(2, 85.0);
                    ps.setInt(3, 1);
                    assertEquals(1, ps.executeUpdate());
                }

                // Verify
                try (PreparedStatement ps = conn.prepareStatement("SELECT name, score FROM users WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Robert", rs.getString("name"));
                        assertEquals(85.0, rs.getDouble("score"), 0.001);
                    }
                }
            }
        }

        @Test
        void deleteRow() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                // Insert two rows
                ((LibSqlConnection) conn).executeBatch(
                        "INSERT INTO users (id, name, email, age, score, active, created_at) VALUES (1, 'A', 'a@t.com', 20, 80.0, 1, 1710000000000000);"
                                + "INSERT INTO users (id, name, email, age, score, active, created_at) VALUES (2, 'B', 'b@t.com', 25, 90.0, 1, 1710000000000000);");

                // Delete one
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                    ps.setInt(1, 1);
                    assertEquals(1, ps.executeUpdate());
                }

                // Verify
                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM users")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt("cnt"));
                    }
                }
            }
        }

        @Test
        void selectEmptyTable() throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM users")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next());
                }
            }
        }

        @Test
        void selectNonExistentRow() throws SQLException {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
                ps.setInt(1, 999);
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse(rs.next());
                }
            }
        }
    }

    @Nested
    class NullHandling {

        @BeforeEach
        void createTable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE nullable_test (id INTEGER PRIMARY KEY, text_val TEXT, int_val INTEGER, real_val REAL)");
            }
        }

        @Test
        void insertAndReadNullValues() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO nullable_test (id, text_val, int_val, real_val) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setNull(2, Types.VARCHAR);
                    ps.setNull(3, Types.INTEGER);
                    ps.setNull(4, Types.DOUBLE);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nullable_test WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertNull(rs.getString("text_val"));
                        assertTrue(rs.wasNull());
                        assertEquals(0, rs.getInt("int_val"));
                        assertTrue(rs.wasNull());
                        assertEquals(0.0, rs.getDouble("real_val"), 0.001);
                        assertTrue(rs.wasNull());
                    }
                }
            }
        }

        @Test
        void mixedNullAndNonNullValues() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO nullable_test (id, text_val, int_val, real_val) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "hello");
                    ps.setNull(3, Types.INTEGER);
                    ps.setDouble(4, 3.14);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nullable_test WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("hello", rs.getString("text_val"));
                        assertFalse(rs.wasNull());
                        assertEquals(0, rs.getInt("int_val"));
                        assertTrue(rs.wasNull());
                        assertEquals(3.14, rs.getDouble("real_val"), 0.001);
                        assertFalse(rs.wasNull());
                    }
                }
            }
        }
    }

    @Nested
    class TypeHandling {

        @BeforeEach
        void createTable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE types_test ("
                                + "id INTEGER PRIMARY KEY,"
                                + "text_val TEXT,"
                                + "int_val INTEGER,"
                                + "real_val REAL,"
                                + "blob_val BLOB,"
                                + "bool_val INTEGER"
                                + ")");
            }
        }

        @Test
        void allSupportedTypes() throws SQLException {
            byte[] blobData = {0x01, 0x02, 0x03, (byte) 0xFF};

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO types_test VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "test string with unicode: \u00E9\u00E8\u00EA");
                    ps.setLong(3, Long.MAX_VALUE);
                    ps.setDouble(4, Math.PI);
                    ps.setBytes(5, blobData);
                    ps.setBoolean(6, true);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM types_test WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("test string with unicode: \u00E9\u00E8\u00EA", rs.getString("text_val"));
                        assertEquals(Long.MAX_VALUE, rs.getLong("int_val"));
                        assertEquals(Math.PI, rs.getDouble("real_val"), 1e-10);
                        assertArrayEquals(blobData, rs.getBytes("blob_val"));
                        assertTrue(rs.getBoolean("bool_val"));
                    }
                }
            }
        }

        @Test
        void emptyStringValue() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO types_test (id, text_val) VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "");
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT text_val FROM types_test WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("", rs.getString("text_val"));
                        assertFalse(rs.wasNull());
                    }
                }
            }
        }

        @Test
        void largeTextValue() throws SQLException {
            String largeText = "x".repeat(100_000);

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO types_test (id, text_val) VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, largeText);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT text_val FROM types_test WHERE id = ?")) {
                    ps.setInt(1, 1);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(largeText, rs.getString("text_val"));
                    }
                }
            }
        }

        @Test
        void resultSetColumnAccessByIndex() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO types_test (id, text_val, int_val, real_val) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "indexed");
                    ps.setLong(3, 42);
                    ps.setDouble(4, 2.718);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, text_val, int_val, real_val FROM types_test")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        // 1-based column index
                        assertEquals(1, rs.getInt(1));
                        assertEquals("indexed", rs.getString(2));
                        assertEquals(42, rs.getLong(3));
                        assertEquals(2.718, rs.getDouble(4), 0.001);
                    }
                }
            }
        }
    }

    @Nested
    class TransactionSupport {

        @BeforeEach
        void createTable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE tx_test (id INTEGER PRIMARY KEY, value TEXT)");
            }
        }

        @Test
        void commitPersistsData() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tx_test VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "committed");
                    ps.executeUpdate();
                }

                conn.commit();
            }

            // Verify from a new connection
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT value FROM tx_test WHERE id = ?")) {
                ps.setInt(1, 1);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("committed", rs.getString(1));
                }
            }
        }

        @Test
        void rollbackDiscardsData() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tx_test VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "rolled-back");
                    ps.executeUpdate();
                }

                conn.rollback();
            }

            // Verify data was NOT persisted
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM tx_test")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt("cnt"));
                }
            }
        }

        @Test
        void multipleCommitsInSameConnection() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                // First transaction
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tx_test VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "first");
                    ps.executeUpdate();
                }
                conn.commit();

                // Second transaction
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tx_test VALUES (?, ?)")) {
                    ps.setInt(1, 2);
                    ps.setString(2, "second");
                    ps.executeUpdate();
                }
                conn.commit();
            }

            // Verify both committed
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM tx_test")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(2, rs.getInt("cnt"));
                }
            }
        }

        @Test
        void closeConnectionRollsBackPendingTransaction() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO tx_test VALUES (?, ?)")) {
                    ps.setInt(1, 1);
                    ps.setString(2, "pending");
                    ps.executeUpdate();
                }
                // Close without committing — should rollback
            }

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM tx_test")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt("cnt"));
                }
            }
        }
    }

    @Nested
    class BatchExecution {

        @Test
        void executesMultipleStatementsInBatch() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE batch_test (id INTEGER PRIMARY KEY, value TEXT);"
                                + "INSERT INTO batch_test VALUES (1, 'one');"
                                + "INSERT INTO batch_test VALUES (2, 'two');"
                                + "INSERT INTO batch_test VALUES (3, 'three');");

                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM batch_test")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(3, rs.getInt("cnt"));
                    }
                }
            }
        }
    }

    @Nested
    class ResultSetMetadata {

        @Test
        void reportsCorrectColumnCountAndNames() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE meta_test (id INTEGER, name TEXT, score REAL);"
                                + "INSERT INTO meta_test VALUES (1, 'test', 99.9);");

                try (PreparedStatement ps = conn.prepareStatement("SELECT id, name, score FROM meta_test");
                     ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    assertEquals(3, meta.getColumnCount());
                    assertEquals("id", meta.getColumnName(1));
                    assertEquals("name", meta.getColumnName(2));
                    assertEquals("score", meta.getColumnName(3));
                }
            }
        }
    }

    @Nested
    class DatabaseMetadata {

        @Test
        void reportsSQLiteProductName() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                java.sql.DatabaseMetaData meta = conn.getMetaData();
                assertEquals("SQLite", meta.getDatabaseProductName());
                assertTrue(meta.supportsTransactions());
                assertEquals("\"", meta.getIdentifierQuoteString());
            }
        }

        @Test
        void listsCreatedTables() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE meta_table_a (id INTEGER);"
                                + "CREATE TABLE meta_table_b (id INTEGER);");

                java.sql.DatabaseMetaData meta = conn.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, null, null)) {
                    List<String> tableNames = new ArrayList<>();
                    while (rs.next()) {
                        tableNames.add(rs.getString("TABLE_NAME"));
                    }
                    assertTrue(tableNames.contains("meta_table_a"));
                    assertTrue(tableNames.contains("meta_table_b"));
                }
            }
        }
    }

    @Nested
    class ConcurrentAccess {

        @Test
        void multipleConnectionsReadConcurrently() throws Exception {
            // Setup shared table
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE concurrent_read (id INTEGER PRIMARY KEY, value TEXT);"
                                + "INSERT INTO concurrent_read VALUES (1, 'shared');");
            }

            int threadCount = 4;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try (Connection conn = dataSource.getConnection();
                         PreparedStatement ps = conn.prepareStatement("SELECT value FROM concurrent_read WHERE id = ?")) {
                        ps.setInt(1, 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return rs.getString(1);
                            }
                        }
                    }
                    return null;
                }));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            for (Future<String> future : futures) {
                assertEquals("shared", future.get());
            }
        }

        @Test
        void multipleConnectionsWriteSequentially() throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch(
                        "CREATE TABLE concurrent_write (id INTEGER PRIMARY KEY, thread_name TEXT)");
            }

            int threadCount = 4;
            int rowsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Integer>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                int threadId = t;
                futures.add(executor.submit(() -> {
                    int inserted = 0;
                    for (int i = 0; i < rowsPerThread; i++) {
                        try (Connection conn = dataSource.getConnection();
                             PreparedStatement ps = conn.prepareStatement(
                                     "INSERT INTO concurrent_write VALUES (?, ?)")) {
                            ps.setInt(1, threadId * 1000 + i);
                            ps.setString(2, "thread-" + threadId);
                            ps.executeUpdate();
                            inserted++;
                        }
                    }
                    return inserted;
                }));
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            int totalInserted = 0;
            for (Future<Integer> future : futures) {
                totalInserted += future.get();
            }
            assertEquals(threadCount * rowsPerThread, totalInserted);

            // Verify total count
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) as cnt FROM concurrent_write")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(threadCount * rowsPerThread, rs.getInt("cnt"));
                }
            }
        }
    }

    @Nested
    class PlatformSchemaCompatibility {

        @Test
        void createsPlatformSchema() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                // Execute the Turso platform migration schema inline
                ((LibSqlConnection) conn).executeBatch("""
                        CREATE TABLE IF NOT EXISTS projects (
                            project_id TEXT NOT NULL,
                            project_name TEXT NOT NULL,
                            workspace_id TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            attributes TEXT NOT NULL,
                            graph_visualization TEXT NOT NULL,
                            PRIMARY KEY (project_id)
                        );
                        CREATE TABLE IF NOT EXISTS profiles (
                            profile_id TEXT NOT NULL,
                            project_id TEXT NOT NULL,
                            profile_name TEXT NOT NULL,
                            event_source TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            recording_id TEXT NOT NULL,
                            PRIMARY KEY (profile_id)
                        );
                        CREATE TABLE IF NOT EXISTS workspaces (
                            workspace_id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            deleted INTEGER NOT NULL
                        );
                        """);

                // Insert a workspace
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO workspaces (workspace_id, name, created_at, type, deleted) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, "ws-001");
                    ps.setString(2, "Default Workspace");
                    ps.setLong(3, 1710000000000000L);
                    ps.setString(4, "LOCAL");
                    ps.setBoolean(5, false);
                    ps.executeUpdate();
                }

                // Insert a project
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO projects (project_id, project_name, workspace_id, created_at, attributes, graph_visualization) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, "proj-001");
                    ps.setString(2, "My Project");
                    ps.setString(3, "ws-001");
                    ps.setLong(4, 1710000000000000L);
                    ps.setString(5, "{}");
                    ps.setString(6, "FLAME");
                    ps.executeUpdate();
                }

                // Insert a profile
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO profiles (profile_id, project_id, profile_name, event_source, created_at, recording_id) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, "profile-001");
                    ps.setString(2, "proj-001");
                    ps.setString(3, "CPU Profile");
                    ps.setString(4, "JFR");
                    ps.setLong(5, 1710000000000000L);
                    ps.setString(6, "rec-001");
                    ps.executeUpdate();
                }

                // Query profiles by project
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT p.profile_name, pr.project_name, w.name as workspace_name "
                                + "FROM profiles p "
                                + "JOIN projects pr ON p.project_id = pr.project_id "
                                + "JOIN workspaces w ON pr.workspace_id = w.workspace_id "
                                + "WHERE p.profile_id = ?")) {
                    ps.setString(1, "profile-001");
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("CPU Profile", rs.getString("profile_name"));
                        assertEquals("My Project", rs.getString("project_name"));
                        assertEquals("Default Workspace", rs.getString("workspace_name"));
                    }
                }
            }
        }

        @Test
        void autoIncrementQueue() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch("""
                        CREATE TABLE IF NOT EXISTS persistent_queue_events (
                            offset_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            queue_name TEXT NOT NULL,
                            scope_id TEXT NOT NULL,
                            dedup_key TEXT,
                            payload TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        );
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_pqe_dedup ON persistent_queue_events(queue_name, scope_id, dedup_key);
                        """);

                // Insert events — offset_id auto-increments
                for (int i = 0; i < 3; i++) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO persistent_queue_events (queue_name, scope_id, payload, created_at) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, "test-queue");
                        ps.setString(2, "scope-1");
                        ps.setString(3, "{\"event\":" + i + "}");
                        ps.setLong(4, 1710000000000000L + i);
                        ps.executeUpdate();
                    }
                }

                // Verify auto-increment worked
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT offset_id, payload FROM persistent_queue_events ORDER BY offset_id")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getLong("offset_id"));
                        assertTrue(rs.next());
                        assertEquals(2, rs.getLong("offset_id"));
                        assertTrue(rs.next());
                        assertEquals(3, rs.getLong("offset_id"));
                        assertFalse(rs.next());
                    }
                }
            }
        }

        @Test
        void deduplicationViaUniqueIndex() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                ((LibSqlConnection) conn).executeBatch("""
                        CREATE TABLE IF NOT EXISTS persistent_queue_events (
                            offset_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            queue_name TEXT NOT NULL,
                            scope_id TEXT NOT NULL,
                            dedup_key TEXT,
                            payload TEXT NOT NULL,
                            created_at INTEGER NOT NULL
                        );
                        CREATE UNIQUE INDEX IF NOT EXISTS idx_pqe_dedup ON persistent_queue_events(queue_name, scope_id, dedup_key);
                        """);

                // First insert succeeds
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO persistent_queue_events (queue_name, scope_id, dedup_key, payload, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, "q");
                    ps.setString(2, "s");
                    ps.setString(3, "key-1");
                    ps.setString(4, "original");
                    ps.setLong(5, 1L);
                    ps.executeUpdate();
                }

                // Duplicate dedup_key should fail with constraint violation
                assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO persistent_queue_events (queue_name, scope_id, dedup_key, payload, created_at) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, "q");
                        ps.setString(2, "s");
                        ps.setString(3, "key-1");
                        ps.setString(4, "duplicate");
                        ps.setLong(5, 2L);
                        ps.executeUpdate();
                    }
                });
            }
        }
    }

    @Nested
    class ConnectionBehavior {

        @Test
        void autoCommitEnabledByDefault() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                assertTrue(conn.getAutoCommit());
            }
        }

        @Test
        void transactionIsolationIsSerializable() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                assertEquals(Connection.TRANSACTION_SERIALIZABLE, conn.getTransactionIsolation());
            }
        }

        @Test
        void closedConnectionThrowsOnOperations() throws SQLException {
            Connection conn = dataSource.getConnection();
            conn.close();

            assertThrows(SQLException.class, () -> conn.prepareStatement("SELECT 1"));
            assertThrows(SQLException.class, () -> conn.setAutoCommit(false));
            assertThrows(SQLException.class, () -> conn.commit());
        }

        @Test
        void metadataReturnsConnection() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                java.sql.DatabaseMetaData meta = conn.getMetaData();
                assertSame(conn, meta.getConnection());
            }
        }
    }
}

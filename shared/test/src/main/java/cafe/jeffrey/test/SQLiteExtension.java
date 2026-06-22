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

package cafe.jeffrey.test;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.*;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Optional;

/**
 * JUnit 5 extension for SQLite integration tests.
 * <p>
 * Creates a fresh in-memory SQLite connection before each test and closes it after.
 * Foreign-key enforcement is turned on per connection so {@code ON DELETE CASCADE} constraints
 * behave as in production. Supports parameter injection of {@link Connection} and {@link DataSource}.
 * <p>
 * When used with {@link SQLiteTest} annotation, can run Flyway migrations before each test.
 */
public class SQLiteExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(SQLiteExtension.class);

    private static final String CONNECTION_KEY = "connection";
    private static final String DATASOURCE_KEY = "dataSource";

    private static final String IN_MEMORY_URL = "jdbc:sqlite::memory:";
    // SQLite enforces foreign keys only per connection; the schema relies on ON DELETE CASCADE.
    private static final String ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys = ON";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Connection conn = DriverManager.getConnection(IN_MEMORY_URL);

        try (Statement statement = conn.createStatement()) {
            statement.execute(ENABLE_FOREIGN_KEYS);
        }

        // Wrap connection in DataSource that doesn't close the connection. The single reused
        // connection keeps the in-memory schema alive across repository getConnection() calls.
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(conn, true);

        // Run migrations if configured
        runMigrations(context, dataSource);

        // Store in context for parameter resolution
        ExtensionContext.Store store = context.getStore(NS);
        store.put(CONNECTION_KEY, conn);
        store.put(DATASOURCE_KEY, dataSource);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NS);

        SingleConnectionDataSource dataSource = store.get(DATASOURCE_KEY, SingleConnectionDataSource.class);
        if (dataSource != null) {
            dataSource.destroy();
        }

        Connection conn = store.get(CONNECTION_KEY, Connection.class);
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext paramContext, ExtensionContext extContext) {
        Class<?> type = paramContext.getParameter().getType();
        return type == Connection.class || type == DataSource.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramContext, ExtensionContext extContext) {
        Class<?> type = paramContext.getParameter().getType();
        ExtensionContext.Store store = extContext.getStore(NS);

        if (type == Connection.class) {
            return store.get(CONNECTION_KEY, Connection.class);
        } else if (type == DataSource.class) {
            return store.get(DATASOURCE_KEY, DataSource.class);
        }

        throw new IllegalArgumentException("Unsupported parameter type: " + type);
    }

    private void runMigrations(ExtensionContext context, DataSource dataSource) {
        findSQLiteTestAnnotation(context)
                .map(SQLiteTest::migration)
                .filter(migration -> !migration.isEmpty())
                .ifPresent(migration -> {
                    Flyway flyway = Flyway.configure()
                            .dataSource(dataSource)
                            .locations(migration)
                            .load();
                    flyway.migrate();
                });
    }

    private Optional<SQLiteTest> findSQLiteTestAnnotation(ExtensionContext context) {
        // For nested test classes, we need to check the enclosing class hierarchy
        return context.getTestClass()
                .flatMap(this::findAnnotationInHierarchy);
    }

    private Optional<SQLiteTest> findAnnotationInHierarchy(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null) {
            SQLiteTest annotation = current.getAnnotation(SQLiteTest.class);
            if (annotation != null) {
                return Optional.of(annotation);
            }
            // Check enclosing class for nested test classes
            current = current.getEnclosingClass();
        }
        return Optional.empty();
    }
}

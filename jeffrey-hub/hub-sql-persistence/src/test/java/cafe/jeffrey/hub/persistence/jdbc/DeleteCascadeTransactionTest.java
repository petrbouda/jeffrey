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

package cafe.jeffrey.hub.persistence.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DuckDBTest(migration = "classpath:db/migration/server")
class DeleteCascadeTransactionTest {

    @Test
    void failingStatement_rollsBackTheWholeCascade(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-full-graph.sql");
        DatabaseClient client = provider.provide(GroupLabel.WORKSPACES);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", "ws-001");

        // The first statement would delete rows; the second fails — the whole cascade
        // must roll back, otherwise the graph is left half-deleted
        List<String> cascade = List.of(
                "DELETE FROM persistent_queue_events WHERE scope_id = :workspace_id",
                "DELETE FROM no_such_table WHERE scope_id = :workspace_id");

        assertThrows(Exception.class,
                () -> client.deleteCascade(StatementLabel.DELETE_WORKSPACE, cascade, params));

        assertEquals(1, countRows(dataSource,
                "SELECT COUNT(*) FROM persistent_queue_events WHERE scope_id = 'ws-001'"),
                "The successful first statement must be rolled back with the failed cascade");
    }

    @Test
    void successfulCascade_commitsAllStatements_andReturnsLastStatementCount(DataSource dataSource) throws SQLException {
        var provider = new DatabaseClientProvider(dataSource);
        TestUtils.executeSql(dataSource, "sql/workspace/insert-workspace-full-graph.sql");
        DatabaseClient client = provider.provide(GroupLabel.WORKSPACES);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", "ws-001");

        List<String> cascade = List.of(
                "DELETE FROM persistent_queue_events WHERE scope_id = :workspace_id",
                "DELETE FROM workspaces WHERE workspace_id = :workspace_id");

        int lastStatementRows = client.deleteCascade(StatementLabel.DELETE_WORKSPACE, cascade, params);

        assertEquals(1, lastStatementRows, "Returns affected rows of the last (root-entity) statement");
        assertEquals(0, countRows(dataSource,
                "SELECT COUNT(*) FROM persistent_queue_events WHERE scope_id = 'ws-001'"));
        assertEquals(0, countRows(dataSource,
                "SELECT COUNT(*) FROM workspaces WHERE workspace_id = 'ws-001'"));
    }

    private int countRows(DataSource dataSource, String sql) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

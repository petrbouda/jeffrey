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

@DuckDBTest(migration = "classpath:db/migration/hub")
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
                "DELETE FROM projects WHERE workspace_id = :workspace_id",
                "DELETE FROM no_such_table WHERE scope_id = :workspace_id");

        assertThrows(Exception.class,
                () -> client.deleteCascade(StatementLabel.DELETE_WORKSPACE, cascade, params));

        // Both of ws-001's projects would have gone with the first statement
        assertEquals(2, countRows(dataSource,
                "SELECT COUNT(*) FROM projects WHERE workspace_id = 'ws-001'"),
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
                "DELETE FROM projects WHERE workspace_id = :workspace_id",
                "DELETE FROM workspaces WHERE workspace_id = :workspace_id");

        int lastStatementRows = client.deleteCascade(StatementLabel.DELETE_WORKSPACE, cascade, params);

        assertEquals(1, lastStatementRows, "Returns affected rows of the last (root-entity) statement");
        assertEquals(0, countRows(dataSource,
                "SELECT COUNT(*) FROM projects WHERE workspace_id = 'ws-001'"));
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

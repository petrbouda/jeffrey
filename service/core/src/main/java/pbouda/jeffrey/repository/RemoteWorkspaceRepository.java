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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.repository.model.RemoteWorkspaceEvent;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class RemoteWorkspaceRepository {

    //language=sql
    private static final String SELECT_ALL_EVENTS = """
            SELECT * FROM workspace_events WHERE order_id > :offset ORDER BY created_at DESC
            """;

    //language=sql
    private static final String DELETE_EVENTS_BY_IDS = """
            DELETE FROM workspace_events WHERE order_id <= :offset
            """;

    private final DatabaseClient databaseClient;

    public RemoteWorkspaceRepository(Path workspacePath) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }
        DataSource dataSource = SQLiteUtils.workspace(workspacePath);
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.EXTERNAL_WORKSPACES);
    }

    public List<RemoteWorkspaceEvent> findAllEventsFrom(long offset) {
        MapSqlParameterSource source = new MapSqlParameterSource("offset", offset);

        return databaseClient.query(
                StatementLabel.FIND_ALL_EXTERNAL_WORKSPACE_EVENTS, SELECT_ALL_EVENTS, source, (rs, _) -> {
                    return new RemoteWorkspaceEvent(
                            rs.getLong("order_id"),
                            rs.getString("event_id"),
                            rs.getString("project_id"),
                            rs.getString("event_type"),
                            rs.getString("content"),
                            Instant.ofEpochMilli(rs.getLong("created_at"))
                    );
                });
    }

    public void deleteEventsUntil(long offset) {
        MapSqlParameterSource source = new MapSqlParameterSource("offset", offset);
        databaseClient.delete(StatementLabel.DELETE_EXTERNAL_WORKSPACE_EVENTS_BY_IDS, DELETE_EVENTS_BY_IDS, source);
    }
}

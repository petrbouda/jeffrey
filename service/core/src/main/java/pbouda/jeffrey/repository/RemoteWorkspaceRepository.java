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
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;
import pbouda.jeffrey.repository.model.RemoteWorkspaceEvent;
import pbouda.jeffrey.scheduler.model.WorkspaceProject;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class RemoteWorkspaceRepository {

    //language=sql
    private static final String SELECT_PROJECTS = """
            SELECT * FROM workspace_projects
            """;

    //language=sql
    private static final String SELECT_ALL_EVENTS = """
            SELECT * FROM workspace_events ORDER BY created_at DESC
            """;

    //language=sql
    private static final String DELETE_EVENTS_BY_IDS = """
            DELETE FROM workspace_events WHERE event_id IN (:event_ids)
            """;

    private final DatabaseClient databaseClient;

    public RemoteWorkspaceRepository(Path workspacePath) {
        if (workspacePath == null) {
            throw new IllegalArgumentException("Workspace path cannot be null");
        }
        DataSource dataSource = SQLiteUtils.workspace(workspacePath);
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.EXTERNAL_WORKSPACES);
    }

    public List<WorkspaceProject> allProjects() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_EXTERNAL_WORKSPACE_PROJECTS, SELECT_PROJECTS, (rs, _) -> {
                    return new WorkspaceProject(
                            rs.getString("project_id"),
                            rs.getString("project_name"),
                            rs.getString("workspace_id"),
                            rs.getLong("created_at"),
                            Json.toMap(rs.getString("attributes"))
                    );
                });
    }

    public List<RemoteWorkspaceEvent> findAllEvents() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_EXTERNAL_WORKSPACE_EVENTS, SELECT_ALL_EVENTS, (rs, _) -> {
                    return new RemoteWorkspaceEvent(
                            rs.getString("event_id"),
                            rs.getString("project_id"),
                            rs.getString("event_type"),
                            rs.getString("content"),
                            Instant.ofEpochMilli(rs.getLong("created_at"))
                    );
                });
    }

    public void deleteEventsByIds(List<String> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        MapSqlParameterSource source = new MapSqlParameterSource("event_ids", eventIds);
        databaseClient.delete(StatementLabel.DELETE_EXTERNAL_WORKSPACE_EVENTS_BY_IDS, DELETE_EVENTS_BY_IDS, source);
    }
}

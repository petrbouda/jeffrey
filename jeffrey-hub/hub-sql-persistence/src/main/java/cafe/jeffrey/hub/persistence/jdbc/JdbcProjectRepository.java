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

import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)
              AND p.project_id = :project_id AND p.deleted_at IS NULL""";

    //language=SQL
    private static final String SELECT_SINGLE_PROJECT_INCLUDING_DELETED = """
            SELECT p.* FROM projects p
            WHERE EXISTS (SELECT 1 FROM workspaces w WHERE w.workspace_id = p.workspace_id)
              AND p.project_id = :project_id""";

    //language=SQL
    private static final String RESTORE_PROJECT =
            "UPDATE projects SET deleted_at = NULL WHERE project_id = :project_id";

    //language=SQL
    private static final List<String> DELETE_PROJECT_CASCADE = List.of(
            "DELETE FROM project_instance_sessions WHERE repository_id IN (SELECT repository_id FROM repositories WHERE project_id = :project_id)",
            "DELETE FROM project_instances WHERE project_id = :project_id",
            "DELETE FROM repositories WHERE project_id = :project_id",
            "DELETE FROM scoped_configs WHERE project_id = :project_id",
            "UPDATE projects SET deleted_at = :deleted_at WHERE project_id = :project_id");

    private final Clock clock;
    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRepository(Clock clock, String projectId, DatabaseClientProvider databaseClientProvider) {
        this.clock = clock;
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.SINGLE_PROJECT);
    }

    @Override
    public void delete() {
        // The application clock, not the database's: purgeDeletedProjects ages the stamp with the same one
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("deleted_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.deleteCascade(StatementLabel.DELETE_PROJECT, DELETE_PROJECT_CASCADE, paramSource);
    }

    @Override
    public Optional<ProjectInfo> find() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT, SELECT_SINGLE_PROJECT, paramSource, HubMappers.projectInfoMapper());
    }

    @Override
    public Optional<ProjectInfo> findIncludingDeleted() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROJECT_INCLUDING_DELETED,
                SELECT_SINGLE_PROJECT_INCLUDING_DELETED,
                paramSource,
                HubMappers.projectInfoMapper());
    }

    @Override
    public void restore() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.update(StatementLabel.RESTORE_PROJECT, RESTORE_PROJECT, paramSource);
    }

}

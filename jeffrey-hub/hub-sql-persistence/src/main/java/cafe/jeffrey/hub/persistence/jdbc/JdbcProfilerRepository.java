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

import cafe.jeffrey.hub.persistence.api.ProfilerRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import cafe.jeffrey.hub.model.ProfilerInfo;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.util.List;

public class JdbcProfilerRepository implements ProfilerRepository {

    /**
     * The UNIQUE key of a settings row, computed in SQL from the two scope ids so that the ids
     * themselves can stay NULL where the scope has none. Spelled once and spliced into every
     * statement that has to name a row.
     */
    private static final String SCOPE_KEY = "COALESCE(:workspace_id, '') || ':' || COALESCE(:project_id, '')";

    //language=SQL
    private static final String UPSERT_SETTINGS = """
            INSERT INTO profiler_settings (workspace_id, project_id, scope_key, agent_settings)
            VALUES (:workspace_id, :project_id, <SCOPE_KEY>, :agent_settings)
            ON CONFLICT (scope_key) DO UPDATE SET agent_settings = EXCLUDED.agent_settings"""
            .replace("<SCOPE_KEY>", SCOPE_KEY);

    //language=SQL
    private static final String FIND_WORKSPACE_SETTINGS = """
            SELECT * FROM profiler_settings
            WHERE workspace_id = :workspace_id OR (workspace_id IS NULL AND project_id IS NULL)""";

    //language=SQL
    private static final String DELETE_SETTINGS = """
            DELETE FROM profiler_settings WHERE scope_key = <SCOPE_KEY>"""
            .replace("<SCOPE_KEY>", SCOPE_KEY);

    // The project's own row, its workspace's, and the global one — whichever of them exist.
    // A NULL parameter matches nothing by equality, so a workspace-level query gets the
    // workspace row and the global one, and a global query only the global one.
    //language=SQL
    private static final String FETCH_PROFILER_SETTINGS = """
            SELECT * FROM profiler_settings
            WHERE (workspace_id = :workspace_id AND project_id = :project_id)
               OR (workspace_id = :workspace_id AND project_id IS NULL)
               OR (workspace_id IS NULL AND project_id IS NULL)""";

    private final DatabaseClient databaseClient;

    public JdbcProfilerRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROFILER);
    }

    @Override
    public void upsertSettings(ProfilerInfo profiler) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", profiler.workspaceId())
                .addValue("project_id", profiler.projectId())
                .addValue("agent_settings", profiler.agentSettings());

        databaseClient.insert(StatementLabel.UPSERT_PROFILER_SETTINGS, UPSERT_SETTINGS, paramSource);
    }

    @Override
    public List<ProfilerInfo> fetchProfilerSettings(String workspaceId, String projectId) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_PROFILER_SETTINGS, FETCH_PROFILER_SETTINGS, paramSource, settingsMapper());
    }

    @Override
    public List<ProfilerInfo> findWorkspaceSettings(String workspaceId) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_WORKSPACE_PROFILER_SETTINGS, FIND_WORKSPACE_SETTINGS, paramSource, settingsMapper());
    }

    @Override
    public void deleteSettings(String workspaceId, String projectId) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_PROFILER_SETTINGS, DELETE_SETTINGS, paramSource);
    }

    private static RowMapper<ProfilerInfo> settingsMapper() {
        return (rs, _) -> new ProfilerInfo(
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("agent_settings"));
    }
}

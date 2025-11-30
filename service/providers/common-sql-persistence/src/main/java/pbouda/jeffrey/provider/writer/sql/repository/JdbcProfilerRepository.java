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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.common.model.ProfilerInfo;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.util.List;
import java.util.Optional;

public class JdbcProfilerRepository implements ProfilerRepository {

    //language=SQL
    private static final String UPSERT_SETTINGS = """
            INSERT INTO profiler_settings (workspace_id, project_id, agent_settings)
            VALUES (:workspace_id, :project_id, :agent_settings)
            ON CONFLICT (workspace_id, project_id) DO UPDATE SET agent_settings = EXCLUDED.agent_settings""";

    //language=SQL
    private static final String FIND_SETTINGS = """
            SELECT * FROM profiler_settings
            WHERE workspace_id = :workspace_id AND project_id = :project_id""";

    //language=SQL
    private static final String FIND_WORKSPACE_SETTINGS = """
            SELECT * FROM profiler_settings
            WHERE workspace_id = :workspace_id OR (workspace_id IS NULL AND project_id IS NULL)""";

    //language=SQL
    private static final String FIND_ALL_SETTINGS = "SELECT * FROM profiler_settings";

    //language=SQL
    private static final String DELETE_SETTINGS = """
            DELETE FROM profiler_settings
            WHERE workspace_id = :workspace_id AND project_id = :project_id""";

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
    public Optional<ProfilerInfo> findSettings(String workspaceId, String projectId) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("project_id", projectId);

        return databaseClient.querySingle(
                StatementLabel.FIND_PROFILER_SETTINGS, FIND_SETTINGS, paramSource, settingsMapper());
    }

    @Override
    public List<ProfilerInfo> findWorkspaceSettings(String workspaceId) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_PROFILER_SETTINGS, FIND_WORKSPACE_SETTINGS, paramSource, settingsMapper());
    }

    @Override
    public List<ProfilerInfo> findAllSettings() {
        return databaseClient.query(StatementLabel.FIND_PROFILER_SETTINGS, FIND_ALL_SETTINGS, settingsMapper());
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
                rs.getString("agent_settings")
        );
    }
}

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
import pbouda.jeffrey.shared.model.ProfilerInfo;
import pbouda.jeffrey.provider.api.repository.ProfilerRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.util.List;
import java.util.Optional;

public class JdbcProfilerRepository implements ProfilerRepository {

    private static final String EMPTY = "$$EMPTY$$";

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
            WHERE workspace_id = :workspace_id OR (workspace_id = '<EMPTY>' AND project_id = '<EMPTY>')"""
            // to handle nulls
            .replace("<EMPTY>", EMPTY);

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

    /**
     * Writes empty strings instead of nulls to the database because of UNIQUE constraints does not work with nulls.
     *
     * @param profiler the profiler info with possible nulls
     * @return the profiler info with empty strings instead of nulls
     */
    private static ProfilerInfo writeEmpty(ProfilerInfo profiler) {
        return new ProfilerInfo(
                profiler.workspaceId() == null ? EMPTY : profiler.workspaceId(),
                profiler.projectId() == null ? EMPTY : profiler.projectId(),
                profiler.agentSettings());
    }

    /**
     * Reads empty strings from the database and converts them to nulls.
     *
     * @param profiler the profiler info with possible empty strings
     * @return the profiler info with nulls instead of empty strings
     */
    private static ProfilerInfo readEmpty(ProfilerInfo profiler) {
        return new ProfilerInfo(
                EMPTY.equals(profiler.workspaceId()) ? null : profiler.workspaceId(),
                EMPTY.equals(profiler.projectId()) ? null : profiler.projectId(),
                profiler.agentSettings());
    }

    @Override
    public void upsertSettings(ProfilerInfo profiler) {
        ProfilerInfo newProfiler = writeEmpty(profiler);

        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", newProfiler.workspaceId())
                .addValue("project_id", newProfiler.projectId())
                .addValue("agent_settings", newProfiler.agentSettings());

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
        ProfilerInfo profilerInfo = new ProfilerInfo(workspaceId, projectId, null);
        ProfilerInfo newProfiler = writeEmpty(profilerInfo);

        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", newProfiler.workspaceId())
                .addValue("project_id", newProfiler.projectId());

        databaseClient.delete(StatementLabel.DELETE_PROFILER_SETTINGS, DELETE_SETTINGS, paramSource);
    }

    private static RowMapper<ProfilerInfo> settingsMapper() {
        return (rs, _) -> {
            ProfilerInfo profilerInfo = new ProfilerInfo(
                    rs.getString("workspace_id"),
                    rs.getString("project_id"),
                    rs.getString("agent_settings"));

            return readEmpty(profilerInfo);
        };
    }
}

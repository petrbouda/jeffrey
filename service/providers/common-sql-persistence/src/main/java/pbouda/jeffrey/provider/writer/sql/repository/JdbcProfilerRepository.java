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

import javax.sql.DataSource;
import java.util.Optional;

public class JdbcProfilerRepository implements ProfilerRepository {

    //language=SQL
    private static final String INSERT_SETTINGS = """
            INSERT INTO profiler_settings (profiler_id, workspace_id, project_id, agent_settings)
            VALUES (:profiler_id, :workspace_id, :project_id, :agent_settings)""";

    //language=SQL
    private static final String UPDATE_SETTINGS = """
            UPDATE profiler_settings
            SET agent_settings = :agent_settings
            WHERE profiler_id = :profiler_id""";

    //language=SQL
    private static final String FIND_SETTINGS = """
            SELECT * FROM profiler_settings
            WHERE workspace_id = :workspace_id AND project_id = :project_id""";

    private final DatabaseClient databaseClient;

    public JdbcProfilerRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROFILER);
    }

    @Override
    public void insertSettings(ProfilerInfo profiler) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profiler_id", profiler.id())
                .addValue("workspace_id", profiler.workspaceId())
                .addValue("project_id", profiler.projectId())
                .addValue("agent_settings", profiler.agentSettings());

        databaseClient.insert(StatementLabel.INSERT_PROFILER_SETTINGS, INSERT_SETTINGS, paramSource);
    }

    @Override
    public void updateSettings(ProfilerInfo profiler) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("profiler_id", profiler.agentSettings());

        databaseClient.insert(StatementLabel.UPDATE_PROFILER_SETTINGS, UPDATE_SETTINGS, paramSource);
    }

    @Override
    public Optional<ProfilerInfo> findSettings(ProfilerInfo profiler) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", profiler.workspaceId())
                .addValue("project_id", profiler.projectId());

        return databaseClient.querySingle(
                StatementLabel.FIND_PROFILER_SETTINGS, FIND_SETTINGS, paramSource, settingsMapper());
    }

    private static RowMapper<ProfilerInfo> settingsMapper() {
        return (rs, _) -> new ProfilerInfo(
                rs.getString("profiler_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("agent_settings")
        );
    }
}

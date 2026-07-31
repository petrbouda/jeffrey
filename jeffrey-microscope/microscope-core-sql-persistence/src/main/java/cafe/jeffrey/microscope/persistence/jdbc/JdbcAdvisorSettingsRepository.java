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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorSettingsRow;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.util.Optional;

public class JdbcAdvisorSettingsRepository implements AdvisorSettingsRepository {

    private static final String PARAM_WORKSPACE_ID = "workspace_id";
    private static final String PARAM_PROJECT_ID = "project_id";

    // DuckDB has no NULL-safe equality operator in ON CONFLICT targets, and a Quick Analysis profile
    // has no workspace, so the lookup compares with IS NOT DISTINCT FROM rather than '='.
    //language=SQL
    private static final String FIND = """
            SELECT workspace_id, project_id, source_path, prune_threshold_pct, regression_threshold_pp,
                   compile_command, test_command, modified_at
            FROM project_advisor_settings
            WHERE workspace_id IS NOT DISTINCT FROM :workspace_id
              AND project_id IS NOT DISTINCT FROM :project_id""";

    //language=SQL
    private static final String DELETE = """
            DELETE FROM project_advisor_settings
            WHERE workspace_id IS NOT DISTINCT FROM :workspace_id
              AND project_id IS NOT DISTINCT FROM :project_id""";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO project_advisor_settings (workspace_id, project_id, source_path, prune_threshold_pct,
                                                  regression_threshold_pp, compile_command, test_command,
                                                  modified_at)
            VALUES (:workspace_id, :project_id, :source_path, :prune_threshold_pct,
                    :regression_threshold_pp, :compile_command, :test_command, :modified_at)""";

    private final DatabaseClient databaseClient;

    public JdbcAdvisorSettingsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.ADVISOR);
    }

    @Override
    public Optional<AdvisorSettingsRow> find(String workspaceId, String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_WORKSPACE_ID, workspaceId)
                .addValue(PARAM_PROJECT_ID, projectId);

        return databaseClient.querySingle(StatementLabel.FIND_ADVISOR_SETTINGS, FIND, params, mapper());
    }

    /**
     * Delete-then-insert rather than {@code ON CONFLICT}: the unique key includes a nullable column, and
     * DuckDB will not match a conflict target on NULLs, so an upsert would silently insert a duplicate
     * row for every Quick Analysis save.
     */
    @Override
    public void upsert(AdvisorSettingsRow settings) {
        MapSqlParameterSource key = new MapSqlParameterSource()
                .addValue(PARAM_WORKSPACE_ID, settings.workspaceId())
                .addValue(PARAM_PROJECT_ID, settings.projectId());
        databaseClient.delete(StatementLabel.UPSERT_ADVISOR_SETTINGS, DELETE, key);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_WORKSPACE_ID, settings.workspaceId())
                .addValue(PARAM_PROJECT_ID, settings.projectId())
                .addValue("source_path", settings.sourcePath())
                .addValue("prune_threshold_pct", settings.pruneThresholdPct())
                .addValue("regression_threshold_pp", settings.regressionThresholdPp())
                .addValue("compile_command", settings.compileCommand())
                .addValue("test_command", settings.testCommand())
                .addValue("modified_at", Timestamp.from(settings.modifiedAt()));

        databaseClient.insert(StatementLabel.UPSERT_ADVISOR_SETTINGS, INSERT, params);
    }

    private static RowMapper<AdvisorSettingsRow> mapper() {
        return (rs, _) -> new AdvisorSettingsRow(
                rs.getString(PARAM_WORKSPACE_ID),
                rs.getString(PARAM_PROJECT_ID),
                rs.getString("source_path"),
                rs.getDouble("prune_threshold_pct"),
                rs.getDouble("regression_threshold_pp"),
                rs.getString("compile_command"),
                rs.getString("test_command"),
                rs.getTimestamp("modified_at").toInstant());
    }
}

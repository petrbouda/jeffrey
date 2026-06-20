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

package cafe.jeffrey.performance.analyst.persistence;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.Optional;

public class JdbcProjectAiConfigurationRepository implements ProjectAiConfigurationRepository {

    //language=SQL
    private static final String SELECT_BY_PROJECT =
            "SELECT * FROM project_ai_configuration WHERE project_id = :project_id";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO project_ai_configuration (project_id, provider, model, prune_threshold_pct, modified_at)
            VALUES (:project_id, :provider, :model, :prune_threshold_pct, :modified_at)
            ON CONFLICT (project_id) DO UPDATE SET
                provider = :provider, model = :model,
                prune_threshold_pct = :prune_threshold_pct, modified_at = :modified_at""";

    private final DatabaseClient databaseClient;

    public JdbcProjectAiConfigurationRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECTS);
    }

    @Override
    public Optional<ProjectAiConfiguration> find(String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("project_id", projectId);
        return databaseClient.querySingle(StatementLabel.FIND_PROJECT_AI_CONFIG, SELECT_BY_PROJECT, params, configMapper());
    }

    @Override
    public void upsert(ProjectAiConfiguration configuration) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", configuration.projectId())
                .addValue("provider", configuration.provider())
                .addValue("model", configuration.model())
                .addValue("prune_threshold_pct", configuration.pruneThresholdPct())
                .addValue("modified_at", configuration.modifiedAt().toEpochMilli());
        databaseClient.update(StatementLabel.UPSERT_PROJECT_AI_CONFIG, UPSERT, params);
    }

    private static RowMapper<ProjectAiConfiguration> configMapper() {
        return (rs, _) -> new ProjectAiConfiguration(
                rs.getString("project_id"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getDouble("prune_threshold_pct"),
                Instant.ofEpochMilli(rs.getLong("modified_at")));
    }
}

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

package cafe.jeffrey.provider.profile.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageResult;
import cafe.jeffrey.provider.profile.api.PipelineRunRepository;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import tools.jackson.core.type.TypeReference;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class JdbcPipelineRunRepository implements PipelineRunRepository {

    private static final String PARAM_PIPELINE_ID = "pipeline_id";
    private static final String PARAM_SCOPE_ID = "scope_id";

    private static final String COLUMNS = """
            pipeline_id, scope_id, state, total_elapsed_ms, total_steps, completed_steps,
            error_code, error_message, stages, started_at, completed_at""";

    //language=SQL
    private static final String FIND = """
            SELECT %s
            FROM pipeline_runs
            WHERE pipeline_id = :pipeline_id AND scope_id = :scope_id""".formatted(COLUMNS);

    //language=SQL
    private static final String FIND_ALL = """
            SELECT %s
            FROM pipeline_runs
            WHERE pipeline_id = :pipeline_id
            ORDER BY scope_id""".formatted(COLUMNS);

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO pipeline_runs (pipeline_id, scope_id, state, total_elapsed_ms, total_steps,
                                       completed_steps, error_code, error_message, stages,
                                       started_at, completed_at)
            VALUES (:pipeline_id, :scope_id, :state, :total_elapsed_ms, :total_steps,
                    :completed_steps, :error_code, :error_message, :stages,
                    :started_at, :completed_at)
            ON CONFLICT (pipeline_id, scope_id) DO UPDATE SET
                state = EXCLUDED.state,
                total_elapsed_ms = EXCLUDED.total_elapsed_ms,
                total_steps = EXCLUDED.total_steps,
                completed_steps = EXCLUDED.completed_steps,
                error_code = EXCLUDED.error_code,
                error_message = EXCLUDED.error_message,
                stages = EXCLUDED.stages,
                started_at = EXCLUDED.started_at,
                completed_at = EXCLUDED.completed_at""";

    //language=SQL
    private static final String DELETE_ALL =
            "DELETE FROM pipeline_runs WHERE pipeline_id = :pipeline_id";

    private static final TypeReference<List<StageResult>> STAGES_TYPE = new TypeReference<>() {
    };

    private final DatabaseClient databaseClient;

    public JdbcPipelineRunRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PIPELINE_RUNS);
    }

    @Override
    public Optional<PipelineRunResult> find(String pipelineId, String scopeId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PIPELINE_ID, pipelineId)
                .addValue(PARAM_SCOPE_ID, scopeId == null ? "" : scopeId);

        return databaseClient.querySingle(StatementLabel.FIND_PIPELINE_RUN, FIND, params, mapper());
    }

    @Override
    public List<PipelineRunResult> findAll(String pipelineId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PIPELINE_ID, pipelineId);

        return databaseClient.query(StatementLabel.FIND_PIPELINE_RUNS, FIND_ALL, params, mapper());
    }

    @Override
    public void upsert(PipelineRunResult run) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PIPELINE_ID, run.pipelineId())
                .addValue(PARAM_SCOPE_ID, run.scopeId())
                .addValue("state", run.state().code())
                .addValue("total_elapsed_ms", run.totalElapsedMs())
                .addValue("total_steps", run.totalSteps())
                .addValue("completed_steps", run.completedSteps())
                .addValue("error_code", run.errorCode())
                .addValue("error_message", run.errorMessage())
                .addValue("stages", Json.toString(run.stages()))
                .addValue("started_at", Timestamp.from(run.startedAt()))
                .addValue("completed_at", Timestamp.from(run.completedAt()));

        databaseClient.insert(StatementLabel.UPSERT_PIPELINE_RUN, UPSERT, params);
    }

    @Override
    public void deleteAll(String pipelineId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_PIPELINE_ID, pipelineId);

        databaseClient.delete(StatementLabel.DELETE_PIPELINE_RUNS, DELETE_ALL, params);
    }

    private static RowMapper<PipelineRunResult> mapper() {
        return (rs, _) -> new PipelineRunResult(
                rs.getString(PARAM_PIPELINE_ID),
                rs.getString(PARAM_SCOPE_ID),
                PipelineState.fromCode(rs.getString("state")),
                rs.getLong("total_elapsed_ms"),
                rs.getInt("total_steps"),
                rs.getInt("completed_steps"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at").toInstant(),
                Json.read(rs.getString("stages"), STAGES_TYPE));
    }
}

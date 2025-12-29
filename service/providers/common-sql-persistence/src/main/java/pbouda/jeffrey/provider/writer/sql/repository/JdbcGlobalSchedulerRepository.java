/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.shared.Json;
import pbouda.jeffrey.shared.model.job.JobInfo;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.util.List;

public class JdbcGlobalSchedulerRepository implements SchedulerRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO schedulers (id, project_id, job_type, params, enabled)
            VALUES (:id, :project_id, :job_type, :params, :enabled)""";

    //language=SQL
    private static final String UPDATE_ENABLED =
            "UPDATE schedulers SET enabled = :enabled WHERE id = :id";

    //language=SQL
    private static final String GET_ALL =
            "SELECT * FROM schedulers WHERE project_id IS NULL";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM schedulers WHERE project_id IS NULL AND id = :id";

    private final DatabaseClient databaseClient;

    public JdbcGlobalSchedulerRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.GLOBAL_SCHEDULERS);
    }

    @Override
    public void insert(JobInfo jobInfo) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", jobInfo.id())
                .addValue("project_id", null) // project_id is null for global schedulers
                .addValue("job_type", jobInfo.jobType().name())
                .addValue("params", Json.toPrettyString(jobInfo.params()))
                .addValue("enabled", jobInfo.enabled());

        databaseClient.insert(StatementLabel.INSERT_GLOBAL_JOB ,INSERT, paramSource);
    }

    @Override
    public List<JobInfo> all() {
        return databaseClient.query(StatementLabel.FIND_ALL_GLOBAL_JOBS, GET_ALL, Mappers.jobInfoMapper());
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        SqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("enabled", enabled);

        databaseClient.update(StatementLabel.ENABLE_GLOBAL_JOB, UPDATE_ENABLED, paramSource);
    }

    @Override
    public void delete(String id) {
        databaseClient.update(StatementLabel.DELETE_GLOBAL_JOB, DELETE, new MapSqlParameterSource("id", id));
    }
}

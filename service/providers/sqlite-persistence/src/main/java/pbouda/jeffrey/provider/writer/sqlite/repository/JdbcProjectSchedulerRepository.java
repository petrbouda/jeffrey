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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.util.List;

public class JdbcProjectSchedulerRepository implements SchedulerRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO schedulers (id, project_id, job_type, params, enabled)
            VALUES (:id, :project_id, :job_type, :params, :enabled)""";

    //language=SQL
    private static final String UPDATE_ENABLED =
            "UPDATE schedulers SET enabled = :enabled WHERE project_id = :project_id AND id = :id";

    //language=SQL
    private static final String GET_ALL =
            "SELECT * FROM schedulers WHERE project_id = :project_id";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM schedulers WHERE project_id = :project_id AND id = :id";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectSchedulerRepository(String projectId, DataSource dataSource) {
        this.projectId = projectId;
        this.databaseClient = new DatabaseClient(dataSource, "project-schedulers");
    }

    @Override
    public void insert(JobInfo jobInfo) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", jobInfo.id())
                .addValue("project_id", projectId)
                .addValue("job_type", jobInfo.jobType().name())
                .addValue("params", Json.toPrettyString(jobInfo.params()))
                .addValue("enabled", jobInfo.enabled());

        databaseClient.insert(INSERT, paramSource);
    }

    @Override
    public List<JobInfo> all() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(GET_ALL, paramSource, Mappers.jobInfoMapper());
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.update(UPDATE_ENABLED, paramSource);
    }

    @Override
    public void delete(String id) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("project_id", projectId);

        databaseClient.delete(DELETE, paramSource);
    }
}

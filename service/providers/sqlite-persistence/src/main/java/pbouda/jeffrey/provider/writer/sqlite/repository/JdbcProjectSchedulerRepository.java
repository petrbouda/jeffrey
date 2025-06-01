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

import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.provider.api.model.job.JobInfo;
import pbouda.jeffrey.provider.api.repository.SchedulerRepository;

import java.util.List;

public class JdbcProjectSchedulerRepository implements SchedulerRepository {

    //language=SQL
    private static final String INSERT =
            "INSERT INTO schedulers (id, project_id, job_type, params, enabled) VALUES (?, ?, ?, ?, ?)";

    //language=SQL
    private static final String UPDATE_ENABLED =
            "UPDATE schedulers SET enabled = ? WHERE project_id = ? AND id = ?";

    //language=SQL
    private static final String GET_ALL =
            "SELECT * FROM schedulers WHERE project_id = ?";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM schedulers WHERE project_id = ? AND id = ?";

    private final String projectId;
    private final JdbcClient jdbcClient;

    public JdbcProjectSchedulerRepository(String projectId, JdbcClient jdbcClient) {
        this.projectId = projectId;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(JobInfo jobInfo) {
        jdbcClient.sql(INSERT)
                .param(jobInfo.id())
                .param(projectId)
                .param(jobInfo.jobType().name())
                .param(Json.toPrettyString(jobInfo.params()))
                .param(jobInfo.enabled())
                .update();
    }

    @Override
    public List<JobInfo> all() {
        return jdbcClient.sql(GET_ALL)
                .param(projectId)
                .query(Mappers.jobInfoMapper())
                .list();
    }

    @Override
    public void updateEnabled(String id, boolean enabled) {
        jdbcClient.sql(UPDATE_ENABLED)
                .params(enabled, projectId, id)
                .update();
    }

    @Override
    public void delete(String id) {
        jdbcClient.sql(DELETE)
                .params(projectId, id)
                .update();
    }
}

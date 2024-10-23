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

package pbouda.jeffrey.repository.project;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.model.JobInfo;
import pbouda.jeffrey.model.JobType;

import java.util.List;

public class ProjectSchedulerRepository {

    private static final String INSERT = """
            INSERT INTO scheduler (id, job_type, params) VALUES (?, ?, ?)
            """;

    private static final String GET_ALL = """
            SELECT * FROM scheduler
            """;

    private static final String DELETE = """
            DELETE FROM scheduler WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProjectSchedulerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(JobInfo jobInfo) {
        jdbcTemplate.update(INSERT, jobInfo.id(), jobInfo.jobType().name(), Json.toPrettyString(jobInfo.params()));
    }

    public List<JobInfo> all() {
        return jdbcTemplate.query(GET_ALL, jobInfoMapper());
    }

    public void delete(String id) {
        jdbcTemplate.update(DELETE, id);
    }

    private static RowMapper<JobInfo> jobInfoMapper() {
        return (rs, __) -> {
            String id = rs.getString("id");
            String jobType = rs.getString("job_type");
            String params = rs.getString("params");
            return new JobInfo(id, JobType.valueOf(jobType), Json.toMap(params));
        };
    }
}

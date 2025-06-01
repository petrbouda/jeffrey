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

package pbouda.jeffrey.provider.writer.sqlite.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.util.List;

public class JdbcProjectRepositoryRepository implements ProjectRepositoryRepository {

    //language=sql
    private static final String INSERT_REPOSITORY = """
            INSERT INTO repositories (project_id, id, path, type, finished_session_detection_file)
            VALUES (:project_id, :id, :path, :type, :finished_session_detection_file)""";

    private final String projectId;
    private final JdbcClient jdbcClient;

    public JdbcProjectRepositoryRepository(String projectId, JdbcClient jdbcClient) {
        this.projectId = projectId;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(DBRepositoryInfo repositoryInfo) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", IDGenerator.generate())
                .addValue("path", repositoryInfo.path().toString())
                .addValue("type", repositoryInfo.type())
                .addValue("finished_session_detection_file", repositoryInfo.finishedSessionDetectionFile());

        jdbcClient.sql(INSERT_REPOSITORY)
                .paramSource(params)
                .update();
    }

    @Override
    public List<DBRepositoryInfo> getAll() {
        return jdbcClient.sql("SELECT * FROM repositories WHERE project_id = :project_id")
                .param("project_id", projectId)
                .query(Mappers.repositoryInfoMapper())
                .list();
    }

    @Override
    public void delete(String id) {
        jdbcClient.sql("DELETE FROM repositories WHERE project_id = :project_id AND id = :id")
                .param("project_id", projectId)
                .param("id", id)
                .update();
    }

    @Override
    public void deleteAll() {
        jdbcClient.sql("DELETE FROM repositories WHERE project_id = :project_id")
                .param("project_id", projectId)
                .update();
    }
}

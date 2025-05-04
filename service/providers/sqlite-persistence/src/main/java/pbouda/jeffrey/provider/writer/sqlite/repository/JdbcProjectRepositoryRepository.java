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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.util.List;
import java.util.Map;

public class JdbcProjectRepositoryRepository implements ProjectRepositoryRepository {

    //language=sql
    private static final String INSERT_REPOSITORY = """
            INSERT INTO repositories (project_id, id, path, type, finished_session_detection_file)
            VALUES (:project_id, :id, :path, :type, :finished_session_detection_file)
            """;

    private final String projectId;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRepositoryRepository(String projectId, JdbcTemplate jdbcTemplate) {
        this.projectId = projectId;
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public void insert(DBRepositoryInfo repositoryInfo) {
        Map<String, Object> params = Map.of(
                "project_id", projectId,
                "id", IDGenerator.generate(),
                "path", repositoryInfo.path().toString(),
                "type", repositoryInfo.type(),
                "finished_session_detection_file", repositoryInfo.finishedSessionDetectionFile());

        jdbcTemplate.update(INSERT_REPOSITORY, params);
    }

    @Override
    public List<DBRepositoryInfo> getAll() {
        return jdbcTemplate.query("SELECT * FROM repositories WHERE project_id = :project_id",
                Map.of("project_id", projectId), Mappers.repositoryInfoMapper());
    }

    @Override
    public void delete(String id) {
        jdbcTemplate.update("DELETE FROM repositories WHERE project_id = :project_id AND id = :id",
                Map.of("projectId", projectId, "id", id));
    }

    @Override
    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM repositories WHERE project_id = :project_id",
                Map.of("project_id", projectId));
    }
}

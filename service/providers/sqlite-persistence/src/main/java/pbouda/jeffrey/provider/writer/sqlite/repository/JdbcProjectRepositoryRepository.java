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
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.util.List;

public class JdbcProjectRepositoryRepository implements ProjectRepositoryRepository {

    //language=sql
    private static final String INSERT_REPOSITORY = """
            INSERT INTO repositories (project_id, id, path, type, finished_session_detection_file)
            VALUES (:project_id, :id, :path, :type, :finished_session_detection_file)""";

    //language=sql
    private static final String ALL_IN_PROJECT = "SELECT * FROM repositories WHERE project_id = :project_id";

    //language=sql
    private static final String DELETE_BY_ID = "DELETE FROM repositories WHERE project_id = :project_id AND id = :id";

    //language=sql
    private static final String DELETE_ALL_IN_PROJECT = "DELETE FROM repositories WHERE project_id = :project_id";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcProjectRepositoryRepository(String projectId, DataSource dataSource) {
        this.projectId = projectId;
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.PROJECT_REPOSITORIES);
    }

    @Override
    public void insert(DBRepositoryInfo repositoryInfo) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("id", IDGenerator.generate())
                .addValue("path", repositoryInfo.path().toString())
                .addValue("type", repositoryInfo.type())
                .addValue("finished_session_detection_file", repositoryInfo.finishedSessionDetectionFile());

        databaseClient.insert(StatementLabel.INSERT_REPOSITORY, INSERT_REPOSITORY, params);
    }

    @Override
    public List<DBRepositoryInfo> getAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_ALL_REPOSITORIES, ALL_IN_PROJECT, paramSource, Mappers.repositoryInfoMapper());
    }

    @Override
    public void delete(String id) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_REPOSITORY, DELETE_BY_ID, paramSource);
    }

    @Override
    public void deleteAll() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_ALL_REPOSITORIES, DELETE_ALL_IN_PROJECT, paramSource);
    }
}

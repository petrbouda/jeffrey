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
import java.util.List;
import java.util.Optional;

public class JdbcProjectRepository implements ProjectRepository {

    //language=SQL
    private static final String SELECT_ALL = "SELECT * FROM projects ORDER BY created_at DESC";

    //language=SQL
    private static final String SELECT_BY_ID = "SELECT * FROM projects WHERE id = :id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO projects (id, name, description, created_at, modified_at)
            VALUES (:id, :name, :description, :created_at, :modified_at)""";

    //language=SQL
    private static final String DELETE = "DELETE FROM projects WHERE id = :id";

    private final DatabaseClient databaseClient;

    public JdbcProjectRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECTS);
    }

    @Override
    public List<Project> findAll() {
        return databaseClient.query(StatementLabel.FIND_ALL_PROJECTS, SELECT_ALL, new MapSqlParameterSource(), projectMapper());
    }

    @Override
    public Optional<Project> findById(String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", projectId);
        return databaseClient.querySingle(StatementLabel.FIND_PROJECT, SELECT_BY_ID, params, projectMapper());
    }

    @Override
    public void insert(Project project) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", project.id())
                .addValue("name", project.name())
                .addValue("description", project.description())
                .addValue("created_at", project.createdAt().toEpochMilli())
                .addValue("modified_at", project.modifiedAt().toEpochMilli());
        databaseClient.insert(StatementLabel.INSERT_PROJECT, INSERT, params);
    }

    @Override
    public void delete(String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", projectId);
        databaseClient.delete(StatementLabel.DELETE_PROJECT, DELETE, params);
    }

    private static RowMapper<Project> projectMapper() {
        return (rs, _) -> new Project(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                Instant.ofEpochMilli(rs.getLong("modified_at")));
    }
}

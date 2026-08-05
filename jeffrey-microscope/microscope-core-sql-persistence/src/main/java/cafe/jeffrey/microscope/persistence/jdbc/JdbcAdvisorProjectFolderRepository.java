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

package cafe.jeffrey.microscope.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolder;
import cafe.jeffrey.microscope.persistence.api.AdvisorProjectFolderRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class JdbcAdvisorProjectFolderRepository implements AdvisorProjectFolderRepository {

    private static final String PARAM_FOLDER_ID = "folder_id";
    private static final String PARAM_NAME = "name";
    private static final String PARAM_PATH = "path";
    private static final String PARAM_CREATED_AT = "created_at";

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT folder_id, name, path, created_at FROM advisor_project_folders ORDER BY name";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT folder_id, name, path, created_at FROM advisor_project_folders WHERE folder_id = :folder_id";

    //language=SQL
    private static final String SELECT_BY_NAME =
            "SELECT folder_id, name, path, created_at FROM advisor_project_folders WHERE name = :name";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO advisor_project_folders (folder_id, name, path, created_at)
            VALUES (:folder_id, :name, :path, :created_at)""";

    //language=SQL
    private static final String UPDATE = """
            UPDATE advisor_project_folders SET
                name = :name,
                path = :path
            WHERE folder_id = :folder_id""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM advisor_project_folders WHERE folder_id = :folder_id";

    private final DatabaseClient databaseClient;

    public JdbcAdvisorProjectFolderRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.ADVISOR);
    }

    @Override
    public List<AdvisorProjectFolder> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_ADVISOR_PROJECT_FOLDERS,
                SELECT_ALL,
                new MapSqlParameterSource(),
                mapper());
    }

    @Override
    public Optional<AdvisorProjectFolder> find(String folderId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_FOLDER_ID, folderId);

        return databaseClient.querySingle(
                StatementLabel.FIND_ADVISOR_PROJECT_FOLDER, SELECT_BY_ID, params, mapper());
    }

    @Override
    public Optional<AdvisorProjectFolder> findByName(String name) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_NAME, name);

        return databaseClient.querySingle(
                StatementLabel.FIND_ADVISOR_PROJECT_FOLDER_BY_NAME, SELECT_BY_NAME, params, mapper());
    }

    @Override
    public void insert(AdvisorProjectFolder folder) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_FOLDER_ID, folder.folderId())
                .addValue(PARAM_NAME, folder.name())
                .addValue(PARAM_PATH, folder.path())
                .addValue(PARAM_CREATED_AT, Timestamp.from(folder.createdAt()));

        databaseClient.insert(StatementLabel.INSERT_ADVISOR_PROJECT_FOLDER, INSERT, params);
    }

    @Override
    public void update(AdvisorProjectFolder folder) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_FOLDER_ID, folder.folderId())
                .addValue(PARAM_NAME, folder.name())
                .addValue(PARAM_PATH, folder.path());

        databaseClient.update(StatementLabel.UPDATE_ADVISOR_PROJECT_FOLDER, UPDATE, params);
    }

    @Override
    public void delete(String folderId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue(PARAM_FOLDER_ID, folderId);

        databaseClient.update(StatementLabel.DELETE_ADVISOR_PROJECT_FOLDER, DELETE, params);
    }

    private static RowMapper<AdvisorProjectFolder> mapper() {
        return (rs, _) -> new AdvisorProjectFolder(
                rs.getString(PARAM_FOLDER_ID),
                rs.getString(PARAM_NAME),
                rs.getString(PARAM_PATH),
                rs.getTimestamp(PARAM_CREATED_AT).toInstant());
    }
}

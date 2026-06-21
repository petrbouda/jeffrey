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
import cafe.jeffrey.shared.common.encryption.SecretEncryptor;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.util.Optional;

/**
 * SQLite-backed {@link VersionSystemStore}. The {@code credentials} JSON is encrypted via
 * {@link SecretEncryptor} before it is written and decrypted when a row is read, so the plaintext
 * token never touches disk.
 */
public class JdbcVersionSystemStore implements VersionSystemStore {

    //language=SQL
    private static final String SELECT_BY_PROJECT =
            "SELECT * FROM version_systems WHERE project_id = :project_id";

    //language=SQL
    private static final String UPSERT = """
            INSERT INTO version_systems (id, project_id, platform, url, credentials, created_at, modified_at)
            VALUES (:id, :project_id, :platform, :url, :credentials, :created_at, :modified_at)
            ON CONFLICT (project_id) DO UPDATE SET
                platform = :platform, url = :url, credentials = :credentials, modified_at = :modified_at""";

    //language=SQL
    private static final String DELETE = "DELETE FROM version_systems WHERE project_id = :project_id";

    private final DatabaseClient databaseClient;
    private final SecretEncryptor secretEncryptor;

    public JdbcVersionSystemStore(DatabaseClientProvider databaseClientProvider, SecretEncryptor secretEncryptor) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.PROJECTS);
        this.secretEncryptor = secretEncryptor;
    }

    @Override
    public Optional<VersionSystem> findByProject(String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("project_id", projectId);
        return databaseClient.querySingle(StatementLabel.FIND_VERSION_SYSTEM, SELECT_BY_PROJECT, params, versionSystemMapper());
    }

    @Override
    public void upsert(VersionSystem versionSystem) {
        String encryptedCredentials = versionSystem.hasCredentials()
                ? secretEncryptor.encrypt(versionSystem.credentials())
                : null;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", versionSystem.id())
                .addValue("project_id", versionSystem.projectId())
                .addValue("platform", versionSystem.platform().code())
                .addValue("url", versionSystem.url())
                .addValue("credentials", encryptedCredentials)
                .addValue("created_at", versionSystem.createdAt().toEpochMilli())
                .addValue("modified_at", versionSystem.modifiedAt().toEpochMilli());
        databaseClient.update(StatementLabel.UPSERT_VERSION_SYSTEM, UPSERT, params);
    }

    @Override
    public void delete(String projectId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("project_id", projectId);
        databaseClient.delete(StatementLabel.DELETE_VERSION_SYSTEM, DELETE, params);
    }

    private RowMapper<VersionSystem> versionSystemMapper() {
        return (rs, _) -> {
            String storedCredentials = rs.getString("credentials");
            String credentials = (storedCredentials == null || storedCredentials.isBlank())
                    ? null
                    : secretEncryptor.decrypt(storedCredentials);
            return new VersionSystem(
                    rs.getString("id"),
                    rs.getString("project_id"),
                    Platform.fromCode(rs.getString("platform")),
                    rs.getString("url"),
                    credentials,
                    Instant.ofEpochMilli(rs.getLong("created_at")),
                    Instant.ofEpochMilli(rs.getLong("modified_at")));
        };
    }
}

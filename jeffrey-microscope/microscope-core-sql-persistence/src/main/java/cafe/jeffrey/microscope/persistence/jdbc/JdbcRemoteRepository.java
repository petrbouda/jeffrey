/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.persistence.jdbc;

import cafe.jeffrey.microscope.model.remote.AzureFilesAddress;
import cafe.jeffrey.microscope.model.remote.AzureFilesAuth;
import cafe.jeffrey.microscope.model.remote.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.remote.HubAddress;
import cafe.jeffrey.microscope.model.remote.RemoteAddress;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;
import cafe.jeffrey.microscope.persistence.api.RemoteRepository;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Optional;

public class JdbcRemoteRepository implements RemoteRepository {

    private static final String KIND_GRPC = "GRPC";
    private static final String KIND_AZURE_FILES = "AZURE_FILES";

    //language=SQL
    private static final String SELECT = "SELECT * FROM remote";

    /** One row, always id 1: connecting replaces whatever was connected. */
    //language=SQL
    private static final String SAVE = """
            INSERT OR REPLACE INTO remote (id, name, kind, hostname, port, plaintext,
                              azure_endpoint, azure_share, azure_root_path, azure_protocol, azure_auth,
                              azure_read_only, created_at, source)
            VALUES (1, :name, :kind, :hostname, :port, :plaintext,
                    :azure_endpoint, :azure_share, :azure_root_path, :azure_protocol, :azure_auth,
                    :azure_read_only, :created_at, :source)""";

    //language=SQL
    private static final String DELETE = "DELETE FROM remote";

    private final DatabaseClient databaseClient;

    public JdbcRemoteRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.REMOTE);
    }

    @Override
    public Optional<RemoteInfo> find() {
        return databaseClient.querySingle(StatementLabel.FIND_REMOTE, SELECT, new MapSqlParameterSource(), mapper());
    }

    @Override
    public void save(RemoteInfo remote) {
        MapSqlParameterSource params = params(remote).addValue("created_at", Timestamp.from(remote.createdAt()));
        databaseClient.update(StatementLabel.SAVE_REMOTE, SAVE, params);
    }

    @Override
    public void delete() {
        databaseClient.update(StatementLabel.DELETE_REMOTE, DELETE, new MapSqlParameterSource());
    }

    /** Every column but {@code created_at}; the other kind's columns are NULL. */
    private static MapSqlParameterSource params(RemoteInfo remote) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("name", remote.name())
                .addValue("source", remote.source().name())
                .addValue("hostname", null)
                .addValue("port", null)
                .addValue("plaintext", false)
                .addValue("azure_endpoint", null)
                .addValue("azure_share", null)
                .addValue("azure_root_path", null)
                .addValue("azure_protocol", null)
                .addValue("azure_auth", null)
                .addValue("azure_read_only", false);

        return switch (remote.address()) {
            case HubAddress grpc -> params
                    .addValue("kind", KIND_GRPC)
                    .addValue("hostname", grpc.hostname())
                    .addValue("port", grpc.port())
                    .addValue("plaintext", grpc.plaintext());
            case AzureFilesAddress azure -> params
                    .addValue("kind", KIND_AZURE_FILES)
                    .addValue("azure_endpoint", azure.endpoint())
                    .addValue("azure_share", azure.share())
                    .addValue("azure_root_path", azure.rootPath())
                    .addValue("azure_protocol", azure.protocol().name())
                    .addValue("azure_auth", azure.auth().name())
                    .addValue("azure_read_only", azure.readOnly());
        };
    }

    private static RowMapper<RemoteInfo> mapper() {
        return (rs, _) -> new RemoteInfo(
                rs.getString("name"),
                address(rs),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC).toInstant(),
                RemoteSource.fromDb(rs.getString("source")));
    }

    /** A row written before the column existed carries the default, {@code GRPC}. */
    private static RemoteAddress address(ResultSet rs) throws SQLException {
        if (KIND_AZURE_FILES.equals(rs.getString("kind"))) {
            return new AzureFilesAddress(
                    rs.getString("azure_endpoint"),
                    rs.getString("azure_share"),
                    rs.getString("azure_root_path"),
                    AzureFilesProtocol.parse(rs.getString("azure_protocol")).orElseThrow(
                            () -> new SQLException("Stored Azure Files remote has no readable protocol")),
                    AzureFilesAuth.parse(rs.getString("azure_auth")).orElseThrow(
                            () -> new SQLException("Stored Azure Files remote has no readable sign-in method")),
                    rs.getBoolean("azure_read_only"));
        }
        return new HubAddress(rs.getString("hostname"), rs.getInt("port"), rs.getBoolean("plaintext"));
    }
}

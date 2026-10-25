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

import cafe.jeffrey.microscope.model.hub.AzureFilesAddress;
import cafe.jeffrey.microscope.model.hub.AzureFilesAuth;
import cafe.jeffrey.microscope.model.hub.AzureFilesProtocol;
import cafe.jeffrey.microscope.model.hub.GrpcHubAddress;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
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
import java.util.List;
import java.util.Optional;

public class JdbcHubsRepository implements HubsRepository {

    private static final String KIND_GRPC = "GRPC";
    private static final String KIND_AZURE_FILES = "AZURE_FILES";

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM hubs ORDER BY created_at";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM hubs WHERE hub_id = :hub_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO hubs (hub_id, name, kind, hostname, port, plaintext,
                              azure_endpoint, azure_share, azure_root_path, azure_protocol, azure_auth,
                              created_at, source)
            VALUES (:hub_id, :name, :kind, :hostname, :port, :plaintext,
                    :azure_endpoint, :azure_share, :azure_root_path, :azure_protocol, :azure_auth,
                    :created_at, :source)""";

    //language=SQL
    private static final String UPDATE = """
            UPDATE hubs
            SET name = :name, kind = :kind, hostname = :hostname, port = :port, plaintext = :plaintext,
                azure_endpoint = :azure_endpoint, azure_share = :azure_share,
                azure_root_path = :azure_root_path, azure_protocol = :azure_protocol,
                azure_auth = :azure_auth, source = :source
            WHERE hub_id = :hub_id""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM hubs WHERE hub_id = :hub_id";

    private final DatabaseClient databaseClient;

    public JdbcHubsRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.HUBS);
    }

    @Override
    public List<HubInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_HUBS,
                SELECT_ALL,
                new MapSqlParameterSource(),
                hubMapper());
    }

    @Override
    public Optional<HubInfo> find(String hubId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubId);

        return databaseClient.querySingle(
                StatementLabel.FIND_HUB_BY_ID, SELECT_BY_ID, params, hubMapper());
    }

    @Override
    public HubInfo create(HubInfo hubInfo) {
        MapSqlParameterSource params = hubParams(hubInfo)
                .addValue("created_at", Timestamp.from(hubInfo.createdAt()));

        databaseClient.update(StatementLabel.INSERT_HUB, INSERT, params);
        return hubInfo;
    }

    @Override
    public void update(HubInfo hubInfo) {
        databaseClient.update(StatementLabel.UPDATE_HUB, UPDATE, hubParams(hubInfo));
    }

    @Override
    public void delete(String hubId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubId);

        databaseClient.update(StatementLabel.DELETE_HUB, DELETE, params);
    }

    /** Every column but {@code created_at}, which only an insert writes; the other kind's columns are NULL. */
    private static MapSqlParameterSource hubParams(HubInfo hubInfo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubInfo.hubId())
                .addValue("name", hubInfo.name())
                .addValue("source", hubInfo.source().name())
                .addValue("hostname", null)
                .addValue("port", null)
                .addValue("plaintext", false)
                .addValue("azure_endpoint", null)
                .addValue("azure_share", null)
                .addValue("azure_root_path", null)
                .addValue("azure_protocol", null)
                .addValue("azure_auth", null);

        return switch (hubInfo.address()) {
            case GrpcHubAddress grpc -> params
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
                    .addValue("azure_auth", azure.auth().name());
        };
    }

    private static RowMapper<HubInfo> hubMapper() {
        return (rs, _) -> new HubInfo(
                rs.getString("hub_id"),
                rs.getString("name"),
                address(rs),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC).toInstant(),
                HubSource.fromDb(rs.getString("source")));
    }

    /** A row written before the column existed carries the default, {@code GRPC}. */
    private static HubAddress address(ResultSet rs) throws SQLException {
        if (KIND_AZURE_FILES.equals(rs.getString("kind"))) {
            return new AzureFilesAddress(
                    rs.getString("azure_endpoint"),
                    rs.getString("azure_share"),
                    rs.getString("azure_root_path"),
                    AzureFilesProtocol.parse(rs.getString("azure_protocol")).orElseThrow(
                            () -> new SQLException("Stored Azure Files hub has no readable protocol")),
                    AzureFilesAuth.parse(rs.getString("azure_auth")).orElseThrow(
                            () -> new SQLException("Stored Azure Files hub has no readable sign-in method")));
        }
        return new GrpcHubAddress(rs.getString("hostname"), rs.getInt("port"), rs.getBoolean("plaintext"));
    }
}

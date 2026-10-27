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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.persistence.api.HubsRepository;
import cafe.jeffrey.microscope.model.hub.HubAddress;
import cafe.jeffrey.microscope.model.hub.HubSource;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcHubsRepository implements HubsRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM hubs ORDER BY created_at";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM hubs WHERE hub_id = :hub_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO hubs (hub_id, name, hostname, port, plaintext, created_at, source)
            VALUES (:hub_id, :name, :hostname, :port, :plaintext, :created_at, :source)""";

    //language=SQL
    private static final String UPDATE = """
            UPDATE hubs
            SET name = :name, hostname = :hostname, port = :port,
                plaintext = :plaintext, source = :source
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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubInfo.hubId())
                .addValue("name", hubInfo.name())
                .addValue("hostname", hubInfo.address().hostname())
                .addValue("port", hubInfo.address().port())
                .addValue("plaintext", hubInfo.address().plaintext())
                .addValue("created_at", Timestamp.from(hubInfo.createdAt()))
                .addValue("source", hubInfo.source().name());

        databaseClient.update(StatementLabel.INSERT_HUB, INSERT, params);
        return hubInfo;
    }

    @Override
    public void update(HubInfo hubInfo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubInfo.hubId())
                .addValue("name", hubInfo.name())
                .addValue("hostname", hubInfo.address().hostname())
                .addValue("port", hubInfo.address().port())
                .addValue("plaintext", hubInfo.address().plaintext())
                .addValue("source", hubInfo.source().name());

        databaseClient.update(StatementLabel.UPDATE_HUB, UPDATE, params);
    }

    @Override
    public void delete(String hubId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hub_id", hubId);

        databaseClient.update(StatementLabel.DELETE_HUB, DELETE, params);
    }

    private static RowMapper<HubInfo> hubMapper() {
        return (rs, _) -> new HubInfo(
                rs.getString("hub_id"),
                rs.getString("name"),
                new HubAddress(
                        rs.getString("hostname"),
                        rs.getInt("port"),
                        rs.getBoolean("plaintext")),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC).toInstant(),
                HubSource.fromDb(rs.getString("source")));
    }
}

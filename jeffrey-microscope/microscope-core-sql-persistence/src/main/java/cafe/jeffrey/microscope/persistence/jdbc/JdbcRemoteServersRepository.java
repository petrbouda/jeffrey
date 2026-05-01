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
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.microscope.persistence.api.RemoteServersRepository;
import cafe.jeffrey.microscope.persistence.api.ServerAddress;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcRemoteServersRepository implements RemoteServersRepository {

    //language=SQL
    private static final String SELECT_ALL =
            "SELECT * FROM remote_servers ORDER BY created_at";

    //language=SQL
    private static final String SELECT_BY_ID =
            "SELECT * FROM remote_servers WHERE server_id = :server_id";

    //language=SQL
    private static final String INSERT = """
            INSERT INTO remote_servers (server_id, name, hostname, port, plaintext, created_at)
            VALUES (:server_id, :name, :hostname, :port, :plaintext, :created_at)""";

    //language=SQL
    private static final String DELETE =
            "DELETE FROM remote_servers WHERE server_id = :server_id";

    private final DatabaseClient databaseClient;

    public JdbcRemoteServersRepository(DatabaseClientProvider databaseClientProvider) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.REMOTE_SERVERS);
    }

    @Override
    public List<RemoteServerInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_REMOTE_SERVERS,
                SELECT_ALL,
                new MapSqlParameterSource(),
                serverMapper());
    }

    @Override
    public Optional<RemoteServerInfo> find(String serverId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("server_id", serverId);

        return databaseClient.querySingle(
                StatementLabel.FIND_REMOTE_SERVER_BY_ID, SELECT_BY_ID, params, serverMapper());
    }

    @Override
    public RemoteServerInfo create(RemoteServerInfo serverInfo) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("server_id", serverInfo.serverId())
                .addValue("name", serverInfo.name())
                .addValue("hostname", serverInfo.address().hostname())
                .addValue("port", serverInfo.address().port())
                .addValue("plaintext", serverInfo.address().plaintext())
                .addValue("created_at", Timestamp.from(serverInfo.createdAt()));

        databaseClient.update(StatementLabel.INSERT_REMOTE_SERVER, INSERT, params);
        return serverInfo;
    }

    @Override
    public void delete(String serverId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("server_id", serverId);

        databaseClient.update(StatementLabel.DELETE_REMOTE_SERVER, DELETE, params);
    }

    private static RowMapper<RemoteServerInfo> serverMapper() {
        return (rs, _) -> new RemoteServerInfo(
                rs.getString("server_id"),
                rs.getString("name"),
                new ServerAddress(
                        rs.getString("hostname"),
                        rs.getInt("port"),
                        rs.getBoolean("plaintext")),
                rs.getTimestamp("created_at").toInstant().atZone(ZoneOffset.UTC).toInstant());
    }
}

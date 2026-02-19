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

package pbouda.jeffrey.provider.platform.repository;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.Severity;
import pbouda.jeffrey.shared.persistence.GroupLabel;
import pbouda.jeffrey.shared.persistence.StatementLabel;
import pbouda.jeffrey.shared.persistence.client.DatabaseClient;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public class JdbcMessageRepository implements MessageRepository {

    //language=SQL
    private static final String INSERT = """
            INSERT INTO messages (id, project_id, session_id, type, title, message, severity, category, source, created_at)
            VALUES (:id, :project_id, :session_id, :type, :title, :message, :severity, :category, :source, :created_at)
            ON CONFLICT (session_id, type, created_at) DO NOTHING""";

    //language=SQL
    private static final String SELECT_ALL = """
            SELECT * FROM messages
            WHERE project_id = :project_id AND created_at >= :from AND created_at <= :to
            ORDER BY created_at DESC""";

    //language=SQL
    private static final String DELETE_BY_PROJECT =
            "DELETE FROM messages WHERE project_id = :project_id";

    private final String projectId;
    private final DatabaseClient databaseClient;

    public JdbcMessageRepository(String projectId, DatabaseClientProvider databaseClientProvider) {
        this.projectId = projectId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.MESSAGES);
    }

    @Override
    public void insert(ImportantMessage message) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("project_id", projectId)
                .addValue("session_id", message.sessionId())
                .addValue("type", message.type())
                .addValue("title", message.title())
                .addValue("message", message.message())
                .addValue("severity", message.severity().name())
                .addValue("category", message.category())
                .addValue("source", message.source())
                .addValue("created_at", message.createdAt().atOffset(ZoneOffset.UTC));

        databaseClient.insert(StatementLabel.INSERT_MESSAGE, INSERT, paramSource);
    }

    @Override
    public List<ImportantMessage> findAll(Instant from, Instant to) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("from", from.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));

        return databaseClient.query(
                StatementLabel.FIND_ALL_MESSAGES, SELECT_ALL, paramSource, messageMapper());
    }

    @Override
    public void deleteByProject() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        databaseClient.delete(StatementLabel.DELETE_MESSAGES_BY_PROJECT, DELETE_BY_PROJECT, paramSource);
    }

    private static RowMapper<ImportantMessage> messageMapper() {
        return (rs, _) -> new ImportantMessage(
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                Severity.fromString(rs.getString("severity")),
                rs.getString("category"),
                rs.getString("source"),
                false,
                rs.getString("session_id"),
                Mappers.instant(rs, "created_at"));
    }
}

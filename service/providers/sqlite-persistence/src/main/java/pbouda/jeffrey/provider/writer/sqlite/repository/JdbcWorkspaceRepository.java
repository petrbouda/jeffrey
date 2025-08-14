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

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.IDGenerator;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.provider.writer.sqlite.GroupLabel;
import pbouda.jeffrey.provider.writer.sqlite.StatementLabel;
import pbouda.jeffrey.provider.writer.sqlite.client.DatabaseClient;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    //language=SQL
    private static final String SELECT_ALL_WORKSPACES = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w
            WHERE w.enabled = true""";

    //language=SQL
    private static final String SELECT_WORKSPACE_BY_ID = """
            SELECT w.*, (SELECT COUNT(*) FROM main.projects p WHERE p.workspace_id = w.workspace_id) as project_count
            FROM main.workspaces w
            WHERE w.workspace_id = :workspace_id AND w.enabled = true""";

    //language=SQL
    private static final String INSERT_WORKSPACE = """
            INSERT INTO main.workspaces (workspace_id, name, description, path, enabled, created_at)
            VALUES (:workspace_id, :name, :description, :path, :enabled, :created_at)""";

    //language=SQL
    private static final String DELETE_WORKSPACE =
            "UPDATE main.workspaces SET enabled = false WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String CHECK_NAME_EXISTS =
            "SELECT COUNT(*) FROM main.workspaces WHERE name = :name AND enabled = true";

    // Workspace Sessions SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_SESSION = """
            INSERT INTO main.workspace_sessions
            (session_id, origin_session_id, project_id, workspace_id, last_detected_file, relative_path, workspaces_path, origin_created_at, created_at)
            VALUES (:session_id, :origin_session_id, :project_id, :workspace_id, :last_detected_file, :relative_path, :workspaces_path, :origin_created_at, :created_at)""";

    //language=SQL
    private static final String SELECT_SESSIONS_BY_PROJECT_ID = """
            SELECT ws.*, w.path as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id WHERE ws.project_id = :project_id""";

    //language=SQL
    private static final String SELECT_SESSION_BY_PROJECT_AND_SESSION_ID = """
            SELECT ws.*, w.path as workspace_path FROM main.workspace_sessions ws
            JOIN main.workspaces w ON ws.workspace_id = w.workspace_id
            WHERE ws.project_id = :project_id AND ws.session_id = :session_id""";

    // Workspace Events SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_EVENT = """
            INSERT OR IGNORE INTO main.workspace_events (event_id, origin_event_id, workspace_id, project_id, event_type, content, origin_created_at, created_at)
            VALUES (:event_id, :origin_event_id, :workspace_id, :project_id, :event_type, :content, :origin_created_at, :created_at)""";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID =
            "SELECT * FROM main.workspace_events WHERE workspace_id = :workspace_id ORDER BY created_at DESC";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID_AND_TYPE = """
            SELECT * FROM main.workspace_events
            WHERE workspace_id = :workspace_id AND event_type = :event_type ORDER BY created_at DESC""";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID_FROM_CREATED_AT = """
            SELECT * FROM main.workspace_events
            WHERE workspace_id = :workspace_id AND created_at >= :from_created_at ORDER BY created_at DESC""";

    // Workspace Event Consumer SQL
    //language=SQL
    private static final String INSERT_EVENT_CONSUMER = """
            INSERT INTO main.workspace_event_consumers (consumer_id, consumer_name, last_execution_at, last_processed_event_at, created_at)
            VALUES (:consumer_id, :consumer_name, :last_execution_at, :last_processed_event_at, :created_at)""";

    //language=SQL
    private static final String UPDATE_EVENT_CONSUMER_EXECUTION = """
            UPDATE main.workspace_event_consumers
            SET last_execution_at = :last_execution_at, last_processed_event_at = :last_processed_event_at
            WHERE consumer_name = :consumer_name""";

    //language=SQL
    private static final String SELECT_EVENT_CONSUMER_BY_NAME =
            "SELECT * FROM main.workspace_event_consumers WHERE consumer_name = :consumer_name";

    private final DatabaseClient databaseClient;

    public JdbcWorkspaceRepository(DataSource dataSource) {
        this.databaseClient = new DatabaseClient(dataSource, GroupLabel.WORKSPACES);
    }

    @Override
    public List<WorkspaceInfo> findAll() {
        return databaseClient.query(
                StatementLabel.FIND_ALL_WORKSPACES,
                SELECT_ALL_WORKSPACES,
                new MapSqlParameterSource(),
                workspaceMapper());
    }

    @Override
    public Optional<WorkspaceInfo> findById(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_WORKSPACE_BY_ID,
                SELECT_WORKSPACE_BY_ID,
                paramSource,
                workspaceMapper());
    }

    @Override
    public WorkspaceInfo create(WorkspaceInfo workspaceInfo) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceInfo.id())
                .addValue("name", workspaceInfo.name())
                .addValue("description", workspaceInfo.description())
                .addValue("path", workspaceInfo.path())
                .addValue("enabled", workspaceInfo.enabled())
                .addValue("created_at", workspaceInfo.createdAt().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE, INSERT_WORKSPACE, paramSource);
        return workspaceInfo;
    }

    @Override
    public boolean delete(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        int rowsAffected = databaseClient.update(StatementLabel.DELETE_WORKSPACE, DELETE_WORKSPACE, paramSource);
        return rowsAffected > 0;
    }

    @Override
    public boolean existsByName(String name) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("name", name);

        Integer count = databaseClient.querySingle(
                StatementLabel.CHECK_NAME_EXISTS,
                CHECK_NAME_EXISTS,
                paramSource,
                (rs, _) -> rs.getInt(1)
        ).orElse(0);

        return count > 0;
    }

    @Override
    public void createSession(WorkspaceSessionInfo session) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("session_id", session.sessionId())
                .addValue("origin_session_id", session.originSessionId())
                .addValue("project_id", session.projectId())
                .addValue("workspace_id", session.workspaceId())
                .addValue("last_detected_file", session.lastDetectedFile())
                .addValue("relative_path", session.relativePath())
                .addValue("workspaces_path", session.workspacesPath())
                .addValue("origin_created_at", session.originCreatedAt().toEpochMilli())
                .addValue("created_at", Instant.now().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_SESSION, INSERT_WORKSPACE_SESSION, paramSource);
    }

    @Override
    public List<WorkspaceSessionInfo> findSessionsByProjectId(String projectId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId);

        return databaseClient.query(
                StatementLabel.FIND_SESSIONS_BY_PROJECT_ID,
                SELECT_SESSIONS_BY_PROJECT_ID,
                paramSource,
                workspaceSessionMapper());
    }

    @Override
    public Optional<WorkspaceSessionInfo> findSessionByProjectIdAndSessionId(String projectId, String sessionId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("project_id", projectId)
                .addValue("session_id", sessionId);

        return databaseClient.querySingle(
                StatementLabel.FIND_SESSION_BY_PROJECT_AND_SESSION_ID,
                SELECT_SESSION_BY_PROJECT_AND_SESSION_ID,
                paramSource,
                workspaceSessionMapper());
    }

    @Override
    public void insertEvent(WorkspaceEvent workspaceEvent) {
        String eventId = workspaceEvent.eventId() != null ? workspaceEvent.eventId() : IDGenerator.generate();

        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("event_id", eventId)
                .addValue("origin_event_id", workspaceEvent.originEventId())
                .addValue("workspace_id", workspaceEvent.workspaceId())
                .addValue("project_id", workspaceEvent.projectId())
                .addValue("event_type", workspaceEvent.eventType().name())
                .addValue("content", workspaceEvent.content())
                .addValue("origin_created_at", workspaceEvent.createdAt().toEpochMilli())
                .addValue("created_at", Instant.now().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_EVENT, INSERT_WORKSPACE_EVENT, paramSource);
    }

    @Override
    public void batchInsertEvents(List<WorkspaceEvent> workspaceEvents) {
        if (workspaceEvents.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] paramSources = new MapSqlParameterSource[workspaceEvents.size()];
        for (int i = 0; i < workspaceEvents.size(); i++) {
            WorkspaceEvent event = workspaceEvents.get(i);
            String eventId = event.eventId() != null ? event.eventId() : IDGenerator.generate();
            paramSources[i] = new MapSqlParameterSource()
                    .addValue("event_id", eventId)
                    .addValue("origin_event_id", event.originEventId())
                    .addValue("workspace_id", event.workspaceId())
                    .addValue("project_id", event.projectId())
                    .addValue("event_type", event.eventType().name())
                    .addValue("content", event.content())
                    .addValue("origin_created_at", event.createdAt().toEpochMilli())
                    .addValue("created_at", Instant.now().toEpochMilli());
        }

        databaseClient.batchInsert(StatementLabel.BATCH_INSERT_WORKSPACE_EVENTS, INSERT_WORKSPACE_EVENT, paramSources);
    }

    @Override
    public List<WorkspaceEvent> findEvents(String workspaceId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID,
                SELECT_EVENTS_BY_WORKSPACE_ID,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public List<WorkspaceEvent> findEventsByEventType(String workspaceId, WorkspaceEventType eventType) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("event_type", eventType.name());

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID_AND_TYPE,
                SELECT_EVENTS_BY_WORKSPACE_ID_AND_TYPE,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public List<WorkspaceEvent> findEventsFromCreatedAt(String workspaceId, Instant fromCreatedAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("from_created_at", fromCreatedAt.toEpochMilli());

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID_FROM_CREATED_AT,
                SELECT_EVENTS_BY_WORKSPACE_ID_FROM_CREATED_AT,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public WorkspaceEventConsumer createEventConsumer(WorkspaceEventConsumer workspaceEventConsumer) {
        String consumerId = workspaceEventConsumer.consumerId() != null ? workspaceEventConsumer.consumerId() : IDGenerator.generate();
        
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("consumer_name", workspaceEventConsumer.consumerName())
                .addValue("last_execution_at", workspaceEventConsumer.lastExecutionAt() != null ? workspaceEventConsumer.lastExecutionAt().toEpochMilli() : null)
                .addValue("last_processed_event_at", workspaceEventConsumer.lastProcessedEventAt() != null ? workspaceEventConsumer.lastProcessedEventAt().toEpochMilli() : null)
                .addValue("created_at", workspaceEventConsumer.createdAt().toEpochMilli());

        databaseClient.update(StatementLabel.INSERT_EVENT_CONSUMER, INSERT_EVENT_CONSUMER, paramSource);
        return new WorkspaceEventConsumer(
                consumerId,
                workspaceEventConsumer.consumerName(),
                workspaceEventConsumer.lastExecutionAt(),
                workspaceEventConsumer.lastProcessedEventAt(),
                workspaceEventConsumer.createdAt()
        );
    }

    @Override
    public void updateEventConsumerExecution(String consumerName, Instant lastProcessedEventAt) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_name", consumerName)
                .addValue("last_execution_at", Instant.now().toEpochMilli())
                .addValue("last_processed_event_at", lastProcessedEventAt);

        databaseClient.update(
                StatementLabel.UPDATE_EVENT_CONSUMER_EXECUTION, UPDATE_EVENT_CONSUMER_EXECUTION, paramSource);
    }

    @Override
    public Optional<WorkspaceEventConsumer> findEventConsumerByName(String consumerName) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_name", consumerName);

        return databaseClient.querySingle(
                StatementLabel.FIND_EVENT_CONSUMER_BY_NAME,
                SELECT_EVENT_CONSUMER_BY_NAME,
                paramSource,
                workspaceEventConsumerMapper());
    }

    private static RowMapper<WorkspaceInfo> workspaceMapper() {
        return (rs, _) -> new WorkspaceInfo(
                rs.getString("workspace_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("path"),
                rs.getBoolean("enabled"),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getInt("project_count")
        );
    }

    private static RowMapper<WorkspaceSessionInfo> workspaceSessionMapper() {
        return (rs, _) -> {
            String workspacesPathStr = rs.getString("workspaces_path");
            Path workspacesPath = workspacesPathStr != null ? Path.of(workspacesPathStr) : null;
            
            return new WorkspaceSessionInfo(
                    rs.getString("session_id"),
                    rs.getString("origin_session_id"),
                    rs.getString("project_id"),
                    rs.getString("workspace_id"),
                    rs.getString("last_detected_file"),
                    Path.of(rs.getString("relative_path")),
                    workspacesPath,
                    Instant.ofEpochMilli(rs.getLong("origin_created_at")),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        };
    }

    private static RowMapper<WorkspaceEvent> workspaceEventMapper() {
        return (rs, _) -> new WorkspaceEvent(
                rs.getString("event_id"),
                rs.getString("origin_event_id"),
                rs.getString("project_id"),
                rs.getString("workspace_id"),
                WorkspaceEventType.valueOf(rs.getString("event_type")),
                rs.getString("content"),
                Instant.ofEpochMilli(rs.getLong("origin_created_at")),
                Instant.ofEpochMilli(rs.getLong("created_at"))
        );
    }

    private static RowMapper<WorkspaceEventConsumer> workspaceEventConsumerMapper() {
        return (rs, _) -> {
            long lastExecutionAt = rs.getLong("last_execution_at");
            long lastProcessedEventAt = rs.getLong("last_processed_event_at");
            
            return new WorkspaceEventConsumer(
                    rs.getString("consumer_id"),
                    rs.getString("consumer_name"),
                    lastExecutionAt == 0 ? null : Instant.ofEpochMilli(lastExecutionAt),
                    lastProcessedEventAt == 0 ? null : Instant.ofEpochMilli(lastProcessedEventAt),
                    Instant.ofEpochMilli(rs.getLong("created_at"))
            );
        };
    }
}

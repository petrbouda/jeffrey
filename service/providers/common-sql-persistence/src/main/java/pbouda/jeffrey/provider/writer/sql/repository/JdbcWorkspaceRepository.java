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

package pbouda.jeffrey.provider.writer.sql.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventConsumer;
import pbouda.jeffrey.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;
import pbouda.jeffrey.provider.writer.sql.GroupLabel;
import pbouda.jeffrey.provider.writer.sql.StatementLabel;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClient;
import pbouda.jeffrey.provider.writer.sql.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

public class JdbcWorkspaceRepository implements WorkspaceRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcWorkspaceRepository.class);

    //language=SQL
    private static final String DELETE_WORKSPACE =
            "UPDATE workspaces SET deleted = true WHERE workspace_id = :workspace_id";

    //language=SQL
    private static final String SELECT_PROJECTS_BY_WORKSPACE_ID = """
            SELECT * FROM projects
            JOIN workspaces ON projects.workspace_id = workspaces.workspace_id
            WHERE workspaces.workspace_id = :workspace_id""";

    // Workspace Events SQL
    //language=SQL
    private static final String INSERT_WORKSPACE_EVENT = """
            INSERT INTO workspace_events (event_id, origin_event_id, workspace_id, project_id, event_type, content, origin_created_at, created_at, created_by)
            VALUES (:event_id, :origin_event_id, :workspace_id, :project_id, :event_type, :content, :origin_created_at, :created_at, :created_by)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID =
            "SELECT * FROM workspace_events WHERE workspace_id = :workspace_id ORDER BY created_at DESC";

    //language=SQL
    private static final String SELECT_EVENTS_BY_WORKSPACE_ID_FROM_OFFSET = """
            SELECT * FROM workspace_events WHERE workspace_id = :workspace_id AND event_id > :from_offset""";

    // Workspace Event Consumer SQL
    //language=SQL
    private static final String INSERT_EVENT_CONSUMER = """
            INSERT INTO workspace_event_consumers (consumer_id, workspace_id, created_at)
            VALUES (:consumer_id, :workspace_id, :created_at)""";

    //language=SQL
    private static final String UPDATE_EVENT_CONSUMER_UPDATE_OFFSET = """
            UPDATE workspace_event_consumers
            SET last_execution_at = :last_execution_at, last_offset = :last_offset
            WHERE consumer_id = :consumer_id AND workspace_id = :workspace_id""";

    //language=SQL
    private static final String SELECT_EVENT_CONSUMER_BY_ID = """
            SELECT * FROM workspace_event_consumers
            WHERE consumer_id = :consumer_id AND workspace_id = :workspace_id""";

    private final String workspaceId;
    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcWorkspaceRepository(String workspaceId, DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.workspaceId = workspaceId;
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACES);
        this.clock = clock;
    }

    @Override
    public boolean delete() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        int rowsAffected = databaseClient.update(StatementLabel.DELETE_WORKSPACE, DELETE_WORKSPACE, paramSource);
        return rowsAffected > 0;
    }

    @Override
    public List<ProjectInfo> findAllProjects() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_PROJECTS_BY_WORKSPACE_ID,
                SELECT_PROJECTS_BY_WORKSPACE_ID,
                paramSource,
                Mappers.projectInfoMapper());
    }

    @Override
    public void insertEvent(WorkspaceEvent workspaceEvent) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("origin_event_id", workspaceEvent.originEventId())
                .addValue("workspace_repository_id", workspaceEvent.workspaceId())
                .addValue("project_id", workspaceEvent.projectId())
                .addValue("event_type", workspaceEvent.eventType().name())
                .addValue("content", workspaceEvent.content())
                .addValue("origin_created_at", workspaceEvent.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC))
                .addValue("created_by", workspaceEvent.createdBy());

        databaseClient.update(StatementLabel.INSERT_WORKSPACE_EVENT, INSERT_WORKSPACE_EVENT, paramSource);
    }

    @Override
    public void batchInsertEvents(List<WorkspaceEvent> workspaceEvents) {
        if (workspaceEvents.isEmpty()) {
            return;
        }

        MapSqlParameterSource[] paramSources = new MapSqlParameterSource[workspaceEvents.size()];
        for (int i = 0; i < workspaceEvents.size(); i++) {
            Instant instant = clock.instant();
            long nanos = instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
            long eventId = nanos + i;

            WorkspaceEvent event = workspaceEvents.get(i);
            paramSources[i] = new MapSqlParameterSource()
                    .addValue("event_id", eventId)
                    .addValue("origin_event_id", event.originEventId())
                    .addValue("workspace_id", event.workspaceId())
                    .addValue("project_id", event.projectId())
                    .addValue("event_type", event.eventType().name())
                    .addValue("content", event.content())
                    .addValue("origin_created_at", event.originCreatedAt().atOffset(ZoneOffset.UTC))
                    .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC))
                    .addValue("created_by", event.createdBy());
        }

        long result = databaseClient.batchInsert(StatementLabel.BATCH_INSERT_WORKSPACE_EVENTS, INSERT_WORKSPACE_EVENT, paramSources);
        if (result != workspaceEvents.size()) {
            LOG.warn("Failed to insert all workspace events: expected={} result={}", workspaceEvents.size(), result);
        }
    }

    @Override
    public List<WorkspaceEvent> findEvents() {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID,
                SELECT_EVENTS_BY_WORKSPACE_ID,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public List<WorkspaceEvent> findEventsFromOffset(long fromOffset) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("workspace_id", this.workspaceId)
                .addValue("from_offset", fromOffset);

        return databaseClient.query(
                StatementLabel.FIND_EVENTS_BY_WORKSPACE_ID_FROM_CREATED_AT,
                SELECT_EVENTS_BY_WORKSPACE_ID_FROM_OFFSET,
                paramSource,
                workspaceEventMapper());
    }

    @Override
    public void createEventConsumer(String consumerId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", this.workspaceId)
                .addValue("created_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(StatementLabel.INSERT_EVENT_CONSUMER, INSERT_EVENT_CONSUMER, paramSource);
    }

    @Override
    public void updateEventConsumerOffset(String consumerId, long lastOffset) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", this.workspaceId)
                .addValue("last_offset", lastOffset)
                .addValue("last_execution_at", clock.instant().atOffset(ZoneOffset.UTC));

        databaseClient.update(
                StatementLabel.UPDATE_EVENT_CONSUMER_UPDATE_OFFSET, UPDATE_EVENT_CONSUMER_UPDATE_OFFSET, paramSource);
    }

    @Override
    public Optional<WorkspaceEventConsumer> findEventConsumer(String consumerId) {
        MapSqlParameterSource paramSource = new MapSqlParameterSource()
                .addValue("consumer_id", consumerId)
                .addValue("workspace_id", this.workspaceId);

        return databaseClient.querySingle(
                StatementLabel.FIND_EVENT_CONSUMER_BY_ID,
                SELECT_EVENT_CONSUMER_BY_ID,
                paramSource,
                workspaceEventConsumerMapper());
    }

    private static RowMapper<WorkspaceEvent> workspaceEventMapper() {
        return (rs, _) -> new WorkspaceEvent(
                rs.getLong("event_id"),
                rs.getString("origin_event_id"),
                rs.getString("project_id"),
                rs.getString("workspace_id"),
                WorkspaceEventType.valueOf(rs.getString("event_type")),
                rs.getString("content"),
                Mappers.instant(rs, "origin_created_at"),
                Mappers.instant(rs, "created_at"),
                rs.getString("created_by")
        );
    }

    private static RowMapper<WorkspaceEventConsumer> workspaceEventConsumerMapper() {
        return (rs, _) -> {
            long lastExecutionAt = rs.getLong("last_execution_at");
            long lastOffset = rs.getLong("last_offset");

            return new WorkspaceEventConsumer(
                    rs.getString("consumer_id"),
                    lastOffset,
                    lastExecutionAt == 0 ? null : Instant.ofEpochMilli(lastExecutionAt),
                    Mappers.instant(rs, "created_at")
            );
        };
    }
}

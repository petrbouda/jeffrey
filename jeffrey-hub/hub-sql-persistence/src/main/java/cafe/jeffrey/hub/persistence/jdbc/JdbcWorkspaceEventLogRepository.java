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

package cafe.jeffrey.hub.persistence.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.persistence.GroupLabel;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * DuckDB-backed implementation of {@link WorkspaceEventLogRepository}.
 *
 * <p>Dedup relies on the unique index over {@code (workspace_id, dedup_key)} together with
 * {@code ON CONFLICT DO NOTHING}: a duplicate key is dropped silently, while {@code NULL}
 * keys are never considered duplicates of each other.</p>
 */
public class JdbcWorkspaceEventLogRepository implements WorkspaceEventLogRepository {

    /** IN-list placeholder used when no project filter is requested — never matches a real id. */
    private static final List<String> NO_PROJECT_FILTER = List.of("");

    //language=SQL
    private static final String INSERT_EVENT = """
            INSERT INTO workspace_events
                (workspace_id, workspace_ref_id, project_id, origin_event_id, event_type,
                 content, origin_created_at, created_at, created_by, dedup_key)
            VALUES
                (:workspace_id, :workspace_ref_id, :project_id, :origin_event_id, :event_type,
                 :content, :origin_created_at, :created_at, :created_by, :dedup_key)
            ON CONFLICT DO NOTHING""";

    //language=SQL
    private static final String SELECT_LATEST_EVENTS = """
            SELECT * FROM workspace_events
            WHERE workspace_id = :workspace_id
              AND (:event_type IS NULL OR event_type = :event_type)
              AND (NOT :filter_by_project OR project_id IN (:project_ids))
            ORDER BY event_id DESC
            LIMIT :limit""";

    //language=SQL
    private static final String COUNT_EVENTS = """
            SELECT COUNT(*) FROM workspace_events
            WHERE workspace_id = :workspace_id""";

    //language=SQL
    private static final String DELETE_OLD_EVENTS = """
            DELETE FROM workspace_events
            WHERE created_at < :cutoff""";

    private final DatabaseClient databaseClient;
    private final Clock clock;

    public JdbcWorkspaceEventLogRepository(DatabaseClientProvider databaseClientProvider, Clock clock) {
        this.databaseClient = databaseClientProvider.provide(GroupLabel.WORKSPACE_EVENT_LOG);
        this.clock = clock;
    }

    @Override
    public int append(String workspaceId, WorkspaceEvent event, String dedupKey) {
        return databaseClient.insert(
                StatementLabel.WORKSPACE_EVENT_LOG_APPEND,
                INSERT_EVENT,
                insertParams(workspaceId, event, dedupKey));
    }

    @Override
    public int appendBatch(String workspaceId, List<PendingWorkspaceEvent> events) {
        if (events.isEmpty()) {
            return 0;
        }

        MapSqlParameterSource[] paramSources = new MapSqlParameterSource[events.size()];
        for (int i = 0; i < events.size(); i++) {
            PendingWorkspaceEvent pending = events.get(i);
            paramSources[i] = insertParams(workspaceId, pending.event(), pending.dedupKey());
        }

        return (int) databaseClient.batchInsert(
                StatementLabel.WORKSPACE_EVENT_LOG_APPEND_BATCH, INSERT_EVENT, paramSources);
    }

    @Override
    public List<WorkspaceEvent> findLatest(WorkspaceEventQuery query) {
        boolean filterByProject = !query.projectIds().isEmpty();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", query.workspaceId())
                .addValue("event_type", query.eventType() != null ? query.eventType().name() : null)
                .addValue("filter_by_project", filterByProject)
                .addValue("project_ids", filterByProject ? List.copyOf(query.projectIds()) : NO_PROJECT_FILTER)
                .addValue("limit", query.limit());

        return databaseClient.query(
                StatementLabel.WORKSPACE_EVENT_LOG_FIND_LATEST,
                SELECT_LATEST_EVENTS,
                params,
                eventMapper());
    }

    @Override
    public long count(String workspaceId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId);

        return databaseClient.queryLong(StatementLabel.WORKSPACE_EVENT_LOG_COUNT, COUNT_EVENTS, params);
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cutoff", cutoff.atOffset(ZoneOffset.UTC));

        return databaseClient.delete(StatementLabel.WORKSPACE_EVENT_LOG_DELETE_OLD, DELETE_OLD_EVENTS, params);
    }

    private MapSqlParameterSource insertParams(String workspaceId, WorkspaceEvent event, String dedupKey) {
        Instant createdAt = event.createdAt() != null ? event.createdAt() : clock.instant();
        return new MapSqlParameterSource()
                .addValue("workspace_id", workspaceId)
                .addValue("workspace_ref_id", event.workspaceRefId())
                .addValue("project_id", event.projectId())
                .addValue("origin_event_id", event.originEventId())
                .addValue("event_type", event.eventType().name())
                .addValue("content", event.content())
                .addValue("origin_created_at",
                        event.originCreatedAt() != null ? event.originCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .addValue("created_at", createdAt.atOffset(ZoneOffset.UTC))
                .addValue("created_by", event.createdBy())
                .addValue("dedup_key", dedupKey);
    }

    private RowMapper<WorkspaceEvent> eventMapper() {
        return (rs, _) -> {
            // Read TIMESTAMPTZ as OffsetDateTime: getTimestamp() would interpret the value
            // in the JVM default timezone and shift instants on non-UTC machines
            OffsetDateTime originCreatedAt = rs.getObject("origin_created_at", OffsetDateTime.class);
            OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
            return new WorkspaceEvent(
                    rs.getLong("event_id"),
                    rs.getString("origin_event_id"),
                    rs.getString("project_id"),
                    rs.getString("workspace_ref_id"),
                    WorkspaceEventType.valueOf(rs.getString("event_type")),
                    rs.getString("content"),
                    originCreatedAt != null ? originCreatedAt.toInstant() : null,
                    createdAt != null ? createdAt.toInstant() : null,
                    rs.getString("created_by"));
        };
    }
}

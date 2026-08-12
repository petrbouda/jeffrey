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

package cafe.jeffrey.hub.persistence.api;

import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Append-only audit log of workspace events. Rows are written alongside the domain change
 * they describe (ideally in the same transaction) and are only ever read back for the
 * Activity/Event Log feed and removed by age-based retention.
 *
 * <p>The log is NOT a work queue: nothing consumes rows, there are no offsets and no
 * acknowledgement. The optional {@code dedupKey} exists solely for stateless producers that
 * re-announce the same fact on every tick (session file announcements) — a duplicate key is
 * silently dropped. Producers of one-shot facts pass a {@code null} key; multiple rows with
 * a {@code null} key are always allowed.</p>
 */
public interface WorkspaceEventLogRepository {

    /**
     * A single event offered to the log, optionally carrying a dedup key.
     */
    record PendingWorkspaceEvent(WorkspaceEvent event, String dedupKey) {

        public static PendingWorkspaceEvent of(WorkspaceEvent event) {
            return new PendingWorkspaceEvent(event, null);
        }

        public static PendingWorkspaceEvent deduped(WorkspaceEvent event, String dedupKey) {
            return new PendingWorkspaceEvent(event, dedupKey);
        }
    }

    /**
     * Filter for reading the latest events of a workspace. {@code eventType} may be
     * {@code null} (no type filter); {@code projectIds} may be empty (no project filter).
     */
    record WorkspaceEventQuery(String workspaceId, WorkspaceEventType eventType, Set<String> projectIds, int limit) {

        public WorkspaceEventQuery {
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("workspaceId must not be blank");
            }
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive: " + limit);
            }
            projectIds = projectIds == null ? Set.of() : Set.copyOf(projectIds);
        }
    }

    /**
     * Appends a single event.
     *
     * @return 1 when the row was inserted, 0 when a duplicate {@code dedupKey} dropped it
     */
    int append(String workspaceId, WorkspaceEvent event, String dedupKey);

    /**
     * Appends a batch of events in one statement.
     *
     * @return the number of rows actually inserted (duplicates by {@code dedupKey} are dropped)
     */
    int appendBatch(String workspaceId, List<PendingWorkspaceEvent> events);

    /**
     * Latest events of a workspace, newest-first by {@code event_id} (stable insert order).
     */
    List<WorkspaceEvent> findLatest(WorkspaceEventQuery query);

    long count(String workspaceId);

    /**
     * Age-based retention across all workspaces. Nothing consumes the log, so there is no
     * lag to protect — rows older than the cutoff are simply removed.
     */
    int deleteOlderThan(Instant cutoff);
}

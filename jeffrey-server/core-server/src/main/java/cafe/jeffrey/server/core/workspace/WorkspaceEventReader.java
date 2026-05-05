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

package cafe.jeffrey.server.core.workspace;

import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;

import java.util.List;

/**
 * Abstraction for reading workspace events. SERVER mode uses a queue-backed
 * implementation; LOCAL mode returns empty results.
 */
public interface WorkspaceEventReader {

    /**
     * Returns the latest events for the workspace, ordered by created_at descending.
     *
     * @param workspaceId the target workspace
     * @param limit       maximum number of events to return; {@code <= 0} = unbounded
     */
    List<WorkspaceEvent> findAll(String workspaceId, int limit);

    /**
     * Total event count for this workspace, ignoring any filters or limits.
     */
    long count(String workspaceId);
}

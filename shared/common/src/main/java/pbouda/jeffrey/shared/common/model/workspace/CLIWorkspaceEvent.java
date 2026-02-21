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

package pbouda.jeffrey.shared.common.model.workspace;

import java.time.Instant;

/**
 * Wire format for CLI→Jeffrey communication. Contains only the domain fields
 * that the CLI can meaningfully populate; queue-infrastructure fields
 * ({@code eventId}, {@code createdAt}) are added later by the replicator job.
 */
public record CLIWorkspaceEvent(
        String originEventId,
        String projectId,
        String workspaceId,
        WorkspaceEventType eventType,
        String content,
        Instant originCreatedAt,
        String createdBy) {
}

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

package cafe.jeffrey.intellij.dto;

import java.util.List;

/**
 * Self-description of a running IDE instance, returned by {@code GET /api/jeffrey/instance}.
 * Microscope aggregates this across all discovered instances to build its target picker, and routes
 * navigation by {@code (port, ProjectInfo.id)}.
 */
public record InstanceResponse(
        int protocolVersion,
        String instanceId,
        String ideName,
        String ideEdition,
        String ideVersion,
        long pid,
        int port,
        String startedAt,
        List<ProjectInfo> projects
) {
}

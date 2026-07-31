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

package cafe.jeffrey.microscope.persistence.api;

import java.time.Instant;

/**
 * A grounded claim, copied out of a profile database so it can be counted across projects.
 *
 * @param profileId   the profile the claim came from
 * @param profileName the profile's display name, for labelling an occurrence
 * @param workspaceId the workspace the profile belongs to, or null
 * @param projectId   the project the profile belongs to
 * @param eventType   the sample event type the claim belongs to
 * @param title       the short title the model gave the recommendation
 * @param citedFrame  the measured frame the claim resolved to
 * @param sourcePath  the source path the model cited, or null
 * @param selfPct     the frame's measured self share
 * @param totalPct    the frame's measured total share
 * @param generatedAt when the claim was recorded
 */
public record AdvisorClaimIndexRow(
        String profileId,
        String profileName,
        String workspaceId,
        String projectId,
        String eventType,
        String title,
        String citedFrame,
        String sourcePath,
        double selfPct,
        double totalPct,
        Instant generatedAt) {
}

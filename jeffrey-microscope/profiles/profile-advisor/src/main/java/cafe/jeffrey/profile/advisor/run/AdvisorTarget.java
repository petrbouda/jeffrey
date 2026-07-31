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

package cafe.jeffrey.profile.advisor.run;

import cafe.jeffrey.shared.common.model.ProfileInfo;

/**
 * Identifies what an advisor run targets: the profile whose call tree is analyzed and the sample event
 * type to analyze it for. The workspace/project identity travels along because the fleet rollup groups
 * grounded frames across projects, and a per-profile database cannot answer that question on its own.
 *
 * @param profileId   the profile being analyzed
 * @param workspaceId the workspace the profile belongs to, or null for a Quick Analysis profile
 * @param projectId   the project the profile belongs to, or null for a Quick Analysis profile
 * @param profileName the profile's display name, so the fleet view can label an occurrence
 * @param eventType   the sample event type whose call tree is analyzed
 */
public record AdvisorTarget(
        String profileId,
        String workspaceId,
        String projectId,
        String profileName,
        String eventType) {

    public AdvisorTarget {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("Profile id must not be blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type must not be blank");
        }
    }

    public static AdvisorTarget of(ProfileInfo profile, String eventType) {
        return new AdvisorTarget(
                profile.id(), profile.workspaceId(), profile.projectId(), profile.name(), eventType);
    }

    /**
     * True when the run belongs to a project, which is what the fleet rollup groups by. Quick Analysis
     * profiles have no project, so their claims are shown on the profile but never rolled up.
     */
    public boolean hasProject() {
        return projectId != null && !projectId.isBlank();
    }
}

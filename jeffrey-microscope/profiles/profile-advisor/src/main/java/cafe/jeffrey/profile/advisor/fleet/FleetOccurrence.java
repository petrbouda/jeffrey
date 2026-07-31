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

package cafe.jeffrey.profile.advisor.fleet;

import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRow;

/**
 * One profile's encounter with a recurring hotspot, carrying enough identity for the UI to deep-link
 * back to the profile that found it.
 */
public record FleetOccurrence(
        String profileId,
        String profileName,
        String workspaceId,
        String projectId,
        String eventType,
        String title,
        String sourcePath,
        double selfPct) {

    public static FleetOccurrence from(AdvisorClaimIndexRow claim) {
        return new FleetOccurrence(
                claim.profileId(),
                claim.profileName(),
                claim.workspaceId(),
                claim.projectId(),
                claim.eventType(),
                claim.title(),
                claim.sourcePath(),
                claim.selfPct());
    }
}

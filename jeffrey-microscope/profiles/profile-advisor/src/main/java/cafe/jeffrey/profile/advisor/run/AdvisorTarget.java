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
 * type to analyze it for.
 *
 * @param profileId the profile being analyzed
 * @param eventType the sample event type whose call tree is analyzed
 */
public record AdvisorTarget(
        String profileId,
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
        return new AdvisorTarget(profile.id(), eventType);
    }
}

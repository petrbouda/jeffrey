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

/**
 * What the pipeline registry files an advisor run under. One run per profile <em>and</em> event type,
 * rather than per profile, because a batch runs all of a profile's types at once and each needs its own
 * slot in the queue — keying by profile alone would let a batch block itself.
 *
 * @param profileId the profile being analyzed
 * @param eventType the sample event type this run covers
 */
public record AdvisorRunKey(String profileId, String eventType) {

    public static AdvisorRunKey of(AdvisorTarget target) {
        return new AdvisorRunKey(target.profileId(), target.eventType());
    }
}

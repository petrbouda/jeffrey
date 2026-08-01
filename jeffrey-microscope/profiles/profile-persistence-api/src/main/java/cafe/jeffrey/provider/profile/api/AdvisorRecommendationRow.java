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

package cafe.jeffrey.provider.profile.api;

import java.time.Instant;

/**
 * A stored advisor result for one sample event type. {@code severity} is Jeffrey's own grade computed
 * from the measured profile.
 *
 * @param eventType        the sample event type analyzed
 * @param severity         the computed severity name
 * @param recommendations  the report markdown
 * @param patch            the proposed unified diff, or null when the model proposed no code edit
 * @param sourceRef        the commit the source tree was on, or null when it was not a git checkout
 * @param generatedAt      when the run completed
 */
public record AdvisorRecommendationRow(
        String eventType,
        String severity,
        String recommendations,
        String patch,
        String sourceRef,
        Instant generatedAt) {
}

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
 * A cached advisor prompt as it is stored: the markdown the model reads plus the serialized call tree
 * behind it. The frame index stays a string at this layer so the persistence module never needs to know
 * the advisor's frame model.
 *
 * @param eventType     the sample event type the prompt describes
 * @param label         the human-readable profile label ("CPU", "Allocation", …)
 * @param samples       total samples the call tree was built from
 * @param markdown      the prompt markdown
 * @param frameIndexJson the serialized frame index, or null when it could not be produced
 * @param generatedAt   when the prompt was generated
 */
public record AdvisorPromptRow(
        String eventType,
        String label,
        long samples,
        String markdown,
        String frameIndexJson,
        Instant generatedAt) {
}

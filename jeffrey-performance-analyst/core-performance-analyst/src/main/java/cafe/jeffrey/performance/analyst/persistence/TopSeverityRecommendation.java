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

package cafe.jeffrey.performance.analyst.persistence;

import cafe.jeffrey.shared.common.model.Severity;

import java.time.Instant;

/**
 * One recording's worst (highest-severity) recommendation, joined to the recording's name — the unit the
 * global Overview ranks. {@code recommendations} is the markdown of that worst recommendation, kept so a
 * short dominant-hotspot headline can be derived for the list row.
 */
public record TopSeverityRecommendation(
        String recordingId,
        String recordingName,
        String hubId,
        String workspaceId,
        String projectId,
        String projectName,
        Severity severity,
        String recommendations,
        Instant generatedAt) {
}

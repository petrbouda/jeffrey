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

import java.time.Instant;

/**
 * One checked citation from a recommendation, in its persistent form. These rows are what makes
 * cross-recording analysis possible: a markdown report cannot be aggregated, but a frame name with a
 * measured share can be grouped, counted and compared across every project in the installation.
 *
 * @param grounded    true when the cited frame was found in the measured profile
 * @param sourceFound true when the cited source path existed in the analyzed checkout
 * @param selfPct     the measured self share of the resolved frame, or 0 when ungrounded
 * @param totalPct    the measured total share of the resolved frame, or 0 when ungrounded
 */
public record StoredClaim(
        String recordingId,
        String eventType,
        String hubId,
        String workspaceId,
        String projectId,
        String projectName,
        String title,
        String citedFrame,
        String sourcePath,
        boolean grounded,
        boolean sourceFound,
        double selfPct,
        double totalPct,
        Instant generatedAt) {
}

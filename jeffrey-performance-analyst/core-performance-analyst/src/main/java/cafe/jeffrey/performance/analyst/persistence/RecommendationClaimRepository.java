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

import java.util.List;

public interface RecommendationClaimRepository {

    /**
     * Replaces every claim stored for one (recording, event type) with {@code claims}. Regenerating a
     * recommendation must not leave the previous run's citations behind, or the fleet rollup would count
     * findings that no longer exist.
     */
    void replaceForRecording(String recordingId, String eventType, List<StoredClaim> claims);

    /**
     * The stored claims for a recording, ordered by measured weight so the UI shows the heaviest first.
     */
    List<StoredClaim> findByRecording(String recordingId);

    /**
     * Grounded frames cited in at least {@code minProjects} distinct projects, ranked by how many
     * projects they affect and then by their worst measured share.
     */
    List<FleetPattern> findRecurringPatterns(int minProjects, int limit);

    /**
     * The grounded claims behind one recurring pattern, one row per project occurrence.
     */
    List<StoredClaim> findByFrame(String citedFrame);

    /**
     * How many distinct projects have any grounded claim — the denominator for "appears in N of M".
     */
    int countAnalyzedProjects();
}

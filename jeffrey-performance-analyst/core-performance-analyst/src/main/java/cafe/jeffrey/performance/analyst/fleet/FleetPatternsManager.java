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

package cafe.jeffrey.performance.analyst.fleet;

import cafe.jeffrey.performance.analyst.persistence.FleetPattern;
import cafe.jeffrey.performance.analyst.persistence.RecommendationClaimRepository;

import java.util.List;

/**
 * Finds the hotspots that show up in more than one project.
 *
 * <p>A per-recording report answers "what is slow here". This answers a question no single recording
 * can: when the same frame costs seven services time, the fix usually belongs where that frame lives
 * rather than in each caller, and that is a different piece of work with a different owner.</p>
 *
 * <p>Grouping is by grounded frame rather than by the model's prose. Free text cannot be aggregated
 * without fuzzy matching, and fuzzy matching over model output would make the rollup less trustworthy
 * than the individual reports it summarises.</p>
 */
public class FleetPatternsManager {

    private static final int MIN_PROJECTS_FOR_PATTERN = 2;

    private final RecommendationClaimRepository claimRepository;

    public FleetPatternsManager(RecommendationClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    /**
     * The recurring patterns with their per-project occurrences, capped at {@code limit} patterns.
     */
    public List<FleetPatternResponse> patterns(int limit) {
        return claimRepository.findRecurringPatterns(MIN_PROJECTS_FOR_PATTERN, limit).stream()
                .map(this::withOccurrences)
                .toList();
    }

    private FleetPatternResponse withOccurrences(FleetPattern pattern) {
        List<FleetOccurrenceResponse> occurrences = claimRepository.findByFrame(pattern.citedFrame()).stream()
                .map(FleetOccurrenceResponse::from)
                .toList();

        return new FleetPatternResponse(
                pattern.citedFrame(),
                pattern.projectCount(),
                pattern.occurrenceCount(),
                pattern.peakSelfPct(),
                occurrences);
    }

    /**
     * How many distinct projects have contributed grounded claims — the denominator the UI shows next to
     * "7 / 12 projects", so a pattern's reach is legible rather than an unanchored count.
     */
    public int analyzedProjectCount() {
        return claimRepository.countAnalyzedProjects();
    }
}

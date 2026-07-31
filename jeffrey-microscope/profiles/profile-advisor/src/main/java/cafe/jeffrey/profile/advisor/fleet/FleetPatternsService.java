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

import cafe.jeffrey.microscope.persistence.api.AdvisorClaimIndexRepository;
import cafe.jeffrey.microscope.persistence.api.AdvisorFrameOccurrences;

import java.util.List;

/**
 * Finds the hotspots that show up in more than one project.
 *
 * <p>A per-profile report answers "what is slow here". This answers a question no single profile can:
 * when the same frame costs seven services time, the fix usually belongs where that frame lives rather
 * than in each caller, and that is a different piece of work with a different owner.</p>
 *
 * <p>Grouping is by grounded frame rather than by the model's prose. Free text cannot be aggregated
 * without fuzzy matching, and fuzzy matching over model output would make the rollup less trustworthy
 * than the individual reports it summarises.</p>
 */
public class FleetPatternsService {

    private static final int MIN_PROJECTS_FOR_PATTERN = 2;

    private final AdvisorClaimIndexRepository claimIndexRepository;

    public FleetPatternsService(AdvisorClaimIndexRepository claimIndexRepository) {
        this.claimIndexRepository = claimIndexRepository;
    }

    /**
     * The recurring patterns with their per-profile occurrences, capped at {@code limit} patterns.
     */
    public FleetPatterns patterns(int limit) {
        List<FleetPattern> patterns = claimIndexRepository
                .findRecurringFrames(MIN_PROJECTS_FOR_PATTERN, limit).stream()
                .map(this::withOccurrences)
                .toList();

        return new FleetPatterns(patterns, claimIndexRepository.countAnalyzedProjects());
    }

    private FleetPattern withOccurrences(AdvisorFrameOccurrences frame) {
        List<FleetOccurrence> occurrences = claimIndexRepository.findByFrame(frame.citedFrame()).stream()
                .map(FleetOccurrence::from)
                .toList();

        return new FleetPattern(
                frame.citedFrame(),
                frame.projectCount(),
                frame.claimCount(),
                frame.peakSelfPct(),
                occurrences);
    }
}

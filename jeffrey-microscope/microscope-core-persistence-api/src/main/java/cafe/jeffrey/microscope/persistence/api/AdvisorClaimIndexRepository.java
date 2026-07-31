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

package cafe.jeffrey.microscope.persistence.api;

import java.util.List;

/**
 * The cross-profile index of grounded Advisor claims. It exists because per-profile databases are
 * isolated: "which frames are hot in more than one project" cannot be asked of any single one of them.
 */
public interface AdvisorClaimIndexRepository {

    /**
     * Replaces one profile/event-type's rows with {@code claims}. A re-run supersedes its predecessor
     * entirely, so the rollup never counts the same profile twice.
     */
    void replaceForProfile(String profileId, String eventType, List<AdvisorClaimIndexRow> claims);

    void deleteForProfile(String profileId);

    /**
     * Frames cited in at least {@code minProjects} distinct projects, heaviest recurrence first.
     */
    List<AdvisorFrameOccurrences> findRecurringFrames(int minProjects, int limit);

    List<AdvisorClaimIndexRow> findByFrame(String citedFrame);

    /**
     * How many distinct projects have produced grounded claims — the denominator behind "7 of 12
     * projects".
     */
    long countAnalyzedProjects();
}

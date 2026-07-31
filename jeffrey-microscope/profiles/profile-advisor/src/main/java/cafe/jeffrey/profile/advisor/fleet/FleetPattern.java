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

import java.util.List;

/**
 * A hotspot that recurs across projects, with the per-profile occurrences behind it.
 *
 * @param citedFrame      the grounded frame the occurrences share
 * @param projectCount    how many distinct projects it costs time in
 * @param occurrenceCount how many profiles cited it
 * @param peakSelfPct     the worst measured self share among those occurrences
 * @param occurrences     one row per occurrence, heaviest first
 */
public record FleetPattern(
        String citedFrame,
        long projectCount,
        long occurrenceCount,
        double peakSelfPct,
        List<FleetOccurrence> occurrences) {

    public FleetPattern {
        occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
    }
}

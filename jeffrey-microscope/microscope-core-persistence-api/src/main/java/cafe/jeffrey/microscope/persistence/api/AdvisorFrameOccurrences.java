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

/**
 * How widely one frame recurs across the analyzed projects — the aggregate row behind a fleet pattern.
 *
 * @param citedFrame   the measured frame
 * @param projectCount how many distinct projects cited it
 * @param claimCount   how many claims cited it in total
 * @param peakSelfPct  the highest measured self share it reached in any of them
 */
public record AdvisorFrameOccurrences(
        String citedFrame,
        long projectCount,
        long claimCount,
        double peakSelfPct) {
}

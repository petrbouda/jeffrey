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

/**
 * A frame that shows up as a hotspot in more than one project — the unit of the fleet rollup. When the
 * same frame is costing several services time, the fix usually belongs wherever that frame is defined
 * rather than in each caller, and that is a different conversation from any single recording's report.
 *
 * @param citedFrame       the grounded frame shared across projects
 * @param projectCount     how many distinct projects it appears in
 * @param occurrenceCount  how many recordings cited it in total
 * @param peakSelfPct      the highest measured self share across those occurrences
 */
public record FleetPattern(String citedFrame, int projectCount, int occurrenceCount, double peakSelfPct) {
}

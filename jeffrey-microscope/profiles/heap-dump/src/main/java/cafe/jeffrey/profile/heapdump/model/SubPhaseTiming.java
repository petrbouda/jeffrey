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

package cafe.jeffrey.profile.heapdump.model;

/**
 * One leaf-level timing inside an init-pipeline stage. Surfaced to the UI so a
 * stage that took, say, 84 s can show the user which slice of that time went
 * where (e.g. CHK fixed-point iteration vs persisting rows back to DuckDB).
 *
 * @param name        machine-readable sub-phase id (e.g. {@code "chk_iter"})
 * @param durationMs  wall-clock duration of this sub-phase
 * @param note        optional free-text note shown alongside the time
 *                    (e.g. {@code "5 iterations"} for CHK); {@code null} when
 *                    the duration alone is enough
 */
public record SubPhaseTiming(
        String name,
        long durationMs,
        String note
) {
}

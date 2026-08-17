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

package cafe.jeffrey.profile.manager.model.trace;

/**
 * One stop-the-world stretch drawn across the waterfall.
 * <p>
 * Positioned in absolute epoch microseconds, the same units {@code TraceSpanRow.startEpochMicros}
 * carries, so a band and a bar are laid out against one window without either side converting.
 *
 * @param category        {@code GC_PAUSE} or {@code SAFEPOINT}
 * @param label           what the pause called itself — the GC phase, the VM operation
 * @param startEpochMicros when it began, absolute
 * @param durationNanos   how long it lasted
 * @param nested          whether this breaks a longer pause down from the inside rather than being
 *                        one of its own — a levelled GC phase runs inside a collection pause. The
 *                        flag travels to the client so the waterfall can draw the breakdown on
 *                        request without a second query, and leave it out by default: five bands
 *                        over one stretch of stopped world say nothing the one band did not
 */
public record TracePause(
        String category,
        String label,
        long startEpochMicros,
        long durationNanos,
        boolean nested) {
}

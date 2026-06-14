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

package cafe.jeffrey.profile.manager.model.stw;

/**
 * One pause on the Unified Stop-The-World timeline: a single occurrence of a pause source, normalized
 * across GC / safepoint / VM-operation / blocking events so they share one time axis.
 *
 * @param category         which lane this belongs to
 * @param scope            global (whole-JVM) vs local (single-thread) stall
 * @param timeOffsetMillis start offset from the recording start
 * @param durationNanos    pause duration
 * @param label            human-readable cause (GC cause, VM operation name, monitor class, …)
 * @param thread           the affected thread, when the source carries one (else {@code null})
 * @param gcId             the GC id for {@code GC_PAUSE} events (else {@code null})
 */
public record StwEvent(
        StwCategory category,
        StwScope scope,
        long timeOffsetMillis,
        long durationNanos,
        String label,
        String thread,
        Long gcId) {
}

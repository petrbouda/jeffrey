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

package cafe.jeffrey.profile.ai.trace;

import java.util.List;

/**
 * Every notification of one kind raised inside a trace, collapsed into a single finding.
 * <p>
 * Grouped the way throws are, and for the same reason: a notification's type says what kind of thing
 * happened, so ten occurrences of a kind are one observation with a count, not ten observations.
 *
 * @param type          what kind of thing happened, the screaming-snake-case name the emitter chose
 * @param severity      {@code CRITICAL}, {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param category      what area it concerns, {@code RESOURCE} or {@code PERFORMANCE} say; may be null
 * @param source        the component that raised it; may be null
 * @param message       the one sentence every occurrence of the kind repeats; may be null
 * @param count         how many times it was raised inside the trace
 * @param firstOffsetMs when the first one fired, in milliseconds after the trace started
 * @param lastOffsetMs  when the last one fired, in milliseconds after the trace started
 * @param attributes    what the first occurrence attached to itself, as the recording held it, or
 *                      {@code null} when it attached nothing
 * @param spans         where they were raised, ranked by how many each accounted for
 */
record TraceNotificationGroup(
        String type,
        String severity,
        String category,
        String source,
        String message,
        long count,
        long firstOffsetMs,
        long lastOffsetMs,
        String attributes,
        List<TraceNotificationGroup.Site> spans) {

    /** One span this was raised in, and how often. */
    record Site(String spanName, long count) {
    }
}

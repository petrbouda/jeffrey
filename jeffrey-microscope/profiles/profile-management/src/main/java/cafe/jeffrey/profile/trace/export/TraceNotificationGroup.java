/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.profile.trace.export;

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

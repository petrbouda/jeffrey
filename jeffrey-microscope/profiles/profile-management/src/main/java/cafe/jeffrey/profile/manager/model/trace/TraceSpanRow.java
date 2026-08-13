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
 * One span of a trace, already placed in the tree.
 * <p>
 * The tree is delivered as a pre-ordered flat list rather than nested objects: the waterfall draws
 * one row per span in exactly this order, so nesting the payload would only force the UI to
 * re-flatten it. {@link #depth()} carries the shape.
 *
 * @param spanId                   hex id of this span
 * @param parentSpanId             hex id of the enclosing span, or {@code null} for a root
 * @param name                     operation name
 * @param kind                     {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param status                   {@code OK}, {@code ERROR} or {@code UNSET}
 * @param errorType                class name of the failure, when the span ended in one
 * @param attributes               the originating event's fields, as a JSON object string
 * @param startMillisFromBeginning span start relative to the recording's start
 * @param startEpochMillis         span start as absolute UTC epoch millis
 * @param durationNanos            total time the span covers
 * @param selfDurationNanos        that time minus what its children covered — where the span's own
 *                                 work actually went
 * @param depth                    nesting level; 0 for a root
 * @param threadHash               thread the span was committed on, as a string for the same
 *                                 safe-integer reason as the ids
 * @param threadName               human-readable name of that thread, when known
 * @param eventType                which event produced the span
 */
public record TraceSpanRow(
        String spanId,
        String parentSpanId,
        String name,
        String kind,
        String status,
        String errorType,
        String attributes,
        long startMillisFromBeginning,
        long startEpochMillis,
        long durationNanos,
        long selfDurationNanos,
        int depth,
        String threadHash,
        String threadName,
        String eventType) {
}

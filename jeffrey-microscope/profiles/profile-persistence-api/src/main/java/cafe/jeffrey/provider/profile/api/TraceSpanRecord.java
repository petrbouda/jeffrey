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

package cafe.jeffrey.provider.profile.api;

/**
 * One span of a trace, as stored. The tree it belongs to is reconstructed above this layer from
 * {@link #parentSpanId()}; this record deliberately carries no children so the repository can stay
 * a flat reader.
 *
 * @param traceId                  the trace this span belongs to
 * @param spanId                   identifies this span within the trace
 * @param parentSpanId             the enclosing span, or {@code null} when this span is a root
 * @param name                     operation name
 * @param kind                     {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param status                   {@code OK}, {@code ERROR} or {@code UNSET}
 * @param errorType                class name of the failure, when the span ended in one
 * @param attributes               the originating event's fields, as a JSON object string
 * @param startMillisFromBeginning span start, in milliseconds relative to the recording's start
 * @param startEpochMillis         span start as an absolute UTC epoch-millis timestamp
 * @param durationNanos            span duration
 * @param threadHash               identity hash of the thread the span was committed on — the join
 *                                 key for pairing the span with the other events on that thread
 * @param threadName               name of that thread, when the recording knew it
 * @param eventType                which event produced the span, e.g. {@code jeffrey.TraceSpan}
 */
public record TraceSpanRecord(
        long traceId,
        long spanId,
        Long parentSpanId,
        String name,
        String kind,
        String status,
        String errorType,
        String attributes,
        long startMillisFromBeginning,
        long startEpochMillis,
        long durationNanos,
        long threadHash,
        String threadName,
        String eventType) {
}

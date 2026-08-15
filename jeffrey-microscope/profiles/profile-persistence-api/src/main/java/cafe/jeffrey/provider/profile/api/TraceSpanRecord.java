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
 * @param startMillisFromBeginning span start, in milliseconds relative to the recording's start
 * @param startEpochMicros         span start as an absolute UTC epoch-micros timestamp. Microseconds
 *                                 rather than milliseconds because a span is routinely shorter than
 *                                 a millisecond: at millisecond resolution two spans that ran one
 *                                 after the other collapse onto the same instant, and the waterfall
 *                                 then draws sequential work as if it overlapped. Microseconds are
 *                                 also the most the stored timestamp carries, and stay inside
 *                                 JavaScript's safe-integer range where epoch nanos would not
 * @param durationNanos            span duration
 * @param selfDurationNanos        that duration minus the stretches the span's same-thread children
 *                                 covered — the time the span spent on its own work. Derived once
 *                                 into the table rather than worked out per read, so the waterfall
 *                                 and the operation breakdown cannot disagree about what "self"
 *                                 means
 * @param threadHash               identity hash of the thread the span was committed on — the join
 *                                 key for pairing the span with the other events on that thread
 * @param threadName               name of that thread, when the recording knew it
 * @param isVirtual                whether that thread was a virtual thread
 * @param eventType                which event produced the span, e.g. {@code jeffrey.TraceSpan}
 * @param attributes               what the span attached to itself — the open JSON map from
 *                                 {@code AbstractTracedEvent.attributes}, whose keys are whatever
 *                                 the developer passed. Any traced event can carry one; in practice
 *                                 a hand-written span is what usually does
 * @param eventFields              what the event declared beyond the span shape, as a JSON object —
 *                                 a statement's {@code sql}, {@code params} and {@code rows}, an
 *                                 exchange's {@code uri} and {@code statusCode}. These are schema
 *                                 rather than attributes: each is a labelled field of its event
 *                                 type, known in advance, which is why they are kept apart from the
 *                                 map above. {@code null} for an event that declares nothing of its
 *                                 own, a hand-written span being the usual case
 */
public record TraceSpanRecord(
        long traceId,
        long spanId,
        Long parentSpanId,
        String name,
        String kind,
        String status,
        String errorType,
        long startMillisFromBeginning,
        long startEpochMicros,
        long durationNanos,
        long selfDurationNanos,
        long threadHash,
        String threadName,
        boolean isVirtual,
        String eventType,
        String attributes,
        String eventFields) {
}

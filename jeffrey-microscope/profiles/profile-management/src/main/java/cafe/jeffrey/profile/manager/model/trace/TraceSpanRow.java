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
 * @param startMillisFromBeginning span start relative to the recording's start
 * @param startEpochMicros         span start as absolute UTC epoch micros — the resolution the
 *                                 waterfall positions bars against, since a span is routinely
 *                                 shorter than a millisecond and sequential spans would otherwise
 *                                 share a start and draw as if they overlapped
 * @param durationNanos            total time the span covers
 * @param selfDurationNanos        that time minus what its children covered — where the span's own
 *                                 work actually went
 * @param criticalPathNanos        how much of the trace's end-to-end duration this span is
 *                                 personally responsible for: the stretches of its own window that
 *                                 no later-finishing child was holding open. Zero means the span was
 *                                 not on the critical path at all — it ran beside work that finished
 *                                 after it, so shortening it would not have shortened the trace
 * @param depth                    nesting level; 0 for a root
 * @param threadHash               thread the span was committed on, as a string for the same
 *                                 safe-integer reason as the ids
 * @param threadName               human-readable name of that thread, when known
 * @param isVirtual                whether that thread was a virtual thread; a span on one cannot be
 *                                 matched to samples, which the profiler attributes to the carrier
 * @param eventType                which event produced the span
 * @param attributes               the span's operation-specific detail as a JSON object string, or
 *                                 {@code null} when it recorded none — a statement's SQL and row
 *                                 count, an exchange's URI and status, a hand-written span's own
 *                                 attributes. Passed through as text rather than parsed: the keys
 *                                 differ per event type, and the UI renders them generically
 * @param synthesized              whether the derivation synthesized this span out of a blocking JDK
 *                                 event rather than reading it off an instrumented span event — what
 *                                 lets the waterfall style and filter promoted waits apart from
 *                                 recorded spans
 * @param ioOrigin                 why a promoted I/O span's operation happened, when the derivation
 *                                 could tell from its stack: {@code CLASS_LOADING} for a read the
 *                                 class loader asked for, {@code null} for everything else. A miss
 *                                 means nothing said it was class loading — including a recording
 *                                 that captured no stack — never that it was established not to be
 */
public record TraceSpanRow(
        String spanId,
        String parentSpanId,
        String name,
        String kind,
        String status,
        String errorType,
        long startMillisFromBeginning,
        long startEpochMicros,
        long durationNanos,
        long selfDurationNanos,
        long criticalPathNanos,
        int depth,
        String threadHash,
        String threadName,
        boolean isVirtual,
        String eventType,
        String attributes,
        String eventFields,
        boolean synthesized,
        String ioOrigin) {
}

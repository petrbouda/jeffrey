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
package cafe.jeffrey.provider.profile.api;

/**
 * One span reduced to what the tree and the critical path are built from: identity, parentage, its
 * window and its thread — no name, payload or attributes.
 * <p>
 * Read for every span of a trace, folded run members included, because the critical path has to
 * see them all: a write that covers part of its parent's window takes that stretch away from the
 * parent. Fetching the full rows to answer that is what made a million-span trace cost seven
 * hundred megabytes; this shape is four numbers a span.
 *
 * @param spanId           identifies the span within its trace
 * @param parentSpanId     the enclosing span as stored, or {@code null} for a root
 * @param startEpochMicros span start, UTC epoch micros
 * @param durationNanos    span duration
 * @param threadHash       identity hash of the span's thread, {@code 0} when unknown
 * @param runId            the folded run this span is a member of, or {@code null} when it is drawn
 *                         on its own
 */
public record TraceSpanShape(
        long spanId,
        Long parentSpanId,
        long startEpochMicros,
        long durationNanos,
        long threadHash,
        Long runId) {
}

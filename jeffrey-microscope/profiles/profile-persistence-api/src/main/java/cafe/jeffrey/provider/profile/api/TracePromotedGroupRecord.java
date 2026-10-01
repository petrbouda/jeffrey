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
 * The synthesized spans of one trace that share an event type and a payload, counted together.
 * <p>
 * Payloads are stored once and referenced, and a trace's synthesized spans carry few distinct ones
 * — a million writes to one upload buffer are a handful of payloads — so this is the cheap way to
 * total them without reading each: what the context summary sums per event type, and what the I/O
 * accounting groups per target.
 *
 * @param eventType   the spans' event type
 * @param eventFields the payload they share, as JSON, or {@code null} for spans that carry none
 * @param count       how many spans share it
 * @param totalNanos  their summed duration
 * @param maxNanos    the longest of them
 */
public record TracePromotedGroupRecord(
        String eventType,
        String eventFields,
        long count,
        long totalNanos,
        long maxNanos) {
}

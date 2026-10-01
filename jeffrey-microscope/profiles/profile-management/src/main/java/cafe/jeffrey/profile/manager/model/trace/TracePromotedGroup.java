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
 * A trace's synthesized spans that share an event type and a payload, totalled — how the I/O
 * accounting reads a million writes without reading each.
 *
 * @param eventType   the spans' event type
 * @param eventFields the payload they share, as JSON, or {@code null} when they carry none
 * @param count       how many spans share it
 * @param totalNanos  their summed duration
 * @param maxNanos    the longest of them
 */
public record TracePromotedGroup(
        String eventType,
        String eventFields,
        long count,
        long totalNanos,
        long maxNanos) {
}

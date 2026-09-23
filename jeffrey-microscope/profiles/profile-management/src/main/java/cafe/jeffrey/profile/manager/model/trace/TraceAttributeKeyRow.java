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
 * One key in the attribute catalog, as the key list renders it.
 *
 * @param source         where the key came from — {@code ATTRIBUTE}, {@code EVENT_FIELD} or
 *                       {@code SPAN_SHAPE}; the list groups by it, because a value a developer
 *                       attached and a field an event type declares do not mean the same thing
 * @param owner          the event type declaring it, for an event field; null otherwise
 * @param key            the key's name as the recording spells it
 * @param valueKind      {@code STRING}, {@code NUMBER} or {@code BOOLEAN} — what decides which
 *                       operators the search offers
 * @param distinctValues how many values it ever took
 * @param carrierCount   how many carriers hold it — spans, or notifications
 * @param traceCount     how many traces have at least one span carrying it
 * @param searchOnly     whether the key has too many values to break down: a facet list, a heatmap
 *                       axis and a difference ranking are all meaningless at this cardinality, and
 *                       saying so is better than rendering eighteen thousand rows of one trace each
 */
public record TraceAttributeKeyRow(
        String source,
        String owner,
        String key,
        String valueKind,
        long distinctValues,
        long carrierCount,
        long traceCount,
        boolean searchOnly) {
}

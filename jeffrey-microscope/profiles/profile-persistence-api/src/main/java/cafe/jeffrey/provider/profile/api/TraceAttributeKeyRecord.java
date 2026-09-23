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
 * One key in the catalog, with everything the key list needs and nothing that requires touching the
 * rows themselves.
 *
 * @param id             what identifies the key
 * @param valueKind      what its values turned out to be
 * @param distinctValues how many values it ever took — the number every guard in this feature keys
 *                       off: above the cap a key is search-only, because a facet list, a heatmap
 *                       axis and a difference ranking are all meaningless at eighteen thousand rows
 * @param carrierCount   how many carriers hold it — spans for a span source, notifications for a
 *                       notification one
 * @param traceCount     how many traces have at least one carrier holding it
 */
public record TraceAttributeKeyRecord(
        TraceAttributeKeyId id,
        TraceAttributeValueKind valueKind,
        long distinctValues,
        long carrierCount,
        long traceCount) {
}

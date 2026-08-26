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

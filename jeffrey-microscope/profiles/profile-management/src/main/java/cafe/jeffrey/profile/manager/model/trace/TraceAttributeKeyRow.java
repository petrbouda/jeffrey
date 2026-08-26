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

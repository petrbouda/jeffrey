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
 * One row of the attribute picker's first step: an event type that produced spans.
 *
 * @param eventType      the event type, as recorded — {@code jeffrey.HttpServerExchange}
 * @param spanCount      spans of this type
 * @param traceCount     traces holding at least one
 * @param errorSpans     how many of those spans ended in {@code ERROR}
 * @param attributeCount keys its spans carried, so the row can say how much the second step holds
 * @param breakableCount of those, how many have few enough values to break down — the rest are
 *                       search-only, and a row promising twelve attributes that opens onto three
 *                       usable ones is a row that lied
 */
public record TraceSpanTypeRow(
        String eventType,
        long spanCount,
        long traceCount,
        long errorSpans,
        int attributeCount,
        int breakableCount) {
}

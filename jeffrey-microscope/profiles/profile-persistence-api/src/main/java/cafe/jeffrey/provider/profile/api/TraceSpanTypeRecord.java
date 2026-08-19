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
 * One event type that produced spans, summarised.
 * <p>
 * The first step of the attribute picker. A recording carries hundreds of JFR event types and this
 * is only the handful that opened spans, which is what makes picking one a reasonable first move
 * rather than a search through a catalog.
 *
 * @param eventType      the event type that produced the spans, e.g. {@code jeffrey.HttpServerExchange}
 * @param spanCount      spans of this type in the profile
 * @param traceCount     traces containing at least one span of this type
 * @param errorSpans     spans of this type that ended in {@code ERROR}
 * @param attributeCount how many keys its spans carried, across all three sources — what the second
 *                       step will list, counted here so the first step can say how much is behind
 *                       each row before it is opened
 * @param breakableCount of those, how many are narrow enough to break down. A row promising twelve
 *                       attributes that opens onto three usable ones is a row that lied, so both
 *                       numbers travel
 */
public record TraceSpanTypeRecord(
        String eventType,
        long spanCount,
        long traceCount,
        long errorSpans,
        int attributeCount,
        int breakableCount) {
}

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
 * One event type whose carriers can be searched, summarised.
 * <p>
 * The first step of the attribute picker. A recording carries hundreds of JFR event types and this
 * is only the handful that opened spans or raised notifications, which is what makes picking one a
 * reasonable first move rather than a search through a catalog.
 *
 * @param eventType      the event type that produced them, e.g. {@code jeffrey.HttpServerExchange}
 * @param carrier        whether this type produced spans or notifications, so the picker can say
 *                       which it is rather than calling a notification a span
 * @param carrierCount      carriers of this type in the profile — spans, or notifications
 * @param traceCount     traces containing at least one carrier of this type
 * @param errorCarriers     spans of this type that ended in {@code ERROR}; always {@code 0} for a
 *                       notification type, because a severity is not an outcome — a CRITICAL
 *                       notification says something went wrong somewhere, not that this event failed
 * @param attributeCount how many keys its carriers had, across every source that applies — what the
 *                       second step will list, counted here so the first step can say how much is
 *                       behind each row before it is opened
 * @param breakableCount of those, how many are narrow enough to break down. A row promising twelve
 *                       attributes that opens onto three usable ones is a row that lied, so both
 *                       numbers travel
 */
public record TraceEventTypeRecord(
        String eventType,
        TraceAttributeCarrier carrier,
        long carrierCount,
        long traceCount,
        long errorCarriers,
        int attributeCount,
        int breakableCount) {
}

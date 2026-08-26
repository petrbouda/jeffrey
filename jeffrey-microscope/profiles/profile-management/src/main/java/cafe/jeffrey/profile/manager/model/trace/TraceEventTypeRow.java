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

import cafe.jeffrey.provider.profile.api.TraceAttributeCarrier;

/**
 * One row of the attribute picker's first step: an event type whose carriers can be searched.
 *
 * @param eventType      the event type, as recorded — {@code jeffrey.HttpServerExchange}
 * @param carrier        whether this type produced spans or notifications, so the step can say which
 *                       it is listing rather than calling a notification a span
 * @param carrierCount      carriers of this type — spans, or notifications
 * @param traceCount     traces holding at least one
 * @param errorCarriers     how many of those spans ended in {@code ERROR}; always {@code 0} for a
 *                       notification type, whose severity says something went wrong somewhere and
 *                       not that this event failed
 * @param attributeCount keys its carriers held, so the row can say how much the second step holds
 * @param breakableCount of those, how many have few enough values to break down — the rest are
 *                       search-only, and a row promising twelve attributes that opens onto three
 *                       usable ones is a row that lied
 */
public record TraceEventTypeRow(
        String eventType,
        TraceAttributeCarrier carrier,
        long carrierCount,
        long traceCount,
        long errorCarriers,
        int attributeCount,
        int breakableCount) {
}

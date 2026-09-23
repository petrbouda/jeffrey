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

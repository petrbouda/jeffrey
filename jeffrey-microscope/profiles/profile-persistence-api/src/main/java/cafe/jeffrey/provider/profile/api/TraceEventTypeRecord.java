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

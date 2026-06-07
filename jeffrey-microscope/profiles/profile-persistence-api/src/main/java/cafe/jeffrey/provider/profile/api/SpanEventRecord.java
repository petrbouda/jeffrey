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
 * A JFR event that occurred on a span's thread within the span's time window — used to drill from a
 * span into "what else was happening" (the {@code @Contextual} correlation).
 *
 * @param eventType        JFR event type code (e.g. {@code jdk.ExecutionSample})
 * @param startEpochMillis absolute UTC epoch-millis start
 * @param durationNanos    duration in nanoseconds (0 for instant events)
 * @param fields           the event's JSON fields as a string (may be {@code null})
 */
public record SpanEventRecord(
        String eventType,
        long startEpochMillis,
        long durationNanos,
        String fields) {
}

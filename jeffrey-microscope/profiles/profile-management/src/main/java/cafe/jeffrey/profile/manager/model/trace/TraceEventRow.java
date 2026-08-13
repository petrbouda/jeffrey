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
 * One JFR event that occurred inside a span — the "what was the JVM doing in here" breakdown.
 *
 * @param eventType        the JFR event type, e.g. {@code jdk.ExecutionSample}
 * @param startEpochMillis when it occurred, absolute UTC epoch millis
 * @param durationNanos    its duration, or 0 for an instantaneous event
 * @param fields           the event's own fields, as a JSON object string
 */
public record TraceEventRow(
        String eventType,
        long startEpochMillis,
        long durationNanos,
        String fields) {
}

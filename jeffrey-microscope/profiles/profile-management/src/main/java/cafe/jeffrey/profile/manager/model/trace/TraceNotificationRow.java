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
 * One thing the application said while a trace was running, as the UI reads it.
 * <p>
 * Ids are hex strings for the same reason a span's are: they are 64-bit values that exceed
 * JavaScript's safe-integer range, and a numeric type would round them silently.
 *
 * @param spanId                   the span it was raised in, or {@code null} when there is no bar
 *                                 to draw it against — either no span was open, or the one it named
 *                                 is not in this profile
 * @param notificationId           identifies it within the trace, so the rail can point the detail
 *                                 panel at one entry
 * @param startMillisFromBeginning when it fired, relative to the recording's start
 * @param startEpochMicros         when it fired, in the same microseconds a span start carries, so
 *                                 the rail and the bars share one axis
 * @param severity                 {@code CRITICAL}, {@code HIGH}, {@code MEDIUM} or {@code LOW}
 */
public record TraceNotificationRow(
        String spanId,
        String notificationId,
        long startMillisFromBeginning,
        long startEpochMicros,
        String type,
        String title,
        String message,
        String severity,
        String category,
        String source,
        String threadHash) {
}

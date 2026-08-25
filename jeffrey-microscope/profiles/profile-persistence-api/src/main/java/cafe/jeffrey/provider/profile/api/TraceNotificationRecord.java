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
 * One thing the application said while a trace was running.
 * <p>
 * A notification is an instant, not a span: it has no duration, and it does not appear in the tree.
 * It carries the ids of the span that was open when it fired, stamped onto the event at commit
 * time, so this is what the recording said rather than what a thread-and-window guess inferred.
 *
 * @param traceId                  the trace it belongs to
 * @param spanId                   the span that was open when it fired, or {@code null} when there
 *                                 was none — including when the span it named is not in this
 *                                 profile. Both cases mean the same thing to a reader: it belongs
 *                                 to the trace, but there is no bar to draw it against
 * @param notificationId           identifies this notification within the trace, so two of the same
 *                                 type on the same span in the same microsecond stay two things
 * @param startMillisFromBeginning when it fired, in milliseconds relative to the recording's start
 * @param startEpochMicros         when it fired, as absolute UTC epoch micros — the same resolution
 *                                 a span start is kept at, so the two can be compared without
 *                                 either being rounded to the other
 * @param type                     stable identifier for this kind of notification, e.g.
 *                                 {@code CONNECTION_POOL_EXHAUSTED}
 * @param title                    short summary
 * @param message                  the detail
 * @param severity                 name of a {@code Severity} constant — the whole of "how serious
 *                                 is this"
 * @param category                 e.g. {@code PERFORMANCE}, {@code AVAILABILITY}
 * @param source                   the component that raised it
 * @param threadHash               identity hash of the thread it was committed on
 */
public record TraceNotificationRecord(
        long traceId,
        Long spanId,
        long notificationId,
        long startMillisFromBeginning,
        long startEpochMicros,
        String type,
        String title,
        String message,
        String severity,
        String category,
        String source,
        long threadHash) {
}

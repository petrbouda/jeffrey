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
 * @param attributes               the open JSON map it attached to itself, verbatim, or {@code null}
 *                                 when it attached none — the same encoding a span's attributes use,
 *                                 so one renderer draws both
 */
public record TraceNotificationRow(
        String spanId,
        String notificationId,
        long startMillisFromBeginning,
        long startEpochMicros,
        String type,
        String message,
        String severity,
        String category,
        String source,
        String attributes,
        String threadHash) {
}

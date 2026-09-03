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

import java.util.List;

/**
 * Every notification of one kind across many traces, as a reader sees it: what the application kept
 * saying, how often, in how many requests, and a few requests to open to see it in context.
 * <p>
 * Trace ids are hex strings for the same reason a span's are: they are 64-bit values that exceed
 * JavaScript's safe-integer range, and the same hex is what {@code traces_traceExport} takes.
 *
 * @param type                     what kind of thing happened, as the emitter named it
 * @param severity                 {@code CRITICAL}, {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param category                 what area it concerns; may be null
 * @param source                   the component that raised it; may be null
 * @param message                  the sentence every occurrence of the kind carries; may be null
 * @param count                    how many times it was raised
 * @param traceCount               how many distinct traces raised it at least once
 * @param firstMillisFromBeginning when the earliest fired, relative to the recording's start
 * @param lastMillisFromBeginning  when the latest fired, relative to the recording's start
 * @param exemplarTraceIds         a few traces that raised it, the slowest first
 */
public record TraceNotificationGroupRow(
        String type,
        String severity,
        String category,
        String source,
        String message,
        long count,
        long traceCount,
        long firstMillisFromBeginning,
        long lastMillisFromBeginning,
        List<String> exemplarTraceIds) {
}

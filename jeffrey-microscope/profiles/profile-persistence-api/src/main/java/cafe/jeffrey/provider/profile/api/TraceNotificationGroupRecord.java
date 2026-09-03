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

import java.util.List;

/**
 * Every notification of one kind across the traces a query selected, collapsed into one row.
 * <p>
 * A kind is the tuple {@code (type, severity, category, source, message)}: Jeffrey's own emitter keeps
 * the message constant per type, but a third-party one may not, and a type that carried two sentences
 * is two findings rather than one with a sentence picked at random.
 *
 * @param count                    how many notifications of this kind there were
 * @param traceCount               how many distinct traces raised at least one
 * @param firstMillisFromBeginning when the earliest fired, relative to the recording's start
 * @param lastMillisFromBeginning  when the latest fired, relative to the recording's start
 * @param exemplarTraceIds         a few traces that raised it, the slowest first, as candidates for a
 *                                 trace export
 */
public record TraceNotificationGroupRecord(
        String type,
        String severity,
        String category,
        String source,
        String message,
        long count,
        long traceCount,
        long firstMillisFromBeginning,
        long lastMillisFromBeginning,
        List<Long> exemplarTraceIds) {

    public TraceNotificationGroupRecord {
        exemplarTraceIds = List.copyOf(exemplarTraceIds);
    }
}

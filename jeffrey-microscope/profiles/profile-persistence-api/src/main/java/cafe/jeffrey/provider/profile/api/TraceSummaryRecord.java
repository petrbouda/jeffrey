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
 * One trace, summarised for the trace list — enough to decide which trace is worth opening,
 * without reading its spans.
 *
 * @param traceId                    identifies the trace; rendered as 16-char hex in the UI
 * @param rootName                   operation name of the trace's root span
 * @param rootKind                   {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param rootEventType              the event type that opened the trace, e.g.
 *                                   {@code jeffrey.HttpServerExchange} — which instrumentation it
 *                                   came from, something the name alone does not say
 * @param startMillisFromBeginning   trace start, in milliseconds relative to the recording's start
 * @param startEpochMillis           trace start as an absolute UTC epoch-millis timestamp
 * @param durationNanos              wall time from the first span's start to the last span's end
 * @param spanCount                  how many spans the trace contains
 * @param errorCount                 how many of them ended in an error
 * @param hasPlatformSpan            whether any of its spans ran on a platform thread, which is what
 *                                   decides whether a sample can ever be attributed to this trace
 */
public record TraceSummaryRecord(
        long traceId,
        String rootName,
        String rootKind,
        String rootEventType,
        long startMillisFromBeginning,
        long startEpochMillis,
        long durationNanos,
        int spanCount,
        int errorCount,
        boolean hasPlatformSpan) {
}

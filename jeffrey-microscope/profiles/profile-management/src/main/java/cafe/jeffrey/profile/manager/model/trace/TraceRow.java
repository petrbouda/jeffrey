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
 * One trace in the trace list.
 * <p>
 * {@code traceId} is a hex string, not a number: a 64-bit id exceeds JavaScript's safe integer
 * range, so sending it as JSON number would silently round it in the browser — the same reason
 * {@code threadHash} already crosses the wire as a string.
 */
public record TraceRow(
        String traceId,
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

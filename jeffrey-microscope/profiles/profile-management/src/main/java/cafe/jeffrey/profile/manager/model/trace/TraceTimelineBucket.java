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
 * One slice of the recording, with what the traces starting inside it did — what the trace list
 * plots to show when the profile was busy and when it was slow.
 *
 * @param fromMillisFromBeginning where the slice starts, relative to the recording's start, so it
 *                                lines up with every other timeline in the profile without
 *                                converting
 * @param count                   how many traces started inside it
 * @param errorCount              how many of those failed
 * @param maxDurationNanos        the slowest of them
 */
public record TraceTimelineBucket(
        long fromMillisFromBeginning,
        long count,
        long errorCount,
        long maxDurationNanos) {
}

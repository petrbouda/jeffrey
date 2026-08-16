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
 * One stretch during which a thread was not running its own code — parked, blocked on a monitor,
 * sleeping, in socket or file I/O — overlapping a timeline window.
 * <p>
 * The thread-scoped counterpart of {@link TracePauseRecord}: a pause stops every thread at once and
 * belongs to the window, while one of these belongs to exactly the thread it was recorded on. It is
 * what lets a timeline explain a blank gap in a track — a gap with a PARKED period under it and a
 * gap with nothing under it are different findings.
 *
 * @param threadHash      the thread the wait was recorded on, matching
 *                        {@link TraceSpanRecord#threadHash()}
 * @param category        a {@link TraceContextCategory.Scope#THREAD}-scoped category
 * @param fromEpochMicros when the wait began, absolute, in the same units a span's start carries
 * @param toEpochMicros   when it ended
 */
public record TraceThreadStateRecord(
        long threadHash,
        TraceContextCategory category,
        long fromEpochMicros,
        long toEpochMicros) {

    /** How long the wait lasted, in nanoseconds, to match every other duration in the API. */
    public long durationNanos() {
        return (toEpochMicros - fromEpochMicros) * 1_000L;
    }
}

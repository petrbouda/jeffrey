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
 * One stretch during which a track's thread was waiting rather than running — the timeline's
 * underlay. What turns a blank gap from an unknown into a finding: a gap over a PARKED period and a
 * gap over nothing are different diagnoses.
 *
 * @param category         a thread-scoped {@code TraceContextCategory} name — the same vocabulary
 *                         the waterfall's stripes and the why-slow panel speak, so one wait reads
 *                         as one colour everywhere
 * @param startEpochMicros when the wait began, absolute, matching {@link TimelineSpan}
 * @param durationNanos    how long it lasted
 */
public record TimelineStatePeriod(String category, long startEpochMicros, long durationNanos) {
}

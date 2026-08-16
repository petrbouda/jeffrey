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
 * One line of a "where did the time go" breakdown.
 *
 * @param category    a {@code TraceContextCategory} name, or {@link #OWN_WORK} for the remainder
 * @param totalNanos  how much time went to it
 * @param occurrences how many events that was; {@code 0} for the residual, which is not an event
 */
public record TraceContextSlice(String category, long totalNanos, long occurrences) {

    /**
     * The residual: time not attributable to any category, which is the code running.
     * <p>
     * Reported as a slice of its own rather than left as the gap between a total and a list of
     * parts. A breakdown that only names the waiting invites the reader to add it up and assume the
     * rest is unexplained; naming the remainder is what makes the panel an explanation instead of a
     * list of complaints.
     */
    public static final String OWN_WORK = "OWN_WORK";
}

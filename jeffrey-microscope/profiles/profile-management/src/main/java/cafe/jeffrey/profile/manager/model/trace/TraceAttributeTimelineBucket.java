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
 * One slice of the recording, for the density strip above the search results: how many traces the
 * search matched, against how many there were.
 * <p>
 * Both numbers travel together because the strip means nothing without the second: a burst of
 * matches is only a burst if the profile was not equally busy everywhere.
 */
public record TraceAttributeTimelineBucket(
        long fromMillisFromBeginning,
        long matched,
        long total) {
}

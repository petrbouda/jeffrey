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
package cafe.jeffrey.profile.heapdump.model;

/**
 * Aggregate hint for an opaque histogram row (e.g. {@code byte[]}): the class
 * name of a referrer plus the percentage of the row's total bytes attributable
 * to that referrer class.
 *
 * @param className fully qualified class name of the referrer
 * @param percent   share of the histogram row's total bytes attributed to this
 *                  class, expressed as 0-100
 */
public record ReferrerSummary(String className, double percent) {
}

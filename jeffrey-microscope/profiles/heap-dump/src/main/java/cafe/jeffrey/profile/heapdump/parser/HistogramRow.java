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
package cafe.jeffrey.profile.heapdump.parser;

/**
 * One row of a class histogram: per-class instance count and total shallow size.
 *
 * Produced by SQL aggregation against the {@code instance} table — the canonical
 * cheap query that motivates the SQL-pushdown design of the new index.
 *
 * {@code className} is null when an instance points to a class id that has no
 * {@code class} row (e.g. corrupt reference, primitive array which has no class
 * entry of its own).
 */
public record HistogramRow(
        Long classId,
        String className,
        long instanceCount,
        long totalShallowSize) {
}

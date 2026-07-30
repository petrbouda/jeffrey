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

package cafe.jeffrey.profile.manager.model.io;

/**
 * How an endpoint's I/O is measured: by the volume it moved, or by how often it was called.
 * <p>
 * The distinction matters because it decides which endpoints are even worth showing. The heaviest
 * peers by bytes and the busiest peers by call count are frequently disjoint sets — a cache or a
 * message broker can dominate the call count while moving a rounding error's worth of bytes — so
 * the metric has to reach the ranking, not just the labels.
 */
public enum IoMetric {
    BYTES,
    COUNT
}

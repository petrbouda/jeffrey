/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.heapdump.model;

/**
 * Distribution of collection fill ratios across buckets.
 *
 * @param empty  count of collections with 0% fill
 * @param low    count of collections with 1-25% fill
 * @param medium count of collections with 26-50% fill
 * @param high   count of collections with 51-75% fill
 * @param full   count of collections with 76-100% fill
 */
public record FillDistribution(
        int empty,
        int low,
        int medium,
        int high,
        int full
) {
}

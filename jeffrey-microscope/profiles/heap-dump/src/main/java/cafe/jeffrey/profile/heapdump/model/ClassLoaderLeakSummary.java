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
 * Aggregate of leak suspects attributed to a single class loader.
 *
 * @param classLoaderId         object ID of the class loader (0 for bootstrap)
 * @param classLoaderClassName  class name of the loader
 * @param totalRetainedSize     sum of {@code retainedSize} across all suspects under this loader
 * @param suspectCount          number of leak suspects rooted in classes loaded by this loader
 */
public record ClassLoaderLeakSummary(
        long classLoaderId,
        String classLoaderClassName,
        long totalRetainedSize,
        int suspectCount
) {
}

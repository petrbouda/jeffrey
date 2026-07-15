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

package cafe.jeffrey.profile.manager.model.nativememory;

/**
 * Headline native-memory metrics for a profile.
 *
 * @param peakRssBytes              highest resident set size observed during the recording
 * @param finalRssBytes             resident set size at the last sample
 * @param rssGrowthBytes            last minus first RSS sample (positive = process grew)
 * @param directBufferCount         direct (off-heap) NIO buffer count at the last sample
 * @param directBufferMemoryUsed    direct buffer memory used at the last sample, in bytes
 * @param directBufferTotalCapacity direct buffer total capacity at the last sample, in bytes
 * @param nativeLibraryCount        number of distinct loaded native libraries
 */
public record NativeMemoryOverview(
        long peakRssBytes,
        long finalRssBytes,
        long rssGrowthBytes,
        long directBufferCount,
        long directBufferMemoryUsed,
        long directBufferTotalCapacity,
        int nativeLibraryCount) {
}

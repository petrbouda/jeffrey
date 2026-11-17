/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

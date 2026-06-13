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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Native (off-heap) memory insight for a single profile, built from {@code jdk.ResidentSetSize},
 * {@code jdk.DirectBufferStatistics}, {@code jdk.NativeLibrary} and {@code jdk.GCHeapSummary}
 * events. The headline analysis is the RSS-vs-heap gap — native memory growth that heap-centric
 * views cannot explain (the "container OOMKilled but the heap was fine" investigation).
 */
public interface NativeMemoryManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, NativeMemoryManager> {
    }

    /**
     * Headline metrics: peak/final/growth RSS, latest direct-buffer stats, native-library count.
     */
    NativeMemoryOverview overview();

    /**
     * Resident set size vs heap used over the recording.
     */
    TimeseriesData rssTimeline();

    /**
     * Direct (off-heap NIO) buffer memory and count over the recording.
     */
    TimeseriesData directBufferTimeline();

    /**
     * Loaded native libraries, ordered by descending mapped size.
     */
    List<NativeLibraryInfo> nativeLibraries();
}

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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryActivityData;
import cafe.jeffrey.profile.manager.model.nativememory.NativeLibraryInfo;
import cafe.jeffrey.profile.manager.model.nativememory.NativeMemoryOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;
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

    /**
     * Native dynamic-library load/unload activity ({@code jdk.NativeLibraryLoad} /
     * {@code jdk.NativeLibraryUnload}): load durations, failed loads, and load/unload timelines.
     */
    NativeLibraryActivityData nativeLibraryActivity();
}

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

import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * JVM Native Memory Tracking (NMT) insight for a single profile, from {@code jdk.NativeMemoryUsage}
 * (per-category reserved/committed) and {@code jdk.NativeMemoryUsageTotal} (totals). NMT is only
 * recorded when the JVM is launched with {@code -XX:NativeMemoryTracking}, so all results are empty
 * unless the events are present — consumers gate on {@link NmtOverview#hasNmtData()}.
 */
public interface NativeMemoryTrackingManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, NativeMemoryTrackingManager> {
    }

    /**
     * Headline metrics plus the {@code hasNmtData} flag that drives the disabled-events notice.
     */
    NmtOverview overview();

    /**
     * Per-category reserved/committed with growth, ordered by committed bytes descending.
     */
    List<NmtCategory> categories();

    /**
     * Committed bytes per category over time (top categories + "Other") for the stacked-area chart.
     */
    TimeseriesData categoryTimeline();

    /**
     * Total reserved vs committed native memory over time.
     */
    TimeseriesData totalTimeline();

    /**
     * Resident set size vs total NMT committed over time — the gap approximates untracked memory.
     */
    TimeseriesData rssVsTrackedTimeline();
}

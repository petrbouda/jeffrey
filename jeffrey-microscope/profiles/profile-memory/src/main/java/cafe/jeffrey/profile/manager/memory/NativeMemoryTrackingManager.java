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

package cafe.jeffrey.profile.manager.memory;

import cafe.jeffrey.profile.manager.model.nmt.NmtCategory;
import cafe.jeffrey.profile.manager.model.nmt.NmtOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
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

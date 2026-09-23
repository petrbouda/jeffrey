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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import cafe.jeffrey.profile.manager.model.blocking.SleepStat;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Blocking insight for a single profile: lock contention ({@code jdk.JavaMonitorEnter}), monitor
 * waits ({@code jdk.JavaMonitorWait}), thread parks ({@code jdk.ThreadPark}), thread sleeps
 * ({@code jdk.ThreadSleep}) and virtual-thread pinning ({@code jdk.VirtualThreadPinned}). Most of
 * these events are threshold- or config-gated, so consumers must handle empty results.
 */
public interface BlockingManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, BlockingManager> {
    }

    /**
     * Headline blocking metrics (locks, waits, parks, sleeps, pinning) for the Blocking page.
     */
    BlockingOverview overview();

    /**
     * Occurrences per second of every blocking type (lock contention, waits, parks, sleeps, pinning),
     * one series per type, for the combined Blocking Operations timeline.
     */
    TimeseriesData blockingTimeline();

    /**
     * Contended monitor enters grouped by monitor class, ordered by total blocked time.
     */
    List<ContentionStat> monitorContention();

    /**
     * Thread parks grouped by blocker class, ordered by total parked time.
     */
    List<ContentionStat> threadParks();

    /**
     * Longest virtual-thread pinning incidents.
     */
    List<PinnedThreadEntry> pinnedThreads();

    /**
     * {@code Object.wait()} events grouped by monitor class, ordered by total wait time.
     */
    List<MonitorWaitStat> monitorWaits();

    /**
     * {@code Thread.sleep()} events grouped by thread, ordered by total slept time.
     */
    List<SleepStat> sleeps();
}

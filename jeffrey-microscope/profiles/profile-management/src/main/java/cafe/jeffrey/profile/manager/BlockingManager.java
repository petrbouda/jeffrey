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

import cafe.jeffrey.profile.manager.model.blocking.BlockingOverview;
import cafe.jeffrey.profile.manager.model.blocking.ContentionStat;
import cafe.jeffrey.profile.manager.model.blocking.MonitorWaitStat;
import cafe.jeffrey.profile.manager.model.blocking.PinnedThreadEntry;
import cafe.jeffrey.profile.manager.model.blocking.SleepStat;
import cafe.jeffrey.shared.common.model.ProfileInfo;
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

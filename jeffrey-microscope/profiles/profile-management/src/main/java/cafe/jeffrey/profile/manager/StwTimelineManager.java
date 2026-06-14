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

import cafe.jeffrey.profile.manager.model.stw.StwEvent;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * The Unified Stop-The-World view for a single profile: every JVM pause source (GC pauses, safepoint
 * VM operations, time-to-safepoint, monitor contention, thread parking, virtual-thread pinning) merged
 * onto one time axis, plus the aggregate app-stop budget.
 */
public interface StwTimelineManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, StwTimelineManager> {
    }

    /**
     * Every pause at least {@code minDurationNanos} long, in chronological order — powers the swimlane
     * timeline, inventory, leaderboard and cause attribution.
     */
    List<StwEvent> timeline(long minDurationNanos);

    /**
     * Frozen time per second as two series — "Global STW" (whole-JVM) and "Local Stalls" (per-thread).
     * Sums all pauses regardless of duration, so it is complete even when {@link #timeline} is thresholded.
     */
    TimeseriesData budget();
}

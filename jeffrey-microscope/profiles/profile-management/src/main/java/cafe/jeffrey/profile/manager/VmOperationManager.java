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

import cafe.jeffrey.profile.manager.model.vmoperation.VmOperationStat;
import cafe.jeffrey.profile.manager.model.vmoperation.VmOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * VM-operation insight for a single profile: JVM-internal stop-the-world activity beyond GC from
 * {@code jdk.ExecuteVMOperation} plus time-to-safepoint from
 * {@code jdk.SafepointStateSynchronization}. Most of these events are threshold- or config-gated, so
 * consumers must handle empty results.
 */
public interface VmOperationManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, VmOperationManager> {
    }

    /**
     * Headline VM-operation / safepoint metrics for the VM Operations page.
     */
    VmOverview overview();

    /**
     * VM operations grouped by name, ordered by descending total duration.
     */
    List<VmOperationStat> vmOperations();

    /**
     * Safepoint pause time per second across the recording, in nanoseconds.
     */
    TimeseriesData pausesTimeline();

    /**
     * Time-to-safepoint per second, in nanoseconds (off by default — usually empty).
     */
    TimeseriesData timeToSafepointTimeline();
}

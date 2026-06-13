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

import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Allocation insight for a single profile, from {@code jdk.ObjectAllocationInNewTLAB} /
 * {@code OutsideTLAB} (preferred) or {@code jdk.ObjectAllocationSample}. Complements the Heap Memory
 * allocation timeseries with the **top allocated types** and the **in-TLAB vs outside-TLAB split**.
 */
public interface AllocationManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, AllocationManager> {
    }

    /**
     * Headline metrics: total allocated bytes, TLAB split, distinct types, dominant type.
     */
    AllocationOverview overview();

    /**
     * Allocated-bytes-per-second across the recording.
     */
    TimeseriesData timeline();

    /**
     * Top allocated classes by bytes.
     */
    List<AllocatedType> topTypes();
}

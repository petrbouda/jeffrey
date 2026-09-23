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

import cafe.jeffrey.profile.manager.model.allocation.AllocatedType;
import cafe.jeffrey.profile.manager.model.allocation.AllocationOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;
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

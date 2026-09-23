/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.custom;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.manager.custom.model.method.CumulationMode;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTimingData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingCumulatedData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingOverviewData;
import cafe.jeffrey.profile.manager.custom.model.method.MethodTracingSlowestData;

import java.util.function.Function;

public interface MethodTracingManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, MethodTracingManager> {
    }

    MethodTracingOverviewData overview();

    MethodTracingSlowestData slowest();

    MethodTracingCumulatedData cumulated(CumulationMode mode);

    /**
     * What {@code jdk.MethodTiming} counted, one row per method.
     * <p>
     * The exact, complete companion to the sampled surfaces beside it: it can watch a method called
     * millions of times for a fixed price, and in exchange keeps no stack, no thread and no
     * individual invocation.
     */
    MethodTimingData methodTiming();
}

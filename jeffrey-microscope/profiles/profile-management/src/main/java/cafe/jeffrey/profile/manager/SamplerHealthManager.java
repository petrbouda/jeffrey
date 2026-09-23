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

import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.microscope.model.ProfileInfo;

import java.util.function.Function;

/**
 * How trustworthy the sampled data in a profile is, as the samplers themselves reported it.
 * <p>
 * Distinct from the analysis managers: nothing here describes the application, it describes the
 * recording. A graph drawn from samples the profiler admits it dropped is still worth reading —
 * but only if the reader is told.
 */
public interface SamplerHealthManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, SamplerHealthManager> {
    }

    /**
     * Captured versus dropped {@code jdk.CPUTimeSample} events, as reported by
     * {@code jdk.CPUTimeSamplesLost}.
     */
    CpuTimeSampleLoss cpuTimeSampleLoss();
}

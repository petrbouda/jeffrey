/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.common.event.JITCompilationStats;
import cafe.jeffrey.profile.common.event.JITLongCompilation;
import cafe.jeffrey.profile.manager.model.jit.CodeCacheData;
import cafe.jeffrey.timeseries.SingleSerie;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

public interface JITCompilationManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, JITCompilationManager> {
    }

    JITCompilationStats statistics();

    List<JITLongCompilation> compilations(int limit);

    SingleSerie timeseries();

    /**
     * Compiler queue backlog over the recording (C1 + C2 series), from
     * {@code jdk.CompilerQueueUtilization}; empty series when the event is absent.
     */
    TimeseriesData compilerQueueTimeline();

    /**
     * Code-cache occupancy per code heap ({@code jdk.CodeCacheStatistics}, latest snapshot)
     * plus the number of {@code jdk.CodeCacheFull} incidents.
     */
    CodeCacheData codeCache();
}

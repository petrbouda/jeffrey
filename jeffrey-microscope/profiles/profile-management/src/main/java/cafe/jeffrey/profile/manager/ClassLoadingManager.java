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

import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Class-loading insight for a single profile, built from the JFR class-loading event family
 * ({@code jdk.ClassLoadingStatistics}, {@code jdk.ClassLoaderStatistics}, {@code jdk.ClassLoad},
 * {@code jdk.ClassRedefinition}, {@code jdk.RetransformClasses}).
 */
public interface ClassLoadingManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ClassLoadingManager> {
    }

    /**
     * Headline metrics: currently-loaded/loaded/unloaded counts, loader count, metaspace and hidden
     * classes, plus flags indicating whether the optional per-class events are present.
     */
    ClassLoadingOverview overview();

    /**
     * Loaded/unloaded class timeline across the recording, from {@code jdk.ClassLoadingStatistics}.
     */
    TimeseriesData timeline();

    /**
     * Latest per-class-loader statistics snapshot, ordered by descending metaspace footprint.
     */
    List<ClassLoaderStat> classLoaders();

    /**
     * Slowest per-class loads from {@code jdk.ClassLoad}; empty when that event is absent (it is
     * disabled by default because of its overhead).
     */
    ClassLoadActivity classLoadActivity();

    /**
     * Bytecode-instrumentation activity: retransformation batches and the class redefinitions within.
     */
    RedefinitionData redefinitions();
}

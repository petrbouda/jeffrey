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

import cafe.jeffrey.profile.manager.model.classloading.ClassLoadActivity;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoaderStat;
import cafe.jeffrey.profile.manager.model.classloading.ClassLoadingOverview;
import cafe.jeffrey.profile.manager.model.classloading.RedefinitionData;
import cafe.jeffrey.shared.common.model.ProfileInfo;
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

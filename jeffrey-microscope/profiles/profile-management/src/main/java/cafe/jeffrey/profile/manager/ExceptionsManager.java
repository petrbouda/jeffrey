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

import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.timeseries.TimeseriesData;

import java.util.List;
import java.util.function.Function;

/**
 * Exception insight for a single profile, built from the JFR exception event family
 * ({@code jdk.ExceptionStatistics}, {@code jdk.JavaExceptionThrow}, {@code jdk.JavaErrorThrow}).
 */
public interface ExceptionsManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ExceptionsManager> {
    }

    /**
     * Headline metrics: cumulative throwables, sampled throw/error counts, distinct types, plus
     * flags indicating whether the per-throw events are present in the recording.
     */
    ExceptionsOverview overview();

    /**
     * Exceptions-per-second timeline derived from the cumulative {@code jdk.ExceptionStatistics}
     * gauge. Always available — the statistics event is part of the default JFR configuration.
     */
    TimeseriesData timeline();

    /**
     * Sampled throws grouped by thrown class, ordered by descending count; empty when neither
     * {@code jdk.JavaExceptionThrow} nor {@code jdk.JavaErrorThrow} is present in the recording.
     */
    List<ExceptionTypeStat> topTypes();
}

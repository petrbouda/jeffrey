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

import cafe.jeffrey.profile.manager.model.exceptions.ExceptionTypeStat;
import cafe.jeffrey.profile.manager.model.exceptions.ExceptionsOverview;
import cafe.jeffrey.shared.common.model.ProfileInfo;
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

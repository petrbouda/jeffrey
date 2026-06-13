/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import cafe.jeffrey.shared.common.model.ProfileInfo;
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

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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.shared.common.model.ThreadInfo;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.profile.common.config.GraphParameters;
import pbouda.jeffrey.shared.common.model.ProfileInfo;
import pbouda.jeffrey.timeseries.TimeseriesData;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface TimeseriesManager {

    record Generate(
            Type eventType,
            GraphParameters graphParameters,
            ThreadInfo threadInfo) {
    }

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, TimeseriesManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, TimeseriesManager> {
    }

    TimeseriesData timeseries(Generate generate);
}

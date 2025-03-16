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

package pbouda.jeffrey.manager;

import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface FlamegraphManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, FlamegraphManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, FlamegraphManager> {
    }

    GraphRepositoryManager graphRepositoryManager();

    List<EventSummaryResult> eventSummaries();

    GraphData generate(GraphParameters graphParameters);

}

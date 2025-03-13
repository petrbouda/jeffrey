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

import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.ThreadInfo;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.config.GraphParameters;
import pbouda.jeffrey.common.model.profile.ProfileInfo;
import pbouda.jeffrey.common.time.TimeRange;
import pbouda.jeffrey.common.time.UndefinedTimeRange;
import pbouda.jeffrey.flamegraph.api.GraphData;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.provider.api.model.graph.GraphContent;
import pbouda.jeffrey.provider.api.model.graph.GraphInfo;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface FlamegraphManager {

    record Generate(
            Type eventType,
            TimeRangeRequest timeRangeRequest,
            GraphParameters graphParameters,
            ThreadInfo threadInfo) {

        public TimeRange timeRange() {
            if (timeRangeRequest != null) {
                return TimeRange.create(
                        timeRangeRequest.start(),
                        timeRangeRequest.end(),
                        timeRangeRequest.absoluteTime());
            }
            return UndefinedTimeRange.INSTANCE;
        }
    }

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, FlamegraphManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, FlamegraphManager> {
    }

    List<GraphInfo> allCustom();

    List<EventSummaryResult> eventSummaries();

    GraphData generate(Generate generateRequest);

    void save(Generate generateRequest, String flamegraphName);

    Optional<GraphContent> get(String flamegraphId);

    void delete(String flamegraphId);

}

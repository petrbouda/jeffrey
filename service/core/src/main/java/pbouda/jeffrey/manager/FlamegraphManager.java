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

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.TimeRangeRequest;
import pbouda.jeffrey.common.TimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.model.EventSummaryResult;
import pbouda.jeffrey.repository.model.GraphContent;
import pbouda.jeffrey.repository.model.GraphInfo;
import pbouda.jeffrey.common.model.ProfileInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface FlamegraphManager {

    record Generate(
            Type eventType,
            TimeRangeRequest timeRangeRequest,
            boolean threadMode,
            boolean excludeNonJavaSamples,
            boolean excludeIdleSamples,
            List<Marker> markers) {

        public TimeRange timeRange() {
            if (timeRangeRequest != null) {
                return TimeRange.create(
                        timeRangeRequest.start(),
                        timeRangeRequest.end(),
                        timeRangeRequest.absoluteTime());
            }
            return null;
        }

        public List<Marker> markers() {
            return markers != null ? markers : List.of();
        }
    }

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, FlamegraphManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, FlamegraphManager> {
    }

    List<GraphInfo> allCustom();

    Map<String, EventSummaryResult> eventSummaries();

    ObjectNode generate(Generate generateRequest);

    // Weight is used for recognizing whether the flamegraph was saved as a regular one or with weight option
    void save(Generate generateRequest, String flamegraphName, boolean useWeight);

    Optional<GraphContent> get(String flamegraphId);

    void export(String flamegraphId);

    void export(Type eventType, TimeRangeRequest timeRange, boolean threadMode);

    void delete(String flamegraphId);

    String generateFilename(Type eventType);
}

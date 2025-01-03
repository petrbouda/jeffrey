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

package pbouda.jeffrey.flamegraph.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.DurationFormatter;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.collector.FrameCollector;
import pbouda.jeffrey.flamegraph.FlameGraphBuilder;

import java.util.List;
import java.util.function.Function;

public abstract class FrameCollectorFactories {
    public static FrameCollector<ObjectNode> simpleJson(List<Marker> markers) {
        return new FrameCollector<>(new FlameGraphBuilder(!markers.isEmpty()), markers);
    }

    public static FrameCollector<ObjectNode> allocJson(List<Marker> markers) {
        return new FrameCollector<>(new FlameGraphBuilder(
                !markers.isEmpty(), weight -> BytesFormatter.format(weight) + " Allocated"), markers);
    }

    public static FrameCollector<ObjectNode> blockingJson(List<Marker> markers) {
        return new FrameCollector<>(new FlameGraphBuilder(
                !markers.isEmpty(), weight -> DurationFormatter.format(weight) + " Blocked"), markers);
    }

    public static FrameCollector<Frame> frame() {
        return new FrameCollector<>(Function.identity());
    }
}

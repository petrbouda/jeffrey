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

package pbouda.jeffrey.generator.flamegraph.collector;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.BytesFormatter;
import pbouda.jeffrey.common.DurationFormatter;
import pbouda.jeffrey.generator.flamegraph.FlameGraphBuilder;
import pbouda.jeffrey.generator.flamegraph.Frame;

import java.util.function.Function;

public abstract class FrameCollectorFactories {

    public static FrameCollector<ObjectNode> simpleJson() {
        return new FrameCollector<>(new FlameGraphBuilder());
    }

    public static FrameCollector<ObjectNode> allocJson() {
        return new FrameCollector<>(
                new FlameGraphBuilder(weight -> BytesFormatter.format(weight) + " Allocated"));
    }

    public static FrameCollector<ObjectNode> blockingJson() {
        return new FrameCollector<>(
                new FlameGraphBuilder(weight -> DurationFormatter.format(weight) + " Blocked"));
    }

    public static FrameCollector<Frame> frame() {
        return new FrameCollector<>(Function.identity());
    }
}
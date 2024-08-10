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
import pbouda.jeffrey.jfrparser.jdk.CollectorFactory;

import java.util.function.Function;
import java.util.stream.Collector;

public abstract class FrameCollectorFactories {

    public static FrameCollectorFactory<ObjectNode> simpleJson() {
        return new FrameCollectorFactory<>(new FlameGraphBuilder());
    }

    public static FrameCollectorFactory<ObjectNode> allocJson() {
        return new FrameCollectorFactory<>(
                new FlameGraphBuilder(weight -> BytesFormatter.format(weight) + " Allocated"));
    }

    public static FrameCollectorFactory<ObjectNode> blockingJson() {
        return new FrameCollectorFactory<>(
                new FlameGraphBuilder(weight -> DurationFormatter.format(weight) + " Blocked"));
    }

    public static FrameCollectorFactory<Frame> frame() {
        return new FrameCollectorFactory<>(Function.identity());
    }

    public static class FrameCollectorFactory<OUTPUT> implements CollectorFactory<Frame, OUTPUT> {

        private final Function<Frame, OUTPUT> builder;

        public FrameCollectorFactory(Function<Frame, OUTPUT> builder) {
            this.builder = builder;
        }

        @Override
        public Collector<Frame, ?, OUTPUT> single() {
            return new SingleFrameCollector<>(builder);
        }

        @Override
        public Collector<Frame, ?, OUTPUT> merging() {
            return new MergingFrameCollector<>(builder);
        }
    }
}

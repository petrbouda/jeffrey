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

package pbouda.jeffrey.frameir.collector;

import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.frameir.Frame;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class FrameCollector<OUTPUT> implements Collector<Frame, OUTPUT> {

    private final Function<Frame, OUTPUT> graphBuilder;

    public FrameCollector(Function<Frame, OUTPUT> graphBuilder) {
        this.graphBuilder = graphBuilder;
    }

    @Override
    public Supplier<Frame> empty() {
        return () -> new Frame("-", 0, 0);
    }

    @Override
    public Frame combiner(Frame partial1, Frame partial2) {
        mergeFrames(partial1, partial2);
        return partial1;
    }

    @Override
    public OUTPUT finisher(Frame combined) {
        return graphBuilder.apply(combined);
    }

    /**
     * Merges the second frame into the first one.
     *
     * @param frame1 the first frame object.
     * @param frame2 the second frame object.
     */
    private void mergeFrames(Frame frame1, Frame frame2) {
        frame1.merge(frame2);

        for (Map.Entry<String, Frame> entry : frame2.entrySet()) {
            Frame frame = frame1.get(entry.getKey());
            if (frame == null) {
                frame1.put(entry.getKey(), entry.getValue());
            } else {
                mergeFrames(frame, entry.getValue());
            }
        }
    }
}

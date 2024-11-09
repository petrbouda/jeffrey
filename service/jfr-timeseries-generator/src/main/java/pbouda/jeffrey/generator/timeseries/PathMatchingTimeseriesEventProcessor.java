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

package pbouda.jeffrey.generator.timeseries;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import pbouda.jeffrey.common.AbsoluteTimeRange;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.analysis.marker.Marker;
import pbouda.jeffrey.frameir.frame.FrameNameBuilder;
import pbouda.jeffrey.jfrparser.api.ProcessableEvents;
import pbouda.jeffrey.jfrparser.jdk.type.JdkStackFrame;
import pbouda.jeffrey.jfrparser.jdk.type.JdkThread;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class PathMatchingTimeseriesEventProcessor extends SplitTimeseriesEventProcessor {

    private final FrameNameBuilder frameNameBuilder = new FrameNameBuilder();
    private final List<Marker> markers;

    public PathMatchingTimeseriesEventProcessor(
            Type eventType,
            Function<RecordedEvent, Long> valueExtractor,
            AbsoluteTimeRange absoluteTimeRange,
            Predicate<RecordedEvent> filtering,
            List<Marker> markers) {

        super(eventType, valueExtractor, absoluteTimeRange, Duration.ZERO, filtering);
        this.markers = markers;
    }

    @Override
    public ProcessableEvents processableEvents() {
        return super.processableEvents();
    }

    @Override
    protected boolean matchesStacktrace(RecordedEvent event, RecordedStackTrace stacktrace) {
        for (Marker marker : markers) {
            if (matchesStacktrace(event, stacktrace, marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesStacktrace(RecordedEvent event, RecordedStackTrace stacktrace, Marker marker) {
        List<String> frames = marker.path().frames();
        List<RecordedFrame> recordedFrames = stacktrace.getFrames().reversed();

        // A path is longer than the current stacktrace (does not match)
        if (frames.size() > recordedFrames.size()) {
            return false;
        }

        for (int i = 0; i < frames.size(); i++) {
            String frameName = frames.get(i);
            RecordedFrame recordedFrame = recordedFrames.get(i);

            String curFrameName = frameNameBuilder.generateName(new JdkStackFrame(recordedFrame), new JdkThread(event));
            if (frameName.equals(curFrameName)) {
                // Check if it's the last frame from the path to match, otherwise continue
                // matching the next frames
                boolean isLastFrameFromPath = i == (frames.size() - 1);
                if (isLastFrameFromPath) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }
}

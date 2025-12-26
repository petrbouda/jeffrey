/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.timeseries;

import pbouda.jeffrey.profile.common.analysis.marker.Marker;
import pbouda.jeffrey.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.frameir.frame.FrameNameBuilder;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.List;

public class PathMatchingTimeseriesBuilder extends SplitTimeseriesBuilder {

    private final FrameNameBuilder frameNameBuilder = new FrameNameBuilder();
    private final List<Marker> markers;

    public PathMatchingTimeseriesBuilder(RelativeTimeRange timeRange, List<Marker> markers) {
        super(timeRange);
        this.markers = markers;
    }

    @Override
    protected boolean matchesStacktrace(JfrStackTrace stacktrace) {
        for (Marker marker : markers) {
            if (matchesStacktrace(stacktrace, marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesStacktrace(JfrStackTrace stacktrace, Marker marker) {
        List<String> frames = marker.path().frames();
        List<? extends JfrStackFrame> recordedFrames = stacktrace.frames();

        // A path is longer than the current stacktrace (does not match)
        if (frames.size() > recordedFrames.size()) {
            return false;
        }

        for (int i = 0; i < frames.size(); i++) {
            String frameName = frames.get(i);
            JfrStackFrame recordedFrame = recordedFrames.get(i);

            String curFrameName = frameNameBuilder.generateName(recordedFrame, null);
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

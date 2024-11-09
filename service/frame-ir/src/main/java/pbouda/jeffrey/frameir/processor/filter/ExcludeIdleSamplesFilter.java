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

package pbouda.jeffrey.frameir.processor.filter;

import jdk.jfr.consumer.*;
import pbouda.jeffrey.frameir.FrameType;

import java.util.List;

public class ExcludeIdleSamplesFilter implements EventProcessorFilter {

    private static final String UNSAFE_CLASS = "jdk.internal.misc.Unsafe";

    private final boolean excludeIdleSamples;

    public ExcludeIdleSamplesFilter(boolean excludeIdleSamples) {
        this.excludeIdleSamples = excludeIdleSamples;
    }

    @Override
    public boolean test(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return true;
        }
        return !this.excludeIdleSamples || !isIdleSample(stackTrace);
    }

    private static boolean isIdleSample(RecordedStackTrace stackTrace) {
        List<RecordedFrame> frames = stackTrace.getFrames();
        for (int i = 0; i < frames.size(); i++) {
            RecordedFrame frame = frames.get(i);
            FrameType frameType = FrameType.fromCode(frame.getType());
            if (frameType.isJavaFrame()) {
                String className = frame.getMethod().getType().getName();
                if (className.equals(UNSAFE_CLASS)) {
                    return findExecutor(frames, i);
                }
                return false;
            }
        }

        return false;
    }

    private static boolean findExecutor(List<RecordedFrame> frames, int index) {
        for (int i = index; i < frames.size(); ++i) {
            RecordedFrame frame = frames.get(i);
            FrameType frameType = FrameType.fromCode(frame.getType());

            if (frameType.isJavaFrame()) {
                RecordedMethod method = frame.getMethod();
                RecordedClass clazz = method.getType();

                // Cannot leave the `java.util.concurrent` package
                if (clazz.getName().startsWith("java.util.concurrent")) {
                    if (matchesForkJoinPool(clazz) || matchesExecutor(clazz)) {
                        return true;
                    }
                }
            } else {
                // We reached non-java frame and the executor was not found
                return false;
            }
        }

        // Reached the end of the stack trace and no executor was found
        return false;
    }

    private static boolean matchesForkJoinPool(RecordedClass clazz) {
        return clazz.getName().equals("java.util.concurrent.ForkJoinPool");
    }

    private static boolean matchesExecutor(RecordedClass clazz) {
        return clazz.getName().endsWith("Executor");
    }
}

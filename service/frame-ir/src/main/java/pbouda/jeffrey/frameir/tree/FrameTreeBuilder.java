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

package pbouda.jeffrey.frameir.tree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.common.model.profile.FrameType;
import pbouda.jeffrey.frameir.frame.*;
import pbouda.jeffrey.frameir.frame.FrameProcessor.NewFrame;
import pbouda.jeffrey.frameir.record.StackBasedRecord;
import pbouda.jeffrey.jfrparser.api.type.JfrStackFrame;
import pbouda.jeffrey.jfrparser.api.type.JfrStackTrace;

import java.util.*;

public abstract class FrameTreeBuilder<T extends StackBasedRecord> {

    private record CachedFrame(Frame frame, FrameType frameType) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(FrameTreeBuilder.class);

    private final Frame root = Frame.emptyFrame();

    private final List<FrameProcessor<T>> processors;

    private final Map<JfrStackTrace, List<CachedFrame>> frameCache = new HashMap<>();

    public FrameTreeBuilder(
            boolean lambdaFrameHandling,
            boolean threadModeEnabled,
            boolean parseLocations,
            FrameProcessor<T> topFrameProcessor) {

        this.processors = new ArrayList<>();
        if (threadModeEnabled) {
            processors.add(new ThreadFrameProcessor<>());
        }

        if (lambdaFrameHandling) {
            processors.add(new LambdaFrameProcessor<>(new LambdaMatcher()));
            processors.add(new NormalFrameProcessor<>(new LambdaMatcher(), parseLocations));
        } else {
            processors.add(new NormalFrameProcessor<>(parseLocations));
        }

        if (topFrameProcessor != null) {
            processors.add(topFrameProcessor);
        }
    }

    public void addRecord(T record) {
        JfrStackTrace stacktrace = record.stackTrace();
        if (stacktrace == null) {
            if (record.thread() != null) {
                LOG.warn("Missing stacktrace: thread={}", record.thread().name());
            } else {
                LOG.warn("Missing stacktrace and thread");
            }
            return;
        }

        // Fast-path (Stacktrace has been already processed)
        List<CachedFrame> cachedFrame = frameCache.get(stacktrace);
        if (cachedFrame != null) {
            processFastPath(cachedFrame, record);
            return;
        }

        // Slow-path
        Frame parent = root;
        List<? extends JfrStackFrame> frames = stacktrace.frames();
        if (frames.isEmpty()) {
            return;
        }

        List<CachedFrame> framePath = new ArrayList<>();
        int newFramesCount;
        for (int i = 0; i < frames.size(); i = i + newFramesCount) {
            newFramesCount = 0;
            for (FrameProcessor<T> processor : processors) {
                for (NewFrame newFrame : processor.checkAndProcess(record, frames, i)) {
                    parent = addFrameToLayer(newFrame, parent);
                    framePath.add(new CachedFrame(parent, newFrame.frameType()));
                    newFramesCount++;
                }
            }
        }

        frameCache.put(stacktrace, framePath);
    }

    private void processFastPath(List<CachedFrame> cachedFrames, T record) {
        for (int i = 0; i < cachedFrames.size(); i++) {
            CachedFrame cachedFrame = cachedFrames.get(i);
            cachedFrame.frame.increment(
                    cachedFrame.frameType,
                    record.sampleWeight(),
                    record.samples(),
                    isLastFrame(i, cachedFrames.size()));
        }
    }

    private static boolean isLastFrame(int i, int frameCount) {
        return (i + 1) == frameCount;
    }

    private static Frame addFrameToLayer(NewFrame newFrame, Frame parent) {
        Frame resolvedFrame = parent.get(newFrame.methodName());
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(parent, newFrame.methodName(), newFrame.lineNumber(), newFrame.bytecodeIndex());
            parent.put(newFrame.methodName(), resolvedFrame);
        }

        resolvedFrame.increment(newFrame.frameType(), newFrame.sampleWeight(), newFrame.samples(), newFrame.isTopFrame());
        return resolvedFrame;
    }

    public Frame build() {
        long allWeight = 0;
        long allSamples = 0;
        for (Frame frame : root.values()) {
            allSamples += frame.totalSamples();
            allWeight += frame.totalWeight();
        }

        root.increment(FrameType.NATIVE, allWeight, allSamples, false);
        return root;
    }
}

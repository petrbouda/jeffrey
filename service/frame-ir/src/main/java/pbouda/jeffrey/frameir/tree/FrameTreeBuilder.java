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

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.FrameType;
import pbouda.jeffrey.frameir.frame.*;
import pbouda.jeffrey.frameir.frame.FrameProcessor.NewFrame;
import pbouda.jeffrey.frameir.record.StackBasedRecord;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public abstract class FrameTreeBuilder<T extends StackBasedRecord> {

    private record CachedFrame(Frame frame, FrameType frameType) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(FrameTreeBuilder.class);

    private final Frame root = Frame.emptyFrame();

    private final List<FrameProcessor<T>> processors;

    private final Map<RecordedStackTrace, List<CachedFrame>> frameCache = new IdentityHashMap<>();

    public FrameTreeBuilder(
            boolean lambdaFrameHandling,
            boolean threadModeEnabled,
            FrameProcessor<T> topFrameProcessor) {

        LambdaMatcher lambdaMatcher = lambdaFrameHandling
                ? new LambdaMatcher()
                : LambdaMatcher.ALWAYS_FALSE;

        this.processors = new ArrayList<>();
        if (threadModeEnabled) {
            processors.add(new ThreadFrameProcessor<>());
        }

        if (lambdaFrameHandling) {
            processors.add(new LambdaFrameProcessor<>(lambdaMatcher));
        }

        processors.add(new NormalFrameProcessor<>(lambdaMatcher));

        if (topFrameProcessor != null) {
            processors.add(topFrameProcessor);
        }
    }

    public void addRecord(T record) {
        RecordedStackTrace stacktrace = record.stackTrace();
        if (stacktrace == null) {
            if (record.thread() != null) {
                LOG.warn("Missing stacktrace: thread={}", record.thread().getJavaName());
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
        List<RecordedFrame> frames = stacktrace.getFrames().reversed();

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
                    cachedFrame.frameType, record.sampleWeight(), isLastFrame(i, cachedFrames.size()));
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

        resolvedFrame.increment(newFrame.frameType(), newFrame.sampleWeight(), newFrame.isTopFrame());
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

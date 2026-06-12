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

package cafe.jeffrey.frameir;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.frameir.frame.*;
import cafe.jeffrey.frameir.frame.FrameProcessor.NewFrame;
import cafe.jeffrey.frameir.frame.FrameProcessor.ProcessedFrames;
import cafe.jeffrey.jfrparser.api.type.JfrStackFrame;
import cafe.jeffrey.jfrparser.api.type.JfrStackTrace;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.provider.profile.api.FlamegraphRecord;

import java.util.ArrayList;
import java.util.List;

public class FrameBuilder implements RecordBuilder<FlamegraphRecord, Frame> {

    private static final Logger LOG = LoggerFactory.getLogger(FrameBuilder.class);

    private final Frame root = Frame.emptyFrame();

    private final List<FrameProcessor> processors;

    public FrameBuilder(
            boolean lambdaFrameHandling,
            boolean threadModeEnabled,
            boolean parseLocations,
            FrameProcessor topFrameProcessor) {

        this.processors = new ArrayList<>();
        if (threadModeEnabled) {
            processors.add(new ThreadFrameProcessor());
        }

        if (lambdaFrameHandling) {
            processors.add(new LambdaFrameProcessor(new LambdaMatcher()));
            processors.add(new NormalFrameProcessor(new LambdaMatcher(), parseLocations));
        } else {
            processors.add(new NormalFrameProcessor(parseLocations));
        }

        if (topFrameProcessor != null) {
            processors.add(topFrameProcessor);
        }
    }

    @Override
    public void onRecord(FlamegraphRecord record) {
        JfrStackTrace stacktrace = record.stackTrace();
        if (stacktrace == null) {
            if (record.thread() != null) {
                LOG.warn("Missing stacktrace: thread={}", record.thread().name());
            } else {
                LOG.warn("Missing stacktrace and thread");
            }
            return;
        }

        List<? extends JfrStackFrame> frames = stacktrace.frames();
        if (frames.isEmpty()) {
            return;
        }

        List<NewFrame> newFrames = collectNewFrames(record, frames);

        Frame parent = root;
        for (int i = 0; i < newFrames.size(); i++) {
            // Only the deepest emitted frame of the record carries the self-time, it keeps the invariant:
            // selfSamples + sum(children.totalSamples) == totalSamples for every node in the tree.
            boolean isTopFrame = i == (newFrames.size() - 1);
            parent = addFrameToLayer(newFrames.get(i), parent, isTopFrame);
        }
    }

    private List<NewFrame> collectNewFrames(FlamegraphRecord record, List<? extends JfrStackFrame> frames) {
        List<NewFrame> newFrames = new ArrayList<>();
        for (int i = 0; i < frames.size(); ) {
            int consumedStackFrames = 0;
            for (FrameProcessor processor : processors) {
                ProcessedFrames processed = processor.checkAndProcess(record, frames, i);
                newFrames.addAll(processed.frames());
                consumedStackFrames += processed.consumedStackFrames();
            }

            if (consumedStackFrames == 0) {
                LOG.warn("No stacktrace element consumed, skipping the rest of the stacktrace: index={} frames={}",
                        i, frames.size());
                break;
            }

            i += consumedStackFrames;
        }
        return newFrames;
    }

    private static Frame addFrameToLayer(NewFrame newFrame, Frame parent, boolean isTopFrame) {
        Frame resolvedFrame = parent.get(newFrame.methodName());
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(parent, newFrame.methodName(), newFrame.lineNumber(), newFrame.bytecodeIndex());
            parent.put(newFrame.methodName(), resolvedFrame);
        }

        resolvedFrame.increment(newFrame.frameType(), newFrame.sampleWeight(), newFrame.samples(), isTopFrame);
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

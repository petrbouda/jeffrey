package pbouda.jeffrey.graph;

import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;

import java.util.ArrayList;
import java.util.List;

public class StackTraceBuilder {

    private final Frame root = new Frame("all", 0, 0);

    public StackTraceBuilder() {
    }

    public void addStackTrace(List<String> frames) {
        Frame parent = root;
        for (int i = 0; i < frames.size(); i++) {
            boolean isTopFrame = i == (frames.size() - 1);
            String frameLine = frames.get(i);

            String[] parts = frameLine.split("\\|");
            parent = addFrameToLayer(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    FrameType.fromCode(parts[3]),
                    isTopFrame,
                    parent
            );
        }
    }

    public void addStackTrace(RecordedStackTrace stackTrace) {
        Frame parent = root;
        List<RecordedFrame> frames = stackTrace.getFrames().reversed();
        for (int i = 0; i < frames.size(); i++) {
            boolean isTopFrame = i == (frames.size() - 1);
            RecordedFrame frame = frames.get(i);

            FrameType frameType = FrameType.fromCode(frame.getType());
            parent = addFrameToLayer(
                    generateName(frame, frameType),
                    frame.getLineNumber(),
                    frame.getBytecodeIndex(),
                    frameType,
                    isTopFrame,
                    parent
            );
        }
    }

    private static String generateName(RecordedFrame frame, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED ->
                frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
            case CPP, KERNEL, NATIVE -> frame.getMethod().getName();
        };
    }

    private Frame addFrameToLayer(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            boolean isTopFrame,
            Frame parent) {

        Frame resolvedFrame = parent.get(methodName);
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(methodName, lineNumber, bytecodeIndex);
            parent.put(methodName, resolvedFrame);
        }

        resolvedFrame.increment(frameType, 1, isTopFrame);
        return resolvedFrame;
    }

    public Frame build() {
        int allWeight = root.values().stream()
                .mapToInt(Frame::totalWeight)
                .sum();

        root.increment(FrameType.NATIVE, allWeight, true);
        return root;
    }
}

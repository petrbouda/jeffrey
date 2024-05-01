package pbouda.jeffrey.generator.flamegraph.tree;

import jdk.jfr.consumer.RecordedFrame;
import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;
import pbouda.jeffrey.generator.flamegraph.record.StackBasedRecord;

import java.util.List;

public abstract class FrameTreeBuilder<T extends StackBasedRecord> {

    private final Frame root = new Frame("-", 0, 0);

    private final boolean specialTopFrameHandling;

    public FrameTreeBuilder(boolean specialTopFrameHandling) {
        this.specialTopFrameHandling = specialTopFrameHandling;
    }

//    public void addRecord(List<String> frames) {
//        Frame parent = root;
//        for (int i = 0; i < frames.size(); i++) {
//            boolean isTopFrame = i == (frames.size() - 1);
//            String frameLine = frames.get(i);
//
//            String[] parts = frameLine.split("\\|");
//            parent = addFrameToLayer(
//                    parts[0],
//                    Integer.parseInt(parts[1]),
//                    Integer.parseInt(parts[2]),
//                    FrameType.fromCode(parts[3]),
//                    isTopFrame,
//                    parent
//            );
//        }
//    }

    public void addRecord(T record) {
        Frame parent = root;
        List<RecordedFrame> frames = record.stackTrace().getFrames().reversed();
        for (int i = 0; i < frames.size(); i++) {
            boolean isTopFrame = i == (frames.size() - 1);
            RecordedFrame frame = frames.get(i);

            FrameType frameType = FrameType.fromCode(frame.getType());

            if (specialTopFrameHandling && isTopFrame) {
                parent = addFrameToLayer(
                        generateName(frame, frameType),
                        frame.getLineNumber(),
                        frame.getBytecodeIndex(),
                        frameType,
                        false,
                        record.sampleWeight(),
                        parent);

                parent = specialTopFrame(record, parent, frame);
            } else {
                parent = addFrameToLayer(
                        generateName(frame, frameType),
                        frame.getLineNumber(),
                        frame.getBytecodeIndex(),
                        frameType,
                        isTopFrame,
                        record.sampleWeight(),
                        parent);
            }
        }
    }

    /**
     * It offers the functionality to a special top frame to visualize some
     * additional functionality. E.g. allocated object, locks, ...
     *
     * @param record       record with the additional data to generate top frame.
     * @param parentFrame  previous processed frame (to add a new frame upon it).
     * @param currentFrame current frame to be processed.
     * @return generated top frame, or {@code null} if the top frame is not generated.
     */
    protected Frame specialTopFrame(T record, Frame parentFrame, RecordedFrame currentFrame) {
        return null;
    }

    private static String generateName(RecordedFrame frame, FrameType frameType) {
        return switch (frameType) {
            case JIT_COMPILED, C1_COMPILED, INTERPRETED, INLINED ->
                    frame.getMethod().getType().getName() + "#" + frame.getMethod().getName();
            case CPP, KERNEL, NATIVE -> frame.getMethod().getName();
            case UNKNOWN -> throw new IllegalArgumentException("Unknown Frame occurred in JFR");
        };
    }

    protected Frame addFrameToLayer(
            String methodName,
            int lineNumber,
            int bytecodeIndex,
            FrameType frameType,
            boolean isTopFrame,
            long sampleWeight,
            Frame parent) {

        Frame resolvedFrame = parent.get(methodName);
        if (resolvedFrame == null) {
            resolvedFrame = new Frame(methodName, lineNumber, bytecodeIndex);
            parent.put(methodName, resolvedFrame);
        }

        resolvedFrame.increment(frameType, sampleWeight, isTopFrame);
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

package pbouda.jeffrey.generator.flamegraph.diff;

import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;

import java.util.TreeMap;

public class DiffFrame extends TreeMap<String, DiffFrame> {

    public enum Type {
        REMOVED, ADDED, SHARED
    }

    public final Type type;
    public final Frame frame;
    public final String methodName;
    public final FrameType frameType;
    public final long secondarySamples;
    public final long secondaryWeight;
    public final long primarySamples;
    public final long primaryWeight;

    public DiffFrame(
            Type type,
            Frame frame,
            String methodName,
            FrameType frameType
    ) {
        this(type, frame, methodName, frameType, -1, -1, -1, -1);
    }

    public DiffFrame(
            Type type,
            Frame frame,
            String methodName,
            FrameType frameType,
            long secondarySamples,
            long secondaryWeight,
            long primarySamples,
            long primaryWeight
    ) {
        this.type = type;
        this.frame = frame;
        this.methodName = methodName;
        this.frameType = frameType;
        this.secondarySamples = secondarySamples;
        this.secondaryWeight = secondaryWeight;
        this.primarySamples = primarySamples;
        this.primaryWeight = primaryWeight;
    }

    public static DiffFrame removed(Frame frame, String methodName) {
        return new DiffFrame(Type.REMOVED, frame, methodName, FrameType.UNKNOWN);
    }

    public static DiffFrame added(Frame frame, String methodName) {
        return new DiffFrame(Type.ADDED, frame, methodName, FrameType.UNKNOWN);
    }

    public static DiffFrame shared(
            String methodName,
            FrameType frameType,
            long secondarySamples,
            long secondaryWeight,
            long primarySamples,
            long primaryWeight) {
        return new DiffFrame(
                Type.SHARED,
                null,
                methodName,
                frameType,
                secondarySamples,
                secondaryWeight,
                primarySamples,
                primaryWeight);
    }

    public long samples() {
        return switch (type) {
            case REMOVED, ADDED -> frame.totalSamples();
            case SHARED -> secondarySamples + primarySamples;
        };
    }

    public long weight() {
        return switch (type) {
            case REMOVED, ADDED -> frame.totalWeight();
            case SHARED -> secondaryWeight + primaryWeight;
        };
    }
}

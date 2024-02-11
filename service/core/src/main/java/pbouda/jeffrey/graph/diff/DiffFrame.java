package pbouda.jeffrey.graph.diff;

import one.Frame;

import java.util.TreeMap;

public class DiffFrame extends TreeMap<String, DiffFrame> {

    public enum Type {
        REMOVED, ADDED, SHARED
    }

    public Type type;
    public Frame frame;
    public String methodName;
    public byte frameType;
    public long baselineSamples;
    public long comparisonSamples;

    public DiffFrame(Type type, Frame frame, String methodName, byte frameType, long baselineSamples, long comparisonSamples) {
        this.type = type;
        this.frame = frame;
        this.methodName = methodName;
        this.frameType = frameType;
        this.baselineSamples = baselineSamples;
        this.comparisonSamples = comparisonSamples;
    }

    public static DiffFrame removed(Frame frame, String methodName) {
        return new DiffFrame(Type.REMOVED, frame, methodName, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame added(Frame frame, String methodName) {
        return new DiffFrame(Type.ADDED, frame, methodName, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame shared(String methodName, byte frameType, long baselineSamples, long comparisonSamples) {
        return new DiffFrame(Type.SHARED, null, methodName, frameType, baselineSamples, comparisonSamples);
    }

    public long samples() {
        return switch (type) {
            case REMOVED, ADDED -> frame.samples;
            case SHARED -> baselineSamples + comparisonSamples;
        };
    }
}

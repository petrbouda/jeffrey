package pbouda.jeffrey.flamegraph.diff;

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
    public long baselineTotal;
    public long comparisonTotal;

    public DiffFrame(Type type, Frame frame, String methodName, byte frameType, long baselineTotal, long comparisonTotal) {
        this.type = type;
        this.frame = frame;
        this.methodName = methodName;
        this.frameType = frameType;
        this.baselineTotal = baselineTotal;
        this.comparisonTotal = comparisonTotal;
    }

    public static DiffFrame removed(Frame frame, String methodName) {
        return new DiffFrame(Type.REMOVED, frame, methodName, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame added(Frame frame, String methodName) {
        return new DiffFrame(Type.ADDED, frame, methodName, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame shared(String methodName, byte frameType, long baselineTotal, long comparisonTotal) {
        return new DiffFrame(Type.SHARED, null, methodName, frameType, baselineTotal, comparisonTotal);
    }

    public long total() {
        return switch (type) {
            case REMOVED, ADDED -> frame.total;
            case SHARED -> baselineTotal + comparisonTotal;
        };
    }
}

package pbouda.jeffrey.flamegraph.diff;

import one.Frame;

import java.util.TreeMap;

public class DiffFrame extends TreeMap<String, DiffFrame> {

    public enum Type {
        REMOVED, ADDED, MID
    }

    public Type type;
    public Frame frame;
    public String frameName;
    public byte frameType;
    public long baselineTotal;
    public long comparisonTotal;

    public DiffFrame(Type type, Frame frame, String frameName, byte frameType, long baselineTotal, long comparisonTotal) {
        this.type = type;
        this.frame = frame;
        this.frameName = frameName;
        this.frameType = frameType;
        this.baselineTotal = baselineTotal;
        this.comparisonTotal = comparisonTotal;
    }

    public static DiffFrame removed(Frame frame) {
        return new DiffFrame(Type.REMOVED, frame, null, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame added(Frame frame) {
        return new DiffFrame(Type.ADDED, frame, null, Byte.MIN_VALUE, -1, -1);
    }

    public static DiffFrame partial(String frameName, byte frameType, long baselineTotal, long comparisonTotal) {
        return new DiffFrame(Type.MID, null, frameName, frameType, baselineTotal, comparisonTotal);
    }
}

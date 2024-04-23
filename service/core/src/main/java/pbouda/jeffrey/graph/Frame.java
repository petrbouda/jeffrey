package pbouda.jeffrey.graph;

import java.util.TreeMap;

public class Frame extends TreeMap<String, Frame> {
    private final String methodName;
    private final int lineNumber;
    private final int bci;

    // weight can be samples, but also allocated memory
    private int totalWeight;
    private int selfWeight;

    private int c1Weight;
    private int nativeWeight;
    private int cppWeight;
    private int interpretedWeight;
    private int jitCompiledWeight;
    private int inlinedWeight;
    private int kernelWeight;

    public Frame(String methodName, int lineNumber, int bci) {
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.bci = bci;
    }

    public void increment(FrameType type, int weight, boolean isTopFrame) {
        totalWeight += weight;

        if (isTopFrame) {
            selfWeight += weight;
        }

        switch (type) {
            case C1_COMPILED -> c1Weight += weight;
            case NATIVE -> nativeWeight += weight;
            case CPP -> cppWeight += weight;
            case INTERPRETED -> interpretedWeight += weight;
            case JIT_COMPILED -> jitCompiledWeight += weight;
            case INLINED -> inlinedWeight += weight;
            case KERNEL -> kernelWeight += weight;
        }
    }

    public FrameType frameType() {
        if (inlinedWeight * 3 >= totalWeight) {
            return FrameType.INLINED;
        } else if (c1Weight * 2 >= totalWeight) {
            return FrameType.C1_COMPILED;
        } else if (interpretedWeight * 2 >= totalWeight) {
            return FrameType.INTERPRETED;
        } else if (cppWeight > 0) {
            return FrameType.CPP;
        } else if (kernelWeight > 0) {
            return FrameType.KERNEL;
        } else if (nativeWeight > 0) {
            return FrameType.NATIVE;
        } else {
            return FrameType.JIT_COMPILED;
        }
    }

    public String methodName() {
        return methodName;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public int bci() {
        return bci;
    }

    public int totalWeight() {
        return totalWeight;
    }

    public int inlinedWeight() {
        return inlinedWeight;
    }

    public int c1Weight() {
        return c1Weight;
    }

    public int interpretedWeight() {
        return interpretedWeight;
    }

    int depth(long cutoff) {
        int depth = 0;
        if (size() > 0) {
            for (Frame child : values()) {
                if (child.totalWeight >= cutoff) {
                    depth = Math.max(depth, child.depth(cutoff));
                }
            }
        }
        return depth + 1;
    }
}

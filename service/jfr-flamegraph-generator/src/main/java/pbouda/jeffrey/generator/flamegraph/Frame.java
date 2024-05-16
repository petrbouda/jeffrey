package pbouda.jeffrey.generator.flamegraph;

import java.util.TreeMap;

public class Frame extends TreeMap<String, Frame> {
    private final String methodName;
    private final int lineNumber;
    private final int bci;

    private FrameType syntheticFrameType;

    // weight can be samples, but also allocated memory
    private long totalSamples;
    private long totalWeight;
    private long selfSamples;
    private long selfWeight;

    private long c1Samples;
    private long nativeSamples;
    private long cppSamples;
    private long interpretedSamples;
    private long jitCompiledSamples;
    private long inlinedSamples;
    private long kernelSamples;

    public Frame(String methodName, int lineNumber, int bci) {
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.bci = bci;
    }

    public void increment(FrameType type, long weight, boolean isTopFrame) {
        increment(type, weight, 1, isTopFrame);
    }

    public void increment(FrameType type, long weight, long samples, boolean isTopFrame) {
        totalSamples += samples;
        totalWeight += weight;

        if (isTopFrame) {
            selfSamples += samples;
            selfWeight += weight;
        }

        switch (type) {
            case C1_COMPILED -> c1Samples += samples;
            case NATIVE -> nativeSamples += samples;
            case CPP -> cppSamples += samples;
            case INTERPRETED -> interpretedSamples += samples;
            case JIT_COMPILED -> jitCompiledSamples += samples;
            case INLINED -> inlinedSamples += samples;
            case KERNEL -> kernelSamples += samples;
            case THREAD_NAME_SYNTHETIC,
                 ALLOCATED_OBJECT_SYNTHETIC,
                 ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC,
                 ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC,
                 BLOCKING_OBJECT_SYNTHETIC -> syntheticFrameType = type;
        }
    }

    public FrameType frameType() {
        if (inlinedSamples * 3 >= totalSamples) {
            return FrameType.INLINED;
        } else if (c1Samples * 2 >= totalSamples) {
            return FrameType.C1_COMPILED;
        } else if (interpretedSamples * 2 >= totalSamples) {
            return FrameType.INTERPRETED;
        } else if (cppSamples > 0) {
            return FrameType.CPP;
        } else if (kernelSamples > 0) {
            return FrameType.KERNEL;
        } else if (nativeSamples > 0) {
            return FrameType.NATIVE;
        } else if (syntheticFrameType != null){
            return syntheticFrameType;
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

    public long totalSamples() {
        return totalSamples;
    }

    public long totalWeight() {
        return totalWeight;
    }

    public long inlinedSamples() {
        return inlinedSamples;
    }

    public long c1Samples() {
        return c1Samples;
    }

    public long jitCompiledSamples() {
        return jitCompiledSamples;
    }

    public long interpretedSamples() {
        return interpretedSamples;
    }

    public long selfWeight() {
        return selfWeight;
    }

    public long selfSamples() {
        return selfSamples;
    }

    int depth(long cutoff) {
        int depth = 0;
        if (size() > 0) {
            for (Frame child : values()) {
                if (child.totalSamples >= cutoff) {
                    depth = Math.max(depth, child.depth(cutoff));
                }
            }
        }
        return depth + 1;
    }
}

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

package cafe.jeffrey.frameir;


import cafe.jeffrey.profile.common.analysis.AnalysisResult.Severity;
import cafe.jeffrey.profile.common.analysis.marker.Marker;
import cafe.jeffrey.profile.common.model.FrameType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

public class Frame extends TreeMap<String, Frame> {
    private final String methodName;
    private final int lineNumber;
    private final int bci;

    private FrameType syntheticFrameType;
    private Severity marker;

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
    private long unknownSamples;

    private final Frame parent;

    public Frame(Frame parent, String methodName, int lineNumber, int bci) {
        this.parent = parent;
        this.methodName = methodName;
        this.lineNumber = lineNumber;
        this.bci = bci;
    }

    public void merge(Frame frame) {
        totalSamples += frame.totalSamples;
        totalWeight += frame.totalWeight;
        selfSamples += frame.selfSamples;
        selfWeight += frame.selfWeight;
        c1Samples += frame.c1Samples;
        nativeSamples += frame.nativeSamples;
        cppSamples += frame.cppSamples;
        interpretedSamples += frame.interpretedSamples;
        jitCompiledSamples += frame.jitCompiledSamples;
        inlinedSamples += frame.inlinedSamples;
        kernelSamples += frame.kernelSamples;
        unknownSamples += frame.unknownSamples;
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
            case UNKNOWN -> unknownSamples += samples;
            case THREAD_NAME_SYNTHETIC,
                 ALLOCATED_OBJECT_SYNTHETIC,
                 ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC,
                 ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC,
                 LAMBDA_SYNTHETIC,
                 BLOCKING_OBJECT_SYNTHETIC,
                 COLLAPSED_SYNTHETIC,
                 TRUNCATED_SYNTHETIC -> syntheticFrameType = type;
        }
    }

    /**
     * Applies the marker to the frame. If the path from the marker is not fully resolved, it will be applied to the
     * first frame that matches the path, and goes iteratively to the next children.
     *
     * @param marker the marker to apply to the frame according to the path.
     */
    public void applyMarker(Marker marker) {
        _applyMarker(marker, 0);
    }

    private void _applyMarker(Marker marker, int frameIndex) {
        List<String> markerFrames = marker.path().frames();

        if (frameIndex == markerFrames.size()) {
            this.marker = marker.markerType();
        } else {
            String frameName = markerFrames.get(frameIndex);
            Frame child = get(frameName);
            if (child != null) {
                child._applyMarker(marker, frameIndex + 1);
            }
        }
    }

    public boolean hasMarker() {
        return marker != null;
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
        } else if (unknownSamples > 0) {
            return FrameType.UNKNOWN;
        } else if (syntheticFrameType != null) {
            return syntheticFrameType;
        } else {
            return FrameType.JIT_COMPILED;
        }
    }

    /**
     * Path to the current frame in the tree structure (root-first, the root frame itself is excluded).
     * The path is derived on demand by walking the parent chain instead of being materialized eagerly
     * on every frame construction.
     *
     * @return method names from the first frame below the root down to this frame.
     */
    public List<String> framePath() {
        List<String> path = new ArrayList<>();
        for (Frame current = this; current.parent != null; current = current.parent) {
            path.add(current.methodName);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }

    public Frame parent() {
        return parent;
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

    public int depth(long cutoff, boolean byWeight) {
        int depth = 0;
        if (size() > 0) {
            for (Frame child : values()) {
                long metric = byWeight ? child.totalWeight : child.totalSamples;
                if (metric >= cutoff) {
                    depth = Math.max(depth, child.depth(cutoff, byWeight));
                }
            }
        }
        return depth + 1;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Frame frame)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        return lineNumber == frame.lineNumber && bci == frame.bci
                && totalSamples == frame.totalSamples && totalWeight == frame.totalWeight
                && selfSamples == frame.selfSamples && selfWeight == frame.selfWeight
                && c1Samples == frame.c1Samples && nativeSamples == frame.nativeSamples
                && cppSamples == frame.cppSamples && interpretedSamples == frame.interpretedSamples
                && jitCompiledSamples == frame.jitCompiledSamples && inlinedSamples == frame.inlinedSamples
                && kernelSamples == frame.kernelSamples && Objects.equals(methodName, frame.methodName)
                && syntheticFrameType == frame.syntheticFrameType;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(methodName);
        result = 31 * result + lineNumber;
        result = 31 * result + bci;
        result = 31 * result + Objects.hashCode(syntheticFrameType);
        result = 31 * result + Long.hashCode(totalSamples);
        result = 31 * result + Long.hashCode(totalWeight);
        result = 31 * result + Long.hashCode(selfSamples);
        result = 31 * result + Long.hashCode(selfWeight);
        result = 31 * result + Long.hashCode(c1Samples);
        result = 31 * result + Long.hashCode(nativeSamples);
        result = 31 * result + Long.hashCode(cppSamples);
        result = 31 * result + Long.hashCode(interpretedSamples);
        result = 31 * result + Long.hashCode(jitCompiledSamples);
        result = 31 * result + Long.hashCode(inlinedSamples);
        result = 31 * result + Long.hashCode(kernelSamples);
        return result;
    }

    @Override
    public String toString() {
        return "Frame{" +
                "methodName='" + methodName + '\'' +
                ", lineNumber=" + lineNumber +
                ", bci=" + bci +
                ", syntheticFrameType=" + syntheticFrameType +
                ", totalSamples=" + totalSamples +
                ", totalWeight=" + totalWeight +
                ", selfSamples=" + selfSamples +
                ", selfWeight=" + selfWeight +
                ", c1Samples=" + c1Samples +
                ", nativeSamples=" + nativeSamples +
                ", cppSamples=" + cppSamples +
                ", interpretedSamples=" + interpretedSamples +
                ", jitCompiledSamples=" + jitCompiledSamples +
                ", inlinedSamples=" + inlinedSamples +
                ", kernelSamples=" + kernelSamples +
                '}';
    }

    public static Frame emptyFrame() {
        return new Frame(null, "-", 0, 0);
    }
}

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

package pbouda.jeffrey.flamegraph;

import pbouda.jeffrey.shared.common.BytesUtils;
import pbouda.jeffrey.shared.common.DurationUtils;
import pbouda.jeffrey.profile.common.model.FrameType;
import pbouda.jeffrey.flamegraph.diff.StringUtils;
import pbouda.jeffrey.flamegraph.proto.FlamegraphData;
import pbouda.jeffrey.flamegraph.proto.Frame;
import pbouda.jeffrey.flamegraph.proto.FramePosition;
import pbouda.jeffrey.flamegraph.proto.FrameSampleTypes;
import pbouda.jeffrey.flamegraph.proto.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds flamegraph data in Protocol Buffers format.
 * This provides ~50-60% size reduction compared to JSON through:
 * - Varint encoding for integers
 * - No field names in wire format
 * - String deduplication via title_pool
 */
public class FlameGraphProtoBuilder implements GraphBuilder<pbouda.jeffrey.frameir.Frame, FlamegraphData> {

    private static final Function<Long, String> ALLOCATION_FORMATTER =
            weight -> BytesUtils.format(weight) + " Allocated";

    private static final Function<Long, String> BLOCKING_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Blocked";

    private static final Function<Long, String> LATENCY_FORMATTER =
            weight -> DurationUtils.formatNanos2Units(weight) + " Latency";

    private static final int MAX_LEVEL = Integer.MAX_VALUE;

    private final boolean withMarker;
    private final boolean withWeight;
    private final Function<Long, String> weightFormatter;

    // Title pool for string deduplication
    private final List<String> titlePool = new ArrayList<>();
    private final Map<String, Integer> titleIndex = new HashMap<>();

    private FlameGraphProtoBuilder(boolean withMarker, Function<Long, String> weightFormatter) {
        this(withMarker, weightFormatter != null, weightFormatter);
    }

    public static FlameGraphProtoBuilder simple(boolean withMarker) {
        return new FlameGraphProtoBuilder(withMarker, null);
    }

    public static FlameGraphProtoBuilder allocation(boolean withMarker) {
        return new FlameGraphProtoBuilder(withMarker, ALLOCATION_FORMATTER);
    }

    public static FlameGraphProtoBuilder blocking(boolean withMarker) {
        return new FlameGraphProtoBuilder(withMarker, BLOCKING_FORMATTER);
    }

    public static FlameGraphProtoBuilder latency(boolean withMarker) {
        return new FlameGraphProtoBuilder(withMarker, LATENCY_FORMATTER);
    }

    public FlameGraphProtoBuilder(boolean withMarker, boolean withWeight, Function<Long, String> weightFormatter) {
        this.withMarker = withMarker;
        this.withWeight = withWeight;
        this.weightFormatter = weightFormatter;
    }

    @Override
    public FlamegraphData build(pbouda.jeffrey.frameir.Frame root) {
        int depth = root.depth(0);

        // Initialize level builders
        List<Level.Builder> levelBuilders = new ArrayList<>(depth);
        for (int i = 0; i < depth; i++) {
            levelBuilders.add(Level.newBuilder());
        }

        // Build root title
        String rootTitle = withWeight
                ? root.totalSamples() + " Event(s), " + weightFormatter.apply(root.totalWeight())
                : root.totalSamples() + " Event(s)";

        // Recursively build frame tree
        buildFrame(levelBuilders, rootTitle, root, 0, 0, 0, false);

        // Build the final FlamegraphData
        FlamegraphData.Builder dataBuilder = FlamegraphData.newBuilder()
                .setDepth(depth);

        for (Level.Builder levelBuilder : levelBuilders) {
            dataBuilder.addLevels(levelBuilder);
        }

        dataBuilder.addAllTitlePool(titlePool);

        return dataBuilder.build();
    }

    private void buildFrame(
            List<Level.Builder> levelBuilders,
            String title,
            pbouda.jeffrey.frameir.Frame frame,
            int level,
            long leftSamples,
            long leftWeight,
            boolean markerCrossed) {

        Frame.Builder frameBuilder = Frame.newBuilder()
                .setLeftSamples(leftSamples)
                .setTotalSamples(frame.totalSamples())
                .setTitleIndex(getOrAddTitle(StringUtils.escape(title)))
                .setType(mapFrameType(frame.frameType()));

        // Only include weight fields when flamegraph uses weight
        if (withWeight) {
            frameBuilder.setLeftWeight(leftWeight);
            frameBuilder.setTotalWeight(frame.totalWeight());
        }

        // Only include selfSamples when non-zero
        if (frame.selfSamples() > 0) {
            frameBuilder.setSelfSamples(frame.selfSamples());
        }

        // Add marker info for guardian analysis coloring
        if (withMarker && !markerCrossed) {
            frameBuilder.setBeforeMarker(true);
        }

        // Add position if available
        if (frame.bci() > 0 || frame.lineNumber() > 0) {
            FramePosition.Builder posBuilder = FramePosition.newBuilder();
            if (frame.bci() > 0) {
                posBuilder.setBci(frame.bci());
            }
            if (frame.lineNumber() > 0) {
                posBuilder.setLine(frame.lineNumber());
            }
            frameBuilder.setPosition(posBuilder);
        }

        // Add sample types if any non-zero
        if (frame.inlinedSamples() > 0 || frame.c1Samples() > 0
                || frame.jitCompiledSamples() > 0 || frame.interpretedSamples() > 0) {
            FrameSampleTypes.Builder sampleTypesBuilder = FrameSampleTypes.newBuilder();
            if (frame.inlinedSamples() > 0) {
                sampleTypesBuilder.setInlined(frame.inlinedSamples());
            }
            if (frame.c1Samples() > 0) {
                sampleTypesBuilder.setC1(frame.c1Samples());
            }
            if (frame.interpretedSamples() > 0) {
                sampleTypesBuilder.setInterpret(frame.interpretedSamples());
            }
            if (frame.jitCompiledSamples() > 0) {
                sampleTypesBuilder.setJit(frame.jitCompiledSamples());
            }
            frameBuilder.setSampleTypes(sampleTypesBuilder);
        }

        levelBuilders.get(level).addFrames(frameBuilder);

        // Process children
        for (Map.Entry<String, pbouda.jeffrey.frameir.Frame> e : frame.entrySet()) {
            pbouda.jeffrey.frameir.Frame child = e.getValue();
            if (level < MAX_LEVEL) {
                boolean markerCrossedLocal = markerCrossed || child.hasMarker();
                buildFrame(levelBuilders, e.getKey(), child, level + 1, leftSamples, leftWeight, markerCrossedLocal);
            }
            leftSamples += child.totalSamples();
            leftWeight += child.totalWeight();
        }
    }

    /**
     * Gets the index for a title from the pool, adding it if not present.
     */
    private int getOrAddTitle(String title) {
        return titleIndex.computeIfAbsent(title, t -> {
            int index = titlePool.size();
            titlePool.add(t);
            return index;
        });
    }

    /**
     * Maps Java FrameType enum to Protobuf FrameType enum.
     */
    private static pbouda.jeffrey.flamegraph.proto.FrameType mapFrameType(FrameType frameType) {
        return switch (frameType) {
            case C1_COMPILED -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_C1_COMPILED;
            case NATIVE -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_NATIVE;
            case CPP -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_CPP;
            case INTERPRETED -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_INTERPRETED;
            case JIT_COMPILED -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_JIT_COMPILED;
            case INLINED -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_INLINED;
            case KERNEL -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_KERNEL;
            case THREAD_NAME_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_THREAD_NAME_SYNTHETIC;
            case ALLOCATED_OBJECT_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC;
            case ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC;
            case ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC;
            case BLOCKING_OBJECT_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC;
            case LAMBDA_SYNTHETIC -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_LAMBDA_SYNTHETIC;
            case HIGHLIGHTED_WARNING -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_HIGHLIGHTED_WARNING;
            case UNKNOWN -> pbouda.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_UNKNOWN;
        };
    }
}

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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.proto.DiffDetails;
import cafe.jeffrey.flamegraph.proto.FlamegraphData;
import cafe.jeffrey.flamegraph.proto.Frame;
import cafe.jeffrey.flamegraph.proto.Level;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.shared.common.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Formats differential flamegraph data in Protocol Buffers format.
 * This provides ~50-60% size reduction compared to JSON.
 */
public class DiffgraphProtoFormatter {

    private final DiffFrame diffFrame;
    private final long minSamples;

    // Title pool for string deduplication
    private final List<String> titlePool = new ArrayList<>();
    private final Map<String, Integer> titleIndex = new HashMap<>();

    public DiffgraphProtoFormatter(DiffFrame diffFrame, double minFrameThresholdPct) {
        this.diffFrame = diffFrame;

        long totalSamples = diffFrame.secondarySamples + diffFrame.primarySamples;
        this.minSamples = (long) (totalSamples * minFrameThresholdPct / 100);
    }

    public FlamegraphData format() {
        List<Level.Builder> levelBuilders = new ArrayList<>();
        walkLayer(levelBuilders, diffFrame, 0, 0, 0);

        FlamegraphData.Builder dataBuilder = FlamegraphData.newBuilder()
                .setDepth(levelBuilders.size());

        for (Level.Builder levelBuilder : levelBuilders) {
            dataBuilder.addLevels(levelBuilder);
        }

        dataBuilder.addAllTitlePool(titlePool);

        return dataBuilder.build();
    }

    private void walkLayer(List<Level.Builder> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        switch (diffFrame.type) {
            case REMOVED -> removedSubtree(out, diffFrame, layer, leftSamples, leftWeight);
            case ADDED -> addedSubtree(out, diffFrame, layer, leftSamples, leftWeight);
            case SHARED -> {
                checkAndAddLayer(out, layer);

                Frame.Builder frameBuilder = Frame.newBuilder()
                        .setLeftSamples(leftSamples)
                        .setLeftWeight(leftWeight)
                        .setTotalSamples(diffFrame.samples())
                        .setTotalWeight(diffFrame.weight())
                        .setType(mapFrameType(diffFrame.frameType))
                        .setTitleIndex(getOrAddTitle(StringUtils.escape(diffFrame.methodName)))
                        .setDiffDetails(resolveDetail(diffFrame));

                out.get(layer).addFrames(frameBuilder);

                for (Map.Entry<String, DiffFrame> e : diffFrame.entrySet()) {
                    DiffFrame child = e.getValue();
                    if (child.samples() > minSamples) {
                        walkLayer(out, child, layer + 1, leftSamples, leftWeight);
                    }
                    leftSamples += child.samples();
                    leftWeight += child.weight();
                }
            }
        }
    }

    private static float roundDecimalPlaces(float shifter, float pct) {
        return (float) Math.round(pct * shifter) / shifter;
    }

    private static float toPercent(long primary, long secondary) {
        long total = primary + secondary;
        long diff = Math.abs(primary - secondary);
        return (float) diff / total;
    }

    private static DiffDetails resolveDetail(DiffFrame diffFrame) {
        return DiffDetails.newBuilder()
                .setSamples(diffFrame.primarySamples - diffFrame.secondarySamples)
                .setWeight(diffFrame.primaryWeight - diffFrame.secondaryWeight)
                .setPercentSamples(roundDecimalPlaces(100f, toPercent(diffFrame.primarySamples, diffFrame.secondarySamples) * 100f))
                .setPercentWeight(roundDecimalPlaces(100f, toPercent(diffFrame.primaryWeight, diffFrame.secondaryWeight) * 100f))
                .build();
    }

    private static void checkAndAddLayer(List<Level.Builder> out, int layer) {
        while (out.size() <= layer) {
            out.add(Level.newBuilder());
        }
    }

    private void removedSubtree(List<Level.Builder> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        processSubtree(out, diffFrame.frame, diffFrame.methodName, layer, leftSamples, leftWeight, false);
    }

    private void addedSubtree(List<Level.Builder> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        processSubtree(out, diffFrame.frame, diffFrame.methodName, layer, leftSamples, leftWeight, true);
    }

    private void processSubtree(
            List<Level.Builder> out,
            cafe.jeffrey.frameir.Frame frame,
            String methodName,
            int layer,
            long leftSamples,
            long leftWeight,
            boolean added) {

        checkAndAddLayer(out, layer);

        Frame.Builder frameBuilder = Frame.newBuilder()
                .setLeftSamples(leftSamples)
                .setLeftWeight(leftWeight)
                .setTotalSamples(frame.totalSamples())
                .setTotalWeight(frame.totalWeight())
                .setType(mapFrameType(frame.frameType()))
                .setTitleIndex(getOrAddTitle(cafe.jeffrey.shared.common.StringUtils.escape(methodName)));

        // Only include selfSamples when non-zero
        if (frame.selfSamples() > 0) {
            frameBuilder.setSelfSamples(frame.selfSamples());
        }

        long samples = frame.totalSamples();
        if (!added) {
            samples = ~samples + 1;
        }

        long weight = frame.totalWeight();
        if (!added) {
            weight = ~weight + 1;
        }

        DiffDetails details = DiffDetails.newBuilder()
                .setSamples(samples)
                .setWeight(weight)
                .setPercentSamples(100)
                .setPercentWeight(100)
                .build();

        frameBuilder.setDiffDetails(details);

        out.get(layer).addFrames(frameBuilder);

        for (Map.Entry<String, cafe.jeffrey.frameir.Frame> e : frame.entrySet()) {
            cafe.jeffrey.frameir.Frame child = e.getValue();
            String method = e.getKey();
            if (child.totalSamples() > minSamples) {
                processSubtree(out, child, method, layer + 1, leftSamples, leftWeight, added);
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
    private static cafe.jeffrey.flamegraph.proto.FrameType mapFrameType(FrameType frameType) {
        return switch (frameType) {
            case C1_COMPILED -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_C1_COMPILED;
            case NATIVE -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_NATIVE;
            case CPP -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_CPP;
            case INTERPRETED -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_INTERPRETED;
            case JIT_COMPILED -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_JIT_COMPILED;
            case INLINED -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_INLINED;
            case KERNEL -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_KERNEL;
            case THREAD_NAME_SYNTHETIC -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_THREAD_NAME_SYNTHETIC;
            case ALLOCATED_OBJECT_SYNTHETIC ->
                    cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_SYNTHETIC;
            case ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC ->
                    cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_IN_NEW_TLAB_SYNTHETIC;
            case ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC ->
                    cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_ALLOCATED_OBJECT_OUTSIDE_TLAB_SYNTHETIC;
            case BLOCKING_OBJECT_SYNTHETIC ->
                    cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_BLOCKING_OBJECT_SYNTHETIC;
            case LAMBDA_SYNTHETIC -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_LAMBDA_SYNTHETIC;
            case COLLAPSED_SYNTHETIC -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_COLLAPSED_SYNTHETIC;
            case HIGHLIGHTED_WARNING -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_HIGHLIGHTED_WARNING;
            case UNKNOWN -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_UNKNOWN;
        };
    }
}

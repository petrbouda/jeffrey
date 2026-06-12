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

    private static final String TRUNCATED_TITLE = "Truncated";

    private final DiffFrame diffFrame;
    private final boolean useWeight;
    private final long minMetric;

    // Title pool for string deduplication
    private final List<String> titlePool = new ArrayList<>();
    private final Map<String, Integer> titleIndex = new HashMap<>();

    public DiffgraphProtoFormatter(DiffFrame diffFrame, double minFrameThresholdPct, boolean useWeight) {
        this.diffFrame = diffFrame;
        this.useWeight = useWeight;

        long rootMetric = useWeight
                ? diffFrame.secondaryWeight + diffFrame.primaryWeight
                : diffFrame.secondarySamples + diffFrame.primarySamples;
        this.minMetric = (long) (rootMetric * minFrameThresholdPct / 100);
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

                long prunedSamples = 0;
                long prunedWeight = 0;
                long prunedPrimarySamples = 0;
                long prunedSecondarySamples = 0;
                long prunedPrimaryWeight = 0;
                long prunedSecondaryWeight = 0;
                int prunedChildrenCount = 0;
                for (Map.Entry<String, DiffFrame> e : diffFrame.entrySet()) {
                    DiffFrame child = e.getValue();
                    long childMetric = useWeight ? child.weight() : child.samples();
                    if (childMetric >= minMetric) {
                        walkLayer(out, child, layer + 1, leftSamples, leftWeight);
                        leftSamples += child.samples();
                        leftWeight += child.weight();
                    } else {
                        prunedSamples += child.samples();
                        prunedWeight += child.weight();
                        prunedPrimarySamples += child.primarySamples;
                        prunedSecondarySamples += child.secondarySamples;
                        prunedPrimaryWeight += child.primaryWeight;
                        prunedSecondaryWeight += child.secondaryWeight;
                        prunedChildrenCount++;
                    }
                }
                if (prunedChildrenCount > 0) {
                    emitTruncatedShared(
                            out, layer + 1, leftSamples, leftWeight,
                            prunedSamples, prunedWeight,
                            prunedPrimarySamples, prunedSecondarySamples,
                            prunedPrimaryWeight, prunedSecondaryWeight,
                            prunedChildrenCount);
                }
            }
        }
    }

    private void emitTruncatedShared(
            List<Level.Builder> out, int layer,
            long leftSamples, long leftWeight,
            long totalSamples, long totalWeight,
            long primarySamples, long secondarySamples,
            long primaryWeight, long secondaryWeight,
            int prunedChildrenCount) {

        checkAndAddLayer(out, layer);
        DiffDetails details = DiffDetails.newBuilder()
                .setSamples(primarySamples - secondarySamples)
                .setWeight(primaryWeight - secondaryWeight)
                .setPercentSamples(roundDecimalPlaces(100f, pctVsBaseline(primarySamples, secondarySamples)))
                .setPercentWeight(roundDecimalPlaces(100f, pctVsBaseline(primaryWeight, secondaryWeight)))
                .setSecondarySamples(secondarySamples)
                .setSecondaryWeight(secondaryWeight)
                .build();
        Frame.Builder synthetic = Frame.newBuilder()
                .setLeftSamples(leftSamples)
                .setLeftWeight(leftWeight)
                .setTotalSamples(totalSamples)
                .setTotalWeight(totalWeight)
                .setType(cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC)
                .setTitleIndex(getOrAddTitle(TRUNCATED_TITLE))
                .setSelfSamples(totalSamples)
                .setPrunedChildrenCount(prunedChildrenCount)
                .setDiffDetails(details);
        out.get(layer).addFrames(synthetic);
    }

    private static float roundDecimalPlaces(float shifter, float pct) {
        return (float) Math.round(pct * shifter) / shifter;
    }

    // Signed percentage change vs baseline: (primary - secondary) / secondary * 100.
    // Returns +Infinity when secondary == 0 (ADDED frames) so the frontend can render "new".
    private static float pctVsBaseline(long primary, long secondary) {
        if (secondary == 0) {
            return Float.POSITIVE_INFINITY;
        }
        return ((float) (primary - secondary) / secondary) * 100f;
    }

    private static DiffDetails resolveDetail(DiffFrame diffFrame) {
        return DiffDetails.newBuilder()
                .setSamples(diffFrame.primarySamples - diffFrame.secondarySamples)
                .setWeight(diffFrame.primaryWeight - diffFrame.secondaryWeight)
                .setPercentSamples(roundDecimalPlaces(100f, pctVsBaseline(diffFrame.primarySamples, diffFrame.secondarySamples)))
                .setPercentWeight(roundDecimalPlaces(100f, pctVsBaseline(diffFrame.primaryWeight, diffFrame.secondaryWeight)))
                .setSecondarySamples(diffFrame.secondarySamples)
                .setSecondaryWeight(diffFrame.secondaryWeight)
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
        long weight = frame.totalWeight();

        DiffDetails.Builder details = DiffDetails.newBuilder();
        if (added) {
            // primary - secondary = X - 0 = X; baseline is zero, % vs baseline is +Infinity.
            details.setSamples(samples)
                    .setWeight(weight)
                    .setPercentSamples(Float.POSITIVE_INFINITY)
                    .setPercentWeight(Float.POSITIVE_INFINITY)
                    .setSecondarySamples(0L)
                    .setSecondaryWeight(0L);
        } else {
            // primary - secondary = 0 - X = -X; % vs baseline = (0 - X)/X = -100.
            details.setSamples(-samples)
                    .setWeight(-weight)
                    .setPercentSamples(-100f)
                    .setPercentWeight(-100f)
                    .setSecondarySamples(samples)
                    .setSecondaryWeight(weight);
        }

        frameBuilder.setDiffDetails(details.build());

        out.get(layer).addFrames(frameBuilder);

        long prunedSamples = 0;
        long prunedWeight = 0;
        int prunedChildrenCount = 0;
        for (Map.Entry<String, cafe.jeffrey.frameir.Frame> e : frame.entrySet()) {
            cafe.jeffrey.frameir.Frame child = e.getValue();
            String method = e.getKey();
            long childMetric = useWeight ? child.totalWeight() : child.totalSamples();
            if (childMetric >= minMetric) {
                processSubtree(out, child, method, layer + 1, leftSamples, leftWeight, added);
                leftSamples += child.totalSamples();
                leftWeight += child.totalWeight();
            } else {
                prunedSamples += child.totalSamples();
                prunedWeight += child.totalWeight();
                prunedChildrenCount++;
            }
        }
        if (prunedChildrenCount > 0) {
            emitTruncatedSingleSided(out, layer + 1, leftSamples, leftWeight, prunedSamples, prunedWeight, prunedChildrenCount, added);
        }
    }

    private void emitTruncatedSingleSided(
            List<Level.Builder> out, int layer,
            long leftSamples, long leftWeight,
            long totalSamples, long totalWeight,
            int prunedChildrenCount, boolean added) {

        checkAndAddLayer(out, layer);
        DiffDetails.Builder details = DiffDetails.newBuilder();
        if (added) {
            details.setSamples(totalSamples)
                    .setWeight(totalWeight)
                    .setPercentSamples(Float.POSITIVE_INFINITY)
                    .setPercentWeight(Float.POSITIVE_INFINITY)
                    .setSecondarySamples(0L)
                    .setSecondaryWeight(0L);
        } else {
            details.setSamples(-totalSamples)
                    .setWeight(-totalWeight)
                    .setPercentSamples(-100f)
                    .setPercentWeight(-100f)
                    .setSecondarySamples(totalSamples)
                    .setSecondaryWeight(totalWeight);
        }
        Frame.Builder synthetic = Frame.newBuilder()
                .setLeftSamples(leftSamples)
                .setLeftWeight(leftWeight)
                .setTotalSamples(totalSamples)
                .setTotalWeight(totalWeight)
                .setType(cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC)
                .setTitleIndex(getOrAddTitle(TRUNCATED_TITLE))
                .setSelfSamples(totalSamples)
                .setPrunedChildrenCount(prunedChildrenCount)
                .setDiffDetails(details.build());
        out.get(layer).addFrames(synthetic);
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
            case TRUNCATED_SYNTHETIC -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_TRUNCATED_SYNTHETIC;
            case HIGHLIGHTED_WARNING -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_HIGHLIGHTED_WARNING;
            case UNKNOWN -> cafe.jeffrey.flamegraph.proto.FrameType.FRAME_TYPE_UNKNOWN;
        };
    }
}

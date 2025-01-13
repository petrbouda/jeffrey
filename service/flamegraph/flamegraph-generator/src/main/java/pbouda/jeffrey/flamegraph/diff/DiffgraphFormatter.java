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

package pbouda.jeffrey.flamegraph.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.frameir.DiffFrame;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.common.model.profile.FrameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiffgraphFormatter {

    private static final double MIN_SAMPLES_IN_PCT = 0.1;
    private static final double MAX_LEVEL = 1000;

    private static final String[] GREEN_COLORS = {
            "#E5FFCC",
            "#E5FFBB",
            "#CCFF99",
            "#B2FF66",
            "#99FF33",
            "#66CC00",
    };

    private static final String[] RED_COLORS = {
            "#FFEEEE",
            "#FFDDDD",
            "#FFCCCC",
            "#FFAAAA",
            "#FF8888",
            "#FF3333",
    };

    private static final String NEUTRAL_COLOR = "#E6E6E6";
    private static final String REMOVED_COLOR = GREEN_COLORS[GREEN_COLORS.length - 1];
    private static final String ADDED_COLOR = RED_COLORS[RED_COLORS.length - 1];

    private final DiffFrame diffFrame;
    private final long minSamples;

    public DiffgraphFormatter(DiffFrame diffFrame) {
        this.diffFrame = diffFrame;

        long totalSamples = diffFrame.secondarySamples + diffFrame.primarySamples;
        this.minSamples = (long) (totalSamples * MIN_SAMPLES_IN_PCT / 100);
    }

    public ObjectNode format() {
        List<List<ObjectNode>> output = new ArrayList<>();
        walkLayer(output, diffFrame, 0, 0, 0);

        int depth = output.size();
        ObjectNode data = Json.createObject()
                .put("depth", depth);

        data.set("levels", Json.mapper().valueToTree(output));
        return data;
    }

    private void walkLayer(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        switch (diffFrame.type) {
            case REMOVED -> removedSubtree(out, diffFrame, layer, leftSamples, leftWeight);
            case ADDED -> addedSubtree(out, diffFrame, layer, leftSamples, leftWeight);
            case SHARED -> {
                checkAndAddLayer(out, layer);

                ObjectNode jsonFrame = Json.createObject()
                        .put("leftSamples", leftSamples)
                        .put("leftWeight", leftWeight)
                        .put("totalSamples", diffFrame.samples())
                        .put("totalWeight", diffFrame.weight())
                        .put("colorSamples", resolveColor(diffFrame.frameType, diffFrame.primarySamples, diffFrame.secondarySamples))
                        .put("colorWeight", resolveColor(diffFrame.frameType, diffFrame.primaryWeight, diffFrame.secondaryWeight))
                        .put("title", StringUtils.escape(diffFrame.methodName));
                jsonFrame.set("diffDetails", resolveDetail(diffFrame));

                List<ObjectNode> layerNodes = out.get(layer);
                layerNodes.add(jsonFrame);

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

    private static String resolveColor(FrameType frameType, long primary, long secondary) {
        if (frameType == FrameType.LAMBDA_SYNTHETIC) {
            return frameType.color();
        }

        float pct = roundDecimalPlaces(10000f, toPercent(primary, secondary));

        int index;
        if (pct <= 0.02) {
            return NEUTRAL_COLOR;
        } else if (pct <= 0.05) {
            index = 0;
        } else if (pct <= 0.1) {
            index = 1;
        } else if (pct <= 0.4) {
            index = 2;
        } else if (pct <= 0.8) {
            index = 3;
        } else {
            index = 4;
        }

        return primary < secondary ? GREEN_COLORS[index] : RED_COLORS[index];
    }

    private static float roundDecimalPlaces(float shifter, float pct) {
        return (float) Math.round(pct * shifter) / shifter;
    }

    private static float toPercent(long primary, long secondary) {
        long total = primary + secondary;
        long diff = Math.abs(primary - secondary);
        return (float) diff / total;
    }

    private static JsonNode resolveDetail(DiffFrame diffFrame) {
        return Json.createObject()
                .put("samples", diffFrame.primarySamples - diffFrame.secondarySamples)
                .put("weight", diffFrame.primaryWeight - diffFrame.secondaryWeight)
                .put("percentSamples", roundDecimalPlaces(100f, toPercent(diffFrame.primarySamples, diffFrame.secondarySamples) * 100f))
                .put("percentWeight", roundDecimalPlaces(100f, toPercent(diffFrame.primaryWeight, diffFrame.secondaryWeight) * 100f));
    }

    /**
     * Create layers lazily in cost of bounds checks. Otherwise, we would need to go over two graphs to get the depth.
     *
     * @param out   all nodes in the final graph.
     * @param layer a current requested level.
     */
    private static void checkAndAddLayer(List<List<ObjectNode>> out, int layer) {
        if (out.size() <= layer) {
            out.add(new ArrayList<>());
        }
    }

    private void removedSubtree(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        oneColorSubtree(out, diffFrame.frame, diffFrame.methodName, layer, leftSamples, leftWeight, REMOVED_COLOR, false);
    }

    private void addedSubtree(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long leftSamples, long leftWeight) {
        oneColorSubtree(out, diffFrame.frame, diffFrame.methodName, layer, leftSamples, leftWeight, ADDED_COLOR, true);
    }

    private void oneColorSubtree(
            List<List<ObjectNode>> out,
            Frame frame,
            String methodName,
            int layer,
            long leftSamples,
            long leftWeight,
            String color,
            boolean added) {

        checkAndAddLayer(out, layer);

        ObjectNode jsonFrame = Json.createObject()
                .put("leftSamples", leftSamples)
                .put("leftWeight", leftWeight)
                .put("totalSamples", frame.totalSamples())
                .put("selfSamples", frame.selfSamples())
                .put("colorSamples", color)
                .put("colorWeight", color)
                .put("title", StringUtils.escape(methodName));

        long samples = frame.totalSamples();
        if (!added) {
            samples = ~samples + 1;
        }

        long weight = frame.totalSamples();
        if (!added) {
            weight = ~weight + 1;
        }

        ObjectNode details = Json.createObject()
                .put("samples", samples)
                .put("weight", weight)
                .put("percentSamples", 100)
                .put("percentWeight", 100);

        jsonFrame.set("details", details);

        List<ObjectNode> layerNodes = out.get(layer);
        layerNodes.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            String method = e.getKey();
            if (child.totalSamples() > minSamples && MAX_LEVEL > layer) {
                oneColorSubtree(out, child, method, layer + 1, leftSamples, leftWeight, color, added);
            }
            leftSamples += child.totalSamples();
            leftWeight += child.totalWeight();
        }
    }
}

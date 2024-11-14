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

package pbouda.jeffrey.generator.flamegraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.common.analysis.AnalysisResult;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.generator.flamegraph.diff.StringUtils;

import java.util.Map;
import java.util.function.Function;

public class FlameGraphBuilder implements GraphBuilder<Frame, ObjectNode> {

    private static final double MAX_LEVEL = 1000;

    private final boolean withMarker;
    private final boolean withWeight;
    private final Function<Long, String> weightFormatter;

    public FlameGraphBuilder(boolean withMarker) {
        this(withMarker, null);
    }

    public FlameGraphBuilder(boolean withMarker, Function<Long, String> weightFormatter) {
        this(withMarker, weightFormatter != null, weightFormatter);
    }

    public FlameGraphBuilder(boolean withMarker, boolean withWeight, Function<Long, String> weightFormatter) {
        this.withMarker = withMarker;
        this.withWeight = withWeight;
        this.weightFormatter = weightFormatter;
    }

    public ObjectNode build(Frame root) {
        int depth = root.depth(0);
        ArrayNode layers = Json.createArray();
        for (int i = 0; i < depth; i++) {
            layers.add(Json.createArray());
        }

        if (withWeight) {
            printFrameJson(layers, root.totalSamples() + " Event(s), " + weightFormatter.apply(root.totalWeight()), root, 0, 0, 0, false);
        } else {
            printFrameJson(layers, root.totalSamples() + " Event(s)", root, 0, 0, 0, false);
        }

        ObjectNode result = Json.createObject()
                .put("depth", depth);
        result.set("levels", layers);
        return result;
    }

    private void printFrameJson(
            ArrayNode layers,
            String title,
            Frame frame,
            int level,
            long leftSamples,
            long leftWeight,
            boolean markerCrossed) {

        ObjectNode jsonFrame = Json.createObject()
                .put("leftSamples", leftSamples)
                .put("leftWeight", leftWeight)
                .put("totalWeight", frame.totalWeight())
                .put("totalSamples", frame.totalSamples())
                .put("selfWeight", frame.selfWeight())
                .put("selfSamples", frame.selfSamples())
                .put("type", frame.frameType().toString())
                .put("typeTitle", frame.frameType().title())
                .put("colorSamples", resolveColor(frame, markerCrossed))
                .put("colorWeight", resolveColor(frame, markerCrossed))
                .put("title", StringUtils.escape(title));

        jsonFrame.set("sampleTypes", frameTypes(frame));
        jsonFrame.set("position", position(frame));

        ArrayNode nodesInLayer = (ArrayNode) layers.get(level);
        nodesInLayer.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (level < MAX_LEVEL) {
                boolean markerCrossedLocal = markerCrossed || child.hasMarker();
                printFrameJson(layers, e.getKey(), child, level + 1, leftSamples, leftWeight, markerCrossedLocal);
            }
            leftSamples += child.totalSamples();
            leftWeight += child.totalWeight();
        }
    }

    private String resolveColor(Frame frame, boolean markerCrossed) {
        if (!withMarker || markerCrossed) {
            return frame.frameType().color();
        } else  {
            return AnalysisResult.Severity.IGNORE.color();
        }
    }

    private static JsonNode position(Frame frame) {
        if (frame.bci() == 0) {
            return Json.mapper().nullNode();
        }

        ObjectNode detail = Json.createObject();
        if (frame.bci() > 0) {
            detail.put("bci", frame.bci());
        }
        if (frame.lineNumber() > 0) {
            detail.put("line", frame.lineNumber());
        }
        return detail;
    }

    private static JsonNode frameTypes(Frame frame) {
        if (frame.inlinedSamples() == 0
                && frame.c1Samples() == 0
                && frame.jitCompiledSamples() == 0
                && frame.interpretedSamples() == 0) {
            return Json.mapper().nullNode();
        }

        ObjectNode detail = Json.createObject();
        if (frame.inlinedSamples() > 0) {
            detail.put("inlined", frame.inlinedSamples());
        }
        if (frame.c1Samples() > 0) {
            detail.put("c1", frame.c1Samples());
        }
        if (frame.interpretedSamples() > 0) {
            detail.put("interpret", frame.interpretedSamples());
        }
        if (frame.jitCompiledSamples() > 0) {
            detail.put("jit", frame.jitCompiledSamples());
        }
        return detail;
    }
}

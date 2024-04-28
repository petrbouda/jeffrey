package pbouda.jeffrey.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.graph.diff.StringUtils;

import java.util.Map;

public class FlameGraphBuilder {

    private static final double MAX_LEVEL = 1000;

    public ObjectNode dumpToJson(Frame root) {
        int depth = root.depth(0);
        ArrayNode layers = Json.createArray();
        for (int i = 0; i < depth; i++) {
            layers.add(Json.createArray());
        }

        printFrameJson(layers, "all", root, 0, 0);

        ObjectNode result = Json.createObject()
                .put("depth", depth);
        result.set("levels", layers);
        return result;
    }

    private void printFrameJson(ArrayNode layers, String title, Frame frame, int level, long x) {
        ObjectNode jsonFrame = Json.createObject()
                .put("left", x)
                .put("total", frame.totalWeight())
                .put("self", frame.selfWeight())
                .put("type", frame.frameType().toString())
                .put("color", frame.frameType().color())
                .put("title", StringUtils.escape(title));

        jsonFrame.set("sample_types", frameTypes(frame));
        jsonFrame.set("position", position(frame));

        ArrayNode nodesInLayer = (ArrayNode) layers.get(level);
        nodesInLayer.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (level < MAX_LEVEL) {
                printFrameJson(layers, e.getKey(), child, level + 1, x);
            }
            x += child.totalWeight();
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
        if (frame.inlinedWeight() == 0
                && frame.c1Weight() == 0
                && frame.jitCompiledWeight() == 0
                && frame.interpretedWeight() == 0) {
            return Json.mapper().nullNode();
        }

        ObjectNode detail = Json.createObject();
        if (frame.inlinedWeight() > 0) {
            detail.put("inlined", frame.inlinedWeight());
        }
        if (frame.c1Weight() > 0) {
            detail.put("c1", frame.c1Weight());
        }
        if (frame.interpretedWeight() > 0) {
            detail.put("interpret", frame.interpretedWeight());
        }
        if (frame.jitCompiledWeight() > 0) {
            detail.put("jit", frame.jitCompiledWeight());
        }
        return detail;
    }
}

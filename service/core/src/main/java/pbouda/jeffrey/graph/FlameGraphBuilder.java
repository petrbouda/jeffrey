package pbouda.jeffrey.graph;

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
                .put("width", frame.totalWeight())
                .put("color", frame.frameType().color())
                .put("title", StringUtils.escape(title))
                .put("details", generateDetail(frame.inlinedWeight(), frame.c1Weight(), frame.interpretedWeight()));

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

    private static String generateDetail(long inlined, long c1, long interpreted) {
        if (inlined != 0 || c1 != 0 || interpreted != 0) {
            StringBuilder output = new StringBuilder();
            output.append("\nTypes:");
            if (inlined != 0) {
                output.append(" int=").append(inlined);
            }
            if (c1 != 0) {
                output.append(" c1=").append(c1);
            }
            if (interpreted != 0) {
                output.append(" int=").append(interpreted);
            }
            return output.toString();
        }

        return "";
    }
}

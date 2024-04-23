package pbouda.jeffrey.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.graph.diff.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class FlameGraphBuilder {

    private static final double MAX_LEVEL = 1000;

    public ObjectNode dumpToJson(Frame root) {
//        if (MIN_SAMPLES_IN_PCT > 0) {
//            minTotal = (long) (root.samples * MIN_SAMPLES_IN_PCT / 100);
//        } else {
//            minTotal = 0;
//        }

        int depth = root.depth(0);

        List<List<ObjectNode>> levels = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            levels.add(new ArrayList<>());
        }

        printFrameJson(levels, "all", root, 0, 0);

        ObjectNode data = Json.createObject()
                .put("depth", depth);

        data.set("levels", Json.mapper().valueToTree(levels));
        return data;
    }

    private void printFrameJson(List<List<ObjectNode>> out, String title, Frame frame, int level, long x) {
        ObjectNode jsonFrame = Json.createObject()
                .put("left", x)
                .put("width", frame.totalWeight())
                .put("color", frame.frameType().color())
                .put("title", StringUtils.escape(title))
                .put("details", generateDetail(frame.inlinedWeight(), frame.c1Weight(), frame.interpretedWeight()));

        List<ObjectNode> nodesInLayer = out.get(level);
        nodesInLayer.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (level < MAX_LEVEL) {
                printFrameJson(out, e.getKey(), child, level + 1, x);
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

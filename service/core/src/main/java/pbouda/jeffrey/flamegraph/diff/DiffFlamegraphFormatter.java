package pbouda.jeffrey.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.Frame;
import pbouda.jeffrey.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DiffFlamegraphFormatter {

    private static final String[] GREEN_COLORS = {
            "#E5FFCC",
            "#CCFF99",
            "#B2FF66",
            "#99FF33",
            "#66CC00",
    };

    private static final String[] RED_COLORS = {
            "#FFCCCC",
            "#FF9999",
            "#FF6666",
            "#FF3333",
            "#CC0000",
    };

    private static final String NEUTRAL_COLOR = "#E6E6E6";
    private static final String REMOVED_COLOR = GREEN_COLORS[GREEN_COLORS.length - 1];
    private static final String ADDED_COLOR = RED_COLORS[RED_COLORS.length - 1];

    private final DiffTree diffTree;

    public DiffFlamegraphFormatter(DiffTree diffTree) {
        this.diffTree = diffTree;
    }

    public ObjectNode format() {
        List<List<ObjectNode>> output = new ArrayList<>();
        walkLayer(output, diffTree.frame(), 0, 0);

        int depth = output.size();
        ObjectNode data = Json.createObject()
                .put("depth", depth);

        data.set("levels", Json.mapper().valueToTree(output));
        return data;
    }

    private void walkLayer(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long x) {
        switch (diffFrame.type) {
            case REMOVED -> removedSubtree(out, diffFrame, layer, x);
            case ADDED -> addedSubtree(out, diffFrame, layer, x);
            case SHARED -> {
                checkAndAddLayer(out, layer);

                ObjectNode jsonFrame = Json.createObject()
                        .put("left", x)
                        .put("width", diffFrame.total())
                        .put("color", resolveColor(diffFrame))
                        .put("title", StringUtils.escape(diffFrame.methodName))
                        .put("details", resolveDetail(diffFrame));

                List<ObjectNode> layerNodes = out.get(layer);
                layerNodes.add(jsonFrame);

                for (Map.Entry<String, DiffFrame> e : diffFrame.entrySet()) {
                    DiffFrame child = e.getValue();
                    walkLayer(out, child, layer + 1, x);
                    x += child.total();
                }
            }
        }
    }

    private static String resolveColor(DiffFrame diffFrame) {
        long baselineSamples = diffFrame.baselineTotal;
        long comparisonSamples = diffFrame.comparisonTotal;

        long total = baselineSamples + comparisonSamples;
        long diff = Math.abs(baselineSamples - comparisonSamples);
        float pct = (float) diff / total;

        float result = (float) Math.round(pct * 100f) / 100f;

        int index;
        if (result <= 0.02) {
            return NEUTRAL_COLOR;
        } else if (result <= 0.1) {
            index = 0;
        } else if (result <= 0.4) {
            index = 1;
        } else if (result <= 0.8) {
            index = 2;
        } else {
            index = 3;
        }

        return baselineSamples > comparisonSamples
                ? GREEN_COLORS[index]
                : RED_COLORS[index];
    }

    private static String resolveDetail(DiffFrame diffFrame) {
        return ", diff=" + (diffFrame.comparisonTotal - diffFrame.baselineTotal);
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

    private static void removedSubtree(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long x) {
        oneColorSubtree(out, diffFrame.frame, diffFrame.methodName, layer, x, REMOVED_COLOR, "Removed");
    }

    private static void addedSubtree(List<List<ObjectNode>> out, DiffFrame diffFrame, int layer, long x) {
        oneColorSubtree(out, diffFrame.frame, diffFrame.methodName, layer, x, ADDED_COLOR, "Added");
    }

    private static void oneColorSubtree(List<List<ObjectNode>> out, Frame frame, String methodName, int layer, long x, String color, String detailPrefix) {
        checkAndAddLayer(out, layer);

        ObjectNode jsonFrame = Json.createObject()
                .put("left", x)
                .put("width", frame.total)
                .put("color", color)
                .put("title", StringUtils.escape(methodName))
                .put("details", detailPrefix + " " + frame.total + " samples");

        List<ObjectNode> layerNodes = out.get(layer);
        layerNodes.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            String method = e.getKey();
            oneColorSubtree(out, child, method, layer + 1, x, color, detailPrefix);
            x += child.total;
        }
    }

    private static String stripSuffix(String title) {
        return title.substring(0, title.length() - 4);
    }
}

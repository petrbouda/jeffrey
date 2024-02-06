package pbouda.jeffrey.flamegraph.diff;

import com.fasterxml.jackson.databind.node.ObjectNode;
import one.Frame;
import pbouda.jeffrey.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static one.FrameType.FRAME_KERNEL;

public class DiffFlamegraphFormatter {

    private final DiffFrame diffFrame;

    public DiffFlamegraphFormatter(DiffFrame diffFrame) {
        this.diffFrame = diffFrame;
    }

    public ObjectNode format() {
        int type = frame.getType();
        if (type == FRAME_KERNEL) {
            title = stripSuffix(title);
        }

        ObjectNode jsonFrame = Json.createObject()
                .put("left", x)
                .put("width", frame.total)
                .put("color", COLORS[type])
                .put("title", escape(title))
                .put("details", generateDetail(frame.inlined, frame.c1, frame.interpreted));

        List<ObjectNode> nodesInLayer = out.get(level);
        nodesInLayer.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (child.total >= mintotal) {
                printFrameJson(out, e.getKey(), child, level + 1, x);
            }
            x += child.total;
        }
    }

    private void walkLayer(List<List<ObjectNode>> out, DiffFrame diffFrame, long layer, long x) {
        List<ObjectNode> layerNodes = new ArrayList<>();
        out.add(layerNodes);

        switch (diffFrame.type) {
            case REMOVED -> {
                out.add(removedSubtree(diffFrame, layer, x));
            }
            case ADDED -> {
                out.add(addedSubtree(diffFrame, layer, x));
            }
            case MID -> {

            }
        }
    }

    private static List<ObjectNode> removedSubtree(DiffFrame diffFrame, long layer, long x) {
        return null;
    }

    private static List<ObjectNode> addedSubtree(DiffFrame diffFrame, long layer, long x) {
        return null;
    }

    private static String stripSuffix(String title) {
        return title.substring(0, title.length() - 4);
    }
}

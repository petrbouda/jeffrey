package one;

/*
 * Copyright 2020 Andrei Pangin
 * Modifications copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.Json;
import pbouda.jeffrey.graph.diff.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static one.FrameType.FRAME_KERNEL;
import static one.FrameType.FRAME_NATIVE;

public class FlameGraph {

    private static final double MIN_SAMPLES_IN_PCT = 0;

    private final Arguments args;
    private final Frame root = new Frame(FRAME_NATIVE);
    private int depth;
    private long minTotal;

    private static final String[] COLORS = {
            "#b2e1b2",
            "#50e150",
            "#50cccc",
            "#e15a5a",
            "#c8c83c",
            "#e17d00",
            "#cce880"
    };

    public FlameGraph(Arguments args) {
        this.args = args;
    }

    public void addSample(String[] trace, long ticks) {
        if (excludeTrace(trace)) {
            return;
        }

        Frame frame = root;
        if (args.reverse) {
            for (int i = trace.length; --i >= args.skip; ) {
                frame = frame.addChild(trace[i], ticks);
            }
        } else {
            for (int i = args.skip; i < trace.length; i++) {
                frame = frame.addChild(trace[i], ticks);
            }
        }
        frame.addLeaf(ticks);

        depth = Math.max(depth, trace.length);
    }

    public ObjectNode dumpToJson() {
        if (MIN_SAMPLES_IN_PCT > 0) {
            minTotal = (long) (root.samples * MIN_SAMPLES_IN_PCT / 100);
        } else {
            minTotal = 0;
        }

        int depth = root.depth(0);

        List<List<ObjectNode>> levels = new ArrayList<>();
        for (int i = 0; i < depth; i++) {
            levels.add(new ArrayList<>());
        }

        printFrameJson(levels, "all", root, 0, 0);

        ObjectNode data = Json.createObject()
                .put("title", args.title)
                .put("depth", depth);

        data.set("levels", Json.mapper().valueToTree(levels));
        return data;
    }

    public Frame getRoot() {
        return root;
    }

    private void printFrameJson(List<List<ObjectNode>> out, String title, Frame frame, int level, long x) {
        int type = frame.getType();
        if (type == FRAME_KERNEL) {
            title = stripSuffix(title);
        }

        ObjectNode jsonFrame = Json.createObject()
                .put("left", x)
                .put("width", frame.samples)
                .put("color", COLORS[type])
                .put("title", StringUtils.escape(title))
                .put("details", generateDetail(frame.inlined, frame.c1, frame.interpreted));

        List<ObjectNode> nodesInLayer = out.get(level);
        nodesInLayer.add(jsonFrame);

        for (Map.Entry<String, Frame> e : frame.entrySet()) {
            Frame child = e.getValue();
            if (child.samples >= minTotal) {
                printFrameJson(out, e.getKey(), child, level + 1, x);
            }
            x += child.samples;
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

    private boolean excludeTrace(String[] trace) {
        Pattern include = args.include;
        Pattern exclude = args.exclude;
        if (include == null && exclude == null) {
            return false;
        }

        for (String frame : trace) {
            if (exclude != null && exclude.matcher(frame).matches()) {
                return true;
            }
            if (include != null && include.matcher(frame).matches()) {
                include = null;
                if (exclude == null) break;
            }
        }

        return include != null;
    }

    static String stripSuffix(String title) {
        return title.substring(0, title.length() - 4);
    }
}

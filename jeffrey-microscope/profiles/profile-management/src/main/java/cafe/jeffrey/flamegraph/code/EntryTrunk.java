/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package cafe.jeffrey.flamegraph.code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where one operation's work enters the application's own code, read from all of its stacks together.
 * <p>
 * The outermost own frame of a stack is often not the handler: a worker loop, an own servlet filter or an
 * executor wrapper sits above it, and a callback the code hands a library (a transaction template, a
 * stream, a JDBC wrapper's body) is own code below one. No rule over a single stack tells them apart; the
 * stacks of one operation narrow it down. They share a trunk — the call path at least {@link #TRUNK_SHARE}
 * of their weight runs through — and on it the handler is the longest run of consecutive own frames (the
 * code calling its own code: controller, service, repository); runs of equal length are all reported, as
 * candidates, never chosen between by a guess. The entry point is a run's first frame, skipping lambda
 * bodies, which are a dispatch's shim, unless nothing else is left.
 * It is a reading of the stacks, not a fact the recording states. When the trunk holds no own frame, the
 * operation has no single entry and the caller falls back to each stack's outermost own frame.
 */
public final class EntryTrunk {

    /** The share of an operation's weight a call path must carry to count as its trunk. */
    public static final double TRUNK_SHARE = 0.9;

    private static final String JAVAC_LAMBDA_PREFIX = "lambda$";
    private static final String KOTLIN_LAMBDA_MARK = "$lambda";

    private final Node root = new Node(null, false);

    /**
     * Adds one stack, outermost frame first, with the weight it carries.
     *
     * @param frames the Java frames of the stack, root first, each with whether it is the application's own
     * @param value  the stack's weight (events, samples, bytes)
     */
    public void add(List<Frame> frames, long value) {
        if (value <= 0) {
            return;
        }
        Node node = root;
        node.value += value;
        for (Frame frame : frames) {
            node = node.children.computeIfAbsent(frame.method(), key -> new Node(key, frame.own()));
            node.value += value;
        }
    }

    /** The weight of every stack added: what each entry's share is taken of. */
    public long weight() {
        return root.value;
    }

    /**
     * The entry points and the weight through each: one, unless the trunk holds several equally long runs of
     * own frames — an own filter and a handler of one frame each, say — when every one of them is a candidate,
     * outermost first, rather than a guess between them. Empty when the trunk reaches no own code.
     */
    public List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (List<Node> run : longestRuns(trunk())) {
            Node entry = run.stream().filter(node -> !isLambda(node.method)).findFirst().orElse(run.getFirst());
            entries.add(new Entry(entry.method, entry.value));
        }
        return entries;
    }

    /** The call path at least {@link #TRUNK_SHARE} of the weight runs through, root first. */
    private List<Node> trunk() {
        List<Node> trunk = new ArrayList<>();
        if (root.value == 0) {
            return trunk;
        }
        long threshold = (long) Math.ceil(root.value * TRUNK_SHARE);
        Node node = root;
        while (true) {
            Node next = null;
            for (Node child : node.children.values()) {
                if (child.value >= threshold) {
                    next = child;
                }
            }
            if (next == null) {
                return trunk;
            }
            trunk.add(next);
            node = next;
        }
    }

    /**
     * The runs of consecutive own frames on the trunk that may hold the handler: the longest. An own filter
     * or worker loop above the handler, and a callback handed to a library below it, are each a short run
     * between library frames; the handler is where the code calls its own code in turn (controller,
     * service, repository). Several when the longest are of equal length. Empty when the trunk holds no
     * own frame.
     */
    private static List<List<Node>> longestRuns(List<Node> trunk) {
        List<List<Node>> runs = new ArrayList<>();
        List<Node> run = new ArrayList<>();
        for (Node node : trunk) {
            if (node.own) {
                run.add(node);
                continue;
            }
            if (!run.isEmpty()) {
                runs.add(run);
            }
            run = new ArrayList<>();
        }
        if (!run.isEmpty()) {
            runs.add(run);
        }
        int longest = runs.stream().mapToInt(List::size).max().orElse(0);
        return runs.stream().filter(candidate -> candidate.size() == longest).toList();
    }

    private static boolean isLambda(MethodKey method) {
        return method.methodName().startsWith(JAVAC_LAMBDA_PREFIX) || method.methodName().contains(KOTLIN_LAMBDA_MARK)
                || method.className().contains(KOTLIN_LAMBDA_MARK);
    }

    /**
     * One Java frame of a stack.
     *
     * @param method the method
     * @param own    whether it is the application's own code
     */
    public record Frame(MethodKey method, boolean own) {
    }

    /**
     * An operation's entry point.
     *
     * @param method the own method the operation enters through
     * @param value  the weight of the stacks that run through it on the trunk
     */
    public record Entry(MethodKey method, long value) {
    }

    private static final class Node {

        private final MethodKey method;
        private final boolean own;
        private final Map<MethodKey, Node> children = new HashMap<>();
        private long value;

        private Node(MethodKey method, boolean own) {
            this.method = method;
            this.own = own;
        }
    }
}

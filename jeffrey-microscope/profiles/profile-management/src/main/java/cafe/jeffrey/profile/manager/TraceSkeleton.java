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
package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.TraceWindow;
import cafe.jeffrey.provider.profile.api.TraceSpanShape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One trace's tree, built from the skinny shape of every span: the order the waterfall draws it
 * in, each span's depth and parent, and the critical path.
 * <p>
 * Built from {@link TraceSpanShape}s rather than full rows because it has to see every span — the
 * critical path is not right otherwise — while the full rows are only ever wanted for what is drawn.
 * A folded run is one entry in the draw order, placed among its siblings at its first member's
 * start; its members take part in the critical path one by one.
 * <p>
 * Two malformed shapes have to survive this without losing a span. A span whose parent is missing
 * — it fell below the event threshold, or was never instrumented — is promoted to a root, and its
 * dangling parent id is dropped so the tree the UI receives is self-consistent. A parent cycle,
 * which cannot arise from correct instrumentation, leaves its members unreachable from any root;
 * rather than dropping them, the traversal breaks into the earliest one and carries on until every
 * span has been placed.
 * <p>
 * Rows sharing an id are the one shape that does lose a span: a span id identifies a span, and the
 * derivation dedupes on it before the primary key enforces it. Only the first row of an id is kept
 * — a defence kept even though a well-formed database cannot produce the shape, because a trace must
 * render whatever the database holds.
 */
final class TraceSkeleton {

    private static final long NANOS_PER_MICRO = 1_000L;

    /** One entry of the draw order: a span drawn on its own, or a folded run drawn as one row. */
    sealed interface Slot permits SpanSlot, RunSlot {

        int depth();

        /** The parent the tree gives the entry, which a dangling or cyclic stored parent is not. */
        Long parentSpanId();
    }

    record SpanSlot(long spanId, int depth, Long parentSpanId) implements Slot {
    }

    record RunSlot(long runId, int depth, Long parentSpanId) implements Slot {
    }

    /** A node of the drawn tree, before it is placed: a span, or a whole run. */
    private sealed interface Node permits SpanNode, RunNode {

        long startEpochMicros();
    }

    private record SpanNode(TraceSpanShape span) implements Node {

        @Override
        public long startEpochMicros() {
            return span.startEpochMicros();
        }
    }

    private record RunNode(long runId, List<TraceSpanShape> members) implements Node {

        @Override
        public long startEpochMicros() {
            return members.getFirst().startEpochMicros();
        }
    }

    /** A node queued for placement, with the position the tree gives it. */
    private record Placement(Node node, int depth, Long parentSpanId) {
    }

    private final Map<Long, TraceSpanShape> byId;
    private final Map<Long, List<TraceSpanShape>> childrenByParent;
    private final Map<Long, List<TraceSpanShape>> membersByRun;
    private final Map<Long, Slot> slotBySpan;
    private final Map<Long, RunSlot> slotByRun;
    private final List<Slot> ordered;
    private final Map<Long, Long> criticalMicros;
    private final TraceWindow window;
    private final int threadCount;

    private TraceSkeleton(
            Map<Long, TraceSpanShape> byId,
            Map<Long, List<TraceSpanShape>> childrenByParent,
            Map<Long, List<TraceSpanShape>> membersByRun,
            List<Slot> ordered,
            Map<Long, Long> criticalMicros,
            TraceWindow window,
            int threadCount) {

        this.byId = byId;
        this.childrenByParent = childrenByParent;
        this.membersByRun = membersByRun;
        this.ordered = List.copyOf(ordered);
        this.criticalMicros = criticalMicros;
        this.window = window;
        this.threadCount = threadCount;

        this.slotBySpan = new HashMap<>();
        this.slotByRun = new HashMap<>();
        for (Slot slot : ordered) {
            switch (slot) {
                case SpanSlot span -> slotBySpan.put(span.spanId(), span);
                case RunSlot run -> slotByRun.put(run.runId(), run);
            }
        }
    }

    /**
     * @param shapes every span of the trace, members of folded runs tagged with their run; never
     *               empty
     */
    static TraceSkeleton of(List<TraceSpanShape> shapes) {
        if (shapes.isEmpty()) {
            throw new IllegalArgumentException("A trace has at least one span");
        }

        // Sorted here although the repository already orders by start: a stable sort of an ordered
        // list costs one pass, and every list below relies on start order for its siblings.
        List<TraceSpanShape> byStart = new ArrayList<>(shapes);
        byStart.sort(Comparator.comparingLong(TraceSpanShape::startEpochMicros));
        Map<Long, TraceSpanShape> byId = new LinkedHashMap<>();
        for (TraceSpanShape shape : byStart) {
            byId.putIfAbsent(shape.spanId(), shape);
        }

        List<TraceSpanShape> roots = new ArrayList<>();
        Map<Long, List<TraceSpanShape>> childrenByParent = new HashMap<>();
        Map<Long, List<TraceSpanShape>> membersByRun = new LinkedHashMap<>();
        Set<Long> threads = new HashSet<>();
        long from = Long.MAX_VALUE;
        long to = Long.MIN_VALUE;
        for (TraceSpanShape span : byId.values()) {
            Long parent = resolvedParent(span, byId);
            if (parent == null) {
                roots.add(span);
            } else {
                childrenByParent.computeIfAbsent(parent, _ -> new ArrayList<>()).add(span);
            }
            if (span.runId() != null) {
                membersByRun.computeIfAbsent(span.runId(), _ -> new ArrayList<>()).add(span);
            }
            threads.add(span.threadHash());
            from = Math.min(from, span.startEpochMicros());
            to = Math.max(to, endMicrosOf(span));
        }

        // Every list above was filled in start order, so roots, each parent's children and each
        // run's members are already sorted by start.
        Map<Long, Long> critical = TraceCriticalPath.micros(roots, childrenByParent);
        List<Slot> ordered = drawOrder(byId, roots, childrenByParent, membersByRun);

        return new TraceSkeleton(
                byId, childrenByParent, membersByRun, ordered, critical,
                new TraceWindow(from, to), threads.size());
    }

    private static Long resolvedParent(TraceSpanShape span, Map<Long, TraceSpanShape> byId) {
        Long parent = span.parentSpanId();
        if (parent == null || parent == span.spanId() || !byId.containsKey(parent)) {
            return null;
        }
        return parent;
    }

    /**
     * Each root followed by its subtree, siblings by start time, a run standing in for all of its
     * members at the place its first one started.
     */
    private static List<Slot> drawOrder(
            Map<Long, TraceSpanShape> byId,
            List<TraceSpanShape> roots,
            Map<Long, List<TraceSpanShape>> childrenByParent,
            Map<Long, List<TraceSpanShape>> membersByRun) {

        List<Slot> ordered = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        traverse(nodesOf(roots, membersByRun), childrenByParent, membersByRun, visited, ordered);

        // Whatever is left belongs to a parent cycle. Breaking in at each still-unplaced span, in
        // start order, renders a malformed trace as a flatter tree instead of an empty one. A run's
        // members are leaves, so a cycle never runs through one.
        for (TraceSpanShape span : byId.values()) {
            if (span.runId() == null && !visited.contains(span.spanId())) {
                traverse(List.of(new SpanNode(span)), childrenByParent, membersByRun, visited, ordered);
            }
        }
        return ordered;
    }

    /**
     * Walks each start node and its subtree depth-first, appending slots in draw order. Start nodes
     * are placed without a parent: they head the tree the caller sees, whatever their stored parent
     * said.
     */
    private static void traverse(
            List<Node> startNodes,
            Map<Long, List<TraceSpanShape>> childrenByParent,
            Map<Long, List<TraceSpanShape>> membersByRun,
            Set<Long> visited,
            List<Slot> ordered) {

        Deque<Placement> pending = new ArrayDeque<>();
        for (int i = startNodes.size() - 1; i >= 0; i--) {
            pending.push(new Placement(startNodes.get(i), 0, null));
        }
        while (!pending.isEmpty()) {
            Placement placement = pending.pop();
            switch (placement.node()) {
                case RunNode run -> ordered.add(
                        new RunSlot(run.runId(), placement.depth(), placement.parentSpanId()));
                case SpanNode node -> {
                    TraceSpanShape span = node.span();
                    if (!visited.add(span.spanId())) {
                        continue;
                    }
                    ordered.add(new SpanSlot(span.spanId(), placement.depth(), placement.parentSpanId()));
                    List<Node> children = nodesOf(
                            childrenByParent.getOrDefault(span.spanId(), List.of()), membersByRun);
                    for (int i = children.size() - 1; i >= 0; i--) {
                        pending.push(new Placement(children.get(i), placement.depth() + 1, span.spanId()));
                    }
                }
            }
        }
    }

    /**
     * Siblings as the tree draws them: each span on its own, each run once, in start order. A run is
     * placed by its first member, which is also the first of its members met in start order.
     */
    private static List<Node> nodesOf(
            List<TraceSpanShape> siblings, Map<Long, List<TraceSpanShape>> membersByRun) {

        List<Node> nodes = new ArrayList<>(siblings.size());
        Set<Long> runsPlaced = new HashSet<>();
        for (TraceSpanShape sibling : siblings) {
            Long runId = sibling.runId();
            if (runId == null) {
                nodes.add(new SpanNode(sibling));
            } else if (runsPlaced.add(runId)) {
                nodes.add(new RunNode(runId, membersByRun.get(runId)));
            }
        }
        nodes.sort(Comparator.comparingLong(Node::startEpochMicros));
        return nodes;
    }

    /** Every span and run in the order the waterfall draws them. */
    List<Slot> ordered() {
        return ordered;
    }

    /**
     * Where a span sits in the tree: its own slot, or — for a member of a folded run — its run's,
     * since a member is drawn at its run's depth under its run's parent.
     */
    Optional<Slot> slotOf(long spanId) {
        Slot slot = slotBySpan.get(spanId);
        if (slot != null) {
            return Optional.of(slot);
        }
        return Optional.ofNullable(byId.get(spanId))
                .map(TraceSpanShape::runId)
                .map(slotByRun::get);
    }

    Optional<TraceSpanShape> shapeOf(long spanId) {
        return Optional.ofNullable(byId.get(spanId));
    }

    /** The run's members in start order; empty when the trace holds no such run. */
    List<TraceSpanShape> membersOf(long runId) {
        return membersByRun.getOrDefault(runId, List.of());
    }

    Optional<RunSlot> runSlotOf(long runId) {
        return Optional.ofNullable(slotByRun.get(runId));
    }

    /** The spans the tree hangs directly under {@code spanId}, in start order. */
    List<TraceSpanShape> childrenOf(long spanId) {
        return childrenByParent.getOrDefault(spanId, List.of());
    }

    /** Every span under {@code spanId}, at any depth, in no particular order. */
    List<TraceSpanShape> descendantsOf(long spanId) {
        List<TraceSpanShape> descendants = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>();
        pending.push(spanId);
        while (!pending.isEmpty()) {
            for (TraceSpanShape child : childrenOf(pending.pop())) {
                if (visited.add(child.spanId())) {
                    descendants.add(child);
                    pending.push(child.spanId());
                }
            }
        }
        return descendants;
    }

    /**
     * The span's own share of the trace's critical path, in nanoseconds.
     * <p>
     * Capped at the span's duration: the walk works in microseconds, and a span whose bounds round
     * outwards could otherwise be credited a sliver more than it ever ran for.
     */
    long criticalNanosOf(long spanId, long durationNanos) {
        long micros = criticalMicros.getOrDefault(spanId, 0L);
        return Math.min(micros * NANOS_PER_MICRO, durationNanos);
    }

    /** The stretch the whole trace occupied, folded members included. */
    TraceWindow window() {
        return window;
    }

    /** How many distinct threads the trace's spans ran on, folded members included. */
    int threadCount() {
        return threadCount;
    }

    static long endMicrosOf(TraceSpanShape span) {
        return span.startEpochMicros() + span.durationNanos() / NANOS_PER_MICRO;
    }
}

/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.flamegraph.diff;

import cafe.jeffrey.flamegraph.ai.WeightContext;
import cafe.jeffrey.frameir.DiffFrame;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.shared.common.model.Type;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a diff tree into a ranked answer: which methods the primary profile spends more in than the
 * baseline, which it spends less in, and which appeared/vanished pairs look like a rename.
 * <p>
 * The tree itself is a poor first read. Pruned to a threshold it is still mostly frames whose delta is
 * nearly zero, and the two or three that actually moved are scattered through it at whatever depth
 * they happen to live. Ranking by self movement puts them on the first line.
 * <p>
 * The walk descends into {@code ADDED} and {@code REMOVED} subtrees rather than charging each to its
 * root. {@link cafe.jeffrey.frameir.DiffTreeGenerator} stops matching the moment a method name is
 * missing on one side, so an entire new call path arrives as one opaque node; attributing all of its
 * weight to the frame at the top would report the framework entry point that happens to sit there
 * instead of the method doing the work.
 */
public final class DiffgraphAnalyzer {

    /** Reads as a call path in the direction calls actually go: caller first. */
    private static final String PATH_SEPARATOR = " -> ";

    /**
     * A path is a landmark for finding the method in the source, not a stack trace to be replayed.
     * Full paths run to a hundred frames on a framework stack and would crowd out the numbers.
     */
    private static final int PATH_FRAMES_KEPT = 6;
    private static final String PATH_TRUNCATION_MARKER = "... ";

    /** Two subtrees this close in size are worth mentioning as possibly the same work renamed. */
    private static final double RENAME_SIZE_TOLERANCE = 0.15;

    /**
     * Below this share of the profile, an appeared/vanished coincidence is likelier than a rename, and
     * the pairing would produce noise rather than a finding.
     */
    private static final double RENAME_MIN_SHARE_PCT = 1.0;

    private final Type eventType;
    private final WeightContext weightContext;
    private final DiffMeasure measure;
    private final ComparisonScale scale;

    private final Map<String, MethodAccumulator> byMethod = new HashMap<>();
    private final List<Subtree> appeared = new ArrayList<>();
    private final List<Subtree> vanished = new ArrayList<>();
    private final List<String> path = new ArrayList<>();

    private DiffgraphAnalyzer(Type eventType, ComparisonScale scale) {
        this.eventType = eventType;
        this.weightContext = WeightContext.of(eventType);
        this.measure = new DiffMeasure(weightContext);
        this.scale = scale;
    }

    /**
     * @param root  the diff tree root, as produced by {@link cafe.jeffrey.frameir.DiffTreeGenerator}
     * @param limit how many movements to report in each direction
     */
    public static ComparisonReport analyze(
            Type eventType, DiffFrame root, ComparisonScale scale, int limit) {

        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: limit=" + limit);
        }
        DiffgraphAnalyzer analyzer = new DiffgraphAnalyzer(eventType, scale);
        if (root != null) {
            analyzer.walk(root);
        }
        return analyzer.report(limit);
    }

    private void walk(DiffFrame node) {
        path.add(node.methodName);
        switch (node.type) {
            case SHARED -> {
                record(node.methodName, selfOf(node, true), selfOf(node, false));
                for (DiffFrame child : node.values()) {
                    walk(child);
                }
            }
            case ADDED -> {
                appeared.add(new Subtree(node.methodName, renderPath(), measure.total(node.frame)));
                walkOneSided(node.methodName, node.frame, true);
            }
            case REMOVED -> {
                vanished.add(new Subtree(node.methodName, renderPath(), measure.total(node.frame)));
                walkOneSided(node.methodName, node.frame, false);
            }
        }
        path.removeLast();
    }

    /**
     * A subtree that exists in only one of the profiles. Its frames are plain {@link Frame}s — the diff
     * tree stopped pairing here — so their self weight counts entirely towards the side they came from.
     */
    private void walkOneSided(String methodName, Frame frame, boolean primarySide) {
        long self = measure.self(frame);
        if (self > 0) {
            record(methodName, primarySide ? self : 0L, primarySide ? 0L : self);
        }
        for (Map.Entry<String, Frame> child : frame.entrySet()) {
            path.add(child.getKey());
            walkOneSided(child.getKey(), child.getValue(), primarySide);
            path.removeLast();
        }
    }

    /**
     * What stayed at a shared frame rather than going deeper: its own measurement minus everything its
     * children account for on the same side.
     * <p>
     * Clamped at zero. The arithmetic is exact by construction — a frame's total is its self plus its
     * children's totals — but a tree assembled from a partially-written recording can violate that, and
     * a negative "self" in the output would read as a nonsensical finding rather than as bad input.
     */
    private long selfOf(DiffFrame node, boolean primarySide) {
        long total = sideMeasure(node, primarySide);
        long children = 0L;
        for (DiffFrame child : node.values()) {
            children += sideMeasure(child, primarySide);
        }
        return Math.max(0L, total - children);
    }

    private long sideMeasure(DiffFrame node, boolean primarySide) {
        return primarySide ? measure.primary(node) : measure.baseline(node);
    }

    private void record(String methodName, long primarySelf, long baselineSelf) {
        if (primarySelf == 0 && baselineSelf == 0) {
            return;
        }
        byMethod.computeIfAbsent(methodName, name -> new MethodAccumulator())
                .add(renderPath(), primarySelf, baselineSelf, scale);
    }

    private String renderPath() {
        int from = Math.max(0, path.size() - PATH_FRAMES_KEPT);
        StringBuilder rendered = new StringBuilder(128);
        if (from > 0) {
            rendered.append(PATH_TRUNCATION_MARKER);
        }
        for (int i = from; i < path.size(); i++) {
            if (i > from) {
                rendered.append(PATH_SEPARATOR);
            }
            rendered.append(path.get(i));
        }
        return rendered.toString();
    }

    private ComparisonReport report(int limit) {
        List<MethodDelta> deltas = byMethod.entrySet().stream()
                .map(entry -> entry.getValue().toDelta(entry.getKey()))
                .toList();

        List<MethodDelta> regressed = deltas.stream()
                .filter(delta -> delta.delta(scale) > 0)
                .sorted(Comparator.comparingLong((MethodDelta delta) -> delta.delta(scale)).reversed())
                .limit(limit)
                .toList();

        List<MethodDelta> improved = deltas.stream()
                .filter(delta -> delta.delta(scale) < 0)
                .sorted(Comparator.comparingLong(delta -> delta.delta(scale)))
                .limit(limit)
                .toList();

        return new ComparisonReport(
                eventType, weightContext, scale, regressed, improved, renameCandidates(), deltas.size());
    }

    /**
     * Pairs each appeared subtree with a vanished one of about the same size, largest first, each used
     * at most once. Greedy rather than optimal on purpose: these are suspicions for a reader holding
     * the source diff to confirm, and an exact assignment would not make a wrong guess any less wrong.
     */
    private List<RenameCandidate> renameCandidates() {
        long floor = Math.round(scale.referenceTotal() * RENAME_MIN_SHARE_PCT / 100.0);

        List<Subtree> candidates = appeared.stream()
                .filter(subtree -> subtree.measure() >= floor)
                .sorted(Comparator.comparingLong(Subtree::measure).reversed())
                .toList();

        List<Subtree> unmatched = new ArrayList<>(vanished.stream()
                .map(subtree -> subtree.scaled(scale))
                .filter(subtree -> subtree.measure() >= floor)
                .sorted(Comparator.comparingLong(Subtree::measure).reversed())
                .toList());

        List<RenameCandidate> pairs = new ArrayList<>();
        for (Subtree candidate : candidates) {
            for (int i = 0; i < unmatched.size(); i++) {
                Subtree other = unmatched.get(i);
                if (similarSize(candidate.measure(), other.measure())) {
                    pairs.add(new RenameCandidate(
                            candidate.methodName(), candidate.path(), candidate.measure(),
                            other.methodName(), other.path(), other.measure()));
                    unmatched.remove(i);
                    break;
                }
            }
        }
        return pairs;
    }

    private static boolean similarSize(long left, long right) {
        long larger = Math.max(left, right);
        if (larger == 0) {
            return false;
        }
        return (double) Math.abs(left - right) / larger <= RENAME_SIZE_TOLERANCE;
    }

    /**
     * One method's movement, still being summed. A method is normally reached by several call paths;
     * the one kept is whichever contributed most to the movement, which is the path a reader should
     * look at first.
     */
    private static final class MethodAccumulator {

        private long primarySelf;
        private long baselineSelf;
        private int callPaths;
        private String heaviestPath;
        private long heaviestContribution = -1L;

        void add(String path, long primary, long baseline, ComparisonScale scale) {
            primarySelf += primary;
            baselineSelf += baseline;
            callPaths++;

            long contribution = Math.abs(primary - scale.scaleBaseline(baseline));
            if (contribution > heaviestContribution) {
                heaviestContribution = contribution;
                heaviestPath = path;
            }
        }

        MethodDelta toDelta(String methodName) {
            return new MethodDelta(methodName, primarySelf, baselineSelf, callPaths, heaviestPath);
        }
    }

    /**
     * A subtree that exists in one profile only, as a rename-pairing candidate.
     */
    private record Subtree(String methodName, String path, long measure) {

        Subtree scaled(ComparisonScale scale) {
            return new Subtree(methodName, path, scale.scaleBaseline(measure));
        }
    }
}

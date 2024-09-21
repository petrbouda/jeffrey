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

package pbouda.jeffrey.guardian.traverse;

import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.guardian.matcher.FrameMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static pbouda.jeffrey.guardian.traverse.Next.DONE;

public abstract class AbstractTraversable implements Traversable {

    private static final String JVM_FIRST_METHOD = "thread_native_entry";

    private final FrameMatcher baseFrameMatcher;
    private final Supplier<List<Traversable>> traversables;
    private final TargetFrameType targetFrameType;
    private final MatchingType matchingType;
    private final List<Frame> selectedFrames = new ArrayList<>();

    private Next globalNext = Next.CONTINUE;
    private long totalSamples = -1;
    private long totalWeight = -1;

    public AbstractTraversable(
            FrameMatcher baseFrameMatcher,
            Supplier<List<Traversable>> traversables,
            TargetFrameType targetFrameType) {

        this(baseFrameMatcher, traversables, targetFrameType, MatchingType.SINGLE_MATCH);
    }

    public AbstractTraversable(
            FrameMatcher baseFrameMatcher,
            Supplier<List<Traversable>> traversables,
            TargetFrameType targetFrameType,
            MatchingType matchingType) {

        this.baseFrameMatcher = baseFrameMatcher;
        this.traversables = traversables;
        this.targetFrameType = targetFrameType;
        this.matchingType = matchingType;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext == DONE) {
            return DONE;
        }

        if (totalSamples == -1) {
            totalSamples = frame.totalSamples();
            totalWeight = frame.totalWeight();
        }

        // Skips part of the tree belonging to the Java frames
        // Useful for JVM or Native stack frames
        if (targetFrameType == TargetFrameType.JVM && frame.frameType().isJavaFrame()) {
            return Next.SKIP_SUBTREE;
        }
        // Skips if we are looking for JAVA methods and encounters the JVM entry method (mainly for JIT and GC)
        if (targetFrameType == TargetFrameType.JAVA && frame.methodName().equals(JVM_FIRST_METHOD)) {
            return Next.SKIP_SUBTREE;
        }

        if (globalNext == Next.CONTINUE && baseFrameMatcher.matches(frame)) {
            // Traverse the tree from the current frame a collect all selected frames
            List<Traversable> traversables = this.traversables.get();
            new FrameTraversal(frame)
                    .traverseWith(traversables);

            // Collects all selected frames from the traversables
            List<Frame> foundFrames = traversables.stream()
                    .flatMap(t -> t.selectedFrames().stream())
                    .toList();

            this.selectedFrames.addAll(foundFrames);

            if (MatchingType.SINGLE_MATCH == matchingType) {
                // Processing for this guard is DONE
                globalNext = DONE;
            } else {
                // Skip the subtree but continue with the traversal for other parts of the tree
                return Next.SKIP_SUBTREE;
            }
        }

        return globalNext;
    }

    protected long getTotalSamples() {
        return totalSamples;
    }

    protected long getTotalWeight() {
        return totalWeight;
    }

    public List<Frame> selectedFrames() {
        return selectedFrames;
    }
}

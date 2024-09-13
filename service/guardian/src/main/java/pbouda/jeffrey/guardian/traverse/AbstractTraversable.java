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

import java.util.List;

import static pbouda.jeffrey.guardian.traverse.Next.DONE;

public abstract class AbstractTraversable implements Traversable {

    private final FrameMatcher baseFrameMatcher;
    private final List<Traversable> traversables;
    private final boolean skipJavaFrames;

    private Next globalNext = Next.CONTINUE;
    private long totalSamples = -1;
    private List<Frame> selectedFrames;

    public AbstractTraversable(FrameMatcher baseFrameMatcher, List<Traversable> traversables) {
        this(baseFrameMatcher, traversables, false);
    }

    public AbstractTraversable(FrameMatcher baseFrameMatcher, List<Traversable> traversables, boolean skipJavaFrames) {
        this.baseFrameMatcher = baseFrameMatcher;
        this.traversables = traversables;
        this.skipJavaFrames = skipJavaFrames;
    }

    @Override
    public Next traverse(Frame frame) {
        if (globalNext == DONE) {
            return DONE;
        }

        if (totalSamples == -1) {
            totalSamples = frame.totalSamples();
        }

        // Skips part of the tree belonging to the Java frames
        // Useful for JVM or Native stack frames
        if (skipJavaFrames && frame.frameType().isJavaFrame()) {
            return Next.SKIP_SUBTREE;
        }

        if (globalNext == Next.CONTINUE && baseFrameMatcher.matches(frame)) {
            // Traverse the tree from the current frame a collect all selected frames
            new FrameTraversal(frame)
                    .traverseWith(traversables);

            // Collects all selected frames from the traversables
            this.selectedFrames = traversables.stream()
                    .flatMap(t -> t.selectedFrames().stream())
                    .toList();

            globalNext = DONE;
        }

        return globalNext;
    }

    protected long getTotalSamples() {
        return totalSamples;
    }

    public List<Frame> selectedFrames() {
        return selectedFrames;
    }
}

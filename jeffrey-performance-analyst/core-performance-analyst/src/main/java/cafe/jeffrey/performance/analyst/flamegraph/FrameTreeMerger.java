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

package cafe.jeffrey.performance.analyst.flamegraph;

import cafe.jeffrey.frameir.Frame;

import java.util.Map;

/**
 * Recursively merges one {@link Frame} tree into another. {@link Frame#merge(Frame)} only combines a
 * single node's metrics, so trees built by separate per-chunk writers must be folded together
 * level by level, matching children by method name.
 */
public final class FrameTreeMerger {

    private FrameTreeMerger() {
    }

    /**
     * Adds every metric and child of {@code source} into {@code target}. After the call {@code target}
     * holds the combined tree; {@code source} is left unchanged.
     */
    public static void mergeInto(Frame target, Frame source) {
        target.merge(source);
        for (Map.Entry<String, Frame> entry : source.entrySet()) {
            String methodName = entry.getKey();
            Frame sourceChild = entry.getValue();
            Frame targetChild = target.get(methodName);
            if (targetChild == null) {
                targetChild = new Frame(
                        target, sourceChild.methodName(), sourceChild.lineNumber(), sourceChild.bci());
                target.put(methodName, targetChild);
            }
            mergeInto(targetChild, sourceChild);
        }
    }
}

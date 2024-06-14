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

package pbouda.jeffrey.generator.flamegraph.diff;

import pbouda.jeffrey.generator.flamegraph.Frame;
import pbouda.jeffrey.generator.flamegraph.FrameType;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

public class DiffTreeGenerator {

    private final Frame primary;
    private final Frame secondary;

    public DiffTreeGenerator(Frame primary, Frame secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public DiffFrame generate() {
        DiffFrame artificialNode = new DiffFrame(DiffFrame.Type.SHARED, null, "-", FrameType.UNKNOWN);
        walkTree(artificialNode, "all", secondary, primary);
        return artificialNode.get("all");
    }

    private void walkTree(TreeMap<String, DiffFrame> diffFrame, String currentMethodName, Frame bFrame, Frame cFrame) {
        if (bFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.added(cFrame, currentMethodName));
        } else if (cFrame == null) {
            diffFrame.put(currentMethodName, DiffFrame.removed(bFrame, currentMethodName));
        } else {
            DiffFrame newFrame = DiffFrame.shared(
                    currentMethodName,
                    bFrame.frameType(),
                    bFrame.totalSamples(),
                    bFrame.totalWeight(),
                    cFrame.totalSamples(),
                    cFrame.totalWeight());

            diffFrame.put(currentMethodName, newFrame);

            Set<String> nextLayer = new HashSet<>();
            nextLayer.addAll(bFrame.keySet());
            nextLayer.addAll(cFrame.keySet());

            for (String methodName : nextLayer) {
                Frame newBFrame = bFrame.get(methodName);
                Frame newCFrame = cFrame.get(methodName);
                walkTree(newFrame, methodName, newBFrame, newCFrame);
            }
        }
    }
}

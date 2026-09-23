/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.frameir;

import cafe.jeffrey.profile.common.model.FrameType;

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
        walkTree(artificialNode, "all", primary, secondary);
        return artificialNode.get("all");
    }

    private void walkTree(TreeMap<String, DiffFrame> diffFrame, String currentMethodName, Frame primary, Frame secondary) {
        if (secondary == null) {
            diffFrame.put(currentMethodName, DiffFrame.added(primary, currentMethodName));
        } else if (primary == null) {
            diffFrame.put(currentMethodName, DiffFrame.removed(secondary, currentMethodName));
        } else {
            DiffFrame newFrame = DiffFrame.shared(
                    currentMethodName,
                    primary.frameType(),
                    primary.totalSamples(),
                    primary.totalWeight(),
                    secondary.totalSamples(),
                    secondary.totalWeight());

            diffFrame.put(currentMethodName, newFrame);

            Set<String> nextLayer = new HashSet<>();
            nextLayer.addAll(secondary.keySet());
            nextLayer.addAll(primary.keySet());

            for (String methodName : nextLayer) {
                Frame newSecondary = secondary.get(methodName);
                Frame newPrimary = primary.get(methodName);
                walkTree(newFrame, methodName, newPrimary, newSecondary);
            }
        }
    }
}

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

            Map<String, List<String>> secondaryKeys = groupByMatchKey(secondary.keySet());
            Map<String, List<String>> primaryKeys = groupByMatchKey(primary.keySet());
            Set<String> ambiguous = new HashSet<>();
            secondaryKeys.forEach((key, names) -> addIfAmbiguous(ambiguous, key, names));
            primaryKeys.forEach((key, names) -> addIfAmbiguous(ambiguous, key, names));
            Map<String, String> secondaryNames = byMatchKey(secondaryKeys, ambiguous);
            Map<String, String> primaryNames = byMatchKey(primaryKeys, ambiguous);
            Set<String> nextLayer = new HashSet<>();
            nextLayer.addAll(secondaryNames.keySet());
            nextLayer.addAll(primaryNames.keySet());

            for (String key : nextLayer) {
                String primaryName = primaryNames.get(key);
                String secondaryName = secondaryNames.get(key);
                Frame newSecondary = secondaryName == null ? null : secondary.get(secondaryName);
                Frame newPrimary = primaryName == null ? null : primary.get(primaryName);
                walkTree(newFrame, primaryName != null ? primaryName : secondaryName, newPrimary, newSecondary);
            }
        }
    }

    /** A layer's frames grouped by the key they are matched by ({@link FrameMatchKey}). */
    private static Map<String, List<String>> groupByMatchKey(Set<String> names) {
        Map<String, List<String>> grouped = new HashMap<>();
        names.forEach(name -> grouped.computeIfAbsent(FrameMatchKey.of(name), _ -> new ArrayList<>()).add(name));
        return grouped;
    }

    private static void addIfAmbiguous(Set<String> ambiguous, String key, List<String> names) {
        if (names.size() > 1) {
            ambiguous.add(key);
        }
    }

    /**
     * A layer's frames by what they are matched by, each to its own name: the key, or — for a key that
     * stands for two frames of one side, two runs' worth of one library in one profile — each frame's
     * own name, on both sides alike, so they are matched as they are named.
     */
    private static Map<String, String> byMatchKey(Map<String, List<String>> grouped, Set<String> ambiguous) {
        Map<String, String> byKey = new HashMap<>();
        grouped.forEach((key, names) -> {
            if (ambiguous.contains(key)) {
                names.forEach(name -> byKey.put(name, name));
            } else {
                byKey.put(key, names.getFirst());
            }
        });
        return byKey;
    }
}

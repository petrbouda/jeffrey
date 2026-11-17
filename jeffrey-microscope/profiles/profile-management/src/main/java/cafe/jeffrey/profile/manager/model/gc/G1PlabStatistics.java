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

package cafe.jeffrey.profile.manager.model.gc;

/**
 * One G1 promotion-buffer (PLAB) evacuation summary, from {@code jdk.G1EvacuationYoungStatistics} /
 * {@code jdk.G1EvacuationOldStatistics} (each wraps a {@code jdk.types.G1EvacuationStatistics} struct).
 * PLABs are the thread-local buffers G1 uses to copy surviving objects during a collection; the waste
 * fields expose how much of that buffer space was thrown away (alignment, refill, undo, evacuation
 * failure), which is the signal for PLAB-sizing tuning.
 *
 * @param gcId            the collection id this evacuation belongs to
 * @param generation      "Young" or "Old"
 * @param allocated       total bytes allocated by PLABs
 * @param used            bytes actually occupied by copied objects
 * @param totalWasted     wasted + undoWaste + regionEndWaste + failureWaste
 * @param wastePercent    {@code totalWasted / allocated} as a percentage
 * @param directAllocated bytes allocated directly (outside PLABs)
 * @param regionsRefilled number of regions refilled
 * @param numPlabsFilled  number of PLABs filled
 * @param failureUsed     bytes occupied by objects in regions where evacuation failed
 * @param failureWaste    bytes left unused in regions where evacuation failed
 */
public record G1PlabStatistics(
        long gcId,
        String generation,
        long allocated,
        long used,
        long totalWasted,
        double wastePercent,
        long directAllocated,
        long regionsRefilled,
        long numPlabsFilled,
        long failureUsed,
        long failureWaste) {

    public static final String YOUNG = "Young";
    public static final String OLD = "Old";
}

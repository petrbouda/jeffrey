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

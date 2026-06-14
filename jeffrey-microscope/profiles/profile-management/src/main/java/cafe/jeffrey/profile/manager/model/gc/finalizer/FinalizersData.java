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

package cafe.jeffrey.profile.manager.model.gc.finalizer;

import java.util.List;

/**
 * Finalization insight from the periodic {@code jdk.FinalizerStatistics} event (one per finalizable
 * class). A class with a high peak pending-object count is the classic finalizer-leak / slow-finalizer
 * signal — finalization is deprecated and a known source of stalls and retained memory.
 *
 * @param header  totals across all finalizable classes
 * @param classes per-class stats, ranked by peak pending objects
 */
public record FinalizersData(Header header, List<FinalizerClassStat> classes) {

    public record Header(long classCount, long totalPendingObjects, long totalFinalizersRun) {
    }

    public record FinalizerClassStat(String className, String codeSource, long peakObjects, long finalizersRun) {
    }
}

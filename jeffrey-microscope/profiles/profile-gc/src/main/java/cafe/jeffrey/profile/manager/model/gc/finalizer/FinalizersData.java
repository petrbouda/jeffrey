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

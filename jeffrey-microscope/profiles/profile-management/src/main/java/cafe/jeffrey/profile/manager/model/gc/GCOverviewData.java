/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import java.math.BigDecimal;
import java.util.List;

public record GCOverviewData(
        GCHeader header,
        List<GCEvent> longestPauses,
        GCPauseDistribution pauseDistribution,
        GCEfficiency efficiency,
        List<GCGenerationStats> generationStats,
        List<ConcurrentEvent> longestConcurrentEvents) {

    /**
     * The overview of a recording that carries no GC events: every count and time is zero.
     */
    public static GCOverviewData empty() {
        GCHeader header = new GCHeader(
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                new ManualGCCalls(0, 0, 0));

        return new GCOverviewData(
                header,
                List.of(),
                new GCPauseDistribution(List.of()),
                new GCEfficiency(0, 0, BigDecimal.ZERO, BigDecimal.ZERO),
                List.of(),
                List.of());
    }
}

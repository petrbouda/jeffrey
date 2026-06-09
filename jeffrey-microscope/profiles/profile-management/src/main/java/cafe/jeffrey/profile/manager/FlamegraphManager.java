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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.SpanInterval;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface FlamegraphManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, FlamegraphManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, FlamegraphManager> {
    }

    List<EventSummaryResult> eventSummaries();

    /**
     * Per-event-type summaries scoped to the union of the given span (thread, window) intervals, so the
     * span flamegraph cards show the real sample/weight counts those spans cover (not profile-wide totals).
     *
     * @param spanIntervals per-span (thread, time-window) intervals to scope the counts to
     * @return span-scoped event summaries (only types with at least one in-scope sample)
     */
    List<EventSummaryResult> eventSummaries(List<SpanInterval> spanIntervals);

    /**
     * Generate graph data in Protocol Buffers format.
     *
     * @param graphParameters graph parameters
     * @return graph data as protobuf bytes
     */
    byte[] generate(GraphParameters graphParameters);

    /**
     * Generate an AI-friendly Markdown export of the flamegraph. The export
     * walks the unpruned IR and applies its own threshold (set at bean
     * construction from
     * {@code jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct},
     * independent of the visualization threshold).
     *
     * @param graphParameters graph parameters
     * @return Markdown string suitable for pasting into an LLM
     * @throws UnsupportedOperationException for graph modes that do not
     *                                       support AI export (e.g. differential)
     */
    String generateAiExport(GraphParameters graphParameters);
}

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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.flamegraph.export.AiExportConfig;

import cafe.jeffrey.profile.common.config.GraphParameters;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.SpanScope;
import cafe.jeffrey.profile.model.EventSummaryResult;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface FlamegraphManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, FlamegraphManager> {
    }

    @FunctionalInterface
    interface DifferentialFactory extends BiFunction<ProfileInfo, ProfileInfo, DifferentialFlamegraphManager> {
    }

    List<EventSummaryResult> eventSummaries();

    /**
     * Summaries for <em>every</em> event type present in the profile, not just the curated JFR
     * {@code SUPPORTED_EVENTS} set. Used by format-specific views (e.g. pprof) whose event types are
     * not part of the JFR {@code Type} enum and would otherwise be filtered out.
     */
    List<EventSummaryResult> allEventSummaries();

    /**
     * Per-event-type summaries narrowed to a {@link SpanScope}, so the span flamegraph cards show the
     * real sample/weight counts those windows cover rather than profile-wide totals.
     *
     * @param spanScope which windows on which threads to count within
     * @return span-scoped event summaries (only types with at least one in-scope sample)
     */
    List<EventSummaryResult> eventSummaries(SpanScope spanScope);

    /**
     * Generate graph data in Protocol Buffers format.
     *
     * @param graphParameters graph parameters
     * @return graph data as protobuf bytes
     */
    byte[] generate(GraphParameters graphParameters);

    /**
     * Generate an AI-friendly Markdown export of the flamegraph. The export
     * walks the unpruned IR and applies its own threshold (the static
     * application property
     * {@code jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct},
     * independent of the visualization threshold).
     *
     * @param graphParameters graph parameters
     * @return Markdown string suitable for handing to a coding agent
     * @throws UnsupportedOperationException for graph modes that do not support AI export
     */
    String generateAiExport(GraphParameters graphParameters);

    /**
     * The same export with a caller-chosen prune threshold. A {@code null} config keeps the configured
     * one, so a caller with no opinion need not look the default up to pass it back.
     *
     * @param graphParameters graph parameters
     * @param aiExportConfig  threshold override, or {@code null} for the configured default
     * @return Markdown string suitable for handing to a coding agent
     * @throws UnsupportedOperationException for graph modes that do not support AI export
     */
    String generateAiExport(GraphParameters graphParameters, AiExportConfig aiExportConfig);
}

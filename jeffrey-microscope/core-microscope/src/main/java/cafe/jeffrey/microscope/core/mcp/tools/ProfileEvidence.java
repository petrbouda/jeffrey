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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.WeightUnit;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stored recording facts shared by profile evidence and pair quality; no inferred workload counts. */
final class ProfileEvidence {

    static final String SETTINGS_SCOPE = "Merged recording settings snapshot; setting changes over time are not preserved";
    static final String WHOLE_RECORDING = "whole-recording";

    private static final String SAMPLES = "samples";
    private static final String DENOMINATOR_DEFINITION = "capturedSamples + lostSamples";
    private static final String NO_LOSS_EVIDENCE =
            "No CPU-time sample-loss evidence; this does not establish zero loss for other samplers";
    private static final String REPORTED_LOSS_ONLY =
            "Reported CPU-time losses only; zero reported loss is not proof of complete recording coverage";
    private static final double PERCENT = 100.0;

    private ProfileEvidence() {
    }

    static Identity identity(ProfileInfo info, String commit) {
        return new Identity(info.id(), info.recordingId(), info.projectId(), info.workspaceId(), info.name(),
                info.eventSource().name(), commit, info.modified(), info.enabled(),
                epochMillis(info.createdAt()), epochMillis(info.profilingStartedAt()),
                epochMillis(info.profilingFinishedAt()), durationMillis(info));
    }

    private static Long epochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    static Long durationMillis(ProfileInfo info) {
        if (info.profilingStartedAt() == null || info.profilingFinishedAt() == null) {
            return null;
        }
        return Duration.between(info.profilingStartedAt(), info.profilingFinishedAt()).toMillis();
    }

    static List<EventEvidence> events(ProfileManager manager) {
        boolean imported = manager.info().eventSource().isFlamegraphOnlyImport();
        return manager.flamegraphManager().allEventSummaries().stream()
                .map(summary -> event(summary, imported)).toList();
    }

    private static EventEvidence event(EventSummaryResult summary, boolean imported) {
        EventSummaryResult.SingleResult single = summary.primary();
        Map<String, String> extras = single.extras() == null ? Map.of() : single.extras();
        String weightUnit;
        if (imported) {
            weightUnit = switch (WeightUnit.fromSampleType(extras.get("sampleType"))) {
                case BYTES -> "bytes";
                case DURATION -> "nanoseconds";
                case NONE -> null;
            };
        } else {
            Type type = Type.fromCode(summary.code());
            weightUnit = type == Type.CPU_TIME_SAMPLE ? "nanoseconds" : WeightContext.of(type).weightUnit();
        }
        return new EventEvidence(summary.code(), summary.label(), single.source(), single.samples(), SAMPLES,
                weightUnit == null ? null : single.weight(), weightUnit, single.calculated(),
                "All recorded events of this type; not a filtered call-tree denominator",
                single.settings(), extras);
    }

    static SamplerHealth samplerHealth(ProfileManager manager) {
        CpuTimeSampleLoss loss = manager.samplerHealthManager().cpuTimeSampleLoss();
        if (loss == null || (loss.capturedSamples() == 0 && loss.lostSamples() == 0)) {
            return new SamplerHealth(Type.CPU_TIME_SAMPLE.code(), WHOLE_RECORDING, SAMPLES,
                    SamplerStatus.UNAVAILABLE, null, null, null, null, null, null, NO_LOSS_EVIDENCE);
        }
        // Both counts are nonnegative longs; their sum overflowing would take more samples than a
        // recording can hold, and addExact says so rather than reporting a negative denominator.
        long denominator = Math.addExact(loss.capturedSamples(), loss.lostSamples());
        return new SamplerHealth(Type.CPU_TIME_SAMPLE.code(), WHOLE_RECORDING, SAMPLES, SamplerStatus.REPORTED,
                loss.capturedSamples(), loss.lostSamples(), loss.lossEvents(), denominator,
                DENOMINATOR_DEFINITION, PERCENT * loss.lostSamples() / denominator, REPORTED_LOSS_ONLY);
    }

    static List<WorkloadEvents> workload(List<EventEvidence> events) {
        return events.stream()
                .filter(event -> event.eventType().equals(Type.HTTP_SERVER_EXCHANGE.code())
                        || event.eventType().equals(Type.GRPC_SERVER_EXCHANGE.code()))
                .map(event -> new WorkloadEvents(event.eventType(), event.samples(), "observed server events",
                        WHOLE_RECORDING, "Completeness, thresholds and workload equivalence are not established"))
                .toList();
    }

    /** Whether the recording reported CPU-time sample loss at all. */
    enum SamplerStatus {
        /** No loss evidence: this does not establish zero loss. */
        UNAVAILABLE,
        /** The sampler reported what it captured and what it lost. */
        REPORTED
    }

    /** Who and what a profile is, with its instants as UTC epoch milliseconds. */
    record Identity(
            String profileId,
            @McpNullable
            String recordingId,
            @McpNullable
            String projectId,
            @McpNullable
            String workspaceId,
            @McpNullable
            String name,
            String eventSource,
            @McpNullable
            @McpDescription("The source commit the profiled build came from; null when unknown")
            String recordingCommit,
            boolean modified,
            boolean enabled,
            @McpNullable
            Long createdAtEpochMs,
            @McpNullable
            Long startedAtEpochMs,
            @McpNullable
            Long finishedAtEpochMs,
            @McpNullable
            @McpMinimum(0)
            Long durationMs) {
    }

    /**
     * CPU-time sample loss over the whole recording. The counts, the denominator and the share are null
     * when {@code status} is {@link SamplerStatus#UNAVAILABLE}.
     */
    record SamplerHealth(
            String eventType,
            String scope,
            String unit,
            SamplerStatus status,
            @McpNullable
            @McpMinimum(0)
            Long capturedSamples,
            @McpNullable
            @McpMinimum(0)
            Long lostSamples,
            @McpNullable
            @McpMinimum(0)
            Long lossEvents,
            @McpNullable
            @McpMinimum(0)
            Long denominator,
            @McpNullable
            String denominatorDefinition,
            @McpNullable
            @McpMinimum(0)
            Double lostSharePct,
            String limitation) {
    }

    record EventEvidence(
            String eventType,
            @McpNullable
            String label,
            @McpNullable
            String source,
            @McpMinimum(0)
            long samples,
            String unit,
            @McpNullable
            Long weight,
            @McpNullable
            String weightUnit,
            boolean calculated,
            String denominatorDefinition,
            Map<String, String> settings,
            Map<String, String> extras) {
    }

    record WorkloadEvents(
            String eventType,
            @McpMinimum(0)
            long count,
            String unit,
            String scope,
            String limitation) {
    }
}

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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.export.WeightContext;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.model.EventSummaryResult;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.shared.common.model.WeightUnit;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Stored recording facts shared by profile evidence and pair quality; no inferred workload counts. */
final class ProfileEvidence {

    static final String SETTINGS_SCOPE = "Merged recording settings snapshot; setting changes over time are not preserved";
    static final String WHOLE_RECORDING = "whole-recording";

    private ProfileEvidence() {
    }

    static ObjectNode identity(ProfileInfo info, String commit) {
        ObjectNode result = Json.createObject().put("profileId", info.id())
                .put("recordingId", info.recordingId()).put("projectId", info.projectId())
                .put("workspaceId", info.workspaceId()).put("name", info.name())
                .put("eventSource", info.eventSource().name()).put("recordingCommit", commit)
                .put("modified", info.modified()).put("enabled", info.enabled());
        result.set("createdAt", Json.toTree(info.createdAt()));
        result.set("startedAt", Json.toTree(info.profilingStartedAt()));
        result.set("finishedAt", Json.toTree(info.profilingFinishedAt()));
        result.set("durationMs", Json.toTree(durationMillis(info)));
        return result;
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
        return new EventEvidence(summary.code(), summary.label(), single.source(), single.samples(), "samples",
                weightUnit == null ? null : single.weight(), weightUnit, single.calculated(),
                "All recorded events of this type; not a filtered call-tree denominator",
                single.settings(), extras);
    }

    static ObjectNode samplerHealth(ProfileManager manager) {
        CpuTimeSampleLoss loss = manager.samplerHealthManager().cpuTimeSampleLoss();
        ObjectNode result = Json.createObject().put("eventType", Type.CPU_TIME_SAMPLE.code())
                .put("scope", WHOLE_RECORDING).put("unit", "samples");
        if (loss == null || (loss.capturedSamples() == 0 && loss.lostSamples() == 0)) {
            return result.put("status", "unavailable")
                    .put("limitation", "No CPU-time sample-loss evidence; this does not establish zero loss for other samplers");
        }
        BigInteger denominator = BigInteger.valueOf(loss.capturedSamples()).add(BigInteger.valueOf(loss.lostSamples()));
        result.put("status", "reported").put("capturedSamples", loss.capturedSamples())
                .put("lostSamples", loss.lostSamples()).put("lossEvents", loss.lossEvents());
        result.set("denominator", Json.toTree(denominator));
        return result.put("denominatorDefinition", "capturedSamples + lostSamples")
                .put("lostSharePct", 100.0 * loss.lostSamples() / denominator.doubleValue())
                .put("limitation", "Reported CPU-time losses only; zero reported loss is not proof of complete recording coverage");
    }

    static List<WorkloadEvents> workload(List<EventEvidence> events) {
        return events.stream()
                .filter(event -> event.eventType().equals(Type.HTTP_SERVER_EXCHANGE.code())
                        || event.eventType().equals(Type.GRPC_SERVER_EXCHANGE.code()))
                .map(event -> new WorkloadEvents(event.eventType(), event.samples(), "observed server events",
                        WHOLE_RECORDING, "Completeness, thresholds and workload equivalence are not established"))
                .toList();
    }

    record EventEvidence(String eventType, String label, String source, long samples, String unit,
                         Long weight, String weightUnit, boolean calculated, String denominatorDefinition,
                         Map<String, String> settings, Map<String, String> extras) {
    }

    record WorkloadEvents(String eventType, long count, String unit, String scope, String limitation) {
    }
}

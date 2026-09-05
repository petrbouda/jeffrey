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

import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What can be said about one profile before analysing anything in it: its identity, what it is capable
 * of answering, and where to look at it.
 * <p>
 * Shares the {@code profiles} prefix with {@link ProfilesMcpTools} so the two read as one family, but
 * is registered profile-scoped — which is what puts {@code profileId} in each schema as a required
 * argument rather than an optional one the model may quietly omit.
 */
public class ProfileMcpTools {

    /**
     * The views a link can point at. Curated rather than a mirror of every route: a name here is a
     * promise that the page answers something, and an unknown one is rejected with the list, so a
     * wrong guess fails loudly instead of becoming a link that 404s after the reader clicks it.
     * <p>
     * A name is the route's own sub-path, so the set doubles as the mapping.
     */
    private static final Set<String> VIEWS = Set.of(
            "dashboard",
            "auto-analysis",
            "overview",
            "event-types",
            "flags",
            "garbage-collection",
            "garbage-collection/timeseries",
            "garbage-collection/configuration",
            "allocations",
            "nmt",
            "native-memory",
            "memory-issues/leak-candidates",
            "thread-statistics",
            "threads-timeline",
            "virtual-threads",
            "thread-dumps",
            "jit-compilation",
            "class-loading",
            "exceptions",
            "vm-operations",
            "container/cpu-throttling",
            "blocking-operations",
            "socket-io",
            "file-io",
            "heap-dump/leak-suspects",
            "heap-dump/biggest-objects",
            "heap-dump/dominator-tree",
            "heap-dump/histogram",
            "heap-dump/gc-root-path",
            "security",
            "system",
            "modules",
            "string-symbol-tables",
            "garbage-collection/g1",
            "garbage-collection/zgc",
            "memory-issues/finalizers",
            "memory-issues/reference-processing",
            "events",
            "oql");

    /**
     * How many auto-analysis findings the summary carries. They are ordered by severity, so the first
     * few are the ones worth acting on; the rest are what jvm_autoAnalysis is for.
     */
    private static final int TOP_FINDINGS_LIMIT = 5;

    private static final String NO_SAMPLER_HEALTH =
            "This profile carries no jdk.CPUTimeSampleLoss events, so there is nothing to say about "
                    + "dropped samples. That event type comes with JDK 25's CPU-time sampler (JEP 509); "
                    + "a recording made with the older jdk.ExecutionSample sampler reports loss nowhere.";

    private static final List<String> SAMPLER_HEALTH_STEPS = List.of(
            "Loss is not spread evenly: the kernel drops samples when the queue is full, which is "
                    + "during the busiest moments, so a lossy recording understates its own hot paths.",
            "profiles_features lists every event type this profile recorded, with its sample totals.");

    /** The one curated view that takes an argument. */
    private static final String GC_ROOT_PATH_VIEW = "heap-dump/gc-root-path";
    private static final String OBJECT_ID_PARAM = "objectId";

    private final ProfileManager profileManager;
    private final RecordingCommitResolver recordingCommitResolver;

    public ProfileMcpTools(ProfileManager profileManager, RecordingCommitResolver recordingCommitResolver) {
        this.profileManager = profileManager;
        this.recordingCommitResolver = recordingCommitResolver;
    }

    @Tool(description = "Details of one profile: its identity, the recording window it covers, how "
            + "much data it holds, and — when the recording was tagged with one — the source commit "
            + "the profiled build came from (recordingCommit, null when unknown). Compare it with the "
            + "checkout before mapping frames to code: a profile of a different commit describes code "
            + "that may no longer exist.")
    public String get() {
        ProfileInfo info = profileManager.info();
        return McpToolOutput.json(new ProfileDetail(
                info.id(),
                info.name(),
                info.projectId(),
                info.workspaceId(),
                info.eventSource().name(),
                info.profilingStartedAt().toString(),
                info.profilingFinishedAt().toString(),
                info.duration().toString(),
                info.createdAt().toString(),
                info.enabled(),
                info.modified(),
                profileManager.sizeInBytes(),
                recordingCommitResolver.resolve(info.recordingId()).orElse(null),
                UiLinks.profile(info.id())));
    }

    @Tool(description = "What this profile can answer: which analysis features it has the data for, and "
            + "every event type it recorded with its sample and weight totals. Call this after "
            + "profiles_list to learn whether a profile carries traces, a heap dump or the "
            + "instrumentation dashboards before asking for them.")
    public String features() {
        return McpToolOutput.json(new ProfileCapabilities(
                disabledFeatures().stream().map(Enum::name).sorted().toList(),
                recordedEventTypes()));
    }

    private List<RecordedEventType> recordedEventTypes() {
        return profileManager.flamegraphManager().allEventSummaries().stream()
                .map(summary -> new RecordedEventType(
                        summary.code(),
                        summary.label(),
                        summary.primary().samples(),
                        summary.primary().weight()))
                .toList();
    }

    @Tool(description = "Whether the samples every other tool reasons over can be trusted: how many "
            + "CPU-time samples the profiler captured, how many the kernel dropped, and how many loss "
            + "events there were. A recording that lost a large share of its samples under load is "
            + "biased exactly where it matters most - the busiest moments are the ones the profiler "
            + "misses - so a flamegraph built on it understates the hot paths rather than being merely "
            + "noisy. Worth one call before reporting shares as fact.")
    public String samplerHealth() {
        CpuTimeSampleLoss loss = profileManager.samplerHealthManager().cpuTimeSampleLoss();
        if (loss == null || (loss.capturedSamples() == 0 && loss.lostSamples() == 0)) {
            return NO_SAMPLER_HEALTH;
        }

        return McpToolOutput.json(new SamplerHealth(
                loss.capturedSamples(),
                loss.lostSamples(),
                loss.lossEvents(),
                SAMPLER_HEALTH_STEPS,
                UiLinks.profile(profileManager.info().id())));
    }

    @Tool(description = "One call that orients you in a profile: what it is and what it covers, which "
            + "analysis features it has data for, every event type it recorded with its totals, "
            + "whether the samples can be trusted, and the auto-analysis findings when they have been "
            + "computed. Start here rather than with profiles_get, profiles_features and "
            + "profiles_samplerHealth in turn — this is those three and the findings, and what it "
            + "reports decides which family answers the question.")
    public String summary() {
        ProfileInfo info = profileManager.info();
        List<AutoAnalysisResult> findings = profileManager.autoAnalysisManager().analysisResults();

        return LinkedOutput.json(new ProfileSummary(
                info.id(),
                info.name(),
                info.eventSource().name(),
                info.profilingStartedAt() == null ? null : info.profilingStartedAt().toEpochMilli(),
                info.profilingFinishedAt() == null ? null : info.profilingFinishedAt().toEpochMilli(),
                disabledFeatures().stream().map(Enum::name).sorted().toList(),
                recordedEventTypes(),
                findings.stream().limit(TOP_FINDINGS_LIMIT).map(Finding::of).toList(),
                findings.isEmpty(),
                UiLinks.profile(info.id())));
    }

    @Tool(description = "A link that opens this profile in the Jeffrey web UI, for a reader who wants "
            + "to look at the interactive version of what was just analysed.")
    public String link() {
        return UiLinks.profile(profileManager.info().id());
    }

    @Tool(description = "A link that opens one specific view of this profile in the Jeffrey web UI - "
            + "the GC, thread, JIT and memory pages, the heap-dump reports, and the rest of the "
            + "dashboards. Use it to hand the reader the page that shows what you just described. The "
            + "URL is for them, not for you: it carries nothing you can analyse and reading it back "
            + "tells you nothing, so call this to end an explanation, not to gather information.")
    public String viewLink(
            @ToolParam(required = false, description = "Which view to open. One of: dashboard, auto-analysis, overview, "
                    + "event-types, flags, garbage-collection, garbage-collection/timeseries, "
                    + "garbage-collection/configuration, allocations, nmt, native-memory, "
                    + "memory-issues/leak-candidates, thread-statistics, threads-timeline, "
                    + "virtual-threads, thread-dumps, jit-compilation, class-loading, exceptions, "
                    + "vm-operations, container/cpu-throttling, blocking-operations, socket-io, "
                    + "file-io, security, system, modules, string-symbol-tables, events, oql, "
                    + "garbage-collection/g1, garbage-collection/zgc, memory-issues/finalizers, "
                    + "memory-issues/reference-processing, heap-dump/leak-suspects, "
                    + "heap-dump/biggest-objects, heap-dump/dominator-tree, heap-dump/histogram, "
                    + "heap-dump/gc-root-path")
            String view,
            @ToolParam(required = false, description = "Object id to preselect. Only meaningful for "
                    + "heap-dump/gc-root-path, where it runs the path-to-GC-root search for that "
                    + "object; ignored by every other view.")
            String objectId) {

        if (!VIEWS.contains(view)) {
            throw new IllegalArgumentException(
                    "Unknown view '" + view + "'. Valid views: "
                            + String.join(", ", VIEWS.stream().sorted().toList()));
        }

        Map<String, String> query = UiLinks.query();
        if (GC_ROOT_PATH_VIEW.equals(view)) {
            query.put(OBJECT_ID_PARAM, objectId);
        }
        return UiLinks.view(profileManager.info().id(), view, query);
    }

    /**
     * The same reasoning {@code ProfileFeaturesController} applies, minus its AI-analysis check: that
     * one describes whether Jeffrey's own assistant is configured, which says nothing about what this
     * profile holds — and the client asking is an assistant already.
     */
    private List<FeatureType> disabledFeatures() {
        List<FeatureType> disabled = new ArrayList<>(profileManager.featuresManager().getDisabledFeatures());
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists() || !heapDumpManager.isCacheReady()) {
            disabled.add(FeatureType.HEAP_DUMP);
        }
        // pprof profiles are aggregated and carry no per-sample timestamps, so the time-resolved views
        // collapse into a single spike and convey no information.
        if (profileManager.info().eventSource() == RecordingEventSource.PPROF) {
            disabled.add(FeatureType.SUBSECOND);
            disabled.add(FeatureType.TIMESERIES);
        }
        return disabled;
    }

    private record ProfileCapabilities(
            List<String> disabledFeatures,
            List<RecordedEventType> eventTypes) {
    }

    /**
     * @param autoAnalysisComputed false when nothing has run the rule set yet, which is why findings is
     *                             empty — different from a profile the rules found nothing wrong with
     */
    private record ProfileSummary(
            String profileId,
            String name,
            String eventSource,
            Long startedAtMillis,
            Long finishedAtMillis,
            List<String> disabledFeatures,
            List<RecordedEventType> eventTypes,
            List<Finding> topFindings,
            boolean autoAnalysisPending,
            String uiLink) {
    }

    private record Finding(String rule, String severity, String summary) {

        static Finding of(AutoAnalysisResult result) {
            return new Finding(result.rule(), result.severity().name(), result.summary());
        }
    }

    private record SamplerHealth(
            long capturedSamples,
            long lostSamples,
            long lossEvents,
            List<String> nextSteps,
            String uiLink) {
    }

    private record RecordedEventType(String name, String label, long samples, long weight) {
    }

    private record ProfileDetail(
            String profileId,
            String name,
            String projectId,
            String workspaceId,
            String eventSource,
            String recordingStartedAt,
            String recordingFinishedAt,
            String duration,
            String createdAt,
            boolean enabled,
            boolean modified,
            long sizeInBytes,
            String recordingCommit,
            String uiLink) {
    }
}

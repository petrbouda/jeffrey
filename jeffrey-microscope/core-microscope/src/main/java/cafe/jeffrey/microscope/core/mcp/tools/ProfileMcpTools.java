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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileCapabilityGaps.CapabilityGap;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisFindings;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.JvmSections;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.profile.common.analysis.AutoAnalysisResult;
import cafe.jeffrey.profile.feature.FeatureType;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.profile.mcp.finding.McpFinding;
import cafe.jeffrey.profile.mcp.finding.McpFindings;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.provider.profile.api.CpuTimeSampleLoss;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
     * The views a link can point at: the {@link MicroscopeView}s offered by viewLink. Curated rather
     * than every route: a name here is a promise that the page answers something on its own, and an
     * unknown one is rejected with the list, so a wrong guess fails loudly instead of becoming a link
     * that 404s after the reader clicks it.
     * <p>
     * The {@code @ToolParamValues} on {@link #viewLink} carries the same paths into the schema — an
     * annotation takes only literals, so it cannot read the enum. ProfileMcpToolsTest holds the two to
     * each other, and the enforcement test holds every view to the frontend's router snapshot.
     */
    private static final Map<String, MicroscopeView> VIEWS = Arrays.stream(MicroscopeView.values())
            .filter(MicroscopeView::offeredByViewLink)
            .collect(Collectors.toUnmodifiableMap(MicroscopeView::path, Function.identity()));

    /**
     * How many auto-analysis findings the summary carries. They are ordered by severity, so the first
     * few are the ones worth acting on; the rest are what jvm_autoAnalysis is for.
     */
    private static final int TOP_FINDINGS_LIMIT = 5;

    private static final String NO_SAMPLER_HEALTH =
            "This profile carries no jdk.CPUTimeSampleLoss events, so there is nothing to say about "
                    + "dropped samples. That event type comes with JDK 25's CPU-time sampler (JEP 509); "
                    + "a recording made with the older jdk.ExecutionSample sampler reports loss nowhere.";

    private static final String UNEVEN_LOSS =
            "Loss is not spread evenly: the kernel drops samples when the queue is full, which is "
                    + "during the busiest moments, so a lossy recording understates its own hot paths.";

    /** The one curated view that takes an argument. */
    private static final MicroscopeView GC_ROOT_PATH_VIEW = MicroscopeView.HEAP_DUMP_GC_ROOT_PATH;
    private static final String OBJECT_ID_PARAM = "objectId";

    /** The page that lists what the profile recorded, behind profiles_features. */
    static final MicroscopeView EVENT_TYPES_VIEW = MicroscopeView.EVENT_TYPES;

    private static final String PROFILE_ID = "profileId";
    private static final String COMPUTE = "compute";
    private static final String PROFILES_SUMMARY = "profiles_summary";
    private static final String PROFILES_FEATURES = "profiles_features";
    private static final String AUTO_ANALYSIS_TOOL = "jvm_autoAnalysis";
    private static final String SUMMARY_WHY = "orients: the features, event types, top findings and capability gaps";
    private static final String FEATURES_WHY = "lists every event type this profile recorded, with its sample totals";
    private static final String COMPUTE_WHY = "runs the auto-analysis rules this answer only reads; slow, it reads "
            + "the whole recording, and it may hand back an operation to follow";

    private final ProfileManager profileManager;
    private final RecordingCommitResolver recordingCommitResolver;
    private final ProfileCapabilityGaps capabilityGaps;
    private final FlamegraphCatalog catalog;
    private final JvmSections sections;
    private final AdvertisedFamilies advertised;

    /**
     * @param advertised the families a next call may route to
     */
    public ProfileMcpTools(
            ProfileManager profileManager,
            RecordingCommitResolver recordingCommitResolver,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            AdvertisedFamilies advertised) {

        this.profileManager = profileManager;
        this.recordingCommitResolver = recordingCommitResolver;
        this.catalog = new FlamegraphCatalog(profileManager, jfrPanelProvider, stackSamplePanelProvider);
        this.sections = JvmSections.standard(profileManager);
        this.capabilityGaps = new ProfileCapabilityGaps(profileManager, catalog, sections);
        this.advertised = advertised;
    }

    @Tool(description = "Returns one profile's details: its identity, the recording window it covers "
            + "(UTC epoch milliseconds, with its length in milliseconds), how much data it holds, and "
            + "recordingCommit - the source commit the profiled build came from when the recording "
            + "was tagged with one, null when unknown. A profile of a different commit than the "
            + "checkout describes code that may no longer exist.")
    @McpOutputSchema(ProfileDetail.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult get() {
        ProfileInfo info = profileManager.info();
        return McpToolResult.of(new ProfileDetail(
                info.id(),
                info.name(),
                info.projectId(),
                info.workspaceId(),
                info.eventSource().name(),
                epochMillis(info.profilingStartedAt()),
                epochMillis(info.profilingFinishedAt()),
                ProfileEvidence.durationMillis(info),
                epochMillis(info.createdAt()),
                info.enabled(),
                info.modified(),
                profileManager.sizeInBytes(),
                recordingCommitResolver.resolve(info.recordingId()).orElse(null),
                NextSteps.builder(advertised)
                        .next(NextCalls.to(PROFILES_SUMMARY).with(PROFILE_ID, info.id()).why(SUMMARY_WHY))
                        .followUp(),
                UiLinks.profile(info.id())));
    }

    /** An instant as UTC epoch milliseconds, or null for a profile whose recording did not carry it. */
    private static Long epochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    @Tool(description = "Reports what this profile can answer: the analysis features it has data for, "
            + "every event type it recorded with its sample and weight totals, and capabilityGaps - "
            + "in words, what this recording cannot answer and which tools that leaves empty. "
            + "profiles_summary carries all of this plus the top findings.")
    @McpOutputSchema(ProfileCapabilities.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult features() {
        String profileId = profileManager.info().id();
        List<FeatureType> disabled = disabledFeatures();
        return McpToolResult.of(new ProfileCapabilities(
                profileId,
                disabled.stream().map(Enum::name).sorted().toList(),
                recordedEventTypes(),
                capabilityGaps.gaps(disabled),
                UiLinks.view(profileId, EVENT_TYPES_VIEW)));
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

    @Tool(description = "Reports whether the samples every other tool reasons over can be trusted: how "
            + "many CPU-time samples the profiler captured, how many the kernel dropped, and how "
            + "many loss events there were. A recording that lost a large share of its samples under "
            + "load is biased exactly where it matters most - the busiest moments are the ones the "
            + "profiler misses - so a flamegraph built on it understates the hot paths rather than "
            + "being merely noisy. status UNAVAILABLE: the recording carries no loss evidence.")
    @McpOutputSchema(SamplerHealth.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult samplerHealth() {
        String profileId = profileManager.info().id();
        CpuTimeSampleLoss loss = profileManager.samplerHealthManager().cpuTimeSampleLoss();
        boolean reported = loss != null && (loss.capturedSamples() != 0 || loss.lostSamples() != 0);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(NextCalls.to(PROFILES_FEATURES).with(PROFILE_ID, profileId).why(FEATURES_WHY))
                .guidanceWhen(reported, UNEVEN_LOSS)
                .followUp();
        if (!reported) {
            return McpToolResult.of(new SamplerHealth(profileId, ProfileEvidence.SamplerStatus.UNAVAILABLE,
                    NO_SAMPLER_HEALTH, null, null, null, followUp, UiLinks.profile(profileId)));
        }

        return McpToolResult.of(new SamplerHealth(
                profileId,
                ProfileEvidence.SamplerStatus.REPORTED,
                null,
                loss.capturedSamples(),
                loss.lostSamples(),
                loss.lossEvents(),
                followUp,
                UiLinks.profile(profileId)));
    }

    @Tool(description = "Returns the one-call orientation to a profile: its id, name and event source, "
            + "the recording window (start and end as UTC epoch milliseconds), which analysis "
            + "features it has data for, every event type it recorded with its totals, whether the "
            + "auto-analysis rules have run (autoAnalysis) and their top findings, and capabilityGaps "
            + "- in words, every question this recording cannot answer and which tools that leaves "
            + "empty, and investigationAreas - the menu to put to the user when the question is open: each "
            + "area it can answer with its weight, the evidence when suggested, and the calls that open it. "
            + "A superset of profiles_features. Reads the cached auto-analysis and never runs "
            + "it. Not included: the sampler's loss figures (profiles_samplerHealth), the source "
            + "commit and the size on disk (profiles_get).")
    @McpOutputSchema(ProfileSummary.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult summary() {
        ProfileInfo info = profileManager.info();
        AutoAnalysisManager autoAnalysis = profileManager.autoAnalysisManager();
        AutoAnalysisStatus status = AutoAnalysisStatus.of(autoAnalysis);
        // Read only when the rules ran: a run that flagged nothing caches an empty list, and that is
        // an answer; anything else is no answer, which autoAnalysis says in so many words.
        List<AutoAnalysisResult> results = status == AutoAnalysisStatus.COMPUTED
                ? autoAnalysis.analysisResults()
                : List.of();
        List<FeatureType> disabled = disabledFeatures();
        List<RecordedEventType> eventTypes = recordedEventTypes();
        List<McpFinding> flagged = AutoAnalysisFindings.flagged(info.id(), results);

        return McpToolResult.of(new ProfileSummary(
                info.id(),
                info.name(),
                info.eventSource().name(),
                epochMillis(info.profilingStartedAt()),
                epochMillis(info.profilingFinishedAt()),
                disabled.stream().map(Enum::name).sorted().toList(),
                eventTypes,
                status,
                McpFindings.reachable(flagged, advertised::servesTool)
                        .stream().limit(TOP_FINDINGS_LIMIT).toList(),
                capabilityGaps.gaps(disabled),
                InvestigationMenu.of(facts(info, disabled, eventTypes, status, flagged), advertised),
                NextSteps.builder(advertised)
                        .nextWhen(status == AutoAnalysisStatus.NOT_COMPUTED, NextCalls.to(AUTO_ANALYSIS_TOOL)
                                .with(PROFILE_ID, info.id())
                                .with(COMPUTE, true)
                                .why(COMPUTE_WHY))
                        .followUp(),
                UiLinks.profile(info.id())));
    }

    @Tool(description = "Returns a link that opens this profile in the Jeffrey web UI, for a reader who "
            + "wants the interactive version of what was just analysed. profiles_viewLink links one "
            + "specific page.")
    @McpOutputSchema(ProfileLink.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult link() {
        String profileId = profileManager.info().id();
        return McpToolResult.of(new ProfileLink(profileId, UiLinks.profile(profileId)));
    }

    @Tool(description = "Returns a link that opens one specific view of this profile in the Jeffrey web "
            + "UI - the GC, thread, JIT and memory pages, the heap-dump reports and the rest of the "
            + "dashboards - so the reader can see the page behind an explanation. The URL is for the "
            + "reader: it carries nothing to analyse.")
    @McpOutputSchema(ViewLink.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult viewLink(
            @ToolParam(required = true, description = "Which view to open")
            @ToolParamValues({"dashboard", "auto-analysis", "overview", "event-types", "flags",
                    "garbage-collection", "garbage-collection/timeseries", "garbage-collection/configuration",
                    "allocations", "nmt", "native-memory", "memory-issues/leak-candidates",
                    "thread-statistics", "threads-timeline", "virtual-threads", "thread-dumps",
                    "jit-compilation", "class-loading", "exceptions", "vm-operations",
                    "container/cpu-throttling", "blocking-operations", "socket-io", "file-io",
                    "heap-dump/leak-suspects", "heap-dump/biggest-objects", "heap-dump/dominator-tree",
                    "heap-dump/histogram", "heap-dump/gc-root-path", "security", "system", "modules",
                    "string-symbol-tables", "garbage-collection/g1", "garbage-collection/zgc",
                    "memory-issues/finalizers", "memory-issues/reference-processing", "events",
                    "heap-dump/oql"})
            String view,
            @ToolParam(required = false, description = "Heap object id to preselect, as a decimal string. Only "
                    + "meaningful for heap-dump/gc-root-path, where it runs the path-to-GC-root search for "
                    + "that object; ignored by every other view.")
            String objectId) {

        MicroscopeView requested = view == null ? null : VIEWS.get(view.trim());
        if (requested == null) {
            throw new IllegalArgumentException(
                    "Unknown view '" + view + "'. Valid views: "
                            + String.join(", ", VIEWS.keySet().stream().sorted().toList()));
        }
        String profileId = profileManager.info().id();

        Map<String, String> query = UiLinks.query();
        String selected = requested == GC_ROOT_PATH_VIEW ? heapObjectId(objectId) : null;
        query.put(OBJECT_ID_PARAM, selected);
        return McpToolResult.of(new ViewLink(profileId, requested.path(), selected,
                UiLinks.view(profileId, requested, query)));
    }

    /**
     * A heap object id as the heap tools hand it out: a decimal string, because an HPROF id is a 64-bit
     * address beyond what a JSON number carries exactly. Null stays null.
     */
    private static String heapObjectId(String objectId) {
        if (objectId == null || objectId.isBlank()) {
            return null;
        }
        String trimmed = objectId.trim();
        try {
            return Long.toUnsignedString(Long.parseUnsignedLong(trimmed));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "objectId must be a decimal heap object id, as the heap tools return it: " + objectId);
        }
    }

    private List<FeatureType> disabledFeatures() {
        return ProfileDisabledFeatures.of(profileManager);
    }

    /** What the summary has read, gathered for the investigation menu, which reads nothing more. */
    private ProfileFacts facts(
            ProfileInfo info,
            List<FeatureType> disabled,
            List<RecordedEventType> eventTypes,
            AutoAnalysisStatus status,
            List<McpFinding> flagged) {

        boolean stackSampleImport = info.eventSource().isFlamegraphOnlyImport();
        return new ProfileFacts(
                info.id(),
                info.eventSource(),
                Set.copyOf(disabled),
                eventTypes.stream()
                        .filter(type -> type.samples() > 0)
                        .map(RecordedEventType::name)
                        .collect(Collectors.toSet()),
                catalog.panels().stream()
                        .map(panel -> new ProfileFacts.Sampled(
                                SampleKind.of(panel, stackSampleImport),
                                panel.event().code(),
                                panel.event().primary().samples()))
                        .toList(),
                sections.availability(),
                status,
                flagged);
    }

    /**
     * @param capabilityGaps what this recording cannot answer, in words, with what would close each gap
     * @param uiLink         the page that lists what the profile recorded
     */
    record ProfileCapabilities(
            String profileId,
            List<String> disabledFeatures,
            List<RecordedEventType> eventTypes,
            List<CapabilityGap> capabilityGaps,
            @McpDescription("The profile's event-types page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * @param autoAnalysis   whether the rules have run: only COMPUTED makes an empty topFindings mean
     *                       the rules cleared the recording
     * @param topFindings    the auto-analysis rules that flagged something, most severe first, in the
     *                       shared finding shape — the passes are left to jvm_autoAnalysis
     * @param capabilityGaps what this recording cannot answer, in words, with what would close each
     *                       gap — read before any negative result is believed
     * @param investigationAreas the menu for an open question: what the user can choose to investigate
     */
    record ProfileSummary(
            String profileId,
            @McpNullable
            String name,
            String eventSource,
            @McpNullable
            @McpDescription("When the recording started, as UTC epoch milliseconds; null when unknown")
            Long startedAtEpochMs,
            @McpNullable
            @McpDescription("When the recording finished, as UTC epoch milliseconds; null when unknown")
            Long finishedAtEpochMs,
            List<String> disabledFeatures,
            List<RecordedEventType> eventTypes,
            @McpDescription("Whether the auto-analysis rules have run; topFindings is theirs only when COMPUTED")
            AutoAnalysisStatus autoAnalysis,
            List<McpFinding> topFindings,
            List<CapabilityGap> capabilityGaps,
            @McpDescription("The menu for an open question: every area this profile can be investigated in, "
                    + "with its weight, the evidence when suggested and the calls that open it, and the areas "
                    + "it cannot answer with what is missing. Empty for a heap dump")
            List<InvestigationOption> investigationAreas,
            McpFollowUp followUp,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * @param status the counts are null when it is UNAVAILABLE, which does not establish zero loss
     * @param reason why there is nothing to report; null when the counts are there
     */
    record SamplerHealth(
            String profileId,
            ProfileEvidence.SamplerStatus status,
            @McpNullable
            String reason,
            @McpNullable
            @McpMinimum(0)
            Long capturedSamples,
            @McpNullable
            @McpMinimum(0)
            Long lostSamples,
            @McpNullable
            @McpMinimum(0)
            Long lossEvents,
            McpFollowUp followUp,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {
    }

    record RecordedEventType(String name, @McpNullable String label, long samples, long weight) {
    }

    record ProfileDetail(
            String profileId,
            @McpNullable
            String name,
            @McpNullable
            String projectId,
            @McpNullable
            String workspaceId,
            String eventSource,
            @McpNullable
            @McpDescription("When the recording started, as UTC epoch milliseconds; null when unknown")
            Long recordingStartedAtEpochMs,
            @McpNullable
            @McpDescription("When the recording finished, as UTC epoch milliseconds; null when unknown")
            Long recordingFinishedAtEpochMs,
            @McpNullable
            @McpMinimum(0)
            @McpDescription("The recording window's length in milliseconds; null when either end is unknown")
            Long durationMs,
            @McpNullable
            @McpDescription("When the profile was created, as UTC epoch milliseconds; null when unknown")
            Long createdAtEpochMs,
            boolean enabled,
            boolean modified,
            @McpMinimum(0)
            long sizeInBytes,
            @McpNullable
            @McpDescription("The source commit the profiled build came from; null when unknown")
            String recordingCommit,
            McpFollowUp followUp,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {
    }

    record ProfileLink(
            String profileId,
            @McpDescription("The profile's page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * @param objectId the heap object the view preselects, as a decimal string; null for every view
     *                 but heap-dump/gc-root-path, and there when none was given
     */
    record ViewLink(
            String profileId,
            String view,
            @McpNullable
            String objectId,
            @McpDescription("The requested page of the profile in the Microscope UI, for the user")
            String uiLink) {
    }
}

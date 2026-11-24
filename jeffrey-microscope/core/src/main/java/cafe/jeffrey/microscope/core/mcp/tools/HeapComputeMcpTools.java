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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.heap.HeapReport;
import cafe.jeffrey.microscope.mcp.server.McpCallContext;
import cafe.jeffrey.microscope.mcp.server.ToolExecutionException;
import cafe.jeffrey.microscope.mcp.server.McpDescription;
import cafe.jeffrey.microscope.mcp.server.McpNullable;
import cafe.jeffrey.microscope.mcp.server.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.server.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.server.McpToolResult;
import cafe.jeffrey.microscope.core.pipeline.PipelineProgress;
import cafe.jeffrey.microscope.core.pipeline.StageStatus;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.microscope.mcp.server.McpFollowUp;
import cafe.jeffrey.microscope.mcp.server.McpNextTool;
import cafe.jeffrey.microscope.mcp.server.McpToolCost;
import cafe.jeffrey.microscope.mcp.server.McpToolHints;
import cafe.jeffrey.microscope.mcp.server.McpToolMeta;
import cafe.jeffrey.microscope.mcp.server.McpToolRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds what a heap dump has to have before it can be asked anything.
 * <p>
 * A parsed heap dump answers most questions from an index, and the expensive parts of that index —
 * the dominator tree, and the nine cached reports the UI's own pages render — were previously built
 * only by opening the profile in a browser. Over MCP the reports could be read and never computed, so
 * a reader working from a terminal met "has not been run yet" and had no way past it. That is the gap
 * these two tools close.
 * <p>
 * Both are asynchronous, and deliberately so. Building a dominator tree over a multi-gigabyte heap
 * takes minutes, which is well past the point where a client gives up on a tool call and retries. The
 * work runs on the same {@code heap-dump-init} pipeline the UI uses, so a run started here shows up in
 * the browser and vice versa, and a second request while one is in flight joins it rather than
 * starting a rival.
 * <p>
 * What they write is a cache — no dump is altered and nothing is deleted. The one other {@code heap_}
 * tool that is not read-only, {@code heap_oql} with retained sizes, fills the same dominator tree.
 */
public class HeapComputeMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HeapComputeMcpTools.class);

    private static final MicroscopeView HEAP_VIEW = MicroscopeView.HEAP_DUMP_OVERVIEW;

    private static final String PREPARE_TOOL = "heap_prepare";
    private static final String STATUS_TOOL = "heap_status";
    private static final String SUMMARY_TOOL = "heap_getHeapSummary";
    private static final String LEAK_SUSPECTS_TOOL = "heap_getLeakSuspects";
    private static final String DOMINATOR_ROOTS_TOOL = "heap_getDominatorTreeRoots";
    private static final String PROFILE_ID = "profileId";
    private static final String REPORT = "report";
    private static final String RETRY = "retry";

    /** The tool that reads each report once it is computed; the others have a UI page only. */
    private static final Map<HeapReport, String> READERS = Map.of(
            HeapReport.STRINGS, "heap_getStringAnalysis",
            HeapReport.DOMINATOR, DOMINATOR_ROOTS_TOOL,
            HeapReport.THREADS, "heap_getThreads",
            HeapReport.BIGGEST, "heap_getBiggestObjects",
            HeapReport.COLLECTIONS, "heap_getCollectionAnalysis",
            HeapReport.LEAKS, LEAK_SUSPECTS_TOOL,
            HeapReport.CLASSLOADERS, "heap_getClassLoaderLeakChains",
            HeapReport.CONSUMERS, "heap_getTopConsumers");

    /** Every report, in pipeline order: what a preparation with no report named computes. */
    private static final List<HeapReport> ALL_REPORTS = List.of(HeapReport.values());

    /** Where a whole prepared dump is best read from first, rather than one call per report. */
    private static final List<String> WHOLE_DUMP_READERS = List.of(SUMMARY_TOOL, LEAK_SUSPECTS_TOOL, DOMINATOR_ROOTS_TOOL);

    private static final String DOMINATOR_FIRST_GUIDANCE =
            "Retained sizes come from the dominator stage. Until it completes they are missing rather "
                    + "than zero, so a ranking by retained size before then is empty for a reason.";
    private static final String RETRY_FAILED_GUIDANCE =
            "The prior attempt failed or was cancelled; the retry call starts a new one.";
    private static final String PREPARED_GUIDANCE =
            "The preparation completed: the reports listed are readable now, and heap_status keeps each "
                    + "stage's timing.";
    private static final String EXPIRED_GUIDANCE =
            "The operation ID expired after one hour; this is the last heap pipeline history.";

    private static final String WHY_STATUS = "reports how far each stage has got";
    private static final String WHY_PREPARE = "builds the index, the dominator tree and the reports";
    private static final String WHY_RETRY = "starts a new attempt at the same preparation";
    private static final String WHY_READ = "reads what the preparation computed";

    private final ProfileManager profileManager;
    private final HeapDumpInitService initService;
    private final AdvertisedFamilies advertised;

    /**
     * @param advertised the families this installation serves, which gate the next calls
     */
    public HeapComputeMcpTools(ProfileManager profileManager, HeapDumpInitService initService,
            AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.initService = initService;
        this.advertised = advertised;
    }

    @Tool(description = "Builds what this profile's heap dump needs before the reading tools can "
            + "answer: the index, the dominator tree that retained sizes come from, and the cached "
            + "reports (leak suspects, biggest objects, class-loader analysis, top consumers, string "
            + "and collection analysis). Runs on this call, streaming notifications/progress stage by "
            + "stage, and returns the stage list when it finishes; heap_status also reports it. A report "
            + "name computes just that one on a dump that is already indexed. Completed work that covers "
            + "the requested reports is reused and an active run is joined. What it writes is a cache.")
    @McpOutputSchema(PrepareAnswer.class)
    @McpToolHints(readOnly = false)
    @McpToolMeta(cost = McpToolCost.SLOW, requires = McpToolRequirement.HEAP_DUMP)
    public McpToolOutcome prepare(
            @ToolParam(required = false, description = "Compute only this report instead of all of them. "
                    + "Omit for a dump that has never been opened, which needs the whole pipeline")
            HeapReport report,
            @ToolParam(required = false, description = "Set true to restart a finished preparation, including failed or cancelled work. "
                    + "Omit to reuse completed work covering the requested reports or inspect a failed/cancelled attempt")
            Boolean retry,
            McpCallContext call) {

        HeapDumpManager heapDumpManager = requireHeapDump();
        String profileId = profileManager.info().id();
        HeapDumpInitService.Preparation preparation = initService.startPreparation(profileId, heapDumpManager,
                report == null ? null : report.stageId(), null, () -> { }, Boolean.TRUE.equals(retry));
        String link = UiLinks.view(profileId, HEAP_VIEW);
        List<HeapReport> computing = reports(preparation.reports());
        McpNextTool retryCall = retryCall(profileId, computing);
        // Prepares on this call and streams progress. PipelineRunRegistry is single-flight per profile,
        // so a concurrent prepare joins the one run; await its completion, then answer with the stages.
        call.reportProgress(0.0, 1.0, "Preparing the heap dump: index, dominator tree and reports");
        try {
            initService.awaitPreparation(profileId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("Interrupted while preparing the heap dump");
        }
        PipelineProgress progress = initService.progress(profileId);
        return McpToolResult.of(new PrepareAnswer(profileId, preparation.started(), computing, Stage.of(progress),
                readerSteps(profileId, computing).guidance(PREPARED_GUIDANCE).followUp(), link));
    }

    /*
     * Reads. Its family is registered as writing because heap_prepare builds the index; this one
     * only reports how that build is going, and says so rather than inheriting the claim.
     */
    @McpToolHints
    @Tool(description = "Reports how far heap_prepare has got on this profile: the state (IDLE, RUNNING, "
            + "COMPLETED or FAILED) and every stage with its status and, once finished, how long it took. "
            + "Unlike a report tool, it tells 'still building' from 'never asked for'. A profile whose dump "
            + "was prepared in a previous session reports the last run rather than nothing.")
    @McpOutputSchema(StatusAnswer.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult status() {
        String profileId = profileManager.info().id();
        PipelineProgress progress = initService.progress(profileId);
        String link = UiLinks.view(profileId, HEAP_VIEW);
        PreparationState state = PreparationState.valueOf(progress.state().name());
        List<Stage> stages = Stage.of(progress);
        Optional<HeapDumpInitService.Preparation> preparation = initService.operation(profileId);
        List<HeapReport> requested = preparation.map(run -> reports(run.reports())).orElse(ALL_REPORTS);
        McpNextTool retryCall = retryCall(profileId, requested);
        McpFollowUp followUp = statusSteps(profileId, progress, retryCall).followUp();
        return McpToolResult.of(new StatusAnswer(profileId, state, progress.isRunning(), progress.errorMessage(),
                stages, followUp, link));
    }

    /**
     * The heap dump this profile carries, or a refusal that says which family to use instead.
     * <p>
     * Checked here rather than left to fail inside the pipeline: a JFR recording asked to prepare a
     * heap dump would otherwise start a run that fails on its first stage, and the reader would read
     * the failure as a broken dump rather than as the wrong question.
     */
    private HeapDumpManager requireHeapDump() {
        return HeapDumpProfiles.require(profileManager);
    }

    private static List<HeapReport> reports(List<String> stageIds) {
        return stageIds.stream().filter(HeapReport::isReport).map(HeapReport::ofStage).toList();
    }

    /**
     * The call that starts the same preparation again: one report when one was asked for, the whole
     * pipeline otherwise.
     */
    private static McpNextTool retryCall(String profileId, List<HeapReport> computing) {
        return NextCalls.to(PREPARE_TOOL).with(PROFILE_ID, profileId)
                .with(REPORT, computing.size() == 1 ? computing.getFirst() : null)
                .with(RETRY, true)
                .why(WHY_RETRY);
    }

    /**
     * What follows the pipeline as it stands. Only a failure is offered the retry: a completed run is
     * routed to what it built, never to building it again.
     */
    private NextSteps.Builder statusSteps(String profileId, PipelineProgress progress, McpNextTool retryCall) {
        return switch (progress.state()) {
            case IDLE -> NextSteps.builder(advertised)
                    .next(onProfile(PREPARE_TOOL, profileId).why(WHY_PREPARE));
            case RUNNING -> NextSteps.builder(advertised)
                    .next(onProfile(STATUS_TOOL, profileId).why(WHY_STATUS))
                    .guidance(DOMINATOR_FIRST_GUIDANCE);
            case FAILED -> NextSteps.builder(advertised)
                    .next(retryCall)
                    .guidance(RETRY_FAILED_GUIDANCE);
            case COMPLETED -> readerSteps(profileId, completedReports(progress));
        };
    }

    /** An expired operation leaves the pipeline's history, which routes as the pipeline stands. */
    private McpFollowUp expiredSteps(String profileId, PipelineProgress progress, McpNextTool retryCall) {
        return statusSteps(profileId, progress, retryCall).guidance(EXPIRED_GUIDANCE).followUp();
    }

    /**
     * The tools that read what a preparation computed: for the whole pipeline the three a heap
     * investigation opens with, for one report the tool that reads it.
     */
    private NextSteps.Builder readerSteps(String profileId, List<HeapReport> computed) {
        NextSteps.Builder steps = NextSteps.builder(advertised);
        if (computed.isEmpty() || computed.containsAll(ALL_REPORTS)) {
            WHOLE_DUMP_READERS.forEach(tool -> steps.next(onProfile(tool, profileId).why(WHY_READ)));
            return steps;
        }
        for (HeapReport report : computed) {
            String reader = READERS.get(report);
            if (reader != null) {
                steps.next(onProfile(reader, profileId).why(WHY_READ));
            }
        }
        return steps;
    }

    private static List<HeapReport> completedReports(PipelineProgress progress) {
        return progress.stages().stream()
                .filter(stage -> stage.status() == StageStatus.COMPLETED && HeapReport.isReport(stage.id()))
                .map(stage -> HeapReport.ofStage(stage.id()))
                .toList();
    }

    private static McpNextTool.Call onProfile(String tool, String profileId) {
        return NextCalls.to(tool).with(PROFILE_ID, profileId);
    }

    /** Where the heap-dump preparation pipeline stands, named as the answer reports it. */
    public enum PreparationState {
        /** Never run in this process, and no earlier run left a result. */
        IDLE,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record PrepareAnswer(
            String profileId,
            @McpDescription("False when a run was already in flight or finished and this call joined it, which "
                    + "is not a failure: the caller is watching the same work either way")
            boolean started,
            @McpDescription("The reports the run computes")
            List<HeapReport> computing,
            @McpDescription("Every stage of the pipeline, in order")
            List<Stage> stages,
            McpFollowUp followUp,
            @McpDescription("The heap-dump overview page in the Microscope UI, for the user")
            String uiLink) {
    }

    public record StatusAnswer(
            String profileId,
            PreparationState state,
            boolean running,
            @McpNullable
            @McpDescription("Why the last run failed; null unless it did")
            String errorMessage,
            @McpDescription("Every stage of the pipeline, in order")
            List<Stage> stages,
            McpFollowUp followUp,
            @McpDescription("The heap-dump overview page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * One stage of the preparation. A stage that computes a report names it as {@code heap_prepare}
     * takes it, beside the pipeline's own lowercase id.
     */
    public record Stage(
            @McpDescription("The pipeline's id of the stage, as the web UI and the operation's progress name it")
            String id,
            @McpNullable
            @McpDescription("The report this stage computes, as heap_prepare's report input takes it; null for "
                    + "the stages that build the index")
            HeapReport report,
            PipelineStage.Status status,
            @McpNullable
            @McpDescription("How long the stage took, in milliseconds; null while it has not finished")
            Long durationMs) {

        static List<Stage> of(PipelineProgress progress) {
            return PipelineStage.of(progress.stages()).stream()
                    .map(stage -> new Stage(stage.id(),
                            HeapReport.isReport(stage.id()) ? HeapReport.ofStage(stage.id()) : null,
                            stage.status(), stage.durationMs()))
                    .toList();
        }
    }
}

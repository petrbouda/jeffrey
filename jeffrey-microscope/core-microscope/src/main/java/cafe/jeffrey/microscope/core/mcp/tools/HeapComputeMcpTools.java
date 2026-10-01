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
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.StageStatus;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
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
    private static final String ALREADY_RUNNING_GUIDANCE =
            "A run was already in flight for this profile, so this call joined it rather than starting "
                    + "a second one. The progress below is that run's.";
    private static final String ALREADY_COMPLETED_GUIDANCE =
            "The preparation already completed, so nothing was restarted: the stages below are that "
                    + "run's history.";
    private static final String REBUILD_GUIDANCE =
            "heap_prepare with retry=true rebuilds it from scratch, which is worth its minutes only when the "
                    + "cached reports are suspect.";
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
    private final Supplier<? extends AutoCloseable> backgroundLease;
    private final McpOperationRegistry operations;
    private final OperationAnswers answers;
    private final AdvertisedFamilies advertised;

    /**
     * @param answers    how a preparation still running is answered, by what the client declared
     * @param advertised the families this installation serves, which gate the next calls
     */
    public HeapComputeMcpTools(ProfileManager profileManager, HeapDumpInitService initService,
            Supplier<? extends AutoCloseable> backgroundLease, McpOperationRegistry operations,
            OperationAnswers answers, AdvertisedFamilies advertised) {
        this.operations = operations;
        this.answers = answers;
        this.backgroundLease = backgroundLease;
        this.profileManager = profileManager;
        this.initService = initService;
        this.advertised = advertised;
    }

    @Tool(description = "Builds what this profile's heap dump needs before the reading tools can "
            + "answer: the index, the dominator tree that retained sizes come from, and the cached "
            + "reports (leak suspects, biggest objects, class-loader analysis, top consumers, string "
            + "and collection analysis). Answers at once with the stage list and an operationId for "
            + "operations_status and operations_cancel, or with a task for a client that declared the "
            + "MCP tasks extension; the work continues in the background and "
            + "heap_status reports it. A report name computes just that one on a dump that is "
            + "already indexed. Completed work that covers the requested reports is reused and an "
            + "active run is joined; a retained failed or cancelled run starts again only with "
            + "retry=true. What it writes is a cache.")
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
        AutoCloseable lease = backgroundLease.get();
        boolean started = false;
        HeapDumpInitService.Preparation preparation;
        try {
            preparation = initService.startPreparation(profileId, heapDumpManager,
                    report == null ? null : report.stageId(), null, () -> release(lease), Boolean.TRUE.equals(retry));
            started = preparation.started();
        } finally {
            // A joined or rejected request handed no work to the service, so it still owns its lease.
            if (!started) {
                release(lease);
            }
        }

        OperationState state = preparation.operation().snapshot().state();
        String link = UiLinks.view(profileId, HEAP_VIEW);
        List<HeapReport> computing = reports(preparation.reports());
        McpNextTool retryCall = retryCall(profileId, computing);
        Optional<String> operationId = register(profileId, preparation, started, link, retryCall);
        if (operationId.isEmpty()) {
            PipelineProgress progress = initService.progress(profileId);
            return McpToolResult.of(new PrepareAnswer(profileId, started, computing, Stage.of(progress), null, null,
                    true, expiredSteps(profileId, progress, retryCall), link));
        }
        String id = operationId.get();
        boolean startedHere = started;
        McpFollowUp followUp = prepareSteps(profileId, startedHere, state, computing, retryCall);
        Supplier<McpToolResult> answer = () -> McpToolResult.of(new PrepareAnswer(profileId, startedHere, computing,
                Stage.of(initService.progress(profileId)), id, operations.status(id), false,
                followUp, link));
        // Answered at once whatever the client declared. A client that can follow a task is handed the
        // preparation as one while it runs, and the task answers once it has finished; a run already
        // finished -- completed, or failed or cancelled with its retry call -- has nothing to follow.
        if (state.terminal()) {
            return answer.get();
        }
        return answers.stillRunning(call, id, answer);
    }

    /**
     * @param started whether the call registering it started the run; heap_status passes false, since
     *                it only ever reads a run someone else started
     * @param link    the heap view, read now because the request is bound here and may not be where
     *                the answer is rendered
     * @param retry   the call that starts the same preparation again, which a failed run's operation names
     */
    private Optional<String> register(String profileId, HeapDumpInitService.Preparation preparation,
            boolean started, String link, McpNextTool retry) {
        List<HeapReport> computing = reports(preparation.reports());
        String operationId = preparation.operation().operationId();
        // What a task following the preparation completes with: the stage list heap_prepare renders,
        // read again now that the stages have run.
        Function<PipelineProgress, McpToolResult> prepared = progress -> McpToolResult.of(new PrepareAnswer(
                profileId, started, computing, Stage.of(initService.progress(profileId)),
                operationId, operations.status(operationId), false,
                readerSteps(profileId, computing).guidance(PREPARED_GUIDANCE).followUp(), link));
        return operations.registerIfRetained(OperationKind.HEAP_PREPARE, preparation.operation(),
                progress -> new PreparedHeap(profileId, computing), prepared, retry);
    }

    /**
     * Reported rather than thrown, because of where this runs: the background run calls it from the
     * {@code finally} that follows storing its result, so an exception here would replace whatever
     * that storage threw — losing the failure worth reading to report the cleanup that followed it.
     * A lease that will not release is a leaked pool entry, which is a thing to find in the log, not
     * a reason to lose the diagnosis.
     */
    private static void release(AutoCloseable lease) {
        try {
            lease.close();
        } catch (Exception e) {
            LOG.warn("Cannot release the heap preparation lease: message={}", e.getMessage(), e);
        }
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
        if (preparation.isEmpty()) {
            return McpToolResult.of(new StatusAnswer(profileId, state, progress.isRunning(), progress.errorMessage(),
                    stages, null, null, false, followUp, link));
        }
        Optional<String> operationId = register(profileId, preparation.get(), false, link, retryCall);
        if (operationId.isEmpty()) {
            return McpToolResult.of(new StatusAnswer(profileId, state, progress.isRunning(), progress.errorMessage(),
                    stages, null, null, true, expiredSteps(profileId, progress, retryCall), link));
        }
        return McpToolResult.of(new StatusAnswer(profileId, state, progress.isRunning(), progress.errorMessage(),
                stages, operationId.get(), operations.status(operationId.get()), false, followUp, link));
    }

    /**
     * The heap dump this profile carries, or a refusal that says which family to use instead.
     * <p>
     * Checked here rather than left to fail inside the pipeline: a JFR recording asked to prepare a
     * heap dump would otherwise start a run that fails on its first stage, and the reader would read
     * the failure as a broken dump rather than as the wrong question.
     */
    private HeapDumpManager requireHeapDump() {
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists()) {
            throw new IllegalArgumentException(
                    "Profile " + profileManager.info().id() + " has no heap dump. Use profiles_features "
                            + "to see what a profile can answer; for a JFR recording use the jfr_, "
                            + "flamegraph_ and traces_ tools instead.");
        }
        return heapDumpManager;
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
     * A joined call reads differently depending on what it joined: a run still going, a run that
     * already finished (which an omitted {@code retry} deliberately does not restart), or one that
     * failed and is waiting for an explicit retry.
     */
    private McpFollowUp prepareSteps(String profileId, boolean started, OperationState joined,
            List<HeapReport> computing, McpNextTool retryCall) {
        if (!started && joined == OperationState.COMPLETED) {
            return readerSteps(profileId, computing)
                    .guidance(ALREADY_COMPLETED_GUIDANCE)
                    .guidance(REBUILD_GUIDANCE)
                    .followUp();
        }
        if (!started && joined.terminal()) {
            return NextSteps.builder(advertised)
                    .next(retryCall)
                    .next(onProfile(STATUS_TOOL, profileId).why(WHY_STATUS))
                    .guidance(RETRY_FAILED_GUIDANCE)
                    .followUp();
        }
        return NextSteps.builder(advertised)
                .next(onProfile(STATUS_TOOL, profileId).why(WHY_STATUS))
                .guidanceWhen(!started, ALREADY_RUNNING_GUIDANCE)
                .guidance(DOMINATOR_FIRST_GUIDANCE)
                .followUp();
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
            @McpNullable
            @McpDescription("The preparation's operation; null when its retention expired")
            String operationId,
            @McpNullable
            @McpDescription("That operation as operations_status reports it; null when its retention expired")
            McpOperationRegistry.Snapshot operation,
            @McpDescription("Whether the operation expired an hour after it finished, leaving only the pipeline's "
                    + "history")
            boolean operationExpired,
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
            @McpNullable
            @McpDescription("The last preparation's operation; null when there was none in this process or it expired")
            String operationId,
            @McpNullable
            McpOperationRegistry.Snapshot operation,
            @McpDescription("Whether the operation expired an hour after it finished, leaving only the pipeline's "
                    + "history")
            boolean operationExpired,
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

    /** What a finished preparation produced, as operations_status reports it. */
    private record PreparedHeap(String profileId, List<HeapReport> reports) {
    }
}

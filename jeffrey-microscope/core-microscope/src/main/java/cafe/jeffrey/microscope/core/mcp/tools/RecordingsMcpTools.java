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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopePage;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpMinimum;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.mcp.JeffreyMcpServer;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The family that brings recordings in: it takes a recording Jeffrey has never seen and turns it into
 * a profile the read-only families can then answer questions about, and removes one that has served
 * its purpose.
 * <p>
 * This is what closes the loop for a reader working in their own repository — a JFR file lands in
 * {@code target/}, and analysing it no longer means leaving the terminal, opening the UI, uploading and
 * clicking Analyze. Everything after that first step is what the {@code profiles_}, {@code jfr_},
 * {@code flamegraph_}, {@code traces_} and {@code heap_} families already did.
 * <p>
 * Ingestion is by <em>local path</em>, not by content: a JFR recording routinely runs to hundreds of
 * megabytes, and base64 through a JSON-RPC message would spend the client's whole context on a file
 * neither side ever reads. The consequence is the constraint stated in every tool description here —
 * the path is resolved by the Jeffrey process, so the file has to be on the machine Jeffrey runs on.
 * That is the normal case (a CLI session and a Jeffrey on one laptop) and a container or a remote
 * Jeffrey is the case where it does not hold.
 * <p>
 * Advertised whenever the MCP server is, unless {@code jeffrey.microscope.mcp.families} or the preset
 * leaves the family out; there is no separate switch for importing.
 */
public class RecordingsMcpTools {

    private static final String RECORDING_VANISHED = "Recording vanished while being analyzed: ";
    private static final String PIPELINE_FAILED = "The analysis this attempt joined failed: ";
    private static final String INTERRUPTED_JOINING_PIPELINE = "Interrupted while waiting for the running analysis";
    private static final String IMPORT_FAILED =
            "Importing and analysing the file failed; operations_status has the same detail: ";
    private static final Logger LOG = LoggerFactory.getLogger(RecordingsMcpTools.class);

    /** The application property that caps how many {@code recordings_analyzeFile} imports run together. */
    public static final String MAX_CONCURRENT_IMPORTS_PROPERTY =
            "jeffrey.microscope.mcp.recordings.max-concurrent-imports";

    /**
     * How many imports run together unless the property says otherwise. An import is a file copy
     * followed by a full parse, and N calls in one turn used to start N of both at once; the ones
     * beyond this wait as {@code queued} and start on their own when a slot frees.
     */
    public static final int DEFAULT_MAX_CONCURRENT_IMPORTS = 2;

    private static final String HOME_PREFIX = "~";
    private static final String USER_HOME_PROPERTY = "user.home";

    /** How many rows {@code recordings_list} shows when the caller does not say. */
    private static final int DEFAULT_LIST_LIMIT = 100;

    /** The most rows {@code recordings_list} shows however many are asked for. */
    private static final int MAX_LIST_LIMIT = 1000;

    private static final String LIST_TOOL = "recordings_list";
    private static final String STATUS_TOOL = "recordings_status";
    private static final String ANALYZE_RECORDING_TOOL = "recordings_analyzeRecording";
    private static final String OPERATIONS_STATUS = "operations_status";
    private static final String PROFILES_SUMMARY = "profiles_summary";
    private static final String RECORDING_ID = "recordingId";
    private static final String PROFILE_ID = "profileId";
    private static final String OPERATION_ID = "operationId";
    private static final String RETRY = "retry";
    private static final String LIMIT = "limit";
    private static final String CURSOR = "cursor";

    private static final String SUMMARY_WHY = "orients: the features, event types, top findings and capability gaps";
    private static final String POLL_RECORDING_WHY = "reports the parse stage by stage, and the profile once it is ready";
    private static final String POLL_OPERATION_WHY = "reports the import's progress, and its result once it finishes";
    private static final String ANALYZE_WHY = "builds the profile from the stored recording";
    private static final String RETRY_WHY = "starts a new attempt at analysing the recording";
    private static final String NEXT_PAGE_WHY = "continues the list past this page";
    private static final String UNANALYSED_WHY = "builds a profile from the newest listed recording that has none";

    private static final String NO_RECORDINGS =
            "The Quick Analysis store is empty. Use recordings_analyzeFile with the absolute path of a "
                    + "JFR recording or heap dump to add one.";

    private static final String ANALYSIS_FAILED = "The analysis failed; errorMessage says why.";

    private static final String PARAGRAPH = "\n\n";
    private static final String NONE_AFTER_CURSOR = "No recordings remain after this cursor.";
    private static final String UNANALYSED_NOTE = "A row with an empty `profile_id` has not been analysed yet - pass its "
            + "`recording_id` to recordings_analyzeRecording.";
    private static final String MORE_RECORDINGS = "More recordings are available (hasMore=true).";
    private static final String END_OF_LIST = "End of the list (hasMore=false).";

    /**
     * Said when the profile row exists and the profile does not yet work. Without it a caller reading
     * a profileId beside a running status would reasonably try to use it.
     */
    private static final String NOT_READY_YET =
            "The profile exists but its events are still being written, so it cannot be analysed yet. "
                    + "Wait for status READY before using the profileId.";

    private static final String INTERRUPTED_NOTE =
            "The profile was left disabled and this process has no active initialization for it. "
                    + "Jeffrey likely restarted while the recording was being analyzed.";

    private static final String NOT_STARTED_NOTE =
            "No profile has been built from this recording, and no attempt is running or retained in this process.";

    /**
     * The order {@code recordings_list} pages in, as a key a cursor can carry: newest first, a recording
     * without a creation time last, and the id breaking a tie between two imports in one instant, so
     * the order is total and a cursor continues exactly after the row it names.
     */
    private static final Comparator<ListKey> NEWEST_FIRST = Comparator
            .comparing(ListKey::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ListKey::recordingId);

    /** Where a keyset cursor of {@code recordings_list} keeps each part of its key. */
    private static final int KEY_CREATED_AT = 0;
    private static final int KEY_RECORDING_ID = 1;
    private static final int KEY_PARTS = 2;

    private final RecordingsManager recordingsManager;
    private final PipelineRunRegistry<String> runRegistry;
    private final BoundedJobs<String, String> jobs;
    private final BoundedJobs<ImportKey, ImportedProfile> imports;
    private final McpOperationRegistry operations;
    private final OperationAnswers answers;
    private final AdvertisedFamilies advertised;

    /**
     * @param runRegistry          the profile-init pipeline, so a poll can report which stage the parse
     *                             is on rather than only that it has not finished
     * @param jobs                 the analysis jobs, keyed by recording id; their wait budget is the
     *                             import jobs' too
     * @param operations           the registry operations_status reads, so every attempt started here
     *                             can be followed there
     * @param answers              how a call that outlasts its wait answers, by what the client declared
     * @param maxConcurrentImports how many {@code recordings_analyzeFile} imports may run together,
     *                             from {@link #MAX_CONCURRENT_IMPORTS_PROPERTY}
     * @param clock                what the import jobs stamp their attempts with; the analysis jobs
     *                             carry their own
     * @param advertised           the families a next call may route to
     */
    public RecordingsMcpTools(RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry, BoundedJobs<String, String> jobs,
            McpOperationRegistry operations, OperationAnswers answers, int maxConcurrentImports, Clock clock,
            AdvertisedFamilies advertised) {
        this.operations = operations;
        this.answers = answers;
        this.advertised = advertised;
        this.imports = new BoundedJobs<>(jobs.waitBudget(), BoundedJobs.COMPLETED_RETENTION, clock,
                Schedulers.sharedVirtual(), maxConcurrentImports, BoundedJobs.DEFAULT_MAX_RETAINED);
        this.recordingsManager = recordingsManager;
        this.runRegistry = runRegistry;
        this.jobs = jobs;
    }

    @Tool(description = "Analyses a recording file - a .jfr, .jfr.lz4, .hprof, .hprof.gz, .pprof or "
            + ".otlp file in the user's repository or filesystem - by importing it into the Quick "
            + "Analysis store and building a profile, and returns the profile id every other tool "
            + "takes. The Jeffrey process opens the path, so the file has to be on the machine "
            + "Jeffrey runs on. A file whose name and size match a stored recording is not imported "
            + "again: its profile comes back with reused=true, or the stored recording is analysed "
            + "when it has none yet; force=true imports it anyway. A small recording answers with "
            + "status READY and its profileId; a large one with status RUNNING and an operationId, "
            + "which operations_status follows through the copy and the analysis - or, for a client "
            + "that declared the MCP tasks extension, with a task after about 5 s.")
    @McpOutputSchema(RecordingAnalysis.class)
    @McpToolMeta(cost = McpToolCost.SLOW)
    public McpToolOutcome analyzeFile(
            @ToolParam(required = true, description = "Absolute path of the recording file to import, e.g. "
                    + "/home/dev/project/target/app.jfr. A leading ~ is expanded. Relative paths are "
                    + "rejected because they would resolve against Jeffrey's working directory, not "
                    + "the caller's")
            String path,
            @ToolParam(required = false, description = "Optional name for the profile. Defaults to the file "
                    + "name. Not applied when an existing profile is returned")
            String name,
            @ToolParam(required = false, description = "Set true to import even when a stored recording "
                    + "already has a file with the same name and size, building a second profile of it. "
                    + "Omit to get the existing profile back")
            Boolean force,
            McpCallContext call) {

        Path recordingPath = validatedPath(path);

        ImportKey key;
        if (Boolean.TRUE.equals(force)) {
            key = ImportKey.Forced.fresh();
        } else {
            ImportKey.SourceFile source = ImportKey.SourceFile.of(recordingPath);
            Optional<Recording> imported = importedBefore(source);
            if (imported.isPresent()) {
                return fromEarlierImport(imported.get(), name, call);
            }
            key = source;
        }

        LOG.info("Importing a recording over MCP: path={}", recordingPath);
        AtomicReference<String> importedRecording = new AtomicReference<>();
        // Keyed on the file's identity, so a call arriving while this copy is still landing — which
        // the reuse check above cannot see yet — joins it rather than importing the file twice. A
        // failed or cancelled attempt is not held against the file: the next call imports it again.
        // A finished one is joined only while what it built still stands; otherwise the store has
        // lost that recording and the file is imported afresh.
        OperationHandle<ImportedProfile> operation = imports.startOrJoin(key, true,
                done -> profileStillAvailable(done.recordingId(), done.profileId()), control -> {
                    control.phase(OperationPhase.IMPORTING);
                    String recordingId = recordingsManager.importRecordingFromPath(recordingPath);
                    importedRecording.set(recordingId);
                    control.progress(OperationDetails.recording(recordingId, List.of()));
                    control.checkCancellation();
                    control.phase(OperationPhase.ANALYZING);
                    OperationHandle<String> analysis = analysisOperation(recordingId, name, true);
                    control.onCancellation(analysis::cancel);
                    control.progressFrom(() -> OperationDetails.recording(recordingId, stagesOf(analysis)));
                    return new ImportedProfile(recordingId, jobs.awaitCompletion(analysis));
                });
        String operationId = operation.operationId();
        // Read here, where the request is bound: the answer is rendered wherever the finished import is
        // first seen, and that need not be a request thread.
        String base = UiLinks.base();
        // The ids the import handed over, not a read of the progress: a progress supplier that failed
        // leaves the progress saying so instead of naming a recording. A call that joined an import in
        // flight never ran its work, so only the result can tell it which recording it is.
        Function<ImportedProfile, McpToolResult> answer = imported -> McpToolResult.of(analysis(
                profileOf(imported.recordingId(), imported.profileId(), base), operationId, base));
        operations.register(OperationKind.RECORDING_IMPORT, operation,
                done -> new ImportResult(done.profileId()), () -> importedRecordingOf(operation, importedRecording),
                answer);
        Optional<ImportedProfile> finished;
        try {
            finished = imports.awaitWithin(operation, answers.waitBudget(call, imports.waitBudget()));
        } catch (RuntimeException failure) {
            // A failure is the call's answer, not a status beside a success: thrown so the envelope
            // marks the result isError, carrying the operation so it can still be inspected.
            throw new ToolExecutionException(
                    IMPORT_FAILED + Json.toString(operations.status(operationId)), failure);
        }
        if (finished.isEmpty()) {
            return answers.stillRunning(call, operationId, () -> McpToolResult.of(analysis(
                    AnalysisFacts.running(importedRecordingOf(operation, importedRecording), null, null),
                    operationId, base)));
        }
        return answer.apply(finished.get());
    }

    /**
     * The stages of the analysis an import is running, as that analysis reports them; none until the
     * parse has a profile to report them for.
     */
    private static List<PipelineStage> stagesOf(OperationHandle<String> analysis) {
        return analysis.snapshot().progress() instanceof OperationDetails details ? details.stages() : List.of();
    }

    /**
     * The recording an import stored, for {@code recordings_status} to find the operation by. The
     * call that started the import learns it the moment the copy lands; a call that joined it — and
     * registered the operation first — learns it only from the finished result.
     */
    private static String importedRecordingOf(
            OperationHandle<ImportedProfile> operation, AtomicReference<String> startedHere) {
        String recordingId = startedHere.get();
        if (recordingId != null) {
            return recordingId;
        }
        ImportedProfile done = operation.lifecycleSnapshot().result();
        return done == null ? null : done.recordingId();
    }

    /**
     * The recording already in the store of a file with this file's name and size. The size is read
     * at the moment of the call, so a file a new run rewrote to a different length never matches the
     * import of the old one; a rewrite to the very same length does.
     */
    private Optional<Recording> importedBefore(ImportKey.SourceFile source) {
        return recordingsManager.findByFileNameAndSize(source.fileName(), source.size());
    }

    /**
     * Answers from the recording an earlier import made, the way recordings_analyzeRecording answers
     * for a stored recording: a working profile is handed back as it is, anything else — no profile
     * yet, or one still being built — goes down the analysis path, which joins a run in flight.
     */
    private McpToolOutcome fromEarlierImport(Recording recording, String name, McpCallContext call) {
        Optional<String> live = enabledProfile(recording.id());
        if (live.isPresent()) {
            LOG.info("Returning the profile of a file already stored over MCP: recording_id={} profile_id={}",
                    recording.id(), live.get());
            String base = UiLinks.base();
            return McpToolResult.of(analysis(profileOf(recording.id(), live.get(), base).asReused(), null, base));
        }
        LOG.info("Analyzing the stored recording of a file already stored over MCP: recording_id={}", recording.id());
        return analyzed(recording.id(), name, false, call);
    }

    @Tool(description = "Analyses a recording that is already in Jeffrey's Quick Analysis store but has "
            + "no profile yet - one uploaded through the web UI, one pulled in by hubs_download, or "
            + "one recordings_list shows without a profileId. Returns status READY with the profile "
            + "id every other tool takes, or status RUNNING when parsing outlasts the call, which "
            + "recordings_status follows - a client that declared the MCP tasks extension gets a task "
            + "after about 5 s instead. A recording that already has a profile is returned as it "
            + "is. Results include an operationId for operations_status and operations_cancel; a "
            + "retained failure starts again only with retry=true, and outcomes are held in memory "
            + "for one hour and forgotten on restart.")
    @McpOutputSchema(RecordingAnalysis.class)
    @McpToolMeta(cost = McpToolCost.SLOW)
    public McpToolOutcome analyzeRecording(
            @ToolParam(required = true, description = "Recording id, as returned by recordings_list")
            String recordingId,
            @ToolParam(required = false, description = "Set true to retry a retained failed or cancelled analysis. Omit to inspect the same attempt")
            Boolean retry,
            McpCallContext call) {

        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException("A recording id is required. Call recordings_list to see them.");
        }

        LOG.info("Analyzing a stored recording over MCP: recording_id={}", recordingId);
        return analyzed(recordingId.trim(), null, Boolean.TRUE.equals(retry), call);
    }

    /*
     * The one tool in this family that takes something away. It is the other half of downloading a
     * window of a hub session: the hub is the copy of record, Microscope holds what is being read,
     * and a window profile that has answered its question has no reason to stay. The only tool that
     * declares itself destructive: the family's hint says it writes, and a client that asks before a
     * destructive call should get to ask here. Deleting twice is refused, not repeated, so it is not
     * idempotent either. A client that declared form elicitation is asked here too, through
     * DeleteConfirmation; the description does not say so, because the tool list is the same for
     * every client.
     */
    @McpToolHints(readOnly = false, destructive = true, idempotent = false)
    @Tool(description = "Deletes a recording from the Quick Analysis store together with the profile "
            + "built from it, its files and everything analysed out of it - a window of a hub "
            + "session pulled in by hubs_download, a partial look taken before the real window, a "
            + "file imported twice. A recording on a hub is untouched: hubs_download can pull it "
            + "again. The profile id stops working the moment this returns. status NOT_CONFIRMED: "
            + "the user declined, and nothing was deleted.")
    @McpOutputSchema(RecordingDeletion.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolOutcome delete(
            @ToolParam(required = true, description = "Recording id from recordings_list, hubs_download or "
                    + "recordings_analyzeFile. A profile id is not accepted; recordings_list shows which "
                    + "recording a profile belongs to")
            String recordingId,
            McpCallContext call) {
        // Validated first, and again on the retry that carries the answer: the user is never asked to
        // confirm a deletion that would be refused, and a parse started since the question still
        // blocks it.
        Recording recording = deletable(recordingId);

        // A client that renders forms is asked; one that does not is not, and its host still sees
        // destructiveHint and may ask on its own.
        if (call.canElicitForm()) {
            switch (DeleteConfirmation.read(call)) {
                case DeleteConfirmation.Confirmation.Unanswered _ -> {
                    return DeleteConfirmation.ask(recording);
                }
                case DeleteConfirmation.Confirmation.Withheld withheld -> {
                    LOG.info("Recording deletion was not confirmed over MCP: recording_id={}", recording.id());
                    return McpToolResult.of(new RecordingDeletion(DeletionStatus.NOT_CONFIRMED, recording.id(),
                            recording.recordingName(), recording.profileId(), withheld.reason(), UiLinks.page(MicroscopePage.RECORDINGS)));
                }
                case DeleteConfirmation.Confirmation.Confirmed _ -> { }
            }
        }

        String id = recording.id();
        LOG.info("Deleting a recording over MCP: recording_id={} profile_id={}", id, recording.profileId());
        recordingsManager.deleteRecording(id);
        return McpToolResult.of(new RecordingDeletion(DeletionStatus.DELETED, id, recording.recordingName(),
                recording.profileId(), null, UiLinks.page(MicroscopePage.RECORDINGS)));
    }

    /**
     * The recording a deletion names, when it may be deleted now.
     *
     * @throws IllegalArgumentException for a blank or unknown id, or a recording whose profile is
     *                                  still being built
     */
    private Recording deletable(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException(
                    "A recording id is required. Call recordings_list to see them.");
        }
        String id = recordingId.trim();
        Recording recording = recordingsManager.findRecording(id)
                .orElseThrow(() -> new IllegalArgumentException("No such recording: " + id));

        // Deleting a profile while it is being built pulls the database out from under the parser.
        // analyzeRecording already refuses to disturb a live run for the same reason; this is the
        // other half of it, and matters more here because an agent can analyse and delete within
        // seconds of each other.
        if (recording.hasProfile() && runRegistry.isRunning(recording.profileId())) {
            throw new IllegalArgumentException("Profile " + recording.profileId()
                    + " is still being built from recording " + id + ", and deleting it now would leave the "
                    + "parse writing into storage that is no longer there. Poll recordings_status until it "
                    + "finishes, or stop it with operations_cancel, then delete.");
        }
        return recording;
    }

    /*
     * Reads. Its family is registered as writing because the tools that build a profile sit in
     * it, and a member that only reports has to say so for itself — the same inheritance that let
     * hubs_download offer a cross-machine transfer as a safe read, running the other way.
     */
    @McpToolHints
    @Tool(description = "Returns the recordings in the Quick Analysis store, newest first, analysed or "
            + "not - one uploaded but never analysed has a null profileId. Times are UTC epoch "
            + "milliseconds. Up to 100 rows by default, at most 1000, further bounded by response "
            + "size: follow nextCursor until hasMore=false. status EMPTY: the store holds nothing.")
    @McpOutputSchema(RecordingPage.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult list(
            @ToolParam(required = false, description = "How many recordings to show (default "
                    + DEFAULT_LIST_LIMIT + ", maximum " + MAX_LIST_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_LIST_LIMIT, min = 1, max = MAX_LIST_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the preceding page")
            String cursor) {
        McpCursor.Filters filters = McpCursor.Filters.of(LIST_TOOL);
        ListKey after = cursor == null ? null : JeffreyMcpServer.CURSOR.decodeKeyset(cursor, filters, ListKey::read);
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_LIST_LIMIT, MAX_LIST_LIMIT);
        String uiLink = UiLinks.page(MicroscopePage.RECORDINGS);
        List<Recording> recordings = recordingsManager.listRecordings();
        if (recordings.isEmpty()) {
            RecordingPage empty = new RecordingPage(CatalogueStatus.EMPTY, NO_RECORDINGS, List.of(), 0, 0, false, null,
                    NextSteps.builder(advertised).followUp(), uiLink);
            return McpToolResult.of(NO_RECORDINGS + LinkedOutput.footer(empty.followUp(), uiLink, null), empty);
        }

        // Continued after the row the cursor names, not at a position: a recording deleted from an
        // earlier page between two calls cannot move an unseen row onto that page.
        List<Recording> remaining = recordings.stream()
                .sorted(Comparator.comparing(ListKey::of, NEWEST_FIRST))
                .filter(recording -> after == null || NEWEST_FIRST.compare(ListKey.of(recording), after) > 0)
                .toList();
        ListRequest request = new ListRequest(remaining.size(), recordings.size(), rows, filters, uiLink);
        return FittingPage.largest(Math.min(remaining.size(), rows),
                        count -> listPage(remaining.subList(0, count), request),
                        ListPage::fits)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A recording's identifiers exceed the response size limit; its row cannot be returned intact."))
                .result();
    }

    private ListPage listPage(List<Recording> page, ListRequest request) {
        boolean hasMore = page.size() < request.remaining();
        McpCursor.Next next = hasMore
                ? new McpCursor.Next(true, JeffreyMcpServer.CURSOR.encode(request.filters(), ListKey.of(page.getLast()).keyset()))
                : McpCursor.Next.END;
        List<RecordingRow> rows = page.stream()
                .map(recording -> new RecordingRow(
                        recording.id(),
                        recording.recordingName(),
                        recording.eventSource() == null ? null : recording.eventSource().name(),
                        recording.recordingStartedAt() == null ? null : recording.recordingStartedAt().toEpochMilli(),
                        recording.hasProfile() ? recording.profileId() : null))
                .toList();
        String unanalysed = rows.stream()
                .filter(row -> row.profileId() == null)
                .map(RecordingRow::recordingId)
                .findFirst()
                .orElse(null);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(next.hasMore(), NextCalls.to(LIST_TOOL)
                        .with(LIMIT, request.limit()).with(CURSOR, next.nextCursor()).why(NEXT_PAGE_WHY))
                .nextWhen(unanalysed != null, NextCalls.to(ANALYZE_RECORDING_TOOL)
                        .with(RECORDING_ID, unanalysed).why(UNANALYSED_WHY))
                .followUp();
        RecordingPage structured = new RecordingPage(CatalogueStatus.OK, null, rows, rows.size(), request.total(),
                next.hasMore(), next.nextCursor(), followUp, request.uiLink());

        MarkdownTable table = MarkdownTable.withColumns(
                "recording_id", "name", "event source", "recorded", "profile_id");
        for (Recording recording : page) {
            table.row(
                    recording.id(),
                    recording.recordingName(),
                    recording.eventSource(),
                    recording.recordingStartedAt(),
                    recording.hasProfile() ? recording.profileId() : "");
        }
        StringBuilder text = new StringBuilder()
                .append("Returned ").append(rows.size()).append(" of ").append(request.total()).append(" recordings.")
                .append(PARAGRAPH);
        if (rows.isEmpty()) {
            text.append(NONE_AFTER_CURSOR);
        } else {
            text.append(table.note(UNANALYSED_NOTE).renderUncapped());
        }
        // The next page is named once, by the footer's Next: line, which carries the cursor.
        text.append(PARAGRAPH).append(next.hasMore() ? MORE_RECORDINGS : END_OF_LIST);
        // The page is measured whole, footer included, and never cut: the footer is appended, not capped.
        text.append(LinkedOutput.footer(followUp, request.uiLink(), null));
        return new ListPage(text.toString(), structured);
    }

    /*
     * Reads. Its family is registered as writing because the tools that build a profile sit in
     * it, and a member that only reports has to say so for itself — the same inheritance that let
     * hubs_download offer a cross-machine transfer as a safe read, running the other way.
     */
    @McpToolHints
    @Tool(description = "Reports how far the analysis of a recording has got, stage by stage, and the "
            + "profile id once it is ready (status READY) - for a recordings_analyzeFile or "
            + "recordings_analyzeRecording call that answered with status RUNNING because parsing "
            + "a large recording outlasts a tool call. Also FAILED, INTERRUPTED (Jeffrey restarted "
            + "mid-parse) and NOT_STARTED.")
    @McpOutputSchema(RecordingAnalysis.class)
    @McpToolMeta(cost = McpToolCost.CHEAP)
    public McpToolResult status(
            @ToolParam(required = true, description = "Recording id, as returned by the analyze tool "
                    + "that reported the analysis was still running")
            String recordingId) {
        AnalysisFacts facts = statusOf(recordingId);
        String operationId = operations.latestForRecording(facts.recordingId()).orElse(null);
        return McpToolResult.of(analysis(facts, operationId, UiLinks.base()));
    }

    private AnalysisFacts statusOf(String recordingId) {
        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException(
                    "A recording id is required. Call recordings_list to see them.");
        }
        String id = recordingId.trim();
        Recording recording = recordingsManager.findRecording(id)
                .orElseThrow(() -> new IllegalArgumentException("No such recording: " + id));

        if (!recording.hasProfile()) {
            Optional<BoundedJobs.Outcome<String>> outcome = jobs.outcome(id);
            if (outcome.isPresent() && outcome.get().failure() != null) {
                return AnalysisFacts.failed(id, null, List.of(), null, outcome.get().failure().getMessage());
            }
            return jobs.isRunning(id)
                    ? AnalysisFacts.running(id, null, null)
                    : AnalysisFacts.of(AnalysisStatus.NOT_STARTED, id, null, List.of(), NOT_STARTED_NOTE);
        }

        // A profile row appears before the parse begins — it is inserted first so the recordings list
        // can show a run in progress — and is enabled only once every stage has finished. Reporting
        // the id at the sight of the row would hand back a profile whose events are still being
        // written, which reads as success and is the one answer worse than "not yet".
        String profileId = recording.profileId();
        PipelineProgress progress = runRegistry.progress(profileId);
        List<PipelineStage> stages = PipelineStage.of(progress.stages());
        if (progress.state() == PipelineState.FAILED) {
            return AnalysisFacts.failed(id, profileId, stages, progress.errorCode(), progress.errorMessage());
        }

        // The parser can have completed while post-parse work in this bounded job (notably an MCP
        // requested rename) is still running. The profile is not the job's result until all of that
        // finalization has finished.
        if (jobs.isRunning(id) || progress.isRunning()) {
            return AnalysisFacts.of(AnalysisStatus.RUNNING, id, profileId, stages, NOT_READY_YET);
        }

        // A live profile outranks a retained failure. The failure this process remembers is about an
        // attempt it made; the profile can have been built since by another one — the UI, or an
        // earlier attempt whose outcome expired — and a reader told "failed" about a profile every
        // other tool answers from would start a third build of it.
        Optional<ProfileInfo> profileInfo = recordingsManager.profile(profileId)
                .map(profile -> profile.info());
        if (profileInfo.isEmpty() || !profileInfo.get().enabled()) {
            Optional<BoundedJobs.Outcome<String>> outcome = jobs.outcome(id);
            if (outcome.isPresent() && outcome.get().failure() != null) {
                return AnalysisFacts.failed(id, profileId, stages, null, outcome.get().failure().getMessage());
            }
            return AnalysisFacts.of(AnalysisStatus.INTERRUPTED, id, profileId, stages, INTERRUPTED_NOTE);
        }

        return AnalysisFacts.ready(id, profileId, profileInfo.get().name(), eventSourceOf(recording));
    }

    /**
     * Builds the profile and renders what the model needs next: the id the other families take, and a
     * link for the reader who wants to look at the interactive version.
     */
    private OperationHandle<String> analysisOperation(String recordingId, String name, boolean retry) {
        String requestedName = name == null || name.isBlank() ? null : name.trim();
        return jobs.startOrJoin(recordingId, retry, profileId -> profileStillAvailable(recordingId, profileId), control -> {
            control.phase(OperationPhase.ANALYZING);
            control.progressFrom(() -> OperationDetails.recording(recordingId, parseStages(recordingId)));
            control.checkCancellation();
            String profileId = recordingsManager.analyzeRecording(recordingId);
            joinRunningPipeline(recordingId, profileId);
            // A durable profile is already produced. Finish the short, accepted naming step and
            // preserve that result even if the analysis could not honour a cancellation request.
            if (control.cancellationRequested()) {
                Thread.interrupted();
            }
            control.phase(OperationPhase.FINALIZING);
            if (requestedName != null) {
                recordingsManager.updateProfileName(profileId, requestedName);
            }
            return profileId;
        });
    }

    /** The stages of the parse building this recording's profile; none until its profile row exists. */
    private List<PipelineStage> parseStages(String recordingId) {
        return recordingsManager.findRecording(recordingId)
                .filter(Recording::hasProfile)
                .map(recording -> PipelineStage.of(runRegistry.progress(recording.profileId()).stages()))
                .orElse(List.of());
    }

    /**
     * A run the manager found already in flight — started from the UI, or by an earlier call whose
     * attempt has since been forgotten — is joined here rather than taken for a result: the manager
     * answers with the profile id the moment it sees such a run, and an attempt that completed on
     * that answer would hand out a link to a profile still being parsed.
     */
    private void joinRunningPipeline(String recordingId, String profileId) {
        Optional<PipelineProgress> outcome;
        try {
            outcome = runRegistry.awaitCompletion(profileId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(INTERRUPTED_JOINING_PIPELINE, e);
        }
        if (outcome.isEmpty() || outcome.get().state() != PipelineState.FAILED) {
            return;
        }
        // A failed run under this key is only this attempt's answer while there is no profile to
        // hand back. The registry retains a failure for as long as it retains anything, and a
        // profile enabled by some other path outranks it — the same precedence status() and
        // analyzed() apply, which this would otherwise contradict from three lines away.
        if (enabledProfile(recordingId).isEmpty()) {
            throw new ToolExecutionException(PIPELINE_FAILED + outcome.get().errorMessage());
        }
    }

    /** The enabled profile this recording is linked to, if it has one that works right now. */
    private Optional<String> enabledProfile(String recordingId) {
        return recordingsManager.findRecording(recordingId)
                .filter(Recording::hasProfile)
                .map(Recording::profileId)
                .filter(profileId -> recordingsManager.profile(profileId)
                        .map(profile -> profile.info().enabled()).orElse(false));
    }

    private boolean profileStillAvailable(String recordingId, String profileId) {
        boolean linked = recordingsManager.findRecording(recordingId)
                .filter(Recording::hasProfile).map(recording -> profileId.equals(recording.profileId())).orElse(false);
        return linked && recordingsManager.profile(profileId).map(profile -> profile.info().enabled()).orElse(false);
    }

    private McpToolOutcome analyzed(String recordingId, String name, boolean retry, McpCallContext call) {
        // Read here, where the request is bound; see analyzeFile.
        String base = UiLinks.base();
        if (!retry) {
            // The same precedence as recordings_status: a retained failure is obsolete once the
            // recording has a working profile, so an inspection call hands that profile back rather
            // than the failure that predates it. Without an operation, because no attempt of this
            // call's produced the profile — the retained one is the failure being set aside.
            Optional<String> live = enabledProfile(recordingId);
            if (live.isPresent()) {
                return McpToolResult.of(analysis(profileOf(recordingId, live.get(), base), null, base));
            }
        }
        OperationHandle<String> operation = analysisOperation(recordingId, name, retry);
        String operationId = operation.operationId();
        Function<String, McpToolResult> answer = profileId -> McpToolResult.of(
                analysis(profileOf(recordingId, profileId, base), operationId, base));
        operations.register(OperationKind.RECORDING_ANALYSIS, operation,
                profileId -> new AnalysisResult(profileId, recordingId), () -> recordingId, answer);
        Optional<String> finished;
        try {
            finished = jobs.awaitWithin(operation, answers.waitBudget(call, jobs.waitBudget()));
        } catch (RuntimeException failure) {
            return McpToolResult.of(analysis(
                    AnalysisFacts.failed(recordingId, null, List.of(), null, failure.getMessage()), operationId, base));
        }
        if (finished.isEmpty()) {
            return answers.stillRunning(call, operationId, () -> McpToolResult.of(
                    analysis(AnalysisFacts.running(recordingId, null, null), operationId, base)));
        }
        return answer.apply(finished.get());
    }

    /**
     * @param base the link base, read by {@link UiLinks#base()} on the request thread of the call, so
     *             an answer rendered later off that thread still links to the profile
     */
    private AnalysisFacts profileOf(String recordingId, String profileId, String base) {
        Recording recording = recordingsManager.findRecording(recordingId)
                .orElseThrow(() -> new ToolExecutionException(RECORDING_VANISHED + recordingId));
        String actualName = recordingsManager.profile(profileId)
                .map(profile -> profile.info().name())
                .orElse(recording.profileName() == null ? recording.recordingName() : recording.profileName());
        return AnalysisFacts.ready(recordingId, profileId, actualName, eventSourceOf(recording));
    }

    /**
     * The answer every analysis tool gives, whatever state the analysis is in: the facts, the
     * operation behind them when there is one, what to call next, and the page for the user — the
     * profile once it is ready, the recordings list, which shows the parse, until then.
     *
     * @param operationId the attempt this answer reports, or null when no attempt of this call's
     *                    produced it
     * @param base        the link base, read on the request thread; see {@link UiLinks#base()}
     */
    private RecordingAnalysis analysis(AnalysisFacts facts, String operationId, String base) {
        McpOperationRegistry.Snapshot operation = operationId == null ? null : operations.status(operationId);
        String uiLink = facts.status() == AnalysisStatus.READY
                ? UiLinks.profile(base, facts.profileId())
                : UiLinks.page(base, MicroscopePage.RECORDINGS);
        return new RecordingAnalysis(facts.status(), facts.recordingId(), facts.profileId(), facts.name(),
                facts.eventSource(), facts.reused(), facts.stages(), facts.errorCode(), facts.errorMessage(),
                facts.reason(), operationId, operation, followUp(facts, operationId), uiLink);
    }

    private McpFollowUp followUp(AnalysisFacts facts, String operationId) {
        String recordingId = facts.recordingId();
        boolean known = recordingId != null;
        NextSteps.Builder next = NextSteps.builder(advertised);
        return switch (facts.status()) {
            case READY -> next
                    .next(NextCalls.to(PROFILES_SUMMARY).with(PROFILE_ID, facts.profileId()).why(SUMMARY_WHY))
                    .followUp();
            case RUNNING -> next
                    .nextWhen(known, NextCalls.to(STATUS_TOOL).with(RECORDING_ID, recordingId)
                            .why(POLL_RECORDING_WHY))
                    .nextWhen(!known && operationId != null, NextCalls.to(OPERATIONS_STATUS)
                            .with(OPERATION_ID, operationId).why(POLL_OPERATION_WHY))
                    .followUp();
            case NOT_STARTED -> next
                    .next(NextCalls.to(ANALYZE_RECORDING_TOOL).with(RECORDING_ID, recordingId).why(ANALYZE_WHY))
                    .followUp();
            case FAILED, INTERRUPTED -> next
                    .nextWhen(known, NextCalls.to(ANALYZE_RECORDING_TOOL).with(RECORDING_ID, recordingId)
                            .with(RETRY, true).why(RETRY_WHY))
                    .followUp();
        };
    }

    /**
     * Rejects up front what {@code importRecordingFromPath} would reject anyway, plus the relative path
     * it would silently accept — resolved against Jeffrey's working directory, which is nowhere near
     * the caller's repository. The messages are the model's only way to recover, so each says which of
     * the three things went wrong rather than reporting a bare failure.
     */
    private static Path validatedPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("A recording path is required.");
        }

        Path resolved = expandHome(path.trim());
        if (!resolved.isAbsolute()) {
            throw new IllegalArgumentException(
                    "The recording path must be absolute: " + path + ". A relative path would be "
                            + "resolved against Jeffrey's working directory, not yours.");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException(
                    "No such recording file: " + resolved + ". The path is opened by the Jeffrey "
                            + "process, so the file has to be on the machine Jeffrey runs on.");
        }
        if (ManagedFile.of(resolved.getFileName().toString()) == ManagedFile.UNKNOWN) {
            throw new IllegalArgumentException(
                    "Unsupported recording file: " + resolved.getFileName()
                            + ". Jeffrey analyses .jfr, .jfr.lz4, .hprof, .hprof.gz, .pprof and .otlp files.");
        }
        return resolved;
    }

    private static Path expandHome(String path) {
        try {
            if (path.equals(HOME_PREFIX) || path.startsWith(HOME_PREFIX + "/")) {
                return Path.of(System.getProperty(USER_HOME_PROPERTY), path.substring(HOME_PREFIX.length()));
            }
            return Path.of(path);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Not a usable filesystem path: " + path);
        }
    }

    private static String eventSourceOf(Recording recording) {
        RecordingEventSource eventSource = recording.eventSource();
        return eventSource == null ? RecordingEventSource.UNKNOWN.name() : eventSource.name();
    }

    /** Where the analysis of one recording stands. */
    enum AnalysisStatus {
        /** The profile is built and works: profileId is what every other tool takes. */
        READY,
        /** The copy or the parse is still going. */
        RUNNING,
        /** Nothing is building a profile for this recording. */
        NOT_STARTED,
        /** The attempt failed; errorMessage says why. */
        FAILED,
        /** The profile was left unfinished by an attempt this process no longer runs. */
        INTERRUPTED
    }

    /**
     * What recordings_analyzeFile, recordings_analyzeRecording and recordings_status answer: one shape
     * for every state of the analysis, so a caller parses the same record whether it waited or polled.
     */
    record RecordingAnalysis(
            AnalysisStatus status,
            @McpNullable
            @McpDescription("The stored recording; null while an import is still copying the file")
            String recordingId,
            @McpNullable
            @McpDescription("The profile every other tool takes once status is READY; present but not "
                    + "usable while it is still being built")
            String profileId,
            @McpNullable
            String name,
            @McpNullable
            String eventSource,
            @McpDescription("True when an earlier import of the same unchanged file built the profile")
            boolean reused,
            @McpDescription("The pipeline stages, so a caller can tell parsing from nearly finished")
            List<PipelineStage> stages,
            @McpNullable
            @McpDescription("Machine-readable parsing failure code, when the pipeline supplied one")
            String errorCode,
            @McpNullable
            String errorMessage,
            @McpNullable
            @McpDescription("What the status means when it is not obvious from the status alone")
            String reason,
            @McpNullable
            @McpDescription("The attempt behind this answer, for operations_status and operations_cancel; "
                    + "null when no attempt of this call's produced it")
            String operationId,
            @McpNullable
            @McpDescription("That attempt as operations_status reports it")
            McpOperationRegistry.Snapshot operation,
            McpFollowUp followUp,
            @McpDescription("The profile's page in the Microscope UI once READY, else the recordings page, "
                    + "for the user")
            String uiLink) {
    }

    /**
     * The facts of an analysis, before the operation, the next calls and the link are added to them.
     *
     * @param reused true when an earlier import of the same unchanged file built the profile
     */
    private record AnalysisFacts(
            AnalysisStatus status,
            String recordingId,
            String profileId,
            String name,
            String eventSource,
            boolean reused,
            List<PipelineStage> stages,
            String errorCode,
            String errorMessage,
            String reason) {

        static AnalysisFacts ready(String recordingId, String profileId, String name, String eventSource) {
            return new AnalysisFacts(AnalysisStatus.READY, recordingId, profileId, name, eventSource, false,
                    List.of(), null, null, null);
        }

        static AnalysisFacts running(String recordingId, String profileId, String reason) {
            return of(AnalysisStatus.RUNNING, recordingId, profileId, List.of(), reason);
        }

        static AnalysisFacts of(
                AnalysisStatus status, String recordingId, String profileId, List<PipelineStage> stages, String reason) {
            return new AnalysisFacts(status, recordingId, profileId, null, null, false, stages, null, null, reason);
        }

        static AnalysisFacts failed(
                String recordingId, String profileId, List<PipelineStage> stages, String errorCode, String errorMessage) {
            return new AnalysisFacts(AnalysisStatus.FAILED, recordingId, profileId, null, null, false, stages,
                    errorCode, errorMessage, ANALYSIS_FAILED);
        }

        /** The same profile, handed back from an earlier import rather than built by this call. */
        AnalysisFacts asReused() {
            return new AnalysisFacts(status, recordingId, profileId, name, eventSource, true, stages, errorCode,
                    errorMessage, reason);
        }
    }

    /** What a deletion came to. */
    enum DeletionStatus {
        /** The recording, its profile and everything analysed out of it are gone. */
        DELETED,
        /** The user did not confirm; nothing was deleted. */
        NOT_CONFIRMED
    }

    /**
     * @param profileId the profile that went with the recording, or null when it had none
     * @param reason    why nothing was deleted; null when it was
     */
    record RecordingDeletion(
            DeletionStatus status,
            String recordingId,
            @McpNullable
            String name,
            @McpNullable
            String profileId,
            @McpNullable
            String reason,
            @McpDescription("The recordings page in the Microscope UI, for the user")
            String uiLink) {
    }

    record RecordingRow(
            String recordingId,
            @McpNullable
            String name,
            @McpNullable
            String eventSource,
            @McpNullable
            @McpDescription("When the recording started, as UTC epoch milliseconds; null when unknown")
            Long recordedEpochMs,
            @McpNullable
            @McpDescription("The profile built from it; null when it has not been analysed")
            String profileId) {
    }

    /** A page of the Quick Analysis store, which a cursor continues past its last row. */
    record RecordingPage(
            CatalogueStatus status,
            @McpNullable
            String reason,
            List<RecordingRow> recordings,
            @McpMinimum(0)
            int returned,
            @McpMinimum(0)
            @McpDescription("How many recordings the store holds, including earlier pages")
            int total,
            boolean hasMore,
            @McpNullable
            @McpDescription("Pass unchanged as cursor; null at the end")
            String nextCursor,
            McpFollowUp followUp,
            @McpDescription("The recordings page in the Microscope UI, for the user")
            String uiLink) {
    }

    /**
     * What one list call asked for, carried into every candidate page the size search renders.
     *
     * @param remaining how many recordings follow the cursor, this page's included
     * @param total     how many recordings the store holds
     * @param limit     the most rows a page holds
     */
    private record ListRequest(int remaining, int total, int limit, McpCursor.Filters filters, String uiLink) {
    }

    /**
     * A recording's place in the list: its creation time, null when it has none, and its id.
     */
    private record ListKey(Instant createdAt, String recordingId) {

        static ListKey of(Recording recording) {
            return new ListKey(recording.createdAt(), recording.id());
        }

        /**
         * The key a cursor carries, the instant as ISO so its nanoseconds survive, and refused when it
         * is not the two parts this list writes.
         */
        static ListKey read(McpCursor.Keyset keyset) {
            List<String> after = keyset.after();
            if (after.size() != KEY_PARTS || after.get(KEY_RECORDING_ID) == null
                    || after.get(KEY_RECORDING_ID).isBlank()) {
                throw new IllegalArgumentException("a recordings_list cursor holds a creation time and a "
                        + "recording id: after=" + after);
            }
            String createdAt = after.get(KEY_CREATED_AT);
            return new ListKey(createdAt == null ? null : Instant.parse(createdAt), after.get(KEY_RECORDING_ID));
        }

        McpCursor.Keyset keyset() {
            return new McpCursor.Keyset(Arrays.asList(
                    createdAt == null ? null : createdAt.toString(), recordingId));
        }
    }

    /** A rendered page: the Markdown table for the model beside the record. */
    private record ListPage(String text, RecordingPage structured) {
        boolean fits() {
            return text.length() <= McpToolOutput.MAX_CHARS
                    && Json.toString(structured).length() <= McpToolOutput.MAX_CHARS;
        }

        McpToolResult result() {
            return McpToolResult.of(text, structured);
        }
    }

    /** What an import leaves behind: the recording it stored and the profile built from it. */
    private record ImportedProfile(String recordingId, String profileId) {
    }

    /** A finished import's result, as operations_status reports it. */
    private record ImportResult(String profileId) {
    }

    /** A finished analysis's result, as operations_status reports it. */
    private record AnalysisResult(String profileId, String recordingId) {
    }
}

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
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one family that writes: it takes a recording Jeffrey has never seen and turns it into a profile
 * the read-only families can then answer questions about.
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
 * Registered only when ingestion is enabled. A family that is off is not advertised rather than left
 * to refuse: an advertised tool that can never succeed spends a slot in the model's context and invites
 * a call whose failure says nothing useful.
 */
public class RecordingsMcpTools {

    private static final String RECORDING_VANISHED = "Recording vanished while being analyzed: ";
    private static final String PIPELINE_FAILED = "The analysis this attempt joined failed: ";
    private static final String INTERRUPTED_JOINING_PIPELINE = "Interrupted while waiting for the running analysis";
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

    private static final String NO_RECORDINGS =
            "The Quick Analysis store is empty. Use recordings_analyzeFile with the absolute path of a "
                    + "JFR recording or heap dump to add one.";

    /**
     * What a caller is told when the parse is still going. A status rather than an error: nothing has
     * gone wrong, the answer is simply not ready, and the difference decides whether the model waits
     * or starts over.
     */
    private static final String STILL_RUNNING = "running";

    private static final String FAILED = "failed";

    private static final String INTERRUPTED = "interrupted";

    /**
     * The recording has no profile and no attempt is running or retained in this process.
     */
    private static final String NEVER_STARTED = "not_started";

    /**
     * Said when the profile row exists and the profile does not yet work. Without it a caller reading
     * a profileId beside a running status would reasonably try to use it.
     */
    private static final String NOT_READY_YET =
            "The profile exists but its events are still being written, so it cannot be analysed yet. "
                    + "Wait for this tool to report it without a status before using the profileId.";

    private static final String INTERRUPTED_NOTE =
            "The profile was left disabled and this process has no active initialization for it. "
                    + "Jeffrey likely restarted while the recording was being analyzed. Call the analyze tool again to retry.";

    private final RecordingsManager recordingsManager;
    private final PipelineRunRegistry<String> runRegistry;
    private final BoundedJobs<String, String> jobs;
    private final BoundedJobs<String, String> imports;
    private final McpOperationRegistry operations;

    public RecordingsMcpTools(
            RecordingsManager recordingsManager, PipelineRunRegistry<String> runRegistry, Clock clock) {
        this(recordingsManager, runRegistry, defaultJobs(clock), clock);
    }

    /**
     * @param runRegistry the profile-init pipeline, so a poll can report which stage the parse is on
     *                    rather than only that it has not finished
     */
    public RecordingsMcpTools(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry,
            BoundedJobs<String, String> jobs,
            Clock clock) {
        this(recordingsManager, runRegistry, jobs, new McpOperationRegistry(clock), clock);
    }

    public RecordingsMcpTools(RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry, McpOperationRegistry operations, Clock clock) {
        this(recordingsManager, runRegistry, operations, DEFAULT_MAX_CONCURRENT_IMPORTS, clock);
    }

    /**
     * @param maxConcurrentImports how many {@code recordings_analyzeFile} imports may run together,
     *                             from {@link #MAX_CONCURRENT_IMPORTS_PROPERTY}
     */
    public RecordingsMcpTools(RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry, McpOperationRegistry operations,
            int maxConcurrentImports, Clock clock) {
        this(recordingsManager, runRegistry, defaultJobs(clock), operations, maxConcurrentImports, clock);
    }

    public RecordingsMcpTools(RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry, BoundedJobs<String, String> jobs,
            McpOperationRegistry operations, Clock clock) {
        this(recordingsManager, runRegistry, jobs, operations, DEFAULT_MAX_CONCURRENT_IMPORTS, clock);
    }

    /**
     * @param clock what the import jobs stamp their attempts with; the analysis jobs carry their own
     */
    public RecordingsMcpTools(RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry, BoundedJobs<String, String> jobs,
            McpOperationRegistry operations, int maxConcurrentImports, Clock clock) {
        this.operations = operations;
        this.imports = new BoundedJobs<>(
                jobs.waitBudget(), BoundedJobs.COMPLETED_RETENTION, clock, maxConcurrentImports);
        this.recordingsManager = recordingsManager;
        this.runRegistry = runRegistry;
        this.jobs = jobs;
    }

    private static BoundedJobs<String, String> defaultJobs(Clock clock) {
        return new BoundedJobs<>(BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, clock);
    }

    @Tool(description = "Analyze a recording file that is not in Jeffrey yet: imports the file at the "
            + "given path into the Quick Analysis store and builds a profile from it, returning the "
            + "profile id every other tool takes. Use this when the user points at a .jfr, .jfr.lz4, "
            + ".hprof, .hprof.gz, .pprof or .otlp file in their repository or filesystem. The path is "
            + "opened by the Jeffrey process, so the file must be on the machine Jeffrey runs on. A "
            + "small recording is analysed inside this call and its profileId comes straight back; a "
            + "large one returns a running operationId; operations_status follows the complete copy "
            + "and analysis lifecycle, including before a recordingId is available. Each call imports the file again and builds "
            + "another profile - call recordings_list first if the same file may already be analysed, "
            + "and poll operations_status rather than calling this a second time.")
    public String analyzeFile(
            @ToolParam(required = true, description = "Absolute path of the recording file to import, e.g. "
                    + "/home/dev/project/target/app.jfr. A leading ~ is expanded. Relative paths are "
                    + "rejected because they would resolve against Jeffrey's working directory, not "
                    + "the caller's")
            String path,
            @ToolParam(required = false, description = "Optional name for the profile. Defaults to the file name")
            String name) {

        Path recordingPath = validatedPath(path);

        LOG.info("Importing a recording over MCP: path={}", recordingPath);
        AtomicReference<String> importedRecording = new AtomicReference<>();
        OperationHandle<String> operation = imports.startOrJoin(UUID.randomUUID().toString(), false,
                value -> true, control -> {
                    control.phase("importing");
                    String recordingId = recordingsManager.importRecordingFromPath(recordingPath);
                    importedRecording.set(recordingId);
                    control.progress(Map.of("recordingId", recordingId));
                    control.checkCancellation();
                    control.phase("analyzing");
                    OperationHandle<String> analysis = analysisOperation(recordingId, name, true);
                    control.onCancellation(analysis::cancel);
                    control.progress((Supplier<Object>) () -> Map.of(
                            "recordingId", recordingId, "analysis", Optional.ofNullable(analysis.snapshot().progress()).orElse(Map.of())));
                    return jobs.awaitCompletion(analysis);
                });
        String operationId = operations.register(OperationKind.RECORDING_IMPORT, operation,
                profileId -> Map.of("profileId", profileId), importedRecording::get);
        Optional<String> finished;
        try {
            finished = imports.awaitWithin(operation);
        } catch (RuntimeException failure) {
            return McpToolOutput.json(operations.status(operationId));
        }
        if (finished.isEmpty()) {
            return operations.decorate(McpToolOutput.json(Map.of("status", STILL_RUNNING)), operationId);
        }
        // The id the import handed over, not a read of the progress map: a progress supplier that
        // failed leaves that map saying so instead of naming a recording.
        return operations.decorate(analyzedProfile(importedRecording.get(), finished.get()), operationId);
    }

    @Tool(description = "Analyze a recording that is already in Jeffrey's Quick Analysis store but has "
            + "no profile yet - one uploaded through the web UI, one pulled in by hubs_download, or "
            + "one recordings_list shows with profile_id empty. Returns the profile id every other "
            + "tool takes, or a status of 'running' when the recording is large enough that parsing "
            + "outlasts the call - recordings_status then says when it is done. A recording that "
            + "already has a profile is returned as it is rather than analysed twice. Results include an "
            + "operationId for operations_status/cancel. Retained failures require retry=true; outcomes "
            + "are held in memory for one hour and forgotten on restart.")
    public String analyzeRecording(
            @ToolParam(required = true, description = "Recording id, as returned by recordings_list")
            String recordingId,
            @ToolParam(required = false, description = "Set true to retry a retained failed or cancelled analysis. Omit to inspect the same attempt")
            Boolean retry) {

        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException("A recording id is required. Call recordings_list to see them.");
        }

        LOG.info("Analyzing a stored recording over MCP: recording_id={}", recordingId);
        return analyzed(recordingId.trim(), null, Boolean.TRUE.equals(retry));
    }

    public String analyzeRecording(String recordingId) {
        return analyzeRecording(recordingId, true);
    }

    /*
     * The one tool in this family that takes something away. It is the other half of downloading a
     * window of a hub session: the hub is the copy of record, Microscope holds what is being read,
     * and a window profile that has answered its question has no reason to stay. The only tool that
     * declares itself destructive: the family's hint says it writes, and a client that asks before a
     * destructive call should get to ask here. Deleting twice is refused, not repeated, so it is not
     * idempotent either.
     */
    @McpToolHints(readOnly = false, destructive = true, idempotent = false)
    @Tool(description = "Delete a recording from the Quick Analysis store together with the profile "
            + "built from it, its files and everything analysed out of it. Use it to clean up after a "
            + "profile has answered its question - a window of a hub session pulled in by hubs_download, "
            + "a partial look taken before the real window, a file imported twice. A recording on a hub "
            + "is untouched: hubs_download can pull it again. The profile id stops working the moment "
            + "this returns; a client that still shows it should call profiles_list again.")
    public String delete(
            @ToolParam(required = true, description = "Recording id from recordings_list, hubs_download or "
                    + "recordings_analyzeFile. A profile id is not accepted; recordings_list shows which "
                    + "recording a profile belongs to")
            String recordingId) {
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

        LOG.info("Deleting a recording over MCP: recording_id={} profile_id={}", id, recording.profileId());
        recordingsManager.deleteRecording(id);
        return McpToolOutput.json(new DeletedRecording(id, recording.recordingName(), recording.profileId()));
    }

    /*
     * Reads. Its family is registered as writing because the tools that build a profile sit in
     * it, and a member that only reports has to say so for itself — the same inheritance that let
     * hubs_download offer a cross-machine transfer as a safe read, running the other way.
     */
    @McpToolHints
    @Tool(description = "Every recording in the Quick Analysis store, whether or not it has been "
            + "analysed. Use it to find a recording that was uploaded but never analysed, or to check "
            + "whether a file is already in Jeffrey before importing it again.")
    public String list() {
        List<Recording> recordings = recordingsManager.listRecordings();
        if (recordings.isEmpty()) {
            return NO_RECORDINGS;
        }

        MarkdownTable table = MarkdownTable.withColumns(
                "recording_id", "name", "event source", "recorded", "profile_id");
        for (Recording recording : recordings) {
            table.row(
                    recording.id(),
                    recording.recordingName(),
                    recording.eventSource(),
                    recording.recordingStartedAt(),
                    recording.hasProfile() ? recording.profileId() : "");
        }
        return table
                .note("A row with an empty `profile_id` has not been analysed yet - pass its "
                        + "`recording_id` to recordings_analyzeRecording.")
                .render();
    }


    /*
     * Reads. Its family is registered as writing because the tools that build a profile sit in
     * it, and a member that only reports has to say so for itself — the same inheritance that let
     * hubs_download offer a cross-machine transfer as a safe read, running the other way.
     */
    @McpToolHints
    @Tool(description = "How far the analysis of a recording has got, and the profile id once it is "
            + "ready to use. Call it when recordings_analyzeFile or recordings_analyzeRecording came "
            + "back with a status of running rather than a profile id — a large recording takes longer "
            + "to parse than a tool call waits, so the work carries on in the background. The answer "
            + "carries the pipeline stages, so you can see whether it is still parsing events or "
            + "nearly done. Poll this rather than analysing again: a second analysis of the same file "
            + "would build a second profile of it.")
    public String status(
            @ToolParam(required = true, description = "Recording id, as returned by the analyze tool "
                    + "that reported the analysis was still running")
            String recordingId) {
        String legacy = statusOf(recordingId);
        return operations.latestForRecording(recordingId.trim())
                .map(operationId -> operations.decorate(legacy, operationId)).orElse(legacy);
    }

    private String statusOf(String recordingId) {
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
                return failed(id, null, List.of(), null, outcome.get().failure().getMessage());
            }
            return McpToolOutput.json(new AnalysisProgress(
                    id, null, jobs.isRunning(id) ? STILL_RUNNING : NEVER_STARTED,
                    List.of(), null, null, null));
        }

        // A profile row appears before the parse begins — it is inserted first so the recordings list
        // can show a run in progress — and is enabled only once every stage has finished. Reporting
        // the id at the sight of the row would hand back a profile whose events are still being
        // written, which reads as success and is the one answer worse than "not yet".
        String profileId = recording.profileId();
        PipelineProgress progress = runRegistry.progress(profileId);
        List<Stage> stages = stages(progress);
        if (progress.state() == PipelineState.FAILED) {
            return failed(id, profileId, stages, progress.errorCode(), progress.errorMessage());
        }

        // The parser can have completed while post-parse work in this bounded job (notably an MCP
        // requested rename) is still running. The profile is not the job's result until all of that
        // finalization has finished.
        if (jobs.isRunning(id) || progress.isRunning()) {
            return McpToolOutput.json(new AnalysisProgress(
                    id, profileId, STILL_RUNNING, stages, null, null, NOT_READY_YET));
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
                return failed(id, profileId, stages, null, outcome.get().failure().getMessage());
            }
            return McpToolOutput.json(new AnalysisProgress(
                    id, profileId, INTERRUPTED, stages, null, null, INTERRUPTED_NOTE));
        }

        return McpToolOutput.json(new AnalyzedProfile(
                profileId,
                id,
                profileInfo.get().name(),
                eventSourceOf(recording),
                UiLinks.profile(profileId)));
    }

    private static String failed(
            String recordingId,
            String profileId,
            List<Stage> stages,
            String errorCode,
            String errorMessage) {

        return McpToolOutput.json(new AnalysisProgress(
                recordingId, profileId, FAILED, stages, errorCode, errorMessage,
                "The analysis failed. Call the analyze tool again to retry."));
    }

    /**
     * The stages of the run building this profile, empty when none is tracked in this process.
     */
    private static List<Stage> stages(PipelineProgress progress) {
        return progress.stages().stream()
                .map(stage -> new Stage(stage.id(), stage.status().name(), stage.durationMs()))
                .toList();
    }


    /**
     * Builds the profile and renders what the model needs next: the id the other families take, and a
     * link for the reader who wants to look at the interactive version.
     */
    private OperationHandle<String> analysisOperation(String recordingId, String name, boolean retry) {
        String requestedName = name == null || name.isBlank() ? null : name.trim();
        return jobs.startOrJoin(recordingId, retry, profileId -> profileStillAvailable(recordingId, profileId), control -> {
            control.phase("analyzing");
            control.progress((Supplier<Object>) () -> {
                Optional<Recording> recording = recordingsManager.findRecording(recordingId);
                Object stages = recording.filter(Recording::hasProfile)
                        .map(value -> (Object) runRegistry.progress(value.profileId()).stages()).orElse(List.of());
                return Map.of("recordingId", recordingId, "stages", stages);
            });
            control.checkCancellation();
            String profileId = recordingsManager.analyzeRecording(recordingId);
            joinRunningPipeline(recordingId, profileId);
            // A durable profile is already produced. Finish the short, accepted naming step and
            // preserve that result even if the analysis could not honour a cancellation request.
            if (control.cancellationRequested()) {
                Thread.interrupted();
            }
            control.phase("finalizing");
            if (requestedName != null) {
                recordingsManager.updateProfileName(profileId, requestedName);
            }
            return profileId;
        });
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

    private String analyzed(String recordingId, String name, boolean retry) {
        if (!retry) {
            // The same precedence as recordings_status: a retained failure is obsolete once the
            // recording has a working profile, so an inspection call hands that profile back rather
            // than the failure that predates it. Undecorated, because no attempt of this call's is
            // what produced the profile — the retained one is the failure being set aside.
            Optional<String> live = enabledProfile(recordingId);
            if (live.isPresent()) {
                return analyzedProfile(recordingId, live.get());
            }
        }
        OperationHandle<String> operation = analysisOperation(recordingId, name, retry);
        String operationId = operations.register(OperationKind.RECORDING_ANALYSIS, operation,
                profileId -> Map.of("profileId", profileId, "recordingId", recordingId), () -> recordingId);
        Optional<String> finished;
        try {
            finished = jobs.awaitWithin(operation);
        } catch (RuntimeException failure) {
            return McpToolOutput.json(operations.status(operationId));
        }
        if (finished.isEmpty()) {
            return operations.decorate(McpToolOutput.json(new AnalysisProgress(
                    recordingId, null, STILL_RUNNING, List.of(), null, null, null)), operationId);
        }
        return operations.decorate(analyzedProfile(recordingId, finished.get()), operationId);
    }

    private String analyzedProfile(String recordingId, String profileId) {
        Recording recording = recordingsManager.findRecording(recordingId)
                .orElseThrow(() -> new ToolExecutionException(RECORDING_VANISHED + recordingId));
        String actualName = recordingsManager.profile(profileId)
                .map(profile -> profile.info().name())
                .orElse(recording.profileName() == null ? recording.recordingName() : recording.profileName());

        return McpToolOutput.json(new AnalyzedProfile(
                profileId,
                recordingId,
                actualName,
                eventSourceOf(recording),
                UiLinks.profile(profileId)));
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

    /**
     * @param profileId the profile being built, once its row exists — present but not yet usable, so
     *                  it is reported for context rather than as something to pass to another tool
     * @param status    {@code running} while the parse continues, {@code not_started} when nothing is
     *                  building a profile for this recording
     * @param stages    the pipeline stages, so a caller can tell parsing from nearly finished
     * @param errorCode machine-readable parsing failure code, when the pipeline supplied one
     * @param errorMessage the failure reason, when analysis failed
     * @param note      what the status means, when it is not obvious from the status alone
     */
    private record AnalysisProgress(
            String recordingId,
            String profileId,
            String status,
            List<Stage> stages,
            String errorCode,
            String errorMessage,
            String note) {
    }

    /**
     * @param durationMs null while the stage has not finished
     */
    private record Stage(String id, String status, Long durationMs) {
    }

    /**
     * @param profileId the profile that went with the recording, or {@code null} when it had none
     */
    private record DeletedRecording(String recordingId, String name, String profileId) {
    }

    private record AnalyzedProfile(
            String profileId,
            String recordingId,
            String name,
            String eventSource,
            String link) {
    }
}

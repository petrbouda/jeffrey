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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsMcpTools.class);

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

    /**
     * The recording has no profile and nothing is building one — the analysis failed, or this process
     * restarted while it ran. Either way the honest next step is to ask for it again.
     */
    private static final String NEVER_STARTED = "not_started";

    /**
     * Said when the profile row exists and the profile does not yet work. Without it a caller reading
     * a profileId beside a running status would reasonably try to use it.
     */
    private static final String NOT_READY_YET =
            "The profile exists but its events are still being written, so it cannot be analysed yet. "
                    + "Wait for this tool to report it without a status before using the profileId.";

    private final RecordingsManager recordingsManager;
    private final PipelineRunRegistry<String> runRegistry;
    private final BoundedJobs<String, String> jobs;

    public RecordingsMcpTools(
            RecordingsManager recordingsManager, PipelineRunRegistry<String> runRegistry) {
        this(recordingsManager, runRegistry, new BoundedJobs<>());
    }

    /**
     * @param runRegistry the profile-init pipeline, so a poll can report which stage the parse is on
     *                    rather than only that it has not finished
     */
    public RecordingsMcpTools(
            RecordingsManager recordingsManager,
            PipelineRunRegistry<String> runRegistry,
            BoundedJobs<String, String> jobs) {
        this.recordingsManager = recordingsManager;
        this.runRegistry = runRegistry;
        this.jobs = jobs;
    }

    @Tool(description = "Analyze a recording file that is not in Jeffrey yet: imports the file at the "
            + "given path into the Quick Analysis store and builds a profile from it, returning the "
            + "profile id every other tool takes. Use this when the user points at a .jfr, .jfr.lz4, "
            + ".hprof, .hprof.gz, .pprof or .otlp file in their repository or filesystem. The path is "
            + "opened by the Jeffrey process, so the file must be on the machine Jeffrey runs on. A "
            + "small recording is analysed inside this call and its profileId comes straight back; a "
            + "large one takes longer than a client waits, so the answer is a status of 'running' and "
            + "recordings_status says when it is done. Each call imports the file again and builds "
            + "another profile - call recordings_list first if the same file may already be analysed, "
            + "and poll recordings_status rather than calling this a second time.")
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
        String recordingId = recordingsManager.importRecordingFromPath(recordingPath);
        return analyzed(recordingId, name);
    }

    @Tool(description = "Analyze a recording that is already in Jeffrey's Quick Analysis store but has "
            + "no profile yet - one uploaded through the web UI, one pulled in by hubs_download, or "
            + "one recordings_list shows with profile_id empty. Returns the profile id every other "
            + "tool takes, or a status of 'running' when the recording is large enough that parsing "
            + "outlasts the call - recordings_status then says when it is done. A recording that "
            + "already has a profile is returned as it is rather than analysed twice.")
    public String analyzeRecording(
            @ToolParam(required = true, description = "Recording id, as returned by recordings_list")
            String recordingId) {

        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException("A recording id is required. Call recordings_list to see them.");
        }

        LOG.info("Analyzing a stored recording over MCP: recordingId={}", recordingId);
        return analyzed(recordingId.trim(), null);
    }

    @Tool(description = "Every recording in the Quick Analysis store, whether or not it has been "
            + "analysed. Use it to find a recording that was uploaded but never analysed, or to check "
            + "whether a file is already in Jeffrey before importing it again.")
    public String list() {
        List<Recording> recordings = recordingsManager.listRecordings();
        if (recordings.isEmpty()) {
            return NO_RECORDINGS;
        }

        StringBuilder out = new StringBuilder(1024);
        out.append("| recording_id | name | event source | recorded | profile_id |\n");
        out.append("|---|---|---|---|---|\n");
        for (Recording recording : recordings) {
            out.append("| ").append(recording.id())
                    .append(" | ").append(sanitize(recording.recordingName()))
                    .append(" | ").append(recording.eventSource())
                    .append(" | ").append(recording.recordingStartedAt())
                    .append(" | ").append(recording.hasProfile() ? recording.profileId() : "")
                    .append(" |\n");
        }
        out.append("\nA row with an empty `profile_id` has not been analysed yet - pass its "
                + "`recording_id` to recordings_analyzeRecording.\n");
        return McpToolOutput.capped(out.toString());
    }


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

        if (recordingId == null || recordingId.isBlank()) {
            throw new IllegalArgumentException(
                    "A recording id is required. Call recordings_list to see them.");
        }
        String id = recordingId.trim();
        Recording recording = recordingsManager.findRecording(id)
                .orElseThrow(() -> new IllegalArgumentException("No such recording: " + id));

        if (!recording.hasProfile()) {
            return McpToolOutput.json(new AnalysisProgress(
                    id, null, jobs.isRunning(id) ? STILL_RUNNING : NEVER_STARTED, List.of(), null));
        }

        // A profile row appears before the parse begins -- it is inserted first so the recordings list
        // can show a run in progress -- and is enabled only once every stage has finished. Reporting
        // the id at the sight of the row would hand back a profile whose events are still being
        // written, which reads as success and is the one answer worse than "not yet".
        String profileId = recording.profileId();
        if (!isReady(profileId)) {
            return McpToolOutput.json(new AnalysisProgress(
                    id, profileId, STILL_RUNNING, stages(profileId), NOT_READY_YET));
        }

        return McpToolOutput.json(new AnalyzedProfile(
                profileId,
                id,
                recording.recordingName(),
                eventSourceOf(recording),
                UiLinks.profile(profileId)));
    }

    /**
     * Whether the profile is finished and usable, rather than merely present.
     */
    private boolean isReady(String profileId) {
        return recordingsManager.profile(profileId)
                .map(profile -> profile.info().enabled())
                .orElse(false);
    }

    /**
     * The stages of the run building this profile, empty when none is tracked here — the parse may
     * have been started by a different process, or by one that has since restarted.
     */
    private List<Stage> stages(String profileId) {
        return runRegistry.progress(profileId).stages().stream()
                .map(stage -> new Stage(stage.id(), stage.status().name(), stage.durationMs()))
                .toList();
    }


    /**
     * Builds the profile and renders what the model needs next: the id the other families take, and a
     * link for the reader who wants to look at the interactive version.
     */
    private String analyzed(String recordingId, String name) {
        Optional<String> finished =
                jobs.runWithin(recordingId, () -> recordingsManager.analyzeRecording(recordingId));
        if (finished.isEmpty()) {
            return McpToolOutput.json(new AnalysisProgress(
                    recordingId, null, STILL_RUNNING, List.of(), null));
        }

        String profileId = finished.get();
        if (name != null && !name.isBlank()) {
            recordingsManager.updateProfileName(profileId, name.trim());
        }

        Recording recording = recordingsManager.findRecording(recordingId)
                .orElseThrow(() -> new IllegalStateException("Recording vanished while being analyzed: " + recordingId));

        return McpToolOutput.json(new AnalyzedProfile(
                profileId,
                recordingId,
                name == null || name.isBlank() ? recording.recordingName() : name.trim(),
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
        if (SupportedRecordingFile.of(resolved.getFileName().toString()) == SupportedRecordingFile.UNKNOWN) {
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
     * Keeps a name on one table row: a pipe in a recording name would otherwise split the cell.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    /**
     * @param profileId the profile being built, once its row exists — present but not yet usable, so
     *                  it is reported for context rather than as something to pass to another tool
     * @param status    {@code running} while the parse continues, {@code not_started} when nothing is
     *                  building a profile for this recording
     * @param stages    the pipeline stages, so a caller can tell parsing from nearly finished
     * @param note      what the status means, when it is not obvious from the status alone
     */
    private record AnalysisProgress(
            String recordingId,
            String profileId,
            String status,
            List<Stage> stages,
            String note) {
    }

    /**
     * @param durationMs null while the stage has not finished
     */
    private record Stage(String id, String status, Long durationMs) {
    }

    private record AnalyzedProfile(
            String profileId,
            String recordingId,
            String name,
            String eventSource,
            String link) {
    }
}

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

    private final RecordingsManager recordingsManager;

    public RecordingsMcpTools(RecordingsManager recordingsManager) {
        this.recordingsManager = recordingsManager;
    }

    @Tool(description = "Analyze a recording file that is not in Jeffrey yet: imports the file at the "
            + "given path into the Quick Analysis store and builds a profile from it, returning the "
            + "profile id every other tool takes. Use this when the user points at a .jfr, .jfr.lz4, "
            + ".hprof, .hprof.gz, .pprof or .otlp file in their repository or filesystem. The path is "
            + "opened by the Jeffrey process, so the file must be on the machine Jeffrey runs on. The "
            + "call returns once the profile is built, which for a large recording can take a while. "
            + "Each call imports the file again and builds another profile - call recordings_list "
            + "first if the same file may already be analysed.")
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
            + "no profile yet - one uploaded through the web UI, or one recordings_list shows with "
            + "profile_id empty. Returns the profile id every other tool takes. A recording that "
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

    /**
     * Builds the profile and renders what the model needs next: the id the other families take, and a
     * link for the reader who wants to look at the interactive version.
     */
    private String analyzed(String recordingId, String name) {
        String profileId = recordingsManager.analyzeRecording(recordingId);
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

    private record AnalyzedProfile(
            String profileId,
            String recordingId,
            String name,
            String eventSource,
            String link) {
    }
}

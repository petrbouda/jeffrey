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

import cafe.jeffrey.profile.mcp.ToolParamValues;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadedSessionIndex;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubScanFilter;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionScan;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The recordings that never reached this machine: everything sitting on a connected Jeffrey Hub.
 * <p>
 * The other write family, {@code recordings_}, starts from a file the reader already has. This one
 * starts from an environment they do not — "the JFR recordings from the last hour on production" —
 * and ends at a local recording the rest of the server can analyse.
 * <p>
 * <strong>Flat, not a tree.</strong> A hub holds workspaces holding projects holding sessions, and
 * the web UI lets a reader walk that. Walking it here would cost four calls before anything is
 * downloaded and would give the model four chances to pair a workspace with the wrong project. So
 * one call fans out across every hub and returns flat rows, and each row carries a
 * {@link HubSessionRef} that {@code hubs_download} takes on its own. The hierarchy is filtering and
 * display; it is never a sequence of questions.
 * <p>
 * <strong>Downloading and analysing stay apart.</strong> {@code hubs_download} returns a recording
 * id and stops, leaving {@code recordings_analyzeRecording} to build the profile. Doing both in one
 * call would mean one request covering a multi-gigabyte transfer <em>and</em> a full analysis, which
 * is exactly the shape that trips a client's tool timeout — and a timeout in the middle tells the
 * model nothing about whether the work survived.
 * <p>
 * <strong>No UI links anywhere in this family.</strong> {@code UiLinks} reads the request bound to
 * the calling thread, and the scan runs on other threads; nothing here has a profile to link to
 * before analysis has happened, so there is nothing to add and a trap to avoid.
 */
public class HubsMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HubsMcpTools.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    /**
     * How long a listing may spend waiting on hubs. Needed because no deadline is set on the hub
     * channels and this runs inside a synchronous tool call, so a hub that neither answers nor
     * refuses would otherwise hang the caller for good.
     */
    private static final Duration SCAN_BUDGET = Duration.ofSeconds(20);

    private static final String NO_HUBS =
            "No Jeffrey Hub is connected to this installation. Recordings can still be analysed from "
                    + "a local file with recordings_analyzeFile.";

    private static final String NO_SESSIONS =
            "No recording sessions matched on any connected hub.";

    private static final String STATUS_OK = "ok";
    private static final String STATUS_UNREACHABLE = "unreachable";

    private final HubsManager hubsManager;
    private final ProjectManagerResolver resolver;
    private final RecordingsManager recordingsManager;
    private static final String DOWNLOAD_STILL_RUNNING =
            "The transfer is still running. Call hubs_download again with the same session_ref to "
                    + "check: it answers from the local store first, so once the transfer lands it "
                    + "returns the recordingId rather than fetching the session a second time.";

    private final Clock clock;

    /**
     * One transfer per session at a time, and no call waits longer than a client will.
     */
    private final BoundedJobs<String, String> downloads = new BoundedJobs<>();
    private final HubSessionScan scan;

    public HubsMcpTools(
            HubsManager hubsManager,
            ProjectManagerResolver resolver,
            RecordingsManager recordingsManager,
            Clock clock) {

        this.hubsManager = hubsManager;
        this.resolver = resolver;
        this.recordingsManager = recordingsManager;
        this.clock = clock;
        this.scan = new HubSessionScan(hubsManager, SCAN_BUDGET);
    }

    @Tool(description = "Every Jeffrey Hub this installation is connected to, and whether it answers "
            + "right now. Call it when hubs_sessions came back empty for a hub you expected to see, "
            + "or to learn the hub names the `hub` filter of hubs_sessions accepts.")
    public String list() {
        List<HubManager> hubs = hubsManager.findAll();
        if (hubs.isEmpty()) {
            return NO_HUBS;
        }

        Map<String, Optional<String>> versions = probeAll(hubs);

        MarkdownTable table = MarkdownTable.withColumns(
                "hub", "hub_id", "address", "source", "status", "hub_version");
        for (HubManager hub : hubs) {
            HubInfo info = hub.info();
            Optional<String> version = versions.getOrDefault(info.hubId(), Optional.empty());
            table.row(
                    info.name(),
                    info.hubId(),
                    address(info),
                    info.source() == null ? "" : info.source().name().toLowerCase(Locale.ROOT),
                    version.isPresent() ? STATUS_OK : STATUS_UNREACHABLE,
                    version.orElse(""));
        }
        return table
                .note("A hub marked `" + STATUS_UNREACHABLE + "` did not answer just now, so "
                        + "hubs_sessions can list nothing from it. A hub whose source is `config` is "
                        + "declared in this installation's configuration and cannot be removed from "
                        + "the UI.")
                .render();
    }

    @Tool(description = "Recording sessions across every connected Jeffrey Hub, newest first, in one "
            + "flat list - this is where to start when the user asks about recordings from an "
            + "environment rather than from a file, such as \"the JFR recordings from the last hour "
            + "on production\". Every row carries a session_ref to pass to hubs_download. The `local` "
            + "column says a session has already been pulled into this Jeffrey, so it can be analysed "
            + "without downloading it again.")
    public String sessions(
            @ToolParam(required = false, description = "Optional hub filter: a hub id, or part of a hub name as "
                    + "hubs_list prints it, e.g. production. Omit to search every hub")
            String hub,
            @ToolParam(required = false, description = "Optional filter on part of a workspace name or its reference id")
            String workspace,
            @ToolParam(required = false, description = "Optional filter on part of a project name or label, e.g. checkout")
            String project,
            @ToolParam(required = false, description = "Only sessions that were recording at some point within the last "
                    + "N minutes - 60 for the last hour, 1440 for the last day. This is an overlap, "
                    + "not a start time: a JVM that began recording three hours ago and is still "
                    + "running does match a 60-minute window")
            Integer withinLastMinutes,
            @ToolParam(required = false, description = "Only sessions in this status: ACTIVE for one still recording, "
                    + "FINISHED for one that has stopped. Omit for both")
            @ToolParamValues({"ACTIVE", "FINISHED"})
            String status,
            @ToolParam(required = false, description = "Most rows to return across all hubs. Default 50, maximum 500")
            Integer limit) {

        int rowLimit = ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        HubScanFilter filter = new HubScanFilter(
                hub, workspace, project, sessionFilter(withinLastMinutes, status, rowLimit));

        HubSessionScan.Result result = scan.scan(filter, rowLimit);
        if (result.rows().isEmpty() && result.complete()) {
            return emptyResult(withinLastMinutes);
        }

        DownloadedSessionIndex local = DownloadedSessionIndex.build(recordingsManager);

        MarkdownTable table = MarkdownTable.withColumns(
                "hub", "workspace", "project", "started", "duration", "status", "files", "size",
                "local", "session_ref");
        for (HubSessionScan.Row row : result.rows()) {
            RecordingSession session = row.session();
            table.row(
                    row.hubName(),
                    row.workspaceName(),
                    row.projectName(),
                    session.createdAt(),
                    duration(session),
                    session.status(),
                    session.files() == null ? 0 : session.files().size(),
                    size(session.totalSizeBytes()),
                    localColumn(local, row.ref()),
                    row.ref().encode());
        }
        return table.note(footer(result)).render();
    }

    @Tool(description = "Download one recording session from its hub into this Jeffrey, merging the "
            + "session's finished recording files into a single local recording and bringing its "
            + "artifacts - heap dumps, JVM and application logs - with it. Takes the session_ref from "
            + "a hubs_sessions row and nothing else. Returns a recording id: pass it to "
            + "recordings_analyzeRecording to build the profile the analysis tools take. A small "
            + "session transfers inside this call; a large one takes longer than a client waits, so "
            + "the answer is a status saying the transfer continues and calling this tool again with "
            + "the same session_ref reports it once it lands. A session already downloaded is returned "
            + "as it is rather than fetched twice.")
    public String download(
            @ToolParam(required = true, description = "The session_ref from a hubs_sessions row, copied exactly")
            String sessionRef) {

        HubSessionRef ref = HubSessionRef.decode(sessionRef);

        Optional<DownloadedSessionIndex.LocalCopy> alreadyHere =
                DownloadedSessionIndex.build(recordingsManager).find(ref);
        if (alreadyHere.isPresent()) {
            LOG.debug("Hub session was already downloaded: session_id={} recording_id={}",
                    ref.sessionId(), alreadyHere.get().recordingId());
            return McpToolOutput.json(existing(ref, alreadyHere.get()));
        }

        HubInfo hubInfo = hubInfo(ref);
        ProjectManager project = projectFor(ref);
        RecordingSession session = preflight(project, ref, hubInfo);

        LOG.info("Downloading a hub session over MCP: hub_id={} project_id={} session_id={}",
                ref.hubId(), ref.projectId(), ref.sessionId());
        Optional<String> transferred = downloads.runWithin(
                ref.sessionId(),
                () -> project.recordingsDownloadManager().mergeAndDownloadSession(ref.sessionId()));
        if (transferred.isEmpty()) {
            // Nothing to poll but this tool: it answers from the local store first, so calling it again
            // with the same ref reports the finished copy once the transfer lands.
            // Names travel unescaped here. Replacing a pipe is a Markdown-cell concern, and these
            // two answers are JSON, where the serialiser escapes what needs escaping and a mangled
            // name is simply the wrong name.
            return McpToolOutput.json(new DownloadInProgress(
                    ref.sessionId(),
                    session.name(),
                    session.totalSizeBytes(),
                    DOWNLOAD_STILL_RUNNING));
        }
        String recordingId = transferred.get();

        List<RepositoryFile> finished = finishedFiles(session);
        return McpToolOutput.json(new DownloadedSession(
                recordingId,
                session.name(),
                hubInfo.name(),
                project.info().name(),
                ref.sessionId(),
                (int) finished.stream().filter(RepositoryFile::isRecordingFile).count(),
                (int) finished.stream().filter(RepositoryFile::isArtifactFile).count(),
                session.totalSizeBytes(),
                "Call recordings_analyzeRecording with recordingId=" + recordingId
                        + " to build the profile every analysis tool takes."));
    }

    /**
     * Asks every hub whether it is there, all at once and under the same budget the scan uses. One
     * hub that hangs must not decide how long the whole listing takes.
     */
    private Map<String, Optional<String>> probeAll(List<HubManager> hubs) {
        List<CompletableFuture<Map.Entry<String, Optional<String>>>> probes = hubs.stream()
                .map(hub -> CompletableFuture.supplyAsync(
                        () -> Map.entry(
                                hub.info().hubId(),
                                hub.tryInfo().map(info -> info.version() == null ? "" : info.version())),
                        Schedulers.sharedVirtual()))
                .toList();

        try {
            CompletableFuture.allOf(probes.toArray(new CompletableFuture[0]))
                    .get(SCAN_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("Not every hub answered a probe within the budget: budget_in_sec={}",
                    SCAN_BUDGET.toSeconds());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while probing hubs", e);
        } catch (Exception e) {
            LOG.warn("Failed to probe hubs", e);
        }

        Map<String, Optional<String>> versions = new HashMap<>();
        for (CompletableFuture<Map.Entry<String, Optional<String>>> probe : probes) {
            if (probe.isDone() && !probe.isCompletedExceptionally()) {
                Map.Entry<String, Optional<String>> entry = probe.join();
                versions.put(entry.getKey(), entry.getValue());
            }
        }
        return versions;
    }

    private RecordingSessionFilter sessionFilter(Integer withinLastMinutes, String status, int limit) {
        RecordingSessionFilter filter = RecordingSessionFilter.ALL;
        if (withinLastMinutes != null) {
            if (withinLastMinutes < 1) {
                throw new IllegalArgumentException(
                        "withinLastMinutes must be at least 1 minute: " + withinLastMinutes);
            }
            filter = RecordingSessionFilter.activeWithinLast(
                    Duration.ofMinutes(withinLastMinutes), clock.instant());
        }
        return filter.withStatus(parseStatus(status)).withLimit(limit);
    }

    /**
     * Parsed by hand rather than declared as an enum parameter: the tool schema only carries the
     * simple types, and an enum would advertise as a string and then fail inside the reflective
     * call rather than here, where the message can say what the accepted values are.
     */
    private static RecordingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (upper.equals(RecordingStatus.ACTIVE.name())) {
            return RecordingStatus.ACTIVE;
        }
        if (upper.equals(RecordingStatus.FINISHED.name())) {
            return RecordingStatus.FINISHED;
        }
        throw new IllegalArgumentException(
                "Unknown session status: " + status + ". Use ACTIVE or FINISHED, or omit it for both.");
    }

    private static String emptyResult(Integer withinLastMinutes) {
        if (withinLastMinutes == null) {
            return NO_SESSIONS + " Call hubs_list to check the hubs are connected and answering.";
        }
        return NO_SESSIONS + " The window was the last " + withinLastMinutes
                + " minutes - widen it with withinLastMinutes, or drop it to see every session.";
    }

    /**
     * What could not be read, under the table rather than instead of it. Rendered even when nothing
     * came back: a bare "no sessions" while production is down is the one answer that would mislead
     * a reader into thinking their recordings are gone.
     */
    private static String footer(HubSessionScan.Result result) {
        StringBuilder footer = new StringBuilder(256);
        footer.append("A row with `local` empty is not in this Jeffrey yet - pass its `session_ref` to "
                + "hubs_download. `recording:<id>` is downloaded but not analysed, so it is ready for "
                + "recordings_analyzeRecording; `profile:<id>` is already analysed and every analysis "
                + "tool takes that id straight away.\n");

        if (!result.complete()) {
            footer.append('\n');
            for (HubSessionScan.Failure failure : result.failures()) {
                footer.append("Not listed: ").append(failure.scope())
                        .append(" - ").append(failure.reason()).append('\n');
            }
            footer.append("Nothing from there appears above. Call hubs_list to check, "
                    + "or ask the user whether that hub should be up.\n");
        }
        return footer.toString();
    }

    private static String localColumn(DownloadedSessionIndex index, HubSessionRef ref) {
        return index.find(ref)
                .map(copy -> copy.analysed()
                        ? "profile:" + copy.profileId()
                        : "recording:" + copy.recordingId())
                .orElse("");
    }

    private HubInfo hubInfo(HubSessionRef ref) {
        try {
            return resolver.resolveServer(ref.hubId()).info();
        } catch (JeffreyException e) {
            throw staleRef(ref, "its hub is no longer connected to this Jeffrey");
        }
    }

    private ProjectManager projectFor(HubSessionRef ref) {
        try {
            return resolver.resolve(ref.hubId(), ref.workspaceId(), ref.projectId()).projectManager();
        } catch (JeffreyException | StatusRuntimeException e) {
            throw staleRef(ref, "its workspace or project is no longer there");
        }
    }

    /**
     * Reads the session before pulling it, so a ref that has gone stale and a session with nothing
     * to merge both fail in a sentence rather than partway through a multi-gigabyte transfer.
     */
    private RecordingSession preflight(ProjectManager project, HubSessionRef ref, HubInfo hubInfo) {
        RecordingSession session;
        try {
            session = project.repositoryManager().recordingSession(ref.sessionId());
        } catch (JeffreyException | StatusRuntimeException e) {
            throw staleRef(ref, "hub " + hubInfo.name() + " no longer has it, "
                    + "which usually means retention removed it");
        }

        if (finishedFiles(session).stream().noneMatch(RepositoryFile::isRecordingFile)) {
            throw new IllegalArgumentException(
                    "Session " + ref.sessionId() + " has no finished recording file to download"
                            + (session.status() == RecordingStatus.ACTIVE
                            ? ", because it is still recording and its first chunk has not been rolled yet."
                            : "."));
        }
        return session;
    }

    private static List<RepositoryFile> finishedFiles(RecordingSession session) {
        if (session.files() == null) {
            return List.of();
        }
        List<RepositoryFile> finished = new ArrayList<>();
        for (RepositoryFile file : session.files()) {
            if (file.isFinished()) {
                finished.add(file);
            }
        }
        return finished;
    }

    private static IllegalArgumentException staleRef(HubSessionRef ref, String why) {
        return new IllegalArgumentException(
                "Session " + ref.sessionId() + " cannot be downloaded: " + why
                        + ". Call hubs_sessions again for a current session_ref.");
    }

    private static DownloadedSession existing(
            HubSessionRef ref, DownloadedSessionIndex.LocalCopy copy) {

        String next = copy.analysed()
                ? "Already analysed as profile " + copy.profileId()
                + " - every analysis tool takes that profileId straight away."
                : "Call recordings_analyzeRecording with recordingId=" + copy.recordingId()
                        + " to build the profile every analysis tool takes.";
        return new DownloadedSession(
                copy.recordingId(), null, null, null, ref.sessionId(), 0, 0, 0L, next);
    }

    private static String address(HubInfo info) {
        return info.address() == null
                ? ""
                : info.address().hostname() + ":" + info.address().port();
    }

    /**
     * How long the JVM has been recording. A session with no finish time is still going, which is a
     * fact about the row rather than a gap in it.
     */
    private static String duration(RecordingSession session) {
        Instant start = session.createdAt();
        if (start == null) {
            return "";
        }
        Instant end = session.finishedAt();
        if (end == null) {
            return "running";
        }
        Duration elapsed = Duration.between(start, end);
        if (elapsed.toHours() > 0) {
            return elapsed.toHours() + "h" + elapsed.toMinutesPart() + "m";
        }
        if (elapsed.toMinutes() > 0) {
            return elapsed.toMinutes() + "m" + elapsed.toSecondsPart() + "s";
        }
        return elapsed.toSeconds() + "s";
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024.0) + "KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return Math.round(bytes / (1024.0 * 1024)) + "MB";
        }
        return String.format(Locale.ROOT, "%.1fGB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * @param status what to do next, rather than a bare flag: the caller is holding a tool result and
     *               needs to know that calling the same tool again is the way to check
     */
    private record DownloadInProgress(
            String sessionId, String sessionName, long totalSizeBytes, String status) {
    }

    private record DownloadedSession(
            String recordingId,
            String sessionName,
            String hub,
            String project,
            String sessionId,
            int recordingFiles,
            int artifactFiles,
            long sizeBytes,
            String nextStep) {
    }
}

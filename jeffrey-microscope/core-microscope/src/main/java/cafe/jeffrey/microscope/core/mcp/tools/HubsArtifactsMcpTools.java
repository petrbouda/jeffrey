/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

import cafe.jeffrey.hub.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadedSessionIndex;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionLocator;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingFile;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.shared.common.model.repository.FileCategory;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import cafe.jeffrey.shared.common.model.repository.StreamedRecordingFile;
import cafe.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The files of a hub session one at a time: what a session holds beside its recording, and how to
 * pull one of them down without pulling the recording.
 * <p>
 * {@code hubs_download} brings every artifact along with the merged recording, and for a session
 * whose recording is wanted that is the right shape. This family is for the other case — a JVM that
 * crashed before its first chunk rolled and left only {@code hs-jvm-err.log}, an application log a
 * reader wants to grep before deciding whether the recording is worth the transfer.
 * <p>
 * <strong>A fetched file is a path, and nothing else.</strong> It lands beside the profile of the
 * session's recording when there is one — {@code profiles/<profileId>/artifacts/<name>}, so it sits
 * with the data it lines up with and goes when the profile goes — and otherwise, for a session that
 * never rolled a chunk and left only a crash file, under
 * {@code artifacts/<hub>/<project>/<session>/<name>}. The answer is that path: the reader is a
 * coding agent on the same machine (the endpoint accepts loopback hosts only) with better tools for
 * a text file than anything a tool result could carry. Nothing is parsed, catalogued or indexed
 * here; both paths are deterministic, so whether a file was fetched before is whether it is there.
 */
public class HubsArtifactsMcpTools {

    private static final Logger LOG = LoggerFactory.getLogger(HubsArtifactsMcpTools.class);

    private static final String FETCH_FAILED = "Hub file fetch failed: ";
    private static final String PROFILE_ARTIFACTS_DIR = "artifacts";
    private static final Duration FETCH_DEADLINE = Duration.ofHours(1);

    private static final ScheduledExecutorService DEADLINE_SCHEDULER = deadlineScheduler();

    private static final String FETCH_STILL_RUNNING =
            "The transfer is still running. Call hubs_fetchFile again with the same session_ref and "
                    + "file_id: a file that has landed is returned as it is rather than fetched a second time.";

    private final HubSessionLocator locator;
    private final RecordingsManager recordings;
    private final Path artifactsDir;
    private final Path profilesDir;
    private final McpOperationRegistry operations;
    private final Duration responseBudget;
    private final Duration fetchDeadline;
    private final BoundedJobs<HubFileRef, Path> fetches;

    public HubsArtifactsMcpTools(
            ProjectManagerResolver resolver,
            RecordingsManager recordings,
            Path artifactsDir,
            Path profilesDir,
            McpOperationRegistry operations,
            Clock clock) {
        this(resolver, recordings, artifactsDir, profilesDir, operations, clock, BoundedJobs.WAIT_BUDGET, FETCH_DEADLINE);
    }

    public HubsArtifactsMcpTools(
            ProjectManagerResolver resolver,
            RecordingsManager recordings,
            Path artifactsDir,
            Path profilesDir,
            McpOperationRegistry operations,
            Clock clock,
            Duration responseBudget,
            Duration fetchDeadline) {
        this.locator = new HubSessionLocator(resolver);
        this.recordings = recordings;
        this.artifactsDir = artifactsDir.toAbsolutePath();
        this.profilesDir = profilesDir.toAbsolutePath();
        this.operations = operations;
        this.responseBudget = responseBudget;
        this.fetchDeadline = fetchDeadline;
        this.fetches = new BoundedJobs<>(responseBudget, BoundedJobs.COMPLETED_RETENTION, clock);
    }

    /**
     * One hub file, as the key one transfer at a time runs under.
     */
    record HubFileRef(HubSessionRef session, String fileId) {
    }

    @Tool(description = "Every file one hub recording session holds - the JFR chunks, and beside them "
            + "the artifacts the JVM left: application logs, the unified-logging file (gc.jvm-log), "
            + "the crash file (hs-jvm-err.log or hs_err_pid*.log), the perf-counters file, a heap dump. "
            + "Call it when the question is about what a JVM wrote rather than what it recorded: an "
            + "exception in the application log, why the JVM died, what a GC log says for a session "
            + "that has no recording. Takes the session_ref from a hubs_sessions row. The `local` "
            + "column says a file is already on this machine: the absolute path of an artifact that was "
            + "fetched or came along with hubs_download - open it with your own tools - or "
            + "recording:<id> / profile:<id> for a recording hubs_download already merged. Pass a "
            + "file_id to hubs_fetchFile to pull one artifact; recording files come through hubs_download.")
    public String files(
            @ToolParam(required = true, description = "The session_ref from a hubs_sessions row, copied exactly")
            String sessionRef) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        HubInfo hubInfo = locator.hubInfo(ref);
        ProjectManager project = locator.project(ref);
        RecordingSession session = locator.session(project, ref, hubInfo);

        List<RepositoryFile> files = session.files() == null ? List.of() : session.files();
        if (files.isEmpty()) {
            return "Session " + session.name() + " on hub " + hubInfo.name() + " holds no files"
                    + (session.status() == RecordingStatus.ACTIVE
                    ? " yet - it is still recording and nothing has been rolled." : ".");
        }

        LocalSession local = localSession(ref);
        MarkdownTable table = MarkdownTable.withColumns(
                "file_id", "name", "type", "category", "status", "size", "created", "local");
        for (RepositoryFile file : files) {
            table.row(
                    file.id(),
                    file.name(),
                    file.fileType().name(),
                    file.fileType().fileCategory().name().toLowerCase(Locale.ROOT),
                    file.status(),
                    ByteSizes.format(file.size()),
                    file.createdAt(),
                    localColumn(file, ref, local));
        }
        return table
                .note("Session " + session.name() + " on hub " + hubInfo.name() + ", project "
                        + project.info().name() + ". An artifact row with `local` empty is not on this "
                        + "machine: pass its file_id to hubs_fetchFile. A `local` path is on the machine "
                        + "Jeffrey runs on; read it with your own tools. A recording row is merged with the "
                        + "session's other recording files by hubs_download, not fetched on its own. Only a "
                        + "FINISHED file can be fetched."
                        + (local.profileId() == null ? "" : " The session's recording is analysed as profile "
                        + local.profileId() + ", whose zero point is " + local.profilingStartedAt()
                        + ": an uptime in its GC log is that instant plus the uptime."))
                .render();
    }

    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Pull one artifact of a hub recording session onto this machine - an application "
            + "log, a JVM unified-logging file, a crash file, a perf-counters file or a heap dump - "
            + "without downloading the session's recording. Takes the session_ref of a hubs_sessions "
            + "row and the file_id of a hubs_files row. Returns the absolute path the file now has on "
            + "the machine Jeffrey runs on: open, grep or parse it there with your own tools - Jeffrey "
            + "hands the file over rather than parsing it. A heap dump's path goes to "
            + "recordings_analyzeFile. A file already fetched is returned as it is rather than "
            + "transferred twice. A large file may take longer than a client waits, in which case "
            + "the answer says the transfer continues and calling again with the same arguments reports "
            + "the path once it lands; every started transfer carries an operationId for "
            + "operations_status and operations_cancel.")
    public String fetchFile(
            @ToolParam(required = true, description = "The session_ref from a hubs_sessions row, copied exactly")
            String sessionRef,
            @ToolParam(required = true, description = "The file_id from a hubs_files row")
            String fileId) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId is required: take it from a hubs_files row.");
        }
        HubFileRef key = new HubFileRef(ref, fileId);

        Deadline responseDeadline = Deadline.after(responseBudget.toNanos(), TimeUnit.NANOSECONDS);
        Preflight preflight = preflightWithin(ref, fileId, responseDeadline);
        RepositoryFile file = preflight.file();
        LocalSession local = localSession(ref);
        Path target = targetOf(ref, file.name(), local);

        if (Files.isRegularFile(target)) {
            OperationHandle<Path> operation = fetches.rememberCompleted(key, target);
            return operations.decorate(McpToolOutput.json(fetched(file, target, true, local)), register(key, operation));
        }

        LOG.info("Fetching a hub artifact over MCP: hub_id={} project_id={} session_id={} file_id={} name={}",
                ref.hubId(), ref.projectId(), ref.sessionId(), fileId, file.name());
        OperationHandle<Path> operation = fetches.startOrJoin(key, false, Files::isRegularFile, control -> {
            control.phase("fetching");
            control.progress(Map.of("sessionRef", ref.encode(), "fileId", fileId,
                    "name", file.name(), "sizeBytes", file.size(), "path", target.toString()));
            return transferWithinDeadline(preflight.project(), ref, fileId, target, control);
        });
        String operationId = register(key, operation);
        Optional<Path> transferred;
        try {
            transferred = fetches.awaitWithin(operation, remaining(responseDeadline));
        } catch (RuntimeException e) {
            throw mapRemoteFailure(e);
        }
        if (transferred.isEmpty()) {
            return operations.decorate(McpToolOutput.json(new FetchInProgress(
                    ref.sessionId(), fileId, file.name(), file.size(), target.toString(), FETCH_STILL_RUNNING)),
                    operationId);
        }
        return operations.decorate(McpToolOutput.json(fetched(file, transferred.get(), false, local)), operationId);
    }

    /**
     * Where a session's file lives once fetched: in the profile's own directory when the session has
     * been analysed, else under the artifacts directory. Deterministic on purpose, and the hub's
     * session ids are unique only within a project, so the project is part of the second path. The
     * file's own name is kept because it is what the reader will recognise ({@code gc.jvm-log.1},
     * {@code hs-jvm-err.log}).
     */
    private Path targetOf(HubSessionRef ref, String filename, LocalSession local) {
        if (local.profileId() != null) {
            return profilesDir.resolve(local.profileId()).resolve(PROFILE_ARTIFACTS_DIR).resolve(filename);
        }
        return artifactsDir.resolve(ref.hubId()).resolve(ref.projectId()).resolve(ref.sessionId()).resolve(filename);
    }

    private String register(HubFileRef key, OperationHandle<Path> operation) {
        String sessionRef = key.session().encode();
        return operations.register(OperationKind.HUB_FETCH, operation,
                path -> Map.of("path", path.toString(), "sessionRef", sessionRef, "fileId", key.fileId()));
    }

    private record Preflight(ProjectManager project, RepositoryFile file) {
    }

    /**
     * Reads the session and finds the file before anything is transferred, so a stale ref, a wrong
     * id, a file still being written and a recording chunk each fail in a sentence.
     */
    private Preflight preflightWithin(HubSessionRef ref, String fileId, Deadline deadline) {
        Context.CancellableContext context = Context.current().withDeadline(deadline, DEADLINE_SCHEDULER);
        try {
            return context.call(() -> {
                HubInfo hubInfo = locator.hubInfo(ref);
                ProjectManager project = locator.project(ref);
                RecordingSession session = locator.session(project, ref, hubInfo);
                return new Preflight(project, fileIn(session, ref, fileId));
            });
        } catch (StatusRuntimeException e) {
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolExecutionException(FETCH_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    private static RepositoryFile fileIn(RecordingSession session, HubSessionRef ref, String fileId) {
        List<RepositoryFile> files = session.files() == null ? List.of() : session.files();
        RepositoryFile file = files.stream()
                .filter(candidate -> fileId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Session " + ref.sessionId()
                        + " has no file with id " + fileId + ". Call hubs_files for its current files."));
        if (file.isRecordingFile()) {
            throw new IllegalArgumentException("File " + file.name() + " is a recording, and recordings are "
                    + "merged by hubs_download rather than fetched one chunk at a time. Call hubs_download "
                    + "with the same session_ref.");
        }
        if (file.fileType().fileCategory() != FileCategory.ARTIFACT) {
            throw new IllegalArgumentException("File " + file.name() + " is " + file.fileType().description()
                    + " (" + file.fileType().fileCategory().name().toLowerCase(Locale.ROOT)
                    + "), which is not an artifact Jeffrey keeps.");
        }
        if (!file.isFinished()) {
            throw new IllegalArgumentException("File " + file.name() + " is still being written (status "
                    + file.status() + "). Fetch it once the session has finished.");
        }
        return file;
    }

    private Path transferWithinDeadline(
            ProjectManager project, HubSessionRef ref, String fileId, Path target, BoundedJobs.JobControl control) {
        Context.CancellableContext context = Context.ROOT.withDeadlineAfter(
                fetchDeadline.toNanos(), TimeUnit.NANOSECONDS, DEADLINE_SCHEDULER);
        control.onCancellation(() -> context.cancel(null));
        try {
            control.checkCancellation();
            return context.call(() -> place(project.repositoryManager().streamFile(ref.sessionId(), fileId), target));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.CANCELLED) {
                control.checkCancellation();
            }
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (Exception e) {
            if (Status.fromThrowable(e).getCode() == Status.Code.CANCELLED) {
                control.checkCancellation();
            }
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolExecutionException(FETCH_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    /**
     * Moves the streamed file to its place and lets the hub client's temporary directory go, whether
     * or not the move succeeded — a failed move must not leave a copy behind in the temp area.
     */
    private static Path place(StreamedRecordingFile streamed, Path target) {
        try {
            FileSystemUtils.createDirectories(target.getParent());
            Files.move(streamed.path(), target, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Stored a fetched hub artifact: path={}", target);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store the fetched file " + streamed.fileName() + ": " + e.getMessage(), e);
        } finally {
            if (streamed.cleanup() != null) {
                try {
                    streamed.cleanup().close();
                } catch (IOException e) {
                    LOG.warn("Could not clean up a streamed hub file: path={} reason={}", streamed.path(), e.getMessage());
                }
            }
        }
    }

    /**
     * What this machine already holds of a session: the recording {@code hubs_download} made, its
     * profile once analysed, and that profile's zero point.
     */
    private record LocalSession(Recording recording, String profileId, Instant profilingStartedAt) {

        static final LocalSession NONE = new LocalSession(null, null, null);
    }

    private LocalSession localSession(HubSessionRef ref) {
        Optional<DownloadedSessionIndex.LocalCopy> copy = DownloadedSessionIndex.build(recordings).find(ref);
        if (copy.isEmpty()) {
            return LocalSession.NONE;
        }
        Recording recording = recordings.findRecording(copy.get().recordingId()).orElse(null);
        String profileId = copy.get().profileId();
        Instant startedAt = profileId == null ? null : recordings.profile(profileId)
                .map(profile -> profile.info().profilingStartedAt())
                .orElse(null);
        return new LocalSession(recording, profileId, startedAt);
    }

    private String localColumn(RepositoryFile file, HubSessionRef ref, LocalSession local) {
        if (file.isRecordingFile()) {
            if (local.profileId() != null) {
                return "profile:" + local.profileId();
            }
            return local.recording() == null ? "" : "recording:" + local.recording().id();
        }
        Path fetched = targetOf(ref, file.name(), local);
        if (Files.isRegularFile(fetched)) {
            return fetched.toString();
        }
        if (local.recording() != null) {
            for (RecordingFile recordingFile : local.recording().files()) {
                if (recordingFile.filename().equals(file.name())) {
                    return recordings.findRecordingFile(local.recording().id(), recordingFile.id())
                            .map(path -> path.toAbsolutePath().toString())
                            .orElse("");
                }
            }
        }
        return "";
    }

    private static FetchedFile fetched(RepositoryFile file, Path path, boolean alreadyHere, LocalSession local) {
        boolean heapDump = file.fileType() == SupportedRecordingFile.HEAP_DUMP
                || file.fileType() == SupportedRecordingFile.HEAP_DUMP_GZ;
        String nextStep = heapDump
                ? "Pass path to recordings_analyzeFile to build the heap profile the heap_ tools take."
                : "Open, grep or parse the file at path with your own tools; it is on the machine Jeffrey runs on."
                + (local.profileId() == null ? "" : " Its timestamps line up with profile " + local.profileId()
                + ", whose zero point is " + local.profilingStartedAt() + ".");
        return new FetchedFile(
                file.name(),
                file.fileType().name(),
                file.size(),
                path.toString(),
                alreadyHere,
                local.recording() == null ? null : local.recording().id(),
                local.profileId(),
                local.profilingStartedAt() == null ? null : local.profilingStartedAt().toString(),
                nextStep);
    }

    private static Duration remaining(Deadline deadline) {
        long remainingNanos = deadline.timeRemaining(TimeUnit.NANOSECONDS);
        if (remainingNanos <= 0) {
            throw GrpcClientErrors.toJeffreyException(
                    Status.DEADLINE_EXCEEDED.withDescription("Hub fetch response deadline elapsed").asRuntimeException());
        }
        return Duration.ofNanos(remainingNanos);
    }

    private static RuntimeException mapRemoteFailure(RuntimeException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof StatusRuntimeException grpc) {
                return GrpcClientErrors.toJeffreyException(grpc);
            }
            cause = cause.getCause();
        }
        return exception;
    }

    private static ScheduledExecutorService deadlineScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("hub-mcp-fetch-deadline-", 0).factory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * @param alreadyHere true when nothing was transferred because the file was already at its path
     */
    private record FetchedFile(
            String filename,
            String type,
            Long sizeBytes,
            String path,
            boolean alreadyHere,
            String recordingId,
            String profileId,
            String profilingStartedAt,
            String nextStep) {
    }

    private record FetchInProgress(
            String sessionId, String fileId, String filename, Long sizeBytes, String path, String status) {
    }
}

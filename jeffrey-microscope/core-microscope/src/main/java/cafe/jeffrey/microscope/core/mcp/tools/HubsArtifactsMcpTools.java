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
import cafe.jeffrey.profile.common.operation.OperationState;
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
import cafe.jeffrey.shared.common.model.repository.StreamedFile;
import cafe.jeffrey.shared.common.model.repository.ManagedFile;
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
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * The files of a hub session one at a time: what a session holds beside its recording, and how to
 * pull one of them down without pulling the recording.
 * <p>
 * {@code hubs_download} brings every artifact along with the recording's files, and for a session
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
    /**
     * Only the default of the short constructor, which is the one the tests use. Production wiring
     * passes {@code jeffrey.microscope.mcp.hubs.download-timeout}, shared with {@code hubs_download}
     * because it measures the same thing: a transfer off the same hub over the same link.
     */
    private static final Duration FETCH_DEADLINE = Duration.ofHours(1);

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

    /** What one bounded lookup reads before a listing is rendered. */
    private record Listing(HubInfo hubInfo, ProjectManager project, RecordingSession session) {
    }

    /**
     * How a row is reached, said in the table rather than left to the reader to infer from the
     * category. A listing that invites a fetch the fetch tool refuses is worse than no column.
     *
     * <p>There is deliberately no "still being written" case. Only one file of a session is ever
     * open — the newest recording chunk — and a recording is never fetched one at a time anyway,
     * so it already reads {@code hubs_download}. Every artifact is fetchable, including one the
     * application is still appending to: a log a reader wants to grep before deciding whether the
     * recording is worth the transfer is what this family exists for, and refusing it until the
     * session ends would refuse it for as long as it is interesting.
     */
    private enum Fetchability {

        FETCH("fetch"),
        DOWNLOAD("hubs_download"),
        NEVER("no");

        private final String label;

        Fetchability(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private static String fetchColumn(RepositoryFile file) {
        if (file.isRecordingFile()) {
            return Fetchability.DOWNLOAD.label();
        }
        if (file.fileType().fileCategory() != FileCategory.ARTIFACT) {
            return Fetchability.NEVER.label();
        }
        return Fetchability.FETCH.label();
    }

    /**
     * What the row's {@code status} column says. A file carries none of its own: the session
     * holds one chunk open while it records, and every other file of it is final.
     */
    private static RecordingStatus statusOf(RecordingSession session, RepositoryFile file) {
        return session.isOpen(file) ? session.status() : RecordingStatus.FINISHED;
    }

    private static String zeroPointNote(LocalSession local) {
        if (local.profileId() == null) {
            return "";
        }
        if (local.profilingStartedAt() == null) {
            return " The session's recording is analysed as profile " + local.profileId()
                    + ", which carries no start instant, so an uptime in its GC log cannot be placed on "
                    + "the JFR timeline.";
        }
        return " The session's recording is analysed as profile " + local.profileId()
                + ", whose zero point is " + local.profilingStartedAt()
                + ": an uptime in its GC log is that instant plus the uptime.";
    }

    @Tool(description = "Every file one hub recording session holds - the JFR chunks, and beside them "
            + "the artifacts the JVM left: application logs, the unified-logging file (gc.jvm-log), "
            + "the crash file (hs-jvm-err.log or hs_err_pid*.log), the perf-counters file, a heap dump. "
            + "Call it when the question is about what a JVM wrote rather than what it recorded: an "
            + "exception in the application log, why the JVM died, what a GC log says for a session "
            + "that has no recording. Takes the session_ref from a hubs_sessions row. The `local` "
            + "column says a file is already on this machine: the absolute path of an artifact that was "
            + "fetched or came along with hubs_download - open it with your own tools - or "
            + "recording:<id> / profile:<id> for a recording hubs_download already brought. The `fetch` "
            + "column says how each row is reached: `fetch` means pass its file_id to hubs_fetchFile, "
            + "`hubs_download` means it is a recording chunk taken with the whole session, and `no` "
            + "means the hub does not serve that file on its own - only hubs_download brings it.")
    public String files(
            @ToolParam(required = true, description = "The session_ref from a hubs_sessions row, copied exactly")
            String sessionRef) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        // Bounded like the fetch preflight: an unresponsive hub must fail in a sentence rather than
        // hold the MCP request open for as long as the channel lets it.
        Listing listing = withinDeadline(
                McpDeadlines.after(responseBudget),
                Context.current(),
                () -> {
                    HubInfo hub = locator.hubInfo(ref);
                    ProjectManager owner = locator.project(ref);
                    return new Listing(hub, owner, locator.session(owner, ref, hub));
                });
        HubInfo hubInfo = listing.hubInfo();
        ProjectManager project = listing.project();
        RecordingSession session = listing.session();

        List<RepositoryFile> files = session.files() == null ? List.of() : session.files();
        if (files.isEmpty()) {
            return "Session " + session.name() + " on hub " + hubInfo.name() + " holds no files"
                    + (session.status() == RecordingStatus.ACTIVE
                    ? " yet - it is still recording and nothing has been rolled." : ".");
        }

        LocalSession local = localSession(ref);
        MarkdownTable table = MarkdownTable.withColumns(
                "file_id", "name", "type", "category", "status", "size", "created", "local", "fetch");
        for (RepositoryFile file : files) {
            table.row(
                    file.id(),
                    file.name(),
                    file.fileType().name(),
                    file.fileType().fileCategory().name().toLowerCase(Locale.ROOT),
                    statusOf(session, file),
                    ByteSizes.format(file.size()),
                    file.createdAt(),
                    localColumn(file, ref, local),
                    fetchColumn(file));
        }
        return table
                .note("Session " + session.name() + " on hub " + hubInfo.name() + ", project "
                        + project.info().name() + ". The `fetch` column says how a row is reached: `"
                        + Fetchability.FETCH.label() + "` means pass its file_id to hubs_fetchFile, `"
                        + Fetchability.DOWNLOAD.label()
                        + "` is a recording chunk taken with the rest by hubs_download rather than fetched on "
                        + "its own, and `"
                        + Fetchability.NEVER.label() + "` is a file the hub does not serve one at a time - a "
                        + "type Jeffrey does not classify, or a transient one. A `local` path is on the "
                        + "machine Jeffrey runs on; read it with your own tools. A `local` cell that is empty "
                        + "means the file is not here yet."
                        + zeroPointNote(local))
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
            + "transferred twice, and without asking the hub, so it stays readable while the hub is "
            + "down. A large file may take longer than a client waits, in which case "
            + "the answer says the transfer continues and calling again with the same arguments reports "
            + "the path once it lands; every started transfer carries an operationId for "
            + "operations_status and operations_cancel. A transfer that failed or was cancelled is "
            + "started again by calling this tool with the same arguments - there is no retry flag. "
            + "Only a row whose `fetch` column reads `fetch` can be fetched.")
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

        // Before the hub is touched at all: a file this call already fetched is a file on this disk,
        // and a disk does not need a round trip to be read. It also means an artifact stays reachable
        // while the hub that held it is down or its session has been retired - the answer is the path,
        // and the path is still good.
        Optional<RetainedFetch> retained = retainedLocally(key, ref);
        if (retained.isPresent()) {
            return operations.decorate(McpToolOutput.json(retained.get().answer()),
                    register(key, retained.get().operation()));
        }

        Deadline responseDeadline = McpDeadlines.after(responseBudget);
        Preflight preflight = preflightWithin(ref, fileId, responseDeadline);
        RepositoryFile file = preflight.file();
        LocalSession local = localSession(ref);
        Path target = targetOf(ref, file.name(), local);

        Optional<Path> here = alreadyHere(ref, file.name(), local, target);
        if (here.isPresent()) {
            OperationHandle<Path> operation = fetches.rememberCompleted(key, here.get());
            return operations.decorate(
                    McpToolOutput.json(fetched(file, here.get(), true, local)), register(key, operation));
        }

        LOG.info("Fetching a hub artifact over MCP: hub_id={} project_id={} session_id={} file_id={} name={}",
                ref.hubId(), ref.projectId(), ref.sessionId(), fileId, file.name());
        // retryFailure is true: a fetch is one file, and a transfer that failed on a blip must be
        // startable again by calling this tool - which is what OperationKind.HUB_FETCH tells the
        // caller to do. reuseSuccess pins the retained path to the one this call resolved, so a
        // session analysed since the last fetch is not answered with the copy under artifacts/.
        OperationHandle<Path> operation = fetches.startOrJoin(key, true,
                previous -> previous.equals(target) && Files.isRegularFile(previous),
                control -> {
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
            return under(profilesDir.resolve(local.profileId()).resolve(PROFILE_ARTIFACTS_DIR), filename, profilesDir);
        }
        return unlinkedTargetOf(ref, filename);
    }

    /**
     * Where a file lands while the session has no analysed recording. Kept separate because a file
     * fetched before the session was analysed stays valid afterwards: it is moved rather than pulled
     * down a second time.
     */
    private Path unlinkedTargetOf(HubSessionRef ref, String filename) {
        Path sessionDir = artifactsDir.resolve(segment(ref.hubId()))
                .resolve(segment(ref.projectId()))
                .resolve(segment(ref.sessionId()));
        return under(sessionDir, filename, artifactsDir);
    }

    /**
     * The file, if this machine already holds it - at the path this call resolved, or at the one an
     * earlier fetch used before the session was analysed, in which case it is moved rather than
     * transferred again. Both names come off the wire, so the resolved path is checked to be inside
     * the directory it was built from before anything is written.
     */
    private Optional<Path> alreadyHere(HubSessionRef ref, String filename, LocalSession local, Path target) {
        if (Files.isRegularFile(target)) {
            return Optional.of(target);
        }
        if (local.profileId() == null) {
            return Optional.empty();
        }
        Path unlinked = unlinkedTargetOf(ref, filename);
        if (!Files.isRegularFile(unlinked)) {
            return Optional.empty();
        }
        try {
            FileSystemUtils.createDirectories(target.getParent());
            Files.move(unlinked, target, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Moved a hub artifact beside its profile: from={} to={}", unlinked, target);
            return Optional.of(target);
        } catch (IOException e) {
            // The copy is still readable where it is; saying so beats refusing or transferring again.
            LOG.warn("Could not move a fetched hub artifact beside its profile: from={} to={} reason={}",
                    unlinked, target, e.getMessage());
            return Optional.of(unlinked);
        }
    }

    /**
     * One path element of a name that arrived over the wire. A hub session id is the one part of a
     * ref Jeffrey never minted, and a file name is whatever the hub reports, so neither may add a
     * directory to the path it is resolved into.
     */
    private static String segment(String value) {
        String name = Path.of(value).getFileName().toString();
        if (name.isBlank() || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Refusing a hub name that is not a single path element: " + value);
        }
        return name;
    }

    private static Path under(Path directory, String filename, Path root) {
        Path resolved = directory.resolve(segment(filename)).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to place " + filename + " outside " + root);
        }
        return resolved;
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
     * id and a recording chunk each fail in a sentence.
     */
    private Preflight preflightWithin(HubSessionRef ref, String fileId, Deadline deadline) {
        return withinDeadline(deadline, Context.current(), () -> {
            HubInfo hubInfo = locator.hubInfo(ref);
            ProjectManager project = locator.project(ref);
            RecordingSession session = locator.session(project, ref, hubInfo);
            return new Preflight(project, fileIn(session, ref, fileId));
        });
    }

    /**
     * Runs a remote lookup under a deadline, so no tool here can wait on a hub for longer than the
     * response budget. The gRPC clients set no per-call deadline of their own, so this is the only
     * thing between an unresponsive hub and an MCP request that never answers.
     */
    private static <T> T withinDeadline(Deadline deadline, Context parent, Callable<T> work) {
        Context.CancellableContext context = McpDeadlines.withDeadline(parent, deadline);
        try {
            return context.call(work);
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
        RepositoryFile file = session.files().stream()
                .filter(candidate -> fileId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Session " + ref.sessionId()
                        + " has no file with id " + fileId + ". Call hubs_files for its current files."));
        if (file.isRecordingFile()) {
            throw new IllegalArgumentException("File " + file.name() + " is a recording, and recordings are "
                    + "taken by hubs_download rather than fetched one chunk at a time. Call hubs_download "
                    + "with the same session_ref.");
        }
        if (file.fileType().fileCategory() != FileCategory.ARTIFACT) {
            // The hub's own streamArtifactFile refuses anything outside this category, so refusing it
            // here keeps the sentence useful rather than turning it into a remote INVALID_ARGUMENT.
            throw new IllegalArgumentException("File " + file.name() + " is " + file.fileType().description()
                    + " (" + file.fileType().fileCategory().name().toLowerCase(Locale.ROOT)
                    + "), and a hub serves only classified artifacts one at a time. Its `fetch` column in "
                    + "hubs_files reads `" + Fetchability.NEVER.label() + "`; hubs_download brings the whole "
                    + "session, this file included.");
        }
        return file;
    }

    private Path transferWithinDeadline(
            ProjectManager project, HubSessionRef ref, String fileId, Path target, BoundedJobs.JobControl control) {
        Context.CancellableContext context = McpDeadlines.withDeadlineAfter(Context.ROOT, fetchDeadline);
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
    private static Path place(StreamedFile streamed, Path target) {
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
        try {
            Path fetched = targetOf(ref, file.name(), local);
            if (Files.isRegularFile(fetched)) {
                return fetched.toString();
            }
            // A file fetched before the session was analysed is still here, under the unlinked path;
            // the next fetchFile moves it beside the profile rather than pulling it down again.
            Path unlinked = unlinkedTargetOf(ref, file.name());
            if (!unlinked.equals(fetched) && Files.isRegularFile(unlinked)) {
                return unlinked.toString();
            }
        } catch (IllegalArgumentException e) {
            // A name that cannot be a path element cannot have been fetched either. One unusable row
            // must not cost the reader the whole listing; fetchFile says why if they try it.
            LOG.debug("A hub file name resolves to no local path: session_id={} name={} reason={}",
                    ref.sessionId(), file.name(), e.getMessage());
            return "";
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

    /**
     * The answer for a file this instance fetched before and which is still where it put it, built
     * without asking the hub anything.
     * <p>
     * The retained operation is the only thing that maps a {@code fileId} back to a file name, which
     * is why this reaches no further back than {@link BoundedJobs#COMPLETED_RETENTION}: past that the
     * name has to come from {@code hubs_files} again. It is deliberately not a catalogue — nothing is
     * written down, and an empty answer here simply costs the round trip it would have saved.
     * <p>
     * A retained path that is no longer the path this session resolves to means the recording has
     * been analysed since, so the fetch proper runs and moves the file beside its profile.
     */
    private Optional<RetainedFetch> retainedLocally(HubFileRef key, HubSessionRef ref) {
        Optional<OperationHandle<Path>> retained = fetches.current(key)
                .filter(handle -> handle.snapshot().state() == OperationState.COMPLETED);
        if (retained.isEmpty()) {
            return Optional.empty();
        }
        OperationHandle<Path> operation = retained.get();
        Path path = operation.snapshot().result();
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }

        String filename = path.getFileName().toString();
        LocalSession local = localSession(ref);
        if (!path.equals(targetOf(ref, filename, local))) {
            return Optional.empty();
        }
        Long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            // It was a regular file a moment ago. Whatever changed, the hub knows more than we do.
            LOG.debug("A retained hub artifact could not be sized: path={} reason={}", path, e.getMessage());
            return Optional.empty();
        }
        LOG.debug("Answering a hub artifact fetch from this disk: session_id={} file_id={} path={}",
                ref.sessionId(), key.fileId(), path);
        return Optional.of(new RetainedFetch(
                operation, fetched(filename, ManagedFile.of(filename), size, path, true, local)));
    }

    /** A fetch answered off this disk: the operation it was, and the answer built from the file. */
    private record RetainedFetch(OperationHandle<Path> operation, FetchedFile answer) {
    }

    private static FetchedFile fetched(RepositoryFile file, Path path, boolean alreadyHere, LocalSession local) {
        return fetched(file.name(), file.fileType(), file.size(), path, alreadyHere, local);
    }

    private static FetchedFile fetched(
            String filename, ManagedFile type, Long sizeBytes, Path path,
            boolean alreadyHere, LocalSession local) {
        boolean heapDump = type == ManagedFile.HEAP_DUMP
                || type == ManagedFile.HEAP_DUMP_GZ;
        String nextStep = heapDump
                ? "Pass path to recordings_analyzeFile to build the heap profile the heap_ tools take."
                : "Open, grep or parse the file at path with your own tools; it is on the machine Jeffrey runs on."
                + (local.profileId() == null ? "" : " Its timestamps line up with profile " + local.profileId()
                + (local.profilingStartedAt() == null ? "." : ", whose zero point is "
                + local.profilingStartedAt() + "."));
        return new FetchedFile(
                filename,
                type.name(),
                sizeBytes,
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

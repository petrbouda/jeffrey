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

import cafe.jeffrey.hub.client.GrpcClientErrors;
import cafe.jeffrey.hub.client.RepositoryFiles;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopePage;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadedSessionIndex;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.Fetch;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.FetchStatus;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.Fetchability;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.FileRow;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubAnswers.SessionFiles;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubCalls;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionLocator;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.ToolExecutionException;
import cafe.jeffrey.microscope.model.hub.HubInfo;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingStatus;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.microscope.model.repository.StreamedFile;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

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
    private static final String RESPONSE_DEADLINE_ELAPSED = "Hub fetch response deadline elapsed";
    private static final String PROFILE_ARTIFACTS_DIR = "artifacts";

    private static final int DEFAULT_FILES_LIMIT = 100;
    private static final int MAX_FILES_LIMIT = 1000;

    private static final Set<ManagedFile> HEAP_DUMPS = Set.of(ManagedFile.HEAP_DUMP, ManagedFile.HEAP_DUMP_GZ);

    private static final String FETCH_NOTE =
            "The `fetch` column says how a row is reached: FETCH means pass its fileId to hubs_fetchFile, "
                    + "DOWNLOAD is a recording chunk taken with the rest by hubs_download rather than fetched on "
                    + "its own, and NEVER is a file the hub does not serve one at a time - a type Jeffrey does "
                    + "not classify, or a transient one. A `local` path is on the machine Jeffrey runs on; read "
                    + "it with your own tools. A `local` cell that is empty means the file is not here yet.";
    private static final String NEXT_FILES_WHY = "continues the session's files past this page";
    private static final String FETCH_GUIDANCE =
            "hubs_fetchFile with a FETCH row's fileId puts that one file on this machine and answers with its path.";
    private static final String DOWNLOAD_GUIDANCE =
            "The DOWNLOAD rows come with hubs_download, which brings the session - or a window of it - as one "
                    + "recording.";

    private static final String JOIN_WHY = "returns the path once the transfer lands, without fetching the file twice";
    private static final String ANALYSE_HEAP_DUMP_WHY = "builds the heap profile the heap_ tools take from this dump";
    /** The recordings family is always served beside hubs; the heap family need not be. */
    private static final String HEAP_NOT_SERVED_GUIDANCE =
            "heap_ is not served by this installation, so this heap dump cannot be analysed here; the file "
                    + "is at path for your own tools.";
    private static final String READ_IT_YOURSELF =
            "Open, grep or parse the file at path with your own tools; it is on the machine Jeffrey runs on.";
    private static final String ZERO_POINT_GUIDANCE =
            "Its timestamps line up with profile %s, whose recording starts at profilingStartedAtEpochMs.";
    private static final String RETRY_WHY = "fetches the file again after a failed or cancelled transfer";

    private final HubSessionLocator locator;
    private final RecordingsManager recordings;
    private final Path artifactsDir;
    private final Path profilesDir;
    private final McpOperationRegistry operations;
    private final OperationAnswers answers;
    private final Duration responseBudget;
    private final Duration fetchDeadline;
    private final BoundedJobs<HubFileRef, Path> fetches;
    private final AdvertisedFamilies advertised;

    /**
     * @param answers        how a call that outlasts its wait answers, by what the client declared
     * @param responseBudget how long a fetch call waits on its preflight and its transfer together; a
     *                       client that declared tasks waits on the transfer for less
     * @param fetchDeadline  the deadline the transfer itself runs under: production passes
     *                       {@code jeffrey.microscope.mcp.hubs.download-timeout}, shared with
     *                       {@code hubs_download} because it measures the same thing, a transfer off the
     *                       same hub over the same link
     */
    public HubsArtifactsMcpTools(
            ProjectManagerResolver resolver,
            RecordingsManager recordings,
            Path artifactsDir,
            Path profilesDir,
            McpOperationRegistry operations,
            OperationAnswers answers,
            Clock clock,
            Duration responseBudget,
            Duration fetchDeadline,
            AdvertisedFamilies advertised) {
        this.answers = answers;
        this.locator = new HubSessionLocator(resolver);
        this.recordings = recordings;
        this.artifactsDir = artifactsDir.toAbsolutePath();
        this.profilesDir = profilesDir.toAbsolutePath();
        this.operations = operations;
        this.responseBudget = responseBudget;
        this.fetchDeadline = fetchDeadline;
        this.fetches = new BoundedJobs<>(responseBudget, BoundedJobs.COMPLETED_RETENTION, clock,
                Schedulers.sharedVirtual(), BoundedJobs.UNBOUNDED_CONCURRENCY, BoundedJobs.DEFAULT_MAX_RETAINED);
        this.advertised = advertised;
    }


    /**
     * One hub file, as the key one transfer at a time runs under.
     */
    record HubFileRef(HubSessionRef session, String fileId) {

        private McpNextTool.Call call() {
            return HubCalls.onSession(HubCalls.HUBS_FETCH_FILE, session.encode()).with(HubCalls.FILE_ID, fileId);
        }

        /** This fetch again: it joins the transfer in flight or answers from disk once it has landed. */
        McpNextTool sameCall(String why) {
            return call().why(why);
        }

        /** This fetch again after it failed or was cancelled, which starts a new transfer. */
        McpNextTool retryCall() {
            return call().why(RETRY_WHY);
        }
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
    private static Fetchability fetchability(RepositoryFile file) {
        if (file.isRecordingFile()) {
            return Fetchability.DOWNLOAD;
        }
        if (!RepositoryFiles.isArtifact(file)) {
            return Fetchability.NEVER;
        }
        return Fetchability.FETCH;
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

    @Tool(description = "Returns every file one hub recording session holds: the JFR chunks, and beside "
            + "them the artifacts the JVM left - application logs, the unified-logging file "
            + "(gc.jvm-log), the crash file (hs-jvm-err.log or hs_err_pid*.log), the perf-counters "
            + "file, a heap dump. For what a JVM wrote rather than what it recorded: an exception in "
            + "the application log, why the JVM died, a GC log for a session with no recording. "
            + "Takes the sessionRef of a hubs_sessions row. localPath names a file already here. "
            + "`fetch` says how a row is reached: FETCH - its fileId goes to hubs_fetchFile; DOWNLOAD - "
            + "a chunk that comes with the session through hubs_download; NEVER - the hub serves it "
            + "only through hubs_download. Pages with cursor: follow nextCursor with the same "
            + "sessionRef. status EMPTY: the session holds no files yet.")
    @McpOutputSchema(SessionFiles.class)
    @McpToolMeta(cost = McpToolCost.CHEAP, requires = McpToolRequirement.HUB)
    public McpToolResult files(
            @ToolParam(required = true, description = "The sessionRef from a hubs_sessions row, copied exactly")
            String sessionRef,
            @ToolParam(required = false, description = "Maximum number of files to return (default "
                    + DEFAULT_FILES_LIMIT + ", maximum " + MAX_FILES_LIMIT + ")")
            @ToolParamBounds(defaultValue = DEFAULT_FILES_LIMIT, min = 1, max = MAX_FILES_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the preceding page with the same "
                    + "sessionRef")
            String cursor) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        String encodedRef = ref.encode();
        McpCursor.Filters filters = McpCursor.Filters.of(HubCalls.HUBS_FILES, encodedRef);
        int start = OffsetPaging.offset(cursor, filters);
        String uiLink = UiLinks.page(MicroscopePage.HUBS);
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
        LocalSession local = localSession(ref);

        List<RepositoryFile> files = session.files() == null ? List.of() : session.files();
        int rows = ToolArguments.boundedLimit(limit, DEFAULT_FILES_LIMIT, MAX_FILES_LIMIT);
        int end = (int) Math.min(files.size(), (long) start + rows);
        List<RepositoryFile> page = start < files.size() ? files.subList(start, end) : List.of();
        McpCursor.Next next = OffsetPaging.next(filters, start, page.size(), end < files.size());

        List<FileRow> fileRows = new ArrayList<>(page.size());
        MarkdownTable table = MarkdownTable.withColumns(
                "fileId", "name", "type", "category", "status", "size", "created", "local", "fetch");
        for (RepositoryFile file : page) {
            ManagedFile type = RepositoryFiles.typeOf(file);
            String localPath = localPath(file, ref, local);
            FileRow row = new FileRow(file.id(), file.name(), type, type.fileCategory(), statusOf(session, file),
                    file.size(), file.createdAt() == null ? null : file.createdAt().toEpochMilli(), localPath,
                    fetchability(file));
            fileRows.add(row);
            table.row(row.fileId(), row.name(), row.type(), row.category(), row.status(),
                    ByteSizes.format(file.size()), file.createdAt(), localColumn(file, local, localPath), row.fetch());
        }

        boolean empty = files.isEmpty();
        String reason = empty ? "Session " + session.name() + " on hub " + hubInfo.name() + " holds no files"
                + (session.status() == RecordingStatus.ACTIVE
                ? " yet - it is still recording and nothing has been rolled." : ".") : null;
        String recordingId = local.recording() == null ? null : local.recording().id();
        McpFollowUp followUp = filesFollowUp(encodedRef, rows, next, fileRows, recordingId, local.profileId());
        SessionFiles answer = new SessionFiles(empty ? CatalogueStatus.EMPTY : CatalogueStatus.OK, reason, encodedRef,
                session.name(), hubInfo.name(), project.info().name(), recordingId, local.profileId(),
                local.profilingStartedAt() == null ? null : local.profilingStartedAt().toEpochMilli(),
                List.copyOf(fileRows), fileRows.size(), files.size(), next.hasMore(), next.nextCursor(), followUp,
                uiLink);
        String body = empty ? reason : "Returned " + fileRows.size() + " of " + files.size() + " files.\n\n"
                + table.note("Session " + session.name() + " on hub " + hubInfo.name() + ", project "
                        + project.info().name() + ". " + FETCH_NOTE + zeroPointNote(local))
                .renderUncapped();
        return McpToolResult.of(LinkedOutput.footed(body, followUp, uiLink, null).text(), answer);
    }

    /**
     * Where a page of a session's files leads: the next page, and the analysis or the profile of the
     * session's recording once a download has brought it. Fetching and downloading are guidance: which
     * file matters, and which part of the session, are the reader's choice, and each moves bytes.
     */
    private McpFollowUp filesFollowUp(String sessionRef, int limit, McpCursor.Next next, List<FileRow> rows,
                                      String recordingId, String profileId) {
        boolean fetchable = rows.stream().anyMatch(row -> row.fetch() == Fetchability.FETCH && row.localPath() == null);
        boolean chunks = rows.stream().anyMatch(row -> row.fetch() == Fetchability.DOWNLOAD);
        return NextSteps.builder(advertised)
                .nextWhen(next.hasMore(), HubCalls.onSession(HubCalls.HUBS_FILES, sessionRef)
                        .with(HubCalls.LIMIT, limit).with(HubCalls.CURSOR, next.nextCursor()).why(NEXT_FILES_WHY))
                .nextWhen(recordingId != null && profileId == null, () -> HubCalls.analyse(recordingId))
                .nextWhen(profileId != null, () -> HubCalls.summary(profileId))
                .guidanceWhen(fetchable, FETCH_GUIDANCE)
                .guidanceWhen(chunks && recordingId == null, DOWNLOAD_GUIDANCE)
                .followUp();
    }

    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Fetches one artifact of a hub recording session onto this machine - an "
            + "application log, a JVM unified-logging file, a crash file, a perf-counters file or a "
            + "heap dump - without the session's recording. Takes the sessionRef of a hubs_sessions "
            + "row and the fileId of a hubs_files row whose `fetch` is FETCH. Returns the file's "
            + "absolute path on the machine Jeffrey runs on; Jeffrey hands the file over rather than "
            + "parsing it, and a heap dump's path goes to recordings_analyzeFile. A file already "
            + "fetched is answered from disk without asking the hub. status FETCHED, or RUNNING when a "
            + "large file outlasts the call - the same call again returns the path once it lands. "
            + "Every started transfer carries an operationId for operations_status and "
            + "operations_cancel, and a client that declared the MCP tasks extension gets a task after "
            + "about 5 s instead. A failed or cancelled transfer starts again on the same call; there "
            + "is no retry flag.")
    @McpOutputSchema(Fetch.class)
    @McpToolMeta(cost = McpToolCost.SLOW, requires = McpToolRequirement.HUB)
    public McpToolOutcome fetchFile(
            @ToolParam(required = true, description = "The sessionRef from a hubs_sessions row, copied exactly")
            String sessionRef,
            @ToolParam(required = true, description = "The fileId from a hubs_files row")
            String fileId,
            McpCallContext call) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId is required: take it from a hubs_files row.");
        }
        HubFileRef key = new HubFileRef(ref, fileId);
        // Read here, where the request is bound: an answer is rendered wherever the finished transfer is
        // first seen, and that need not be a request thread.
        String uiLink = UiLinks.page(MicroscopePage.HUBS);

        // Before the hub is touched at all: a file this call already fetched is a file on this disk,
        // and a disk does not need a round trip to be read. It also means an artifact stays reachable
        // while the hub that held it is down or its session has been retired - the answer is the path,
        // and the path is still good.
        Optional<RetainedFetch> retained = retainedLocally(key, ref);
        if (retained.isPresent()) {
            FetchFacts facts = retained.get().facts();
            String operationId = register(key, retained.get().operation(), path -> facts, uiLink);
            return answer(key, facts, operationId, uiLink);
        }

        // The preflight has the whole response budget, whatever the client declared: only the wait
        // on the transfer below is shortened for a client that can follow a task.
        Deadline responseDeadline = McpDeadlines.after(responseBudget);
        Preflight preflight = preflightWithin(ref, fileId, responseDeadline);
        RepositoryFile file = preflight.file();
        LocalSession local = localSession(ref);
        Path target = targetOf(ref, file.name(), local);

        Optional<Path> here = alreadyHere(ref, file.name(), local, target);
        if (here.isPresent()) {
            OperationHandle<Path> operation = fetches.rememberCompleted(key, here.get());
            FetchFacts facts = fetched(file, here.get(), true, local);
            String operationId = register(key, operation, path -> facts, uiLink);
            return answer(key, facts, operationId, uiLink);
        }

        LOG.info("Fetching a hub artifact over MCP: hub_id={} project_id={} session_id={} file_id={} name={}",
                ref.hubId(), ref.projectId(), ref.sessionId(), fileId, file.name());
        // retryFailure is true: a fetch is one file, and a transfer that failed on a blip must be
        // startable again by calling this tool - which is the retry its operation names. reuseSuccess
        // pins the retained path to the one this call resolved, so a session analysed since the last
        // fetch is not answered with the copy under artifacts/.
        OperationHandle<Path> operation = fetches.startOrJoin(key, true,
                previous -> previous.equals(target) && Files.isRegularFile(previous),
                control -> {
                    control.phase(OperationPhase.FETCHING);
                    control.progress(OperationDetails.hubFetch(
                            ref.encode(), fileId, file.name(), file.size(), target.toString()));
                    return transferWithinDeadline(preflight.project(), ref, fileId, target, control);
                });
        String operationId = register(key, operation, path -> fetched(file, path, false, local), uiLink);
        Optional<Path> transferred;
        try {
            transferred = fetches.awaitWithin(operation, McpDeadlines.remainingWithin(
                    responseDeadline, answers.waitBudget(call, responseBudget), RESPONSE_DEADLINE_ELAPSED));
        } catch (RuntimeException e) {
            throw mapRemoteFailure(e);
        }
        if (transferred.isEmpty()) {
            return answers.stillRunning(call, operationId, () -> answer(key,
                    running(file, target, local), operationId, uiLink));
        }
        return answer(key, fetched(file, transferred.get(), false, local), operationId, uiLink);
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

    /**
     * Registers the transfer with the call that retries it - this tool again, which starts a failed
     * or cancelled fetch afresh - and the answer a task following it completes with.
     *
     * @return the operation id
     */
    private String register(HubFileRef key, OperationHandle<Path> operation, Function<Path, FetchFacts> landed,
                            String uiLink) {
        String operationId = operation.operationId();
        return operations.register(OperationKind.HUB_FETCH, operation, fetchResult(key),
                path -> answer(key, landed.apply(path), operationId, uiLink), key.retryCall());
    }

    private static Function<Path, FetchResult> fetchResult(HubFileRef key) {
        String sessionRef = key.session().encode();
        return path -> new FetchResult(path.toString(), sessionRef, key.fileId());
    }

    /** What a finished fetch produced, as operations_status reports it. */
    private record FetchResult(String path, String sessionRef, String fileId) {
    }

    /**
     * The one answer every state of a fetch gives: the file, the transfer behind it, what to do with
     * it next and the page for the user.
     */
    private McpToolResult answer(HubFileRef key, FetchFacts facts, String operationId, String uiLink) {
        McpOperationRegistry.Snapshot operation = operationId == null ? null : operations.status(operationId);
        return McpToolResult.of(new Fetch(facts.status(), key.session().encode(), key.fileId(), facts.filename(),
                facts.type(), facts.sizeBytes(), facts.path(), facts.alreadyHere(), facts.recordingId(),
                facts.profileId(), facts.profilingStartedAtEpochMs(), operationId, operation,
                followUp(key, facts, operationId), uiLink));
    }

    private McpFollowUp followUp(HubFileRef key, FetchFacts facts, String operationId) {
        NextSteps.Builder next = NextSteps.builder(advertised);
        if (facts.status() == FetchStatus.RUNNING) {
            return next.next(HubCalls.poll(operationId))
                    .next(key.sameCall(JOIN_WHY))
                    .followUp();
        }
        boolean heapDump = HEAP_DUMPS.contains(facts.type());
        boolean heapServed = advertised.has(AdvertisedFamilies.HEAP);
        return next
                .nextWhen(heapDump && heapServed, NextCalls.to(HubCalls.RECORDINGS_ANALYZE_FILE)
                        .with(HubCalls.PATH, facts.path()).why(ANALYSE_HEAP_DUMP_WHY))
                .guidanceWhen(heapDump && !heapServed, HEAP_NOT_SERVED_GUIDANCE)
                .guidanceWhen(!heapDump, READ_IT_YOURSELF)
                .guidanceWhen(!heapDump && facts.profileId() != null,
                        () -> ZERO_POINT_GUIDANCE.formatted(facts.profileId()))
                .followUp();
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
                    + "with the same sessionRef.");
        }
        if (!RepositoryFiles.isArtifact(file)) {
            // Refused here rather than by the hub, which serves any file it holds: the sentence
            // names what the file is, where a remote INVALID_ARGUMENT would not.
            ManagedFile type = RepositoryFiles.typeOf(file);
            throw new IllegalArgumentException("File " + file.name() + " is " + type.description()
                    + " (" + type.fileCategory().name().toLowerCase(Locale.ROOT)
                    + "), and a hub serves only classified artifacts one at a time. Its `fetch` column in "
                    + "hubs_files reads `" + Fetchability.NEVER + "`; hubs_download brings the whole "
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

    /**
     * Where this machine holds a file of the session: the absolute path of an artifact fetched before,
     * or of one a download brought beside the recording's chunks; {@code null} for a chunk, which the
     * session's recording or profile stands for, and for a file that is not here.
     */
    private String localPath(RepositoryFile file, HubSessionRef ref, LocalSession local) {
        if (file.isRecordingFile()) {
            return null;
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
            return null;
        }
        if (local.recording() != null) {
            for (RecordingFile recordingFile : local.recording().files()) {
                if (recordingFile.filename().equals(file.name())) {
                    return recordings.findRecordingFile(local.recording().id(), recordingFile.id())
                            .map(path -> path.toAbsolutePath().toString())
                            .orElse(null);
                }
            }
        }
        return null;
    }

    /** What the table's {@code local} column says: the path, or the recording or profile a chunk came with. */
    private static String localColumn(RepositoryFile file, LocalSession local, String localPath) {
        if (!file.isRecordingFile()) {
            return localPath;
        }
        if (local.profileId() != null) {
            return "profile:" + local.profileId();
        }
        return local.recording() == null ? null : "recording:" + local.recording().id();
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
                operation, facts(FetchStatus.FETCHED, filename, ManagedFile.of(filename), size, path, true, local)));
    }

    /** A fetch answered off this disk: the operation it was, and what it reports. */
    private record RetainedFetch(OperationHandle<Path> operation, FetchFacts facts) {
    }

    private static FetchFacts fetched(RepositoryFile file, Path path, boolean alreadyHere, LocalSession local) {
        return facts(FetchStatus.FETCHED, file.name(), RepositoryFiles.typeOf(file), file.size(), path, alreadyHere,
                local);
    }

    private static FetchFacts running(RepositoryFile file, Path target, LocalSession local) {
        return facts(FetchStatus.RUNNING, file.name(), RepositoryFiles.typeOf(file), file.size(), target, false,
                local);
    }

    private static FetchFacts facts(FetchStatus status, String filename, ManagedFile type, Long sizeBytes, Path path,
                                    boolean alreadyHere, LocalSession local) {
        return new FetchFacts(status, filename, type, sizeBytes, path.toString(), alreadyHere,
                local.recording() == null ? null : local.recording().id(),
                local.profileId(),
                local.profilingStartedAt() == null ? null : local.profilingStartedAt().toEpochMilli());
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
     * What one state of a fetch reports, before the transfer, the next calls and the link are added.
     *
     * @param alreadyHere true when nothing was transferred because the file was already at its path
     */
    private record FetchFacts(
            FetchStatus status,
            String filename,
            ManagedFile type,
            Long sizeBytes,
            String path,
            boolean alreadyHere,
            String recordingId,
            String profileId,
            Long profilingStartedAtEpochMs) {
    }
}

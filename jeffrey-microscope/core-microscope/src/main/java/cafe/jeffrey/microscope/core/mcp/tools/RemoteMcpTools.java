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

import cafe.jeffrey.microscope.remote.client.RepositoryFiles;
import cafe.jeffrey.microscope.core.manager.remote.RemoteConnection;
import cafe.jeffrey.microscope.core.manager.remote.RemoteManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.MicroscopePage;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.core.mcp.tools.remote.DownloadWindow;
import cafe.jeffrey.microscope.core.mcp.tools.remote.DownloadWindowQuestion;
import cafe.jeffrey.microscope.core.mcp.tools.remote.DownloadedSessionIndex;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.BundleItem;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.ChosenWindow;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.Download;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.DownloadStatus;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.RemoteState;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.RemoteStatus;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.SessionRow;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteAnswers.Sessions;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteCalls;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteScanFilter;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteSessionLocator;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteSessionRef;
import cafe.jeffrey.microscope.core.mcp.tools.remote.RemoteSessionScan;
import cafe.jeffrey.microscope.core.mcp.tools.remote.WindowAnswer;
import cafe.jeffrey.microscope.core.mcp.tools.remote.WindowArguments;
import cafe.jeffrey.microscope.core.mcp.tools.remote.WindowResolution;
import cafe.jeffrey.microscope.core.mcp.tools.remote.WindowSubject;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpCallContext;
import cafe.jeffrey.microscope.mcp.protocol.McpCursor;
import cafe.jeffrey.microscope.mcp.protocol.McpInputResponse;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.repository.ChunkWindow;
import cafe.jeffrey.microscope.model.repository.RecordingSession;
import cafe.jeffrey.microscope.model.repository.RecordingSessionFilter;
import cafe.jeffrey.microscope.model.repository.RepositoryFile;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.JeffreyMcpServer;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ToolParamBounds;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The recordings that never reached this machine: everything on the configured remote, the
 * recording volume on an Azure file share.
 * <p>
 * The other write family, {@code recordings_}, starts from a file the reader already has. This one
 * starts from an environment they do not — "the JFR recordings from the last hour on production" —
 * and ends at a local recording the rest of the server can analyse.
 * <p>
 * <strong>Flat, not a tree.</strong> The remote holds workspaces holding projects holding sessions,
 * and the web UI lets a reader walk that. Walking it here would cost three calls before anything is
 * downloaded and would give the model three chances to pair a workspace with the wrong project. So
 * one call fans out across every project and returns flat rows, and each row carries a
 * {@link RemoteSessionRef} that {@code remote_download} takes on its own. The hierarchy is filtering and
 * display; it is never a sequence of questions.
 * <p>
 * <strong>Downloading and analysing stay apart.</strong> {@code remote_download} returns a recording
 * id and stops, leaving {@code recordings_analyzeRecording} to build the profile. Doing both in one
 * call would mean one request covering a multi-gigabyte transfer <em>and</em> a full analysis, which
 * is exactly the shape that trips a client's tool timeout — and a timeout in the middle tells the
 * model nothing about whether the work survived.
 * <p>
 * <strong>Links to the remote's page.</strong> Every answer carries a {@code uiLink} for the user: the
 * remote's page for its sessions, and the recording or its profile once a download has
 * brought one here. {@code UiLinks} reads the request bound to the calling thread, and the scan and the
 * transfer run on other threads, so a link is built on the call's own thread - or from a base read
 * there, for an answer a task completes with later.
 */
public class RemoteMcpTools {

    /**
     * The application property: a whole-session download of a session longer than this asks a client
     * that renders forms which part to bring (an ISO-8601 duration, default one hour).
     */
    public static final String ASK_WINDOW_OVER_DURATION_PROPERTY =
            "jeffrey.microscope.mcp.remote.ask-window-over-duration";

    /**
     * The application property: likewise for a session bigger than this (a Spring data size, default
     * {@code 1GB}).
     */
    public static final String ASK_WINDOW_OVER_SIZE_PROPERTY = "jeffrey.microscope.mcp.remote.ask-window-over-size";

    /** How long a remote_download or remote_fetchFile call waits before answering with an operationId. */
    public static final String DOWNLOAD_RESPONSE_TIMEOUT_PROPERTY = "jeffrey.microscope.mcp.remote.download-response-timeout";

    private static final String OMITTED_FAILURES_SCOPE = "additional remote scopes";
    private static final String OMITTED_FAILURES = "%d additional failed scopes omitted; narrow filters for details.";
    private static final String WINDOW_AND_FILE_IDS = "Choose the part of the session one way: a window "
            + "(startEpochMs/endEpochMs) or fileIds, not both.";

    private static final String RESPONSE_DEADLINE_ELAPSED = "Remote download response deadline elapsed";
    private static final Logger LOG = LoggerFactory.getLogger(RemoteMcpTools.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final int DISPLAY_CHARS = 256;
    private static final int FAILURE_CHARS = 512;
    private static final int MAX_DISPLAYED_FAILURES = 24;
    private static final String LIVE_PAGING_NOTE =
            "Live view: a cursor continues after the last returned row among observed sessions. "
                    + "Newer insertions require a fresh scan. Missing remote scopes remain incomplete "
                    + "even when hasMore=false. Long display names and failure details are shortened; "
                    + "sessionRef identities are preserved.";

    // Declaring the remote stays with the user, in configuration only -- never an MCP tool or the UI,
    // since the address it dials would otherwise come from whatever text the agent last read.
    private static final String CONFIGURED_REMOTE_PROPERTY = "jeffrey.microscope.remote";
    private static final String NOT_CONFIGURED =
            ("No remote is configured for this installation. The remote is the recording volume on an Azure "
                    + "file share, declared in configuration only; declare it and restart Microscope "
                    + "(auth=entra signs in with the machine's Azure identity instead of a SAS token; "
                    + "read-only=true refuses every change):\n\n"
                    + "  %1$s.name=Production\n"
                    + "  %1$s.azure-files.endpoint=https://<account>.file.core.windows.net\n"
                    + "  %1$s.azure-files.share=recordings\n"
                    + "  %1$s.azure-files.protocol=smb\n"
                    + "  %1$s.azure-files.sas-token=<SAS token>\n\n"
                    + "Recordings can still be analysed from a local file with recordings_analyzeFile.")
                    .formatted(CONFIGURED_REMOTE_PROPERTY);
    private static final String STATUS_NOTE =
            "A remote marked `UNREACHABLE` did not answer just now, so remote_sessions can list nothing from it. "
                    + "A `read_only` remote lists and downloads as usual but refuses every change to it "
                    + "(no delete, no pin).";

    private static final String SESSIONS_WHY = "lists the remote's recording sessions";
    private static final String UNREACHABLE_GUIDANCE =
            "The remote did not answer; ask the user whether it should be up before relying on remote_sessions "
                    + "for its recordings.";

    private static final String FOOTER_LOCAL =
            "A row with `local` empty is not in this Jeffrey yet; `recording:<id>` is downloaded but not "
                    + "analysed; `profile:<id>` is already analysed and every analysis tool takes that id.\n";

    private static final String NO_SESSIONS = "No recording sessions matched on the remote.";
    private static final String NO_SESSIONS_WIDEN =
            NO_SESSIONS + " The window was the last %d minutes: widen withinLastMinutes, or drop it for every session.";

    private static final String NEXT_SESSIONS_WHY = "continues the session list past this page with the same filters";
    private static final String FILES_WHY =
            "lists what the session holds - its chunks and the artifacts beside them - before anything is transferred";
    private static final String CHECK_REMOTE_WHY = "shows which remote is configured and whether it answers";
    private static final String DOWNLOAD_GUIDANCE =
            "remote_download brings a session as one local recording. Ask the user which part they need - the last "
                    + "hour, the minutes around an incident - and pass it as startEpochMs/endEpochMs rather than "
                    + "bringing a whole long session.";

    private static final String NOT_DOWNLOADED_GUIDANCE =
            "Nothing was transferred. To download without being asked, call remote_download with startEpochMs and "
                    + "endEpochMs (UTC epoch milliseconds) for a window, or fileIds from remote_files; otherwise ask "
                    + "the user which part of the session they need.";
    private static final String CHOOSE_FILES_WHY = "names the session's chunks, whose fileIds bring an exact part";
    private static final String RUNNING_REASON =
            "The transfer is still running. The same remote_download call joins it and, once the recording lands, "
                    + "returns it from the local store rather than fetching the session a second time.";
    private static final String JOIN_WHY = "joins this transfer and returns the recordingId once it lands";
    private static final String RETRY_WHY = "starts a new transfer of the same part of the session";
    private static final String PART_GUIDANCE =
            "This recording covers only the part asked for, so its figures are about that span, not the whole "
                    + "session; recordings_delete removes it once the question is answered. The same sessionRef "
                    + "with the same startEpochMs/endEpochMs or fileIds asks for this part again.";
    private static final String WHOLE_ANALYSED_GUIDANCE =
            "The whole session is also here, already analysed as profile %s.";
    private static final String WHOLE_RECORDING_GUIDANCE = "The whole session is also here as recording %s.";
    private static final String HEAP_ITEMS_GUIDANCE = "The download is a bundle: each heap dump in it is a "
            + "recording of its own (%s). recordings_analyzeRecording with that recordingId builds its profile "
            + "for the heap_ tools; the session's recording above is the JFR profile.";

    private static final String WINDOW_WITH_FILE_IDS = "Choose the part of the session one way: a window or "
            + "fileIds, not both.";
    private static final String NOT_RETAINED_GUIDANCE = "The session's first chunk is gone from the remote. Ask the "
            + "user whether the oldest chunk kept, LATEST, PEAK or a window will do instead.";
    private static final String NO_CHUNK =
            "No chunk covers the window from %s to %s: the session's recording there is missing or "
                    + "not on the remote yet. Choose another window, or the whole session.";

    private final RemoteManager remoteManager;
    private final ProjectManagerResolver resolver;
    private final RecordingsManager recordingsManager;
    private final Clock clock;
    private final Duration downloadResponseBudget;

    /**
     * One transfer per session at a time, and no call waits longer than a client will.
     */
    private final BoundedJobs<DownloadKey, String> downloads;
    private final RemoteSessionScan scan;
    private final RemoteSessionLocator locator;
    private final McpOperationRegistry operations;
    private final OperationAnswers answers;
    private final DownloadWindowQuestion windowQuestion;
    private final AdvertisedFamilies advertised;

    /**
     * @param scanBudget             how long a listing may spend waiting on the remote. Needed because a
     *                               listing runs inside a synchronous tool call, so a remote that neither
     *                               answers nor refuses would otherwise hang the caller for good
     * @param downloadResponseBudget how long a download call waits on its preflight and its transfer
     *                               together; a client that declared tasks waits on the transfer for
     *                               less, see {@link OperationAnswers}
     * @param operations             the registry operations_status reads
     * @param answers                how a call that outlasts its wait answers, by what the client declared
     * @param windowQuestion         which whole-session downloads a client that renders forms is first
     *                               asked about, from the {@code ask-window-over-*} properties
     * @param advertised             the families this installation serves, so a next call never names a
     *                               tool it does not
     */
    public RemoteMcpTools(RemoteManager remoteManager, ProjectManagerResolver resolver,
            RecordingsManager recordingsManager, Clock clock, Duration scanBudget,
            Duration downloadResponseBudget, McpOperationRegistry operations,
            OperationAnswers answers, DownloadWindowQuestion windowQuestion, AdvertisedFamilies advertised) {
        this.windowQuestion = Objects.requireNonNull(windowQuestion, "windowQuestion");
        this.advertised = Objects.requireNonNull(advertised, "advertised");
        this.operations = operations;
        this.answers = answers;
        this.remoteManager = remoteManager;
        this.resolver = resolver;
        this.recordingsManager = recordingsManager;
        this.clock = clock;
        this.downloadResponseBudget = requirePositive(downloadResponseBudget, "downloadResponseBudget");
        this.downloads = new BoundedJobs<>(downloadResponseBudget, BoundedJobs.COMPLETED_RETENTION, clock,
                Schedulers.sharedVirtual(), BoundedJobs.UNBOUNDED_CONCURRENCY, BoundedJobs.DEFAULT_MAX_RETAINED);
        this.scan = new RemoteSessionScan(remoteManager, scanBudget);
        this.locator = new RemoteSessionLocator(resolver);
    }

    @Tool(description = "Returns the one remote this installation's configuration declares - the recording "
            + "volume on an Azure file share - whether it refuses changes (readOnly), and whether it answers "
            + "right now (state REACHABLE or UNREACHABLE): why remote_sessions shows nothing when it is down. state NOT_CONFIGURED: configuration declares no "
            + "remote, and the reason says how the user declares one.")
    @McpOutputSchema(RemoteStatus.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult status() {
        String uiLink = UiLinks.page(MicroscopePage.REMOTE);
        Optional<RemoteConnection> configured = remoteManager.current();
        if (configured.isEmpty()) {
            McpFollowUp followUp = NextSteps.builder(advertised).followUp();
            RemoteStatus none = new RemoteStatus(RemoteState.NOT_CONFIGURED, NOT_CONFIGURED, null, null, null, null,
                    followUp, uiLink);
            return McpToolResult.of(LinkedOutput.footed(NOT_CONFIGURED, followUp, uiLink, null).text(), none);
        }

        RemoteInfo remote = configured.get().info();
        RemoteSessionScan.ProbeResult probe = scan.probe(configured.get());
        RemoteState state = probe.reachable() ? RemoteState.REACHABLE : RemoteState.UNREACHABLE;
        McpFollowUp followUp = NextSteps.builder(advertised)
                .nextWhen(probe.reachable(), NextCalls.to(RemoteCalls.REMOTE_SESSIONS).why(SESSIONS_WHY))
                .guidanceWhen(!probe.reachable(), UNREACHABLE_GUIDANCE)
                .followUp();
        RemoteStatus answer = new RemoteStatus(state, null, remote.name(), remote.address().identity(), remote.address().readOnly(),
                probe.version().orElse(null), followUp, uiLink);
        MarkdownTable table = MarkdownTable.withColumns(
                "remote", "address", "read_only", "state", "version");
        table.row(answer.name(), answer.address(), answer.readOnly(),
                answer.state(), answer.version());
        String body = table.note(STATUS_NOTE).renderUncapped();
        return McpToolResult.of(LinkedOutput.footed(body, followUp, uiLink, null).text(), answer);
    }

    @Tool(description = "Returns recording sessions across every project on the remote, newest first, "
            + "in one flat list - recordings from an environment rather than from a file, such as "
            + "\"the JFR recordings from the last hour on production\". Every row carries a "
            + "sessionRef for remote_download and remote_files; recordingId or profileId say a session is "
            + "already in this Jeffrey, and startedAtEpochMs and durationMs give the span a window can "
            + "be chosen from. Follow nextCursor with the same filters for more rows. A live view: "
            + "complete says whether all remote scopes answered, independently of hasMore; a "
            + "relative time window keeps its original cutoff across pages. status EMPTY: nothing "
            + "matched and the remote answered.")
    @McpOutputSchema(Sessions.class)
    @McpToolMeta(cost = McpToolCost.MODERATE, requires = McpToolRequirement.REMOTE)
    public McpToolResult sessions(
            @ToolParam(required = false, description = "Optional filter on part of a workspace name or its reference id")
            String workspace,
            @ToolParam(required = false, description = "Optional filter on part of a project name, e.g. checkout")
            String project,
            @ToolParam(required = false, description = "Only sessions whose recorded data reaches into the last "
                    + "N minutes - 60 for the last hour, 1440 for the last day. This is an overlap, "
                    + "not a start time: a JVM that began recording three hours ago and whose last "
                    + "chunk landed ten minutes ago does match a 60-minute window")
            @ToolParamBounds(min = 1)
            Integer withinLastMinutes,
            @ToolParam(required = false, description = "Most rows to return across all projects. Default "
                    + DEFAULT_LIMIT + ", maximum " + MAX_LIMIT)
            @ToolParamBounds(defaultValue = DEFAULT_LIMIT, min = 1, max = MAX_LIMIT)
            Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the previous page. Keep all "
                    + "filters unchanged; limit may change. Omit to start a fresh live scan")
            String cursor) {

        int rowLimit = ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        RemoteScanFilter requested = new RemoteScanFilter(
                workspace, project, sessionFilter(withinLastMinutes));
        McpCursor.Filters filters = McpCursor.Filters.of(RemoteCalls.REMOTE_SESSIONS,
                requested.workspace(), requested.project(), withinLastMinutes);
        SessionPosition continuation = cursor == null ? null : JeffreyMcpServer.CURSOR.decodeKeyset(
                cursor, filters, keyset -> SessionPosition.read(keyset, withinLastMinutes != null));
        RemoteScanFilter filter = continuation == null ? requested : requested.withSessions(
                new RecordingSessionFilter(continuation.activeFrom(), null, RecordingSessionFilter.NO_LIMIT));

        RemoteSessionScan.Result scanned = scan.scan(filter);
        List<RemoteSessionScan.Row> remaining = scanned.rows().stream()
                .filter(row -> continuation == null || row.key().compareTo(continuation.after()) > 0)
                .toList();
        SessionPage page = new SessionPage(filter, filters, withinLastMinutes, scanned.rows().size(),
                remaining.size(), boundedFailures(scanned.failures()),
                new SessionQuery(workspace, project, withinLastMinutes, rowLimit),
                UiLinks.page(MicroscopePage.REMOTE));
        DownloadedSessionIndex local = DownloadedSessionIndex.build(recordingsManager, locator.currentAddress());
        int selected = Math.min(rowLimit, remaining.size());
        PageCandidate whole = renderPage(page, remaining.subList(0, selected), local);
        if (fits(whole)) {
            return McpToolResult.of(whole.text(), whole.structuredContent());
        }

        // The largest complete prefix that fits, found by halving rather than by dropping one row at a
        // time: the limit reaches 500 and a page nearly fills the budget, so shrinking row by row
        // re-renders the whole answer hundreds of times to arrive at the same place. profiles_list
        // pages the same way, and renderPage derives hasMore and nextCursor from the rows it is
        // handed, so whichever prefix this settles on is a correct page for exactly those rows.
        int low = 1;
        int high = selected - 1;
        PageCandidate fitting = null;
        while (low <= high) {
            int count = low + (high - low) / 2;
            PageCandidate candidate = renderPage(page, remaining.subList(0, count), local);
            if (fits(candidate)) {
                fitting = candidate;
                low = count + 1;
            } else {
                high = count - 1;
            }
        }
        if (fitting == null) {
            throw new IllegalArgumentException(
                    "A session identity exceeds the catalogue response limit and cannot be returned intact.");
        }
        return McpToolResult.of(fitting.text(), fitting.structuredContent());
    }

    private static boolean fits(PageCandidate candidate) {
        return candidate.text().length() <= McpToolOutput.MAX_CHARS
                && Json.toString(candidate.structuredContent()).length() <= McpToolOutput.MAX_CHARS;
    }

    /**
     * What one listing call asked for, as its arguments name it: the filters a next page repeats, and
     * the most rows a page holds.
     */
    private record SessionQuery(String workspace, String project, Integer withinLastMinutes, int limit) {

        McpNextTool nextPage(String cursor) {
            McpNextTool.Call call = NextCalls.to(RemoteCalls.REMOTE_SESSIONS)
                    .with(RemoteCalls.WORKSPACE, workspace)
                    .with(RemoteCalls.PROJECT, project);
            if (withinLastMinutes != null) {
                call.with(RemoteCalls.WITHIN_LAST_MINUTES, withinLastMinutes.longValue());
            }
            return call.with(RemoteCalls.LIMIT, limit)
                    .with(RemoteCalls.CURSOR, cursor)
                    .why(NEXT_SESSIONS_WHY);
        }
    }

    private record SessionPage(RemoteScanFilter filter, McpCursor.Filters cursorFilters, Integer withinLastMinutes,
                               int observedTotal, int remaining, List<RemoteSessionScan.Failure> failures,
                               SessionQuery query, String uiLink) {

        boolean complete() {
            return failures.isEmpty();
        }
    }

    private record PageCandidate(String text, Sessions structuredContent) {
    }

    /**
     * Where a {@code remote_sessions} page continues: after the last returned row, keeping the cutoff
     * the first page's relative window resolved to, so a later page does not slide the window.
     */
    private record SessionPosition(Instant activeFrom, RemoteSessionScan.Key after) {

        /** Where each value sits in the keyset: the window cutoff, then the last row's sort key. */
        private static final int ACTIVE_FROM = 0;
        private static final int CREATED_AT = 1;
        private static final int SESSION_REF = 2;
        private static final int VALUES = 3;

        McpCursor.Keyset keyset() {
            String[] values = new String[VALUES];
            values[ACTIVE_FROM] = text(activeFrom);
            values[CREATED_AT] = text(after.createdAt());
            values[SESSION_REF] = after.ref().encode();
            return new McpCursor.Keyset(Arrays.asList(values));
        }

        /**
         * The position a keyset holds; refused when its shape is not this tool's, or when it carries a
         * cutoff for a scan without a window or none for a scan with one.
         */
        static SessionPosition read(McpCursor.Keyset keyset, boolean windowed) {
            List<String> values = keyset.after();
            if (values.size() != VALUES || values.get(SESSION_REF) == null) {
                throw new IllegalArgumentException("not a remote_sessions position: after=" + values);
            }
            Instant activeFrom = instant(values.get(ACTIVE_FROM));
            if (windowed != (activeFrom != null)) {
                throw new IllegalArgumentException(
                        "the cursor's window does not match the call: windowed=" + windowed);
            }
            return new SessionPosition(activeFrom,
                    new RemoteSessionScan.Key(instant(values.get(CREATED_AT)), RemoteSessionRef.decode(values.get(SESSION_REF))));
        }

        private static String text(Instant value) {
            return value == null ? null : value.toString();
        }

        private static Instant instant(String value) {
            return value == null ? null : Instant.parse(value);
        }
    }

    private PageCandidate renderPage(
            SessionPage page, List<RemoteSessionScan.Row> rows, DownloadedSessionIndex local) {
        boolean hasMore = rows.size() < page.remaining();
        String nextCursor = hasMore && !rows.isEmpty() ? JeffreyMcpServer.CURSOR.encode(page.cursorFilters(),
                new SessionPosition(page.filter().sessions().activeFrom(), rows.getLast().key()).keyset()) : null;
        List<SessionRow> sessions = new ArrayList<>(rows.size());
        MarkdownTable table = MarkdownTable.withColumns(
                "workspace", "project", "started", "duration", "newest", "files", "size",
                "local", "sessionRef");
        for (RemoteSessionScan.Row row : rows) {
            RecordingSession session = row.session();
            String workspace = bounded(row.workspaceName(), DISPLAY_CHARS);
            String project = bounded(row.projectName(), DISPLAY_CHARS);
            Optional<DownloadedSessionIndex.LocalCopy> copy = local.find(row.ref());
            String ref = row.ref().encode();
            int files = session.fileCount();
            table.row(workspace, project, session.createdAt(), duration(session), session.newest() ? "yes" : "", files,
                    ByteSizes.format(session.totalSizeBytes()), localColumn(copy), ref);
            sessions.add(new SessionRow(workspace, project,
                    session.createdAt() == null ? null : session.createdAt().toEpochMilli(),
                    durationMs(session), session.newest(), files, session.totalSizeBytes(),
                    copy.map(DownloadedSessionIndex.LocalCopy::recordingId).orElse(null),
                    copy.map(DownloadedSessionIndex.LocalCopy::profileId).orElse(null),
                    ref));
        }
        boolean empty = page.observedTotal() == 0 && page.complete();
        String reason = empty ? emptyReason(page.withinLastMinutes()) : null;
        McpFollowUp followUp = sessionsFollowUp(page, sessions, nextCursor);
        Sessions structured = new Sessions(empty ? CatalogueStatus.EMPTY : CatalogueStatus.OK, reason,
                List.copyOf(sessions), rows.size(), page.complete() ? page.observedTotal() : null,
                page.observedTotal(), hasMore, nextCursor, page.complete(), page.failures(), followUp, page.uiLink());
        String metadata = "Returned " + rows.size() + " of " + page.observedTotal() + " observed sessions. "
                + "complete=" + page.complete() + "; hasMore=" + hasMore + ". " + LIVE_PAGING_NOTE;
        String text;
        if (empty) {
            text = reason + "\n\n" + metadata;
        } else {
            text = table.note(footer(new RemoteSessionScan.Result(rows, page.failures())))
                    .note(metadata).renderUncapped();
        }
        // The page is measured whole, footer included, and never cut: the footer is appended, not capped.
        return new PageCandidate(text + LinkedOutput.footer(followUp, page.uiLink(), null), structured);
    }

    /**
     * Where a page of sessions leads: the next page, a look at the first session not here yet, the
     * analysis of one downloaded but not analysed, or the profile of one that is. A download is never
     * offered as a call: which part of a session to bring is the user's choice, so it is guidance.
     */
    private McpFollowUp sessionsFollowUp(SessionPage page, List<SessionRow> sessions, String nextCursor) {
        Optional<SessionRow> notHere = sessions.stream().filter(row -> row.recordingId() == null).findFirst();
        Optional<SessionRow> unanalysed = sessions.stream()
                .filter(row -> row.recordingId() != null && row.profileId() == null).findFirst();
        Optional<SessionRow> analysed = sessions.stream().filter(row -> row.profileId() != null).findFirst();
        boolean emptyWithoutWindow = page.observedTotal() == 0 && page.withinLastMinutes() == null;
        return NextSteps.builder(advertised)
                .nextWhen(nextCursor != null, () -> page.query().nextPage(nextCursor))
                .nextWhen(notHere.isPresent(), () -> RemoteCalls.onSession(RemoteCalls.REMOTE_FILES,
                        notHere.orElseThrow().sessionRef()).why(FILES_WHY))
                .nextWhen(unanalysed.isPresent(), () -> RemoteCalls.analyse(unanalysed.orElseThrow().recordingId()))
                .nextWhen(analysed.isPresent(), () -> RemoteCalls.summary(analysed.orElseThrow().profileId()))
                .nextWhen(!page.complete() || emptyWithoutWindow,
                        NextCalls.to(RemoteCalls.REMOTE_STATUS).why(CHECK_REMOTE_WHY))
                .guidanceWhen(notHere.isPresent(), DOWNLOAD_GUIDANCE)
                .followUp();
    }

    private static List<RemoteSessionScan.Failure> boundedFailures(List<RemoteSessionScan.Failure> failures) {
        List<RemoteSessionScan.Failure> displayed = new ArrayList<>();
        for (RemoteSessionScan.Failure failure : failures.subList(0, Math.min(failures.size(), MAX_DISPLAYED_FAILURES))) {
            displayed.add(new RemoteSessionScan.Failure(
                    bounded(failure.scope(), FAILURE_CHARS), failure.kind(), bounded(failure.reason(), FAILURE_CHARS)));
        }
        if (failures.size() > displayed.size()) {
            displayed.add(new RemoteSessionScan.Failure(OMITTED_FAILURES_SCOPE, RemoteSessionScan.Failure.Kind.OTHER,
                    OMITTED_FAILURES.formatted(failures.size() - displayed.size())));
        }
        return List.copyOf(displayed);
    }

    private static String bounded(String value, int limit) {
        if (value == null) {
            return "";
        }
        if (value.length() <= limit) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit;
        return value.substring(0, end) + "…";
    }

    /*
     * The one tool in this family that writes. The family is advertised READS_REMOTE because the other
     * two observe; this one creates a local recording and can move gigabytes across a network to do it,
     * which is exactly the case a client's read-only hint is there to let a reader approve knowingly.
     * A client that declared form elicitation is also asked which part of a large session to bring
     * (DownloadWindowQuestion) before a whole-session transfer starts; the description does not say so,
     * because the tool list is the same for every client.
     */
    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Downloads a recording session from the remote into this Jeffrey as one bundle, by "
            + "the sessionRef of a remote_sessions row, and returns the recordingId of its recording for "
            + "recordings_analyzeRecording. With no selection it brings the session's recording "
            + "files as one recording, each heap dump and the perf counters as recordings of their own "
            + "(items lists them), and the JVM and application logs as files of the bundle. A part "
            + "instead: window names one - the last minutes, the startup, latest or peak chunk, the minutes before or around a moment, or a custom span; "
            + "startEpochMs and/or endEpochMs bring every chunk whose span touches them; or fileIds "
            + "bring named remote_files rows - an unbroken run of chunks and any artifacts beside them. "
            + "The answer reports the span actually covered, and what a window picked and why. A "
            + "session a newer one ended, already downloaded whole, is returned as it is; a copy of an "
            + "instance's newest session is downloaded again, and a part always makes a bundle of its "
            + "own. status DOWNLOADED, STARTUP_NOT_RETAINED when the remote no longer holds the first "
            + "chunk, or RUNNING when a large transfer outlasts the call - the call in followUp joins "
            + "it. Every started transfer carries an operationId for operations_status and "
            + "operations_cancel (a task after about 5 s for a client that declared tasks). A FAILED or CANCELLED outcome is kept in memory for one hour and "
            + "reported rather than restarted unless retry=true.")
    @McpOutputSchema(Download.class)
    @McpToolMeta(cost = McpToolCost.SLOW, requires = McpToolRequirement.REMOTE)
    public McpToolOutcome download(
            @ToolParam(required = true, description = "The sessionRef from a remote_sessions row, copied exactly")
            String sessionRef,
            @ToolParam(required = false, description = "A part chosen by name. WHOLE: all of it. LAST_MINUTES: "
                    + "the last minutes up to the end of its recorded data. STARTUP: its first chunk. "
                    + "LATEST: the newest chunk. PEAK: the largest compressed chunk. BEFORE/AROUND: "
                    + "minutes ending at, or centred on, atEpochMs. CUSTOM: startEpochMs/endEpochMs. Not "
                    + "combined with fileIds")
            DownloadWindow window,
            @ToolParam(required = false, description = "Length in minutes for LAST_MINUTES, BEFORE and AROUND; "
                    + "60 (the last hour) when omitted. Only with those windows")
            @ToolParamBounds(min = 1, max = DownloadWindow.MAX_WINDOW_MINUTES)
            Integer minutes,
            @ToolParam(required = false, description = "The moment BEFORE ends at and AROUND is centred on, as UTC "
                    + "epoch milliseconds - for a heap dump or crash file, the createdAtEpochMs of its remote_files "
                    + "row. Required with those two windows, refused with any other")
            Long atEpochMs,
            @ToolParam(required = false, description = "Start of the window as UTC epoch milliseconds, with window "
                    + "CUSTOM or no window. Omit with endEpochMs for the whole session; omit on its own to reach "
                    + "back to the session's start")
            Long startEpochMs,
            @ToolParam(required = false, description = "End of the window as UTC epoch milliseconds, after "
                    + "startEpochMs, with window CUSTOM or no window. Omit on its own to reach forward to the "
                    + "session's end")
            Long endEpochMs,
            @ToolParam(required = false, description = "The fileId values from remote_files rows to "
                    + "bring instead of a window - at least one must be a JFR chunk, and the chunks must be an "
                    + "unbroken run of the session because the recording reports one span across them. Artifacts beside "
                    + "them are free to pick. Not combined with window, startEpochMs or endEpochMs")
            List<String> fileIds,
            @ToolParam(required = false, description = "Retry a failed transfer while its outcome is retained "
                    + "(one hour after completion, in memory). Omit or false to inspect a retained failure. "
                    + "After expiry or a server restart, this call can start a new transfer regardless of retry")
            Boolean retry,
            McpCallContext call) {
        RemoteSessionRef ref = RemoteSessionRef.decode(sessionRef);
        WindowArguments given = new WindowArguments(minutes, atEpochMs, startEpochMs, endEpochMs);
        // Read here, where the request is bound: an answer is rendered wherever the finished transfer is
        // first seen, and that need not be a request thread.
        String base = UiLinks.base();
        if (window != null) {
            if (fileIds != null && !fileIds.isEmpty()) {
                throw new IllegalArgumentException(WINDOW_WITH_FILE_IDS);
            }
            window.checkArguments(given);
            // The session is read only when the window needs it; WHOLE, a span or a moment does not.
            SessionReading reading = new SessionReading(ref);
            WindowResolution resolution = window.resolve(given, reading::subject);
            return downloadResolved(ref, resolution, retry, call, reading.read(), base, Optional.empty());
        }
        DownloadWindow.checkWithoutWindow(given);
        DownloadKey key = DownloadKey.of(ref, startEpochMs, endEpochMs, fileIds);
        if (!mayAskForWindow(key, call)) {
            return downloadReportingRetained(key, null, retry, call, Optional.empty(), base);
        }

        // Asked before anything crosses the network. The session is read once, under the call's
        // whole response budget, and that read is what the transfer then starts from.
        McpDeadline responseDeadline = McpDeadline.after(downloadResponseBudget);
        DownloadPreflight preflight = preflight(ref);
        Optional<SessionRead> read = Optional.of(new SessionRead(responseDeadline, preflight));
        Instant now = clock.instant();
        if (!windowQuestion.asks(preflight.session(), now)) {
            return downloadReportingRetained(key, null, retry, call, read, base);
        }
        WindowSubject subject = new WindowSubject(
                preflight.session(), preflight.remote().name(), preflight.project().info().name(), now);
        Optional<McpInputResponse> response = call.inputResponse(DownloadWindowQuestion.KEY);
        if (response.isEmpty()) {
            LOG.info("Asking which part of a large remote session to download: session_id={} size_bytes={}",
                    ref.sessionId(), preflight.session().totalSizeBytes());
            return windowQuestion.ask(subject);
        }
        return switch (windowQuestion.read(response.get(), subject)) {
            case WindowAnswer.Chosen chosen -> downloadResolved(ref,
                    chosen.window().resolve(chosen.given(), () -> subject), retry, call, read, base,
                    Optional.of(subject));
            case WindowAnswer.NotAnswered notAnswered -> answer(key, null, DownloadFacts.notDownloaded(
                    notAnswered.reason(), preflight), null, base);
            case WindowAnswer.Incomplete incomplete -> windowQuestion.ask(subject, incomplete.problem());
        };
    }

    /**
     * The part a window resolved to, downloaded — the same for a window the call named and one the
     * user chose in the form. What cannot be taken is refused to a call and asked again of a user.
     *
     * @param askAgain the session the question was asked about, when the window came from the form
     */
    private McpToolOutcome downloadResolved(RemoteSessionRef ref, WindowResolution resolution, Boolean retry,
                                            McpCallContext call, Optional<SessionRead> read, String base,
                                            Optional<WindowSubject> askAgain) {
        return switch (resolution) {
            case WindowResolution.Span span -> downloadPart(DownloadKey.of(ref, epochMillis(span.window().start()),
                    epochMillis(span.window().end()), null), span.chosen(), retry, call, read, base, askAgain);
            case WindowResolution.OneChunk one -> downloadPart(DownloadKey.of(ref, null, null, List.of(one.fileId())),
                    one.chosen(), retry, call, read, base, askAgain);
            case WindowResolution.Whole whole ->
                    downloadReportingRetained(DownloadKey.of(ref, null, null, null), whole.chosen(), retry, call,
                            read, base);
            case WindowResolution.NotRetained notRetained -> askAgain
                    .<McpToolOutcome>map(subject -> windowQuestion.ask(subject, notRetained.reason()))
                    .orElseGet(() -> answer(DownloadKey.of(ref, null, null, null), notRetained.chosen(),
                            DownloadFacts.notRetained(notRetained.reason(), read.orElseThrow().preflight()),
                            null, base));
            case WindowResolution.Refused refused -> askAgain
                    .<McpToolOutcome>map(subject -> windowQuestion.ask(subject, refused.problem()))
                    .orElseThrow(() -> new IllegalArgumentException(refused.problem()));
        };
    }

    private static Long epochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /**
     * A part of the session, unless the user chose a window no chunk covers — then the
     * question is put again, saying so, rather than the transfer failing on an empty selection. A
     * call's window that covers nothing is refused by the selection itself.
     */
    private McpToolOutcome downloadPart(DownloadKey key, ChosenWindow chosen, Boolean retry,
                                        McpCallContext call, Optional<SessionRead> read, String base,
                                        Optional<WindowSubject> askAgain) {
        if (askAgain.isPresent() && key.coversNothing(askAgain.get().session())) {
            return windowQuestion.ask(askAgain.get(),
                    NO_CHUNK.formatted(key.window().start(), key.window().end()));
        }
        return downloadReportingRetained(key, chosen, retry, call, read, base);
    }

    /**
     * The session read once for a window that needs it — its chunks, its span — under the call's
     * whole response budget, and kept so the transfer that follows starts from the same read.
     */
    private final class SessionReading {

        private final RemoteSessionRef ref;
        private SessionRead read;
        private WindowSubject subject;

        private SessionReading(RemoteSessionRef ref) {
            this.ref = ref;
        }

        WindowSubject subject() {
            if (subject == null) {
                McpDeadline responseDeadline = McpDeadline.after(downloadResponseBudget);
                DownloadPreflight preflight = preflight(ref);
                read = new SessionRead(responseDeadline, preflight);
                subject = new WindowSubject(preflight.session(), preflight.remote().name(),
                        preflight.project().info().name(), clock.instant());
            }
            return subject;
        }

        Optional<SessionRead> read() {
            return Optional.ofNullable(read);
        }
    }

    /**
     * Whether this call may be asked which part of the session to bring: only a client that renders
     * forms, only for the whole session, and only when there is nothing to answer from already — no
     * attempt under that key, running or retained, and no whole copy here. A large session is judged
     * after the preflight has read it.
     */
    private boolean mayAskForWindow(DownloadKey key, McpCallContext call) {
        return call.canElicitForm()
                && key.wholeSession()
                && downloads.current(key).isEmpty()
                && DownloadedSessionIndex.build(recordingsManager, locator.currentAddress()).find(key.ref())
                        .filter(DownloadedSessionIndex.LocalCopy::complete)
                        .isEmpty();
    }

    /**
     * The download, with a retained failure of this very attempt turned into its status rather than
     * thrown again.
     *
     * @param read the session as the window question already read it, if it did
     * @param base the link base, read on the request thread
     */
    private McpToolOutcome downloadReportingRetained(DownloadKey key, ChosenWindow chosen, Boolean retry,
                                                     McpCallContext call, Optional<SessionRead> read, String base) {
        Optional<OperationHandle<String>> before = downloads.current(key);
        String priorId = before.map(OperationHandle::operationId).orElse(null);
        boolean priorTerminal = before.map(handle -> handle.snapshot().state().terminal()).orElse(false);
        try {
            return download(key, chosen, retry, call, read, base);
        } catch (RuntimeException failure) {
            Optional<OperationHandle<String>> attempt = downloads.current(key);
            if (attempt.isPresent()) {
                OperationHandle<String> current = attempt.get();
                OperationState state = current.snapshot().state();
                boolean reportsThisAttempt = !Boolean.TRUE.equals(retry) || !priorTerminal
                        || !current.operationId().equals(priorId);
                if (reportsThisAttempt && (state == OperationState.FAILED || state == OperationState.CANCELLED)) {
                    String operationId = registerDownload(key, chosen, current, null, base);
                    return answer(key, chosen, DownloadFacts.ended(state, failureMessage(current)), operationId, base);
                }
            }
            // A rejected retry preflight did not create an attempt. Its own error must not be
            // replaced by the previous attempt's retained outcome.
            throw failure;
        }
    }

    private static String failureMessage(OperationHandle<String> attempt) {
        Throwable failure = attempt.snapshot().failure();
        return failure == null ? null : failure.getMessage();
    }

    /**
     * The transfer itself; {@link #downloadReportingRetained} turns a retained failure into a status.
     *
     * @param read the session as the window question already read it, reused rather than read twice;
     *             empty to read it here
     * @param base the link base, read on the request thread
     */
    private McpToolOutcome download(DownloadKey key, ChosenWindow chosen, Boolean retry,
                                    McpCallContext call, Optional<SessionRead> read, String base) {
        RemoteSessionRef ref = key.ref();
        boolean retryFailed = Boolean.TRUE.equals(retry);

        // A window is a recording of its own: two windows of one session are two recordings, and
        // neither is the session, so "already here" is an answer only the whole session gets. A
        // window is still told about a whole-session copy that is here, further down, because the
        // reader may not need a second recording at all.
        Optional<DownloadedSessionIndex.LocalCopy> alreadyHere =
                DownloadedSessionIndex.build(recordingsManager, locator.currentAddress()).find(ref);
        // Only a copy of a session a newer one has ended is the session: one of an instance's newest
        // session stops where it stood then, and is downloaded again rather than handed back.
        if (key.wholeSession() && alreadyHere.filter(DownloadedSessionIndex.LocalCopy::complete).isPresent()) {
            DownloadedSessionIndex.LocalCopy copy = alreadyHere.get();
            LOG.debug("Remote session was already downloaded: session_id={} recording_id={}",
                    ref.sessionId(), copy.recordingId());
            OperationHandle<String> operation = downloads.rememberCompleted(key, copy.recordingId());
            DownloadFacts facts = DownloadFacts.here(copy.recordingId(), copy.profileId());
            String operationId = registerDownload(key, chosen, operation, recordingId -> facts, base);
            return answer(key, chosen, facts, operationId, base);
        }

        Optional<BoundedJobs.Outcome<String>> prior = downloads.outcome(key);
        if (prior.isPresent() && prior.get().failure() != null && !retryFailed) {
            throw prior.get().failure();
        }

        // The preflight has the whole response budget, whatever the client declared: only the wait
        // on the transfer below is shortened for a client that can follow a task.
        McpDeadline responseDeadline = read.map(SessionRead::responseDeadline)
                .orElseGet(() -> McpDeadline.after(downloadResponseBudget));
        DownloadPreflight preflight = read.map(SessionRead::preflight)
                .orElseGet(() -> preflight(ref));
        ProjectManager project = preflight.project();
        RecordingSession session = preflight.session();
        ChunkWindow.Selection selection = key.select(session);
        Landing landing = new Landing(key, preflight, selection, alreadyHere);

        // Handing back the recording an identical call already made, unless the answer may have moved
        // on since. A window with no end means "up to the last chunk" on an instance's newest
        // session, which keeps adding chunks: the retained recording stops where the session stood
        // an hour ago. A part holding that session's newest recording may have been copied during
        // the profiler's dump. Everything else is settled and is answered from what is here.
        if (prior.isPresent() && !key.mayHaveChanged(session)) {
            String recordingId = prior.get().value();
            if (recordingId != null && recordingsManager.findRecording(recordingId).isPresent()) {
                String operationId = registerDownload(key, chosen, downloads.current(key).orElseThrow(), landing::landed, base);
                return answer(key, chosen, landing.landed(recordingId), operationId, base);
            }
        }

        LOG.info("Downloading a remote session over MCP: project_id={} session_id={} window={} file_ids={}",
                ref.projectId(), ref.sessionId(), key.window(), key.fileIds());
        // A recording an identical call already made is handed back instead of fetched again --
        // unless what was asked for may have changed since, in which case the retained one may answer
        // a shorter question than the one being put. A transfer still in flight is joined either way.
        boolean stillGrowing = key.mayHaveChanged(session);
        long transferBytes = selection == null
                ? session.totalSizeBytes()
                : selection.files().stream().mapToLong(RepositoryFile::size).sum();
        OperationHandle<String> operation = downloads.startOrJoin(key, retryFailed,
                recordingId -> !stillGrowing && recordingsManager.findRecording(recordingId).isPresent(),
                control -> {
                    control.phase(OperationPhase.DOWNLOADING);
                    control.progress(OperationDetails.remoteDownload(ref.encode(), ref.sessionId(), transferBytes));
                    return transfer(project, key, control);
                });
        String operationId = registerDownload(key, chosen, operation, landing::landed, base);
        Optional<String> transferred = downloads.awaitWithin(operation, responseDeadline.remainingWithin(
                answers.waitBudget(call, downloadResponseBudget), RESPONSE_DEADLINE_ELAPSED));
        if (transferred.isEmpty()) {
            // Nothing to poll but this tool and the operation: the tool answers from the local store
            // first, so calling it again with the same part reports the finished copy once it lands.
            return answers.stillRunning(call, operationId,
                    () -> answer(key, chosen, DownloadFacts.running(preflight, transferBytes), operationId, base));
        }
        return answer(key, chosen, landing.landed(transferred.get()), operationId, base);
    }

    /**
     * Registers the transfer with the call that retries it and, when it can finish, the answer a task
     * following it completes with: the answer a caller that waited for it is given.
     *
     * @param landed what the finished transfer reports, or {@code null} for an attempt already ended
     *               without a recording
     * @return the operation id
     */
    private String registerDownload(DownloadKey key, ChosenWindow chosen, OperationHandle<String> operation,
                                    Function<String, DownloadFacts> landed, String base) {
        String operationId = operation.operationId();
        Function<String, McpToolResult> finished = landed == null
                ? recordingId -> answer(key, chosen, DownloadFacts.here(recordingId, null), operationId, base)
                : recordingId -> answer(key, chosen, landed.apply(recordingId), operationId, base);
        return operations.register(OperationKind.REMOTE_DOWNLOAD, operation, downloadResult(key), finished,
                key.retryCall());
    }

    private static Function<String, DownloadResult> downloadResult(DownloadKey key) {
        String sessionRef = key.ref().encode();
        return recordingId -> new DownloadResult(recordingId, sessionRef);
    }

    /** What a finished download produced, as operations_status reports it. */
    private record DownloadResult(String recordingId, String sessionRef) {
    }

    /**
     * The one answer every state of a download gives: the facts, the part asked for, the transfer
     * behind them, what to call next and the page for the user.
     *
     * @param operationId the transfer this answer reports, or null when there is none
     * @param base        the link base, read on the request thread; see {@link UiLinks#base()}
     */
    private McpToolResult answer(DownloadKey key, ChosenWindow chosen, DownloadFacts facts,
                                 String operationId, String base) {
        McpOperationRegistry.Snapshot operation = operationId == null ? null : operations.status(operationId);
        LandedBundle bundle = LandedBundle.of(recordingsManager, facts.recordingId());
        String uiLink = facts.profileId() != null
                ? UiLinks.profile(base, facts.profileId())
                : facts.recordingId() != null
                ? UiLinks.page(base, MicroscopePage.RECORDINGS)
                : UiLinks.page(base, MicroscopePage.REMOTE);
        return McpToolResult.of(new Download(facts.status(), facts.reason(), key.ref().encode(), key.ref().sessionId(),
                facts.sessionName(), facts.project(), key.startEpochMs(), key.endEpochMs(),
                key.fileIds() == null ? List.of() : key.fileIds(), facts.recordingId(), facts.profileId(),
                bundle.bundleId(), bundle.items(),
                facts.recordingFiles(), facts.artifactFiles(), facts.sizeBytes(), facts.coveredStartEpochMs(),
                facts.coveredEndEpochMs(), chosen, operationId, operation, followUp(key, facts, bundle, operationId),
                uiLink));
    }

    private McpFollowUp followUp(DownloadKey key, DownloadFacts facts, LandedBundle bundle, String operationId) {
        NextSteps.Builder next = NextSteps.builder(advertised);
        return switch (facts.status()) {
            case DOWNLOADED -> next
                    .nextWhen(facts.profileId() != null, () -> RemoteCalls.summary(facts.profileId()))
                    .nextWhen(facts.profileId() == null, RemoteCalls.analyse(facts.recordingId()))
                    .guidanceWhen(!key.wholeSession(), PART_GUIDANCE)
                    .guidance(facts.wholeCopyNote())
                    .guidanceWhen(!bundle.heapDumpIds().isEmpty(),
                            () -> HEAP_ITEMS_GUIDANCE.formatted(String.join(", ", bundle.heapDumpIds())))
                    .followUp();
            case RUNNING -> next
                    .next(RemoteCalls.poll(operationId))
                    .next(key.sameCall(JOIN_WHY))
                    .followUp();
            case NOT_DOWNLOADED -> next
                    .next(RemoteCalls.onSession(RemoteCalls.REMOTE_FILES, key.ref().encode()).why(CHOOSE_FILES_WHY))
                    .guidance(NOT_DOWNLOADED_GUIDANCE)
                    .followUp();
            case FAILED, CANCELLED -> next
                    .next(key.retryCall())
                    .followUp();
            case STARTUP_NOT_RETAINED -> next
                    .next(RemoteCalls.onSession(RemoteCalls.REMOTE_FILES, key.ref().encode()).why(CHOOSE_FILES_WHY))
                    .guidance(NOT_RETAINED_GUIDANCE)
                    .followUp();
        };
    }

    private RecordingSessionFilter sessionFilter(Integer withinLastMinutes) {
        RecordingSessionFilter filter = RecordingSessionFilter.ALL;
        if (withinLastMinutes != null) {
            if (withinLastMinutes < 1) {
                throw new IllegalArgumentException(
                        "withinLastMinutes must be at least 1 minute: " + withinLastMinutes);
            }
            filter = RecordingSessionFilter.activeWithinLast(
                    Duration.ofMinutes(withinLastMinutes), clock.instant());
        }
        return filter;
    }

    private static String emptyReason(Integer withinLastMinutes) {
        if (withinLastMinutes == null) {
            return NO_SESSIONS;
        }
        return NO_SESSIONS_WIDEN.formatted(withinLastMinutes);
    }

    /**
     * What could not be read, under the table rather than instead of it. Rendered even when nothing
     * came back: a bare "no sessions" while production is down is the one answer that would mislead
     * a reader into thinking their recordings are gone.
     */
    private static String footer(RemoteSessionScan.Result result) {
        StringBuilder footer = new StringBuilder(256).append(FOOTER_LOCAL);
        if (!result.complete()) {
            footer.append('\n');
            for (RemoteSessionScan.Failure failure : result.failures()) {
                footer.append("Not listed: ").append(failure.scope())
                        .append(" - ").append(failure.reason()).append('\n');
            }
            footer.append("Nothing from there appears above. Check the remote with remote_status, "
                    + "or ask the user whether it should be up.\n");
        }
        return footer.toString();
    }

    private static String localColumn(Optional<DownloadedSessionIndex.LocalCopy> copy) {
        return copy.map(local -> local.analysed()
                        ? "profile:" + local.profileId()
                        : "recording:" + local.recordingId())
                .orElse("");
    }

    /**
     * Reads the session before pulling it, so a ref that has gone stale and a session with nothing
     * to download both fail in a sentence rather than partway through a multi-gigabyte transfer.
     */
    private RecordingSession preflight(ProjectManager project, RemoteSessionRef ref, RemoteInfo remote) {
        RecordingSession session = locator.session(project, ref, remote);
        if (session.recordings().isEmpty()) {
            throw new IllegalArgumentException(
                    "Session " + ref.sessionId() + " has no recording file to download"
                            + (session.newest()
                            ? ", because its profiler has not finished its first chunk yet."
                            : "."));
        }
        return session;
    }

    private DownloadPreflight preflight(RemoteSessionRef ref) {
        RemoteInfo remote = locator.remoteInfo(ref);
        ProjectManager project = locator.project(ref);
        return new DownloadPreflight(remote, project, preflight(project, ref, remote));
    }

    private static String transfer(ProjectManager project, DownloadKey key, BoundedJobs.JobControl control) {
        control.checkCancellation();
        return key.transfer(project.recordingsDownloadManager());
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
    }

    /**
     * The session's span in milliseconds, from its start to the end of its recorded data, or null
     * while it has no recording or never started — zero would claim a span it does not have.
     */
    private static Long durationMs(RecordingSession session) {
        if (session.createdAt() == null || session.lastDataAt() == null) {
            return null;
        }
        return Duration.between(session.createdAt(), session.lastDataAt()).toMillis();
    }

    /**
     * How long the JVM recorded, for the table: to the end of its recorded data. A session with no
     * recording yet has nothing to measure, which is a fact about the row rather than a gap in it.
     */
    private static String duration(RecordingSession session) {
        Instant start = session.createdAt();
        if (start == null) {
            return "";
        }
        Instant end = session.lastDataAt();
        if (end == null) {
            return "no data yet";
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

    private record DownloadPreflight(
            RemoteInfo remote, ProjectManager project, RecordingSession session) {
    }

    /**
     * The session as the window question read it, and the response deadline that read ran under, so
     * the transfer that follows starts from the same read within the same budget.
     */
    private record SessionRead(McpDeadline responseDeadline, DownloadPreflight preflight) {
    }

    /**
     * The bundle a landed download is in, as the answer lists it: the session's recording first, then
     * the other items. A copy made before downloads were bundled is its recording alone.
     */
    private record LandedBundle(String bundleId, List<BundleItem> items) {

        private static final LandedBundle NONE = new LandedBundle(null, List.of());

        static LandedBundle of(RecordingsManager recordings, String recordingId) {
            if (recordingId == null) {
                return NONE;
            }
            Optional<Recording> recording = recordings.findRecording(recordingId);
            if (recording.isEmpty()) {
                return NONE;
            }
            if (!recording.get().inBundle()) {
                return new LandedBundle(null, List.of(item(recording.get())));
            }
            String bundleId = recording.get().bundleId();
            List<BundleItem> items = recordings.findBundleMembers(bundleId).stream()
                    .sorted(Comparator.comparing((Recording member) -> !member.id().equals(recordingId))
                            .thenComparing(Recording::recordingName))
                    .map(LandedBundle::item)
                    .toList();
            return new LandedBundle(bundleId, items);
        }

        private static BundleItem item(Recording recording) {
            return new BundleItem(recording.id(), recording.eventSource(), recording.recordingName(),
                    recording.hasProfile() ? recording.profileId() : null);
        }

        List<String> heapDumpIds() {
            return items.stream()
                    .filter(item -> item.eventSource().kind() == ProfileKind.HEAP_DUMP)
                    .map(BundleItem::recordingId)
                    .toList();
        }
    }

    /**
     * What one state of a download reports, before the part asked for, the transfer, the next calls
     * and the link are added to it. A count this answer did not make is null, never a zero.
     *
     * @param wholeCopyNote what is said on a part when the whole session is here too, or blank
     */
    private record DownloadFacts(
            DownloadStatus status,
            String reason,
            String sessionName,
            String project,
            String recordingId,
            String profileId,
            Integer recordingFiles,
            Integer artifactFiles,
            Long sizeBytes,
            Long coveredStartEpochMs,
            Long coveredEndEpochMs,
            String wholeCopyNote) {

        /** A recording already here: what the local store knows of it, and nothing it would have to guess. */
        static DownloadFacts here(String recordingId, String profileId) {
            return new DownloadFacts(DownloadStatus.DOWNLOADED, null, null, null, recordingId, profileId,
                    null, null, null, null, null, "");
        }

        static DownloadFacts running(DownloadPreflight preflight, long transferBytes) {
            return new DownloadFacts(DownloadStatus.RUNNING, RUNNING_REASON, preflight.session().name(),
                    preflight.project().info().name(), null, null, null, null,
                    transferBytes, null, null, "");
        }

        static DownloadFacts notDownloaded(String reason, DownloadPreflight preflight) {
            return new DownloadFacts(DownloadStatus.NOT_DOWNLOADED, reason, preflight.session().name(),
                    preflight.project().info().name(), null, null, null, null, null,
                    null, null, "");
        }

        /** STARTUP asked for on a session whose first chunk the remote no longer holds; nothing moved. */
        static DownloadFacts notRetained(String reason, DownloadPreflight preflight) {
            return new DownloadFacts(DownloadStatus.STARTUP_NOT_RETAINED, reason, preflight.session().name(),
                    preflight.project().info().name(), null, null, null, null, null,
                    null, null, "");
        }

        /** An attempt that ended without a recording, retained and reported rather than started again. */
        static DownloadFacts ended(OperationState state, String message) {
            return new DownloadFacts(state == OperationState.CANCELLED ? DownloadStatus.CANCELLED : DownloadStatus.FAILED,
                    message, null, null, null, null, null, null, null, null, null, "");
        }
    }

    /**
     * What a finished transfer is reported with, gathered before it starts so that the call that waited
     * for it and a task that followed it render the same answer.
     *
     * @param selection the chunks of a window or of named files; {@code null} for the whole session
     * @param wholeCopy the whole session, when it is already here beside a part being downloaded
     */
    private record Landing(
            DownloadKey key,
            DownloadPreflight preflight,
            ChunkWindow.Selection selection,
            Optional<DownloadedSessionIndex.LocalCopy> wholeCopy) {

        DownloadFacts landed(String recordingId) {
            RecordingSession session = preflight.session();
            List<RepositoryFile> files = session.files();
            String sessionName = session.name();
            String project = preflight.project().info().name();
            if (selection != null) {
                List<RepositoryFile> others = key.others(files);
                return new DownloadFacts(DownloadStatus.DOWNLOADED, null, sessionName, project, recordingId, null,
                        selection.files().size(),
                        others.size(),
                        selection.files().stream().mapToLong(RepositoryFile::size).sum()
                                + others.stream().mapToLong(RepositoryFile::size).sum(),
                        epochMs(selection.coverageStart()),
                        epochMs(selection.coverageEnd()),
                        wholeCopyNote());
            }
            return new DownloadFacts(DownloadStatus.DOWNLOADED, null, sessionName, project, recordingId, null,
                    (int) files.stream().filter(RepositoryFile::isRecordingFile).count(),
                    (int) files.stream().filter(RepositoryFiles::isArtifact).count(),
                    session.totalSizeBytes(), null, null, "");
        }

        /**
         * Said on a part download when the whole session is here too: a reader who asked for an hour
         * of it may well be able to use what is already analysed instead of keeping a second recording.
         */
        private String wholeCopyNote() {
            if (wholeCopy.isEmpty()) {
                return "";
            }
            DownloadedSessionIndex.LocalCopy copy = wholeCopy.get();
            return copy.analysed()
                    ? WHOLE_ANALYSED_GUIDANCE.formatted(copy.profileId())
                    : WHOLE_RECORDING_GUIDANCE.formatted(copy.recordingId());
        }

        private static Long epochMs(Instant instant) {
            return instant == null ? null : instant.toEpochMilli();
        }
    }

    /**
     * What one download is: a session and the part of it — a window, files named by id, or neither
     * for all of it. The jobs are keyed on this rather than on the session alone so that a part
     * neither joins a running whole-session transfer nor is short-circuited by one that finished.
     *
     * @param window  the span asked for, or {@code null}
     * @param fileIds the files asked for by id, or {@code null}; never set together with the window
     */
    private record DownloadKey(RemoteSessionRef ref, ChunkWindow window, List<String> fileIds) {

        static DownloadKey of(RemoteSessionRef ref, Long startEpochMs, Long endEpochMs, List<String> fileIds) {
            boolean windowed = startEpochMs != null || endEpochMs != null;
            boolean named = fileIds != null && !fileIds.isEmpty();
            if (windowed && named) {
                throw new IllegalArgumentException(WINDOW_AND_FILE_IDS);
            }
            return new DownloadKey(ref,
                    windowed ? ChunkWindow.ofEpochMillis(startEpochMs, endEpochMs) : null,
                    named ? List.copyOf(new TreeSet<>(fileIds)) : null);
        }

        boolean wholeSession() {
            return window == null && fileIds == null;
        }

        Long startEpochMs() {
            return window == null || window.start() == null ? null : window.start().toEpochMilli();
        }

        Long endEpochMs() {
            return window == null || window.end() == null ? null : window.end().toEpochMilli();
        }

        /**
         * The call that names this download again: it builds an equal key, so it joins the attempt in
         * flight or returns the recording it made. For a window the user chose when asked, its exact
         * bounds, since a whole-session call would put the question again rather than find the transfer;
         * for the whole session, window WHOLE, for the same reason.
         */
        McpNextTool sameCall(String why) {
            return arguments().why(why);
        }

        /** The same call with retry=true, which starts a new attempt once this one failed or was cancelled. */
        McpNextTool retryCall() {
            return arguments().with(RemoteCalls.RETRY, true).why(RETRY_WHY);
        }

        private McpNextTool.Call arguments() {
            McpNextTool.Call call = RemoteCalls.onSession(RemoteCalls.REMOTE_DOWNLOAD, ref.encode());
            Long start = startEpochMs();
            Long end = endEpochMs();
            if (start != null) {
                call.with(RemoteCalls.START_EPOCH_MS, start.longValue());
            }
            if (end != null) {
                call.with(RemoteCalls.END_EPOCH_MS, end.longValue());
            }
            if (wholeSession()) {
                // The whole session is already decided, so naming it again never asks which part.
                call.with(RemoteCalls.WINDOW, DownloadWindow.WHOLE);
            }
            return call.with(RemoteCalls.FILE_IDS, fileIds);
        }

        /** Whether this is a window no chunk of the session covers. */
        boolean coversNothing(RecordingSession session) {
            return window != null && window.select(session).isEmpty();
        }

        /**
         * Whether an answer retained from an earlier call may be short of this question. An open-ended
         * window on an instance's newest session covers more of it with every chunk that lands; and a
         * part holding that session's newest recording may have been taken while the profiler was
         * still writing the file into place, which the arrival check cannot always tell from a whole
         * one. Either is fetched again rather than handed back.
         */
        boolean mayHaveChanged(RecordingSession session) {
            if (!session.newest()) {
                return false;
            }
            if (window != null && window.end() == null) {
                return true;
            }
            return session.openRecording()
                    .filter(newestFile -> takes(session, newestFile))
                    .isPresent();
        }

        /** Whether this part brings {@code file} of the session. */
        private boolean takes(RecordingSession session, RepositoryFile file) {
            if (window != null) {
                return window.select(session).files().contains(file);
            }
            if (fileIds != null) {
                return fileIds.contains(file.id());
            }
            return true;
        }

        /** The non-chunk files a part brings: those named by id; a window brings none. */
        List<RepositoryFile> others(List<RepositoryFile> files) {
            if (fileIds == null) {
                return List.of();
            }
            return files.stream()
                    .filter(file -> fileIds.contains(file.id()))
                    .filter(RepositoryFiles::isArtifact)
                    .toList();
        }

        String transfer(RecordingsDownloadManager manager) {
            if (window != null) {
                return manager.downloadWindow(ref.sessionId(), window);
            }
            if (fileIds != null) {
                return manager.downloadRecordings(ref.sessionId(), fileIds);
            }
            return manager.downloadSession(ref.sessionId());
        }

        /**
         * The chunks the part takes from the session as read at preflight, so a window nothing
         * covers and an id the session does not hold both fail in a sentence, before the transfer
         * starts. A chunk may equally be absent from a whole-session download; that is preflight's
         * own check.
         *
         * @return the selection, or {@code null} for the whole session
         */
        ChunkWindow.Selection select(RecordingSession session) {
            if (wholeSession()) {
                return null;
            }
            List<RepositoryFile> files = session.files();
            if (window != null) {
                ChunkWindow.Selection selection = window.select(session);
                if (selection.isEmpty()) {
                    throw new IllegalArgumentException("No chunk of session " + ref.sessionId()
                            + " covers the window: the session started at " + session.createdAt()
                            + (session.lastDataAt() == null ? " and has no recorded data yet"
                                    : " and its recorded data ends at " + session.lastDataAt())
                            + ". Choose a window inside that span, as startEpochMs/endEpochMs in UTC epoch milliseconds.");
                }
                return selection;
            }
            Set<String> known = files.stream()
                    .map(RepositoryFile::id)
                    .collect(Collectors.toSet());
            List<String> unknown = fileIds.stream().filter(id -> !known.contains(id)).toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Session " + ref.sessionId() + " has no file "
                        + unknown + ". Take fileId values from remote_files.");
            }
            ChunkWindow.Selection selection = ChunkWindow.ofFiles(session, Set.copyOf(fileIds));
            if (selection.isEmpty()) {
                throw new IllegalArgumentException("None of the fileIds is a JFR chunk of session " + ref.sessionId()
                        + ": a recording needs at least one. A log or a heap dump on its own is what remote_fetchFile is for.");
            }
            // The chunks become one recording reporting one span, so a skipped one leaves no trace in the
            // result: the profile would claim a span it only partly holds, and every rate read off
            // it would be wrong by the size of the hole. Refused here and again in
            // RemoteRecordingsDownloadManager; the remote serves one file per call and never sees a
            // selection to judge.
            if (!selection.contiguous()) {
                throw new IllegalArgumentException("The chunks named for session " + ref.sessionId()
                        + " are not next to each other: " + selection.describeGap(session)
                        + " lies between them. remote_download makes them one recording reporting one span, so they have "
                        + "to be an unbroken run. Name the chunks in between as well, or ask for the span with "
                        + "startEpochMs and endEpochMs and let the window pick them.");
            }
            return selection;
        }
    }
}

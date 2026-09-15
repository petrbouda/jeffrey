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

import cafe.jeffrey.hub.client.GrpcClientErrors;
import cafe.jeffrey.microscope.core.manager.hub.HubManager;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.DownloadedSessionIndex;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubScanFilter;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionLocator;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionRef;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionCursor;
import cafe.jeffrey.microscope.core.mcp.tools.hubs.HubSessionScan;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.profile.mcp.McpToolHints;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.operation.OperationState;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolResult;
import cafe.jeffrey.profile.mcp.ToolExecutionException;
import cafe.jeffrey.profile.mcp.McpOutputSchema;
import cafe.jeffrey.shared.common.Json;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.ArrayNode;
import cafe.jeffrey.shared.common.model.hub.HubInfo;
import cafe.jeffrey.recordings.core.RecordingsDownloadManager;
import cafe.jeffrey.shared.common.model.repository.ChunkWindow;
import cafe.jeffrey.shared.common.model.repository.RecordingSession;
import cafe.jeffrey.shared.common.model.repository.RecordingSessionFilter;
import cafe.jeffrey.shared.common.model.repository.RecordingStatus;
import cafe.jeffrey.shared.common.model.repository.RepositoryFile;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

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

    private static final String DOWNLOAD_PREFLIGHT_FAILED = "Hub download preflight failed: ";
    private static final String DOWNLOAD_FAILED = "Hub download failed: ";
    private static final Logger LOG = LoggerFactory.getLogger(HubsMcpTools.class);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;
    private static final int DISPLAY_CHARS = 256;
    private static final int FAILURE_CHARS = 512;
    private static final int MAX_DISPLAYED_FAILURES = 24;
    private static final String LIVE_PAGING_NOTE =
            "Live view: a cursor continues after the last returned row among observed sessions. "
                    + "Newer insertions require a fresh scan. Missing remote scopes remain incomplete "
                    + "even when hasMore=false. Long display names and failure details are shortened; "
                    + "session_ref identities are preserved.";

    /**
     * How long a listing may spend waiting on hubs. Needed because no deadline is set on the hub
     * channels and this runs inside a synchronous tool call, so a hub that neither answers nor
     * refuses would otherwise hang the caller for good.
     */
    private static final Duration SCAN_BUDGET = Duration.ofSeconds(20);
    private static final Duration DOWNLOAD_RESPONSE_BUDGET = BoundedJobs.WAIT_BUDGET;
    private static final Duration DOWNLOAD_DEADLINE = Duration.ofHours(1);

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
    private final Duration downloadResponseBudget;
    private final Duration downloadDeadline;

    /**
     * One transfer per session at a time, and no call waits longer than a client will.
     */
    private final BoundedJobs<DownloadKey, String> downloads;
    private final HubSessionScan scan;
    private final HubSessionLocator locator;
    private final McpOperationRegistry operations;

    public HubsMcpTools(
            HubsManager hubsManager,
            ProjectManagerResolver resolver,
            RecordingsManager recordingsManager,
            Clock clock) {

        this(
                hubsManager,
                resolver,
                recordingsManager,
                clock,
                SCAN_BUDGET,
                DOWNLOAD_RESPONSE_BUDGET,
                DOWNLOAD_DEADLINE);
    }

    public HubsMcpTools(
            HubsManager hubsManager,
            ProjectManagerResolver resolver,
            RecordingsManager recordingsManager,
            Clock clock,
            Duration scanBudget,
            Duration downloadResponseBudget,
            Duration downloadDeadline) {

        this(hubsManager, resolver, recordingsManager, clock, scanBudget,
                downloadResponseBudget, downloadDeadline, new McpOperationRegistry(clock));
    }

    public HubsMcpTools(HubsManager hubsManager, ProjectManagerResolver resolver,
            RecordingsManager recordingsManager, Clock clock, McpOperationRegistry operations) {
        this(hubsManager, resolver, recordingsManager, clock, SCAN_BUDGET,
                DOWNLOAD_RESPONSE_BUDGET, DOWNLOAD_DEADLINE, operations);
    }

    public HubsMcpTools(HubsManager hubsManager, ProjectManagerResolver resolver,
            RecordingsManager recordingsManager, Clock clock, Duration scanBudget,
            Duration downloadResponseBudget, Duration downloadDeadline, McpOperationRegistry operations) {
        this.operations = operations;
        this.hubsManager = hubsManager;
        this.resolver = resolver;
        this.recordingsManager = recordingsManager;
        this.clock = clock;
        this.downloadResponseBudget = requirePositive(downloadResponseBudget, "downloadResponseBudget");
        this.downloadDeadline = requirePositive(downloadDeadline, "downloadDeadline");
        this.downloads = new BoundedJobs<>(downloadResponseBudget, BoundedJobs.COMPLETED_RETENTION, clock);
        this.scan = new HubSessionScan(hubsManager, scanBudget);
        this.locator = new HubSessionLocator(resolver);
    }

    @Tool(description = "Every Jeffrey Hub this installation is connected to, and whether it answers "
            + "right now. Call it when hubs_sessions came back empty for a hub you expected to see, "
            + "or to learn the hub names the `hub` filter of hubs_sessions accepts.")
    public String list() {
        List<HubManager> hubs = hubsManager.findAll();
        if (hubs.isEmpty()) {
            return NO_HUBS;
        }

        Map<String, Optional<String>> versions = scan.probeVersions(hubs);

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
            + "without downloading it again. Follow nextCursor with the same filters for more rows. "
            + "This is a live view: complete describes whether all remote scopes answered, independently "
            + "of hasMore. A relative time window keeps its original cutoff across pages.")
    @McpOutputSchema("""
            {"type":"object","properties":{
              "sessions":{"type":"array","items":{"type":"object","properties":{
                "hub":{"type":"string"},"workspace":{"type":"string"},"project":{"type":"string"},
                "started":{"type":["string","null"]},"duration":{"type":"string"},
                "status":{"type":["string","null"]},"files":{"type":"integer"},
                "size":{"type":"string"},"local":{"type":"string"},"session_ref":{"type":"string"}
              },"required":["hub","workspace","project","started","duration","status","files","size","local","session_ref"]}},
              "returned":{"type":"integer"},"total":{"type":["integer","null"]},
              "observedTotal":{"type":"integer"},"hasMore":{"type":"boolean"},
              "nextCursor":{"type":["string","null"]},"complete":{"type":"boolean"},
              "failures":{"type":"array","items":{"type":"object","properties":{
                "hubName":{"type":"string"},"scope":{"type":"string"},
                "kind":{"type":"string","description":"UNREACHABLE, DEADLINE_EXCEEDED, CAPACITY_EXHAUSTED or OTHER"},
                "reason":{"type":"string"}
              },"required":["hubName","scope","kind","reason"]}}
            },"required":["sessions","returned","total","observedTotal","hasMore","nextCursor","complete","failures"]}
            """)
    public McpToolResult sessions(
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
            RecordingStatus status,
            @ToolParam(required = false, description = "Most rows to return across all hubs. Default 50, maximum 500")
            Integer limit,
            @ToolParam(required = false, description = "Opaque nextCursor from the previous page. Keep all "
                    + "filters unchanged; limit may change. Omit to start a fresh live scan")
            String cursor) {

        int rowLimit = ToolArguments.boundedLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
        HubScanFilter requested = new HubScanFilter(
                hub, workspace, project, sessionFilter(withinLastMinutes, status));
        String fingerprint = HubSessionCursor.fingerprint(requested, withinLastMinutes);
        HubSessionCursor continuation = cursor == null ? null
                : HubSessionCursor.decode(cursor, fingerprint, withinLastMinutes != null);
        HubScanFilter filter = continuation == null ? requested : requested.withSessions(
                new RecordingSessionFilter(continuation.activeFrom(), null, status, RecordingSessionFilter.NO_LIMIT));

        HubSessionScan.Result scanned = scan.scan(filter);
        List<HubSessionScan.Row> remaining = scanned.rows().stream()
                .filter(row -> continuation == null || row.key().compareTo(continuation.after()) > 0)
                .toList();
        SessionPage page = new SessionPage(filter, fingerprint, withinLastMinutes, scanned.rows().size(),
                remaining.size(), boundedFailures(scanned.failures()));
        DownloadedSessionIndex local = DownloadedSessionIndex.build(recordingsManager);
        int selected = Math.min(rowLimit, remaining.size());
        PageCandidate whole = renderPage(page, remaining.subList(0, selected), local);
        if (fits(whole)) {
            return new McpToolResult(whole.text(), whole.structuredContent());
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
        return new McpToolResult(fitting.text(), fitting.structuredContent());
    }

    private static boolean fits(PageCandidate candidate) {
        return candidate.text().length() <= McpToolOutput.MAX_CHARS
                && Json.toString(candidate.structuredContent()).length() <= McpToolOutput.MAX_CHARS;
    }

    /** Java callers retain the original text-only contract; MCP reflects the cursor overload. */
    public String sessions(String hub, String workspace, String project, Integer withinLastMinutes,
                           RecordingStatus status, Integer limit) {
        return sessions(hub, workspace, project, withinLastMinutes, status, limit, null).text();
    }

    private record SessionPage(HubScanFilter filter, String fingerprint, Integer withinLastMinutes,
                               int observedTotal, int remaining, List<HubSessionScan.Failure> failures) {

        boolean complete() {
            return failures.isEmpty();
        }
    }

    private record PageCandidate(String text, ObjectNode structuredContent) {
    }

    private static PageCandidate renderPage(
            SessionPage page, List<HubSessionScan.Row> rows, DownloadedSessionIndex local) {
        boolean hasMore = rows.size() < page.remaining();
        String nextCursor = hasMore && !rows.isEmpty() ? new HubSessionCursor(
                page.fingerprint(), page.filter().sessions().activeFrom(), rows.getLast().key()).encode() : null;
        ObjectNode structured = Json.createObject();
        ArrayNode sessions = structured.putArray("sessions");
        structured.put("returned", rows.size());
        if (page.complete()) {
            structured.put("total", page.observedTotal());
        } else {
            structured.putNull("total");
        }
        structured.put("observedTotal", page.observedTotal());
        structured.put("hasMore", hasMore);
        structured.put("nextCursor", nextCursor);
        structured.put("complete", page.complete());
        structured.set("failures", Json.toTree(page.failures()));
        MarkdownTable table = MarkdownTable.withColumns(
                "hub", "workspace", "project", "started", "duration", "status", "files", "size",
                "local", "session_ref");
        for (HubSessionScan.Row row : rows) {
            RecordingSession session = row.session();
            String hub = bounded(row.hubName(), DISPLAY_CHARS);
            String workspace = bounded(row.workspaceName(), DISPLAY_CHARS);
            String project = bounded(row.projectName(), DISPLAY_CHARS);
            String duration = duration(session);
            String size = ByteSizes.format(session.totalSizeBytes());
            String localCopy = localColumn(local, row.ref());
            String ref = row.ref().encode();
            int files = session.files() == null ? 0 : session.files().size();
            table.row(hub, workspace, project, session.createdAt(), duration, session.status(), files, size,
                    localCopy, ref);
            ObjectNode entry = sessions.addObject();
            entry.put("hub", hub);
            entry.put("workspace", workspace);
            entry.put("project", project);
            entry.put("started", session.createdAt() == null ? null : session.createdAt().toString());
            entry.put("duration", duration);
            entry.put("status", session.status() == null ? null : session.status().name());
            entry.put("files", files);
            entry.put("size", size);
            entry.put("local", localCopy);
            entry.put("session_ref", ref);
        }
        String metadata = "Returned " + rows.size() + " of " + page.observedTotal() + " observed sessions. "
                + "complete=" + page.complete() + "; hasMore=" + hasMore + ". "
                + (nextCursor == null ? "" : "nextCursor: `" + nextCursor + "`\n") + LIVE_PAGING_NOTE;
        String text;
        if (page.observedTotal() == 0 && page.complete()) {
            text = emptyResult(page.withinLastMinutes()) + "\n\n" + metadata;
        } else {
            text = table.note(footer(new HubSessionScan.Result(rows, page.failures())))
                    .note(metadata).renderUncapped();
        }
        return new PageCandidate(text, structured);
    }

    private static List<HubSessionScan.Failure> boundedFailures(List<HubSessionScan.Failure> failures) {
        List<HubSessionScan.Failure> displayed = new ArrayList<>();
        for (HubSessionScan.Failure failure : failures.subList(0, Math.min(failures.size(), MAX_DISPLAYED_FAILURES))) {
            displayed.add(new HubSessionScan.Failure(bounded(failure.hubName(), DISPLAY_CHARS),
                    bounded(failure.scope(), FAILURE_CHARS), failure.kind(), bounded(failure.reason(), FAILURE_CHARS)));
        }
        if (failures.size() > displayed.size()) {
            displayed.add(new HubSessionScan.Failure("", "additional remote scopes", HubSessionScan.Failure.Kind.OTHER,
                    (failures.size() - displayed.size()) + " additional failed scopes omitted; narrow filters for details."));
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
     */
    @McpToolHints(readOnly = false, openWorld = true)
    @Tool(description = "Download a recording session from its hub into this Jeffrey - the whole "
            + "session, or the part of it that matters. Without a selection it brings the session's "
            + "finished recording files, kept as the several files they are, and its artifacts - heap "
            + "dumps, JVM and application logs - with them, as one local recording. A session on a hub can run for days and roll a chunk every few minutes, so the "
            + "part is usually what to pull, chosen one of two ways: startTime and/or endTime name a "
            + "span, and the chunks covering it are brought - every one whose span touches the window, "
            + "so a chunk straddling a bound is included and the recording always covers the whole "
            + "window with some slack at either end; or fileIds name files from hubs_files by id, "
            + "an unbroken run of chunks and any artifact beside them. Either way the chunks become one "
            + "recording and the answer reports the span they actually cover. The started and duration "
            + "columns of hubs_sessions say what span there is to choose from. Takes the "
            + "session_ref from a hubs_sessions row. Returns a recording id: pass it to "
            + "recordings_analyzeRecording to build the profile the analysis tools take, and "
            + "recordings_delete removes it once its question is answered. A small "
            + "transfer completes inside this call; a large one takes longer than a client waits, so "
            + "the answer is a status saying the transfer continues and calling this tool again with "
            + "the same arguments reports it once it lands. A whole session already downloaded is "
            + "returned as it is rather than fetched twice; a part always makes a recording of its "
            + "own. Failed transfer outcomes are retained in memory for "
            + "one hour after completion. During that window, later calls report the failure without "
            + "restarting unless retry=true. Every started transfer includes an operationId; use "
            + "operations_status to poll locally or operations_cancel to request cancellation. "
            + "Fast transfer failures also return their operationId. After expiry or a server restart, this tool can "
            + "start a new transfer even when retry is omitted or false.")
    public String download(
            @ToolParam(required = true, description = "The session_ref from a hubs_sessions row, copied exactly")
            String sessionRef,
            @ToolParam(required = false, description = "Retry a failed transfer while its outcome is retained "
                    + "(one hour after completion, in memory). Omit or false to inspect a retained failure. "
                    + "After expiry or a server restart, this call can start a new transfer regardless of retry")
            Boolean retry,
            @ToolParam(required = false, description = "Start of the window as UTC epoch milliseconds. Omit "
                    + "with endTime for the whole session; omit on its own to reach back to the session's start")
            Long startTime,
            @ToolParam(required = false, description = "End of the window as UTC epoch milliseconds, after "
                    + "startTime. Omit on its own to reach forward to the session's end")
            Long endTime,
            @ToolParam(required = false, description = "Comma-separated file_id values from hubs_files rows to "
                    + "bring instead of a window - at least one must be a JFR chunk, and the chunks must be an "
                    + "unbroken run of the session because the recording reports one span across them. Artifacts beside "
                    + "them are free to pick. Not combined with startTime or endTime")
            String fileIds) {
        HubSessionRef ref = HubSessionRef.decode(sessionRef);
        DownloadKey key = DownloadKey.of(ref, startTime, endTime, ToolArguments.commaSeparated(fileIds));
        Optional<OperationHandle<String>> before = downloads.current(key);
        String priorId = before.map(OperationHandle::operationId).orElse(null);
        boolean priorTerminal = before.map(handle -> handle.snapshot().state().terminal()).orElse(false);
        try {
            return download(key, retry);
        } catch (RuntimeException failure) {
            Optional<OperationHandle<String>> attempt = downloads.current(key);
            if (attempt.isPresent()) {
                OperationHandle<String> current = attempt.get();
                OperationState state = current.snapshot().state();
                boolean reportsThisAttempt = !Boolean.TRUE.equals(retry) || !priorTerminal
                        || !current.operationId().equals(priorId);
                if (reportsThisAttempt && (state == OperationState.FAILED || state == OperationState.CANCELLED)) {
                    return McpToolOutput.json(operations.status(registerDownload(key, current)));
                }
            }
            // A rejected retry preflight did not create an attempt. Its own error must not be
            // replaced by the previous attempt's retained outcome.
            throw failure;
        }
    }

    /** The transfer itself; the annotated method above turns a retained failure into a status. */
    private String download(DownloadKey key, Boolean retry) {
        HubSessionRef ref = key.ref();
        boolean retryFailed = Boolean.TRUE.equals(retry);

        // A window is a recording of its own: two windows of one session are two recordings, and
        // neither is the session, so "already here" is an answer only the whole session gets. A
        // window is still told about a whole-session copy that is here, further down, because the
        // reader may not need a second recording at all.
        Optional<DownloadedSessionIndex.LocalCopy> alreadyHere =
                DownloadedSessionIndex.build(recordingsManager).find(ref);
        if (key.wholeSession() && alreadyHere.isPresent()) {
            LOG.debug("Hub session was already downloaded: session_id={} recording_id={}",
                    ref.sessionId(), alreadyHere.get().recordingId());
            OperationHandle<String> operation = downloads.rememberCompleted(key, alreadyHere.get().recordingId());
            return operations.decorate(McpToolOutput.json(existing(ref, alreadyHere.get())),
                    registerDownload(key, operation));
        }

        Optional<BoundedJobs.Outcome<String>> prior = downloads.outcome(key);
        if (prior.isPresent() && prior.get().failure() != null && !retryFailed) {
            throw mapRemoteFailure(prior.get().failure());
        }

        Deadline responseDeadline = McpDeadlines.after(downloadResponseBudget);
        DownloadPreflight preflight = preflightWithin(ref, responseDeadline);
        HubInfo hubInfo = preflight.hubInfo();
        ProjectManager project = preflight.project();
        RecordingSession session = preflight.session();
        ChunkWindow.Selection selection = key.select(session);

        // Handing back the recording an identical call already made, unless the answer has moved
        // on since. A window with no end means "up to now" on a session that is still recording,
        // and "now" is later than it was: the retained recording stops where the session stood an
        // hour ago, so the same question asked twice would get a shorter answer the second time.
        // Everything else is settled and is answered from what is here.
        if (prior.isPresent() && !key.reachesPastTheEnd(session)) {
            String recordingId = prior.get().value();
            if (recordingId != null && recordingsManager.findRecording(recordingId).isPresent()) {
                return operations.decorate(McpToolOutput.json(completedOutcome(ref, recordingId)),
                        registerDownload(key, downloads.current(key).orElseThrow()));
            }
        }

        LOG.info("Downloading a hub session over MCP: hub_id={} project_id={} session_id={} window={} file_ids={}",
                ref.hubId(), ref.projectId(), ref.sessionId(), key.window(), key.fileIds());
        // A recording an identical call already made is handed back instead of fetched again --
        // unless what was asked for is still growing, in which case the retained one answers a
        // shorter question than the one being put. A transfer still in flight is joined either way.
        boolean stillGrowing = key.reachesPastTheEnd(session);
        OperationHandle<String> operation = downloads.startOrJoin(key, retryFailed,
                recordingId -> !stillGrowing && recordingsManager.findRecording(recordingId).isPresent(),
                control -> {
                    control.phase("downloading");
                    control.progress(Map.of("sessionRef", ref.encode(), "sessionId", ref.sessionId(),
                            "totalSizeBytes", selection == null
                                    ? session.totalSizeBytes()
                                    : selection.files().stream().mapToLong(RepositoryFile::size).sum()));
                    return transferWithinDeadline(project, key, control);
                });
        String operationId = registerDownload(key, operation);
        Optional<String> transferred;
        try {
            transferred = downloads.awaitWithin(operation, remaining(responseDeadline));
        } catch (RuntimeException e) {
            throw mapRemoteFailure(e);
        }
        if (transferred.isEmpty()) {
            // Nothing to poll but this tool: it answers from the local store first, so calling it again
            // with the same ref reports the finished copy once the transfer lands.
            // Names travel unescaped here. Replacing a pipe is a Markdown-cell concern, and these
            // two answers are JSON, where the serialiser escapes what needs escaping and a mangled
            // name is simply the wrong name.
            return operations.decorate(McpToolOutput.json(new DownloadInProgress(
                    ref.sessionId(),
                    session.name(),
                    session.totalSizeBytes(),
                    DOWNLOAD_STILL_RUNNING)), operationId);
        }
        String recordingId = transferred.get();

        List<RepositoryFile> finished = finishedFiles(session);
        if (selection != null) {
            List<RepositoryFile> others = key.others(finished);
            return operations.decorate(McpToolOutput.json(new DownloadedSession(
                    recordingId,
                    session.name(),
                    hubInfo.name(),
                    project.info().name(),
                    ref.sessionId(),
                    selection.files().size(),
                    others.size(),
                    selection.files().stream().mapToLong(RepositoryFile::size).sum()
                            + others.stream().mapToLong(RepositoryFile::size).sum(),
                    selection.coverageStart(),
                    selection.coverageEnd(),
                    "Call recordings_analyzeRecording with recordingId=" + recordingId
                            + " to build the profile every analysis tool takes. Its figures are about "
                            + "the covered span, not the whole session; recordings_delete removes it once "
                            + "the question is answered." + wholeSessionNote(alreadyHere))), operationId);
        }
        return operations.decorate(McpToolOutput.json(new DownloadedSession(
                recordingId,
                session.name(),
                hubInfo.name(),
                project.info().name(),
                ref.sessionId(),
                (int) finished.stream().filter(RepositoryFile::isRecordingFile).count(),
                (int) finished.stream().filter(RepositoryFile::isArtifactFile).count(),
                session.totalSizeBytes(),
                null,
                null,
                "Call recordings_analyzeRecording with recordingId=" + recordingId
                        + " to build the profile every analysis tool takes.")), operationId);
    }

    private String registerDownload(DownloadKey key, OperationHandle<String> operation) {
        String sessionRef = key.ref().encode();
        return operations.register(OperationKind.HUB_DOWNLOAD, operation,
                recordingId -> Map.of("recordingId", recordingId, "sessionRef", sessionRef));
    }

    /**
     * Java callers written before failed-download retry became explicit keep their source contract.
     * MCP reflection uses the annotated method above.
     */
    public String download(String sessionRef) {
        return download(new DownloadKey(HubSessionRef.decode(sessionRef), null, null), false);
    }

    /** The two-argument form the tests written before the window keep calling. */
    public String download(String sessionRef, Boolean retry) {
        return download(sessionRef, retry, null, null, null);
    }

    private RecordingSessionFilter sessionFilter(
            Integer withinLastMinutes, RecordingStatus status) {
        RecordingSessionFilter filter = RecordingSessionFilter.ALL;
        if (withinLastMinutes != null) {
            if (withinLastMinutes < 1) {
                throw new IllegalArgumentException(
                        "withinLastMinutes must be at least 1 minute: " + withinLastMinutes);
            }
            filter = RecordingSessionFilter.activeWithinLast(
                    Duration.ofMinutes(withinLastMinutes), clock.instant());
        }
        return filter.withStatus(status);
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
        return locator.hubInfo(ref);
    }

    private ProjectManager projectFor(HubSessionRef ref) {
        return locator.project(ref);
    }

    /**
     * Reads the session before pulling it, so a ref that has gone stale and a session with nothing
     * to download both fail in a sentence rather than partway through a multi-gigabyte transfer.
     */
    private RecordingSession preflight(ProjectManager project, HubSessionRef ref, HubInfo hubInfo) {
        RecordingSession session = locator.session(project, ref, hubInfo);
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

    private DownloadPreflight preflightWithin(HubSessionRef ref, Deadline deadline) {
        Context.CancellableContext context = McpDeadlines.withDeadline(Context.current(), deadline);
        try {
            return context.call(() -> {
                HubInfo hubInfo = hubInfo(ref);
                ProjectManager project = projectFor(ref);
                return new DownloadPreflight(hubInfo, project, preflight(project, ref, hubInfo));
            });
        } catch (StatusRuntimeException e) {
            throw GrpcClientErrors.toJeffreyException(e);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ToolExecutionException(DOWNLOAD_PREFLIGHT_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    private String transferWithinDeadline(ProjectManager project, DownloadKey key, BoundedJobs.JobControl control) {
        Context.CancellableContext context = McpDeadlines.withDeadlineAfter(Context.ROOT, downloadDeadline);
        control.onCancellation(() -> context.cancel(null));
        try {
            control.checkCancellation();
            return context.call(() -> key.transfer(project.recordingsDownloadManager()));
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
            throw new ToolExecutionException(DOWNLOAD_FAILED + e.getMessage(), e);
        } finally {
            context.cancel(null);
        }
    }

    private static Duration remaining(Deadline deadline) {
        long remainingNanos = deadline.timeRemaining(TimeUnit.NANOSECONDS);
        if (remainingNanos <= 0) {
            throw GrpcClientErrors.toJeffreyException(
                    Status.DEADLINE_EXCEEDED.withDescription("Hub download response deadline elapsed")
                            .asRuntimeException());
        }
        return Duration.ofNanos(remainingNanos);
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive: " + value);
        }
        return value;
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

    private static DownloadedSession existing(
            HubSessionRef ref, DownloadedSessionIndex.LocalCopy copy) {

        String next = copy.analysed()
                ? "Already analysed as profile " + copy.profileId()
                + " - every analysis tool takes that profileId straight away."
                : "Call recordings_analyzeRecording with recordingId=" + copy.recordingId()
                        + " to build the profile every analysis tool takes.";
        return new DownloadedSession(
                copy.recordingId(), null, null, null, ref.sessionId(), 0, 0, 0L, null, null, next);
    }

    /**
     * Said on a part download when the whole session is here too: a reader who asked for an hour
     * of it may well be able to use what is already analysed instead of keeping a second recording.
     */
    private static String wholeSessionNote(Optional<DownloadedSessionIndex.LocalCopy> wholeSession) {
        if (wholeSession.isEmpty()) {
            return "";
        }
        DownloadedSessionIndex.LocalCopy copy = wholeSession.get();
        return copy.analysed()
                ? " The whole session is also here, already analysed as profile " + copy.profileId() + "."
                : " The whole session is also here as recording " + copy.recordingId() + ".";
    }

    private static DownloadedSession completedOutcome(HubSessionRef ref, String recordingId) {
        return new DownloadedSession(
                recordingId,
                null,
                null,
                null,
                ref.sessionId(),
                0,
                0,
                0L,
                null,
                null,
                "Call recordings_analyzeRecording with recordingId=" + recordingId
                        + " to build the profile every analysis tool takes.");
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

    /**
     * @param status what to do next, rather than a bare flag: the caller is holding a tool result and
     *               needs to know that calling the same tool again is the way to check
     */
    private record DownloadInProgress(
            String sessionId, String sessionName, long totalSizeBytes, String status) {
    }

    private record DownloadPreflight(
            HubInfo hubInfo, ProjectManager project, RecordingSession session) {
    }

    /**
     * @param windowStart when the first downloaded chunk started, for a window download; {@code null}
     *                    for the whole session
     * @param windowEnd   when the last downloaded chunk ended; {@code null} for the whole session and
     *                    for a chunk the session is still writing
     */
    private record DownloadedSession(
            String recordingId,
            String sessionName,
            String hub,
            String project,
            String sessionId,
            int recordingFiles,
            int artifactFiles,
            long sizeBytes,
            Instant windowStart,
            Instant windowEnd,
            String nextStep) {
    }

    /**
     * What one download is: a session and the part of it — a window, files named by id, or neither
     * for all of it. The jobs are keyed on this rather than on the session alone so that a part
     * neither joins a running whole-session transfer nor is short-circuited by one that finished.
     *
     * @param window  the span asked for, or {@code null}
     * @param fileIds the files asked for by id, or {@code null}; never set together with the window
     */
    private record DownloadKey(HubSessionRef ref, ChunkWindow window, List<String> fileIds) {

        static DownloadKey of(HubSessionRef ref, Long startTime, Long endTime, List<String> fileIds) {
            boolean windowed = startTime != null || endTime != null;
            boolean named = fileIds != null && !fileIds.isEmpty();
            if (windowed && named) {
                throw new IllegalArgumentException(
                        "Choose the part of the session one way: a window (startTime/endTime) or fileIds, not both.");
            }
            return new DownloadKey(ref,
                    windowed ? ChunkWindow.ofEpochMillis(startTime, endTime) : null,
                    named ? List.copyOf(new TreeSet<>(fileIds)) : null);
        }

        boolean wholeSession() {
            return window == null && fileIds == null;
        }

        /**
         * Whether what this asks for is still growing: an open-ended window on a session that is
         * still recording covers more of it with every chunk that rolls, so an answer retained
         * from an earlier call is already short of the question.
         */
        boolean reachesPastTheEnd(RecordingSession session) {
            return window != null && window.end() == null && session.status() == RecordingStatus.ACTIVE;
        }

        /** The non-chunk files a part brings: those named by id; a window brings none. */
        List<RepositoryFile> others(List<RepositoryFile> finished) {
            if (fileIds == null) {
                return List.of();
            }
            return finished.stream()
                    .filter(file -> fileIds.contains(file.id()))
                    .filter(RepositoryFile::isArtifactFile)
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
            List<RepositoryFile> finished = finishedFiles(session);
            if (window != null) {
                ChunkWindow.Selection selection = window.select(finished, session.finishedAt());
                if (selection.isEmpty()) {
                    throw new IllegalArgumentException("No finished chunk of session " + ref.sessionId()
                            + " covers the window: the session started at " + session.createdAt()
                            + (session.finishedAt() == null ? " and is still recording" : " and finished at " + session.finishedAt())
                            + ". Choose a window inside that span, in UTC epoch milliseconds.");
                }
                return selection;
            }
            Set<String> known = finished.stream()
                    .map(RepositoryFile::id)
                    .collect(Collectors.toSet());
            List<String> unknown = fileIds.stream().filter(id -> !known.contains(id)).toList();
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Session " + ref.sessionId() + " has no finished file "
                        + unknown + ". Take file_id values from hubs_files; a file still being written is not one yet.");
            }
            ChunkWindow.Selection selection = ChunkWindow.ofFiles(finished, Set.copyOf(fileIds), session.finishedAt());
            if (selection.isEmpty()) {
                throw new IllegalArgumentException("None of the fileIds is a JFR chunk of session " + ref.sessionId()
                        + ": a recording needs at least one. A log or a heap dump on its own is what hubs_fetchFile is for.");
            }
            // The chunks become one recording reporting one span, so a skipped one leaves no trace in the
            // result: the profile would claim a span it only partly holds, and every rate read off
            // it would be wrong by the size of the hole. Refused here and again in
            // RemoteRecordingsDownloadManager; the hub serves one file per call and never sees a
            // selection to judge.
            if (!selection.contiguous()) {
                throw new IllegalArgumentException("The chunks named for session " + ref.sessionId()
                        + " are not next to each other: " + selection.describeGap(finished)
                        + " lies between them. hubs_download makes them one recording reporting one span, so they have "
                        + "to be an unbroken run. Name the chunks in between as well, or ask for the span with "
                        + "startTime and endTime and let the window pick them.");
            }
            return selection;
        }
    }
}

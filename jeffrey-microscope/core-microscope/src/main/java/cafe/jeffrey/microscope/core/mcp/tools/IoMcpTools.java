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

import cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies;
import cafe.jeffrey.microscope.core.mcp.MicroscopeView;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.microscope.mcp.protocol.McpDescription;
import cafe.jeffrey.microscope.mcp.protocol.McpNullable;
import cafe.jeffrey.microscope.mcp.protocol.McpOutputSchema;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
import cafe.jeffrey.profile.mcp.McpFollowUp;
import cafe.jeffrey.profile.mcp.McpNextTool;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Locale;

/**
 * What the application talked to, and what waiting for it cost.
 * <p>
 * Sockets and files are one family because they are one question — time spent outside the process,
 * which a CPU flamegraph cannot show at all: a thread blocked on a socket read is not on-CPU, so it
 * contributes no samples and the graph reports the application as idle rather than as waiting.
 */
public class IoMcpTools {

    private static final MicroscopeView SOCKET_VIEW = MicroscopeView.SOCKET_IO;
    private static final MicroscopeView FILE_VIEW = MicroscopeView.FILE_IO;

    private static final int MAX_ENDPOINTS = 40;

    private static final String NO_IO_DATA =
            "This profile recorded no %s I/O events. That is a profiler-configuration finding rather "
                    + "than evidence the application did no %s work - jdk.SocketRead, jdk.SocketWrite, "
                    + "jdk.FileRead and jdk.FileWrite are threshold-gated, so a recording can also hold "
                    + "none because every operation was faster than the threshold.";

    private static final String ENDPOINTS_WHY = "ranks the individual targets - hosts, ports, files - by what they cost";
    private static final String SLOWEST_WHY = "the individual operations, with the thread that waited";
    private static final String OTHER_KIND_WHY = "the other half of I/O - files when this was sockets, sockets when files";
    private static final String NOT_ON_CPU =
            "Time spent here is off-CPU and invisible in jdk.ExecutionSample. The flamegraph that does "
                    + "cover waiting is profiler.WallClockSample, when the recording carries it.";

    private final ProfileManager profileManager;
    private final AdvertisedFamilies advertised;

    public IoMcpTools(ProfileManager profileManager, AdvertisedFamilies advertised) {
        this.profileManager = profileManager;
        this.advertised = advertised;
    }

    @Tool(description = "Returns the I/O dashboard for one kind: bytes read and written, how many "
            + "operations, and the slowest single one (in nanoseconds) with its target. Answers 'what "
            + "is this application talking to, and how slow is it'. Time spent waiting here does not "
            + "appear in a CPU flamegraph, because a blocked thread produces no samples. status "
            + "NOT_RECORDED: no I/O event of that kind was recorded.")
    @McpOutputSchema(IoDashboard.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult overview(
            @ToolParam(required = true, description = "Which I/O to report: SOCKET for network, FILE for disk")
            IoKind kind) {

        IoKind ioKind = requireKind(kind);
        String uiLink = link(ioKind);
        IoOverview overview = profileManager.ioManager().overview(ioKind);
        if (!overview.hasEvents()) {
            return McpToolResult.of(new IoDashboard(DashboardStatus.NOT_RECORDED, noData(ioKind), profileId(),
                    ioKind, null, notRecordedFollowUp(ioKind), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.IO_ENDPOINTS, ioKind).why(ENDPOINTS_WHY))
                .next(call(FollowUpCalls.IO_SLOWEST, ioKind).why(SLOWEST_WHY))
                .guidance(NOT_ON_CPU)
                .followUp();
        return McpToolResult.of(new IoDashboard(DashboardStatus.OK, null, profileId(), ioKind,
                IoTotals.of(overview), followUp, uiLink));
    }

    @Tool(description = "Ranks the targets this application did I/O with by cost: for sockets the hosts "
            + "and ports, for files the paths, each with its operation count, bytes and total and "
            + "maximum time in nanoseconds - which endpoint the io_overview time went to. The "
            + "costliest 40 are kept and omittedEndpoints counts the rest. status NOT_RECORDED: no "
            + "I/O event of that kind was recorded.")
    @McpOutputSchema(IoEndpoints.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult endpoints(
            @ToolParam(required = true, description = "Which I/O to report: SOCKET for network, FILE for disk")
            IoKind kind) {

        IoKind ioKind = requireKind(kind);
        String uiLink = link(ioKind);
        List<IoEndpoint> endpoints = profileManager.ioManager().endpoints(ioKind);
        if (endpoints.isEmpty()) {
            return McpToolResult.of(new IoEndpoints(DashboardStatus.NOT_RECORDED, noData(ioKind), profileId(),
                    ioKind, List.of(), null, notRecordedFollowUp(ioKind), uiLink));
        }

        List<IoEndpoint> shown = ToolArguments.firstOf(endpoints, MAX_ENDPOINTS);
        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.IO_SLOWEST, ioKind).why(SLOWEST_WHY))
                .next(call(FollowUpCalls.IO_OVERVIEW, other(ioKind)).why(OTHER_KIND_WHY))
                .followUp();
        return McpToolResult.of(new IoEndpoints(DashboardStatus.OK, null, profileId(), ioKind,
                shown.stream().map(Target::of).toList(), endpoints.size() - shown.size(), followUp, uiLink));
    }

    @Tool(description = "Returns the slowest individual I/O operations, each with its target, the bytes "
            + "moved, its duration in nanoseconds and the thread that waited. A single pathological "
            + "operation and a uniformly slow endpoint look identical in the totals and different "
            + "here. status NOT_RECORDED: no I/O event of that kind was recorded.")
    @McpOutputSchema(IoSlowest.class)
    @McpToolMeta(cost = McpToolCost.MODERATE)
    public McpToolResult slowest(
            @ToolParam(required = true, description = "Which I/O to report: SOCKET for network, FILE for disk")
            IoKind kind) {

        IoKind ioKind = requireKind(kind);
        String uiLink = link(ioKind);
        List<IoOperation> operations = profileManager.ioManager().slowestOperations(ioKind);
        if (operations.isEmpty()) {
            return McpToolResult.of(new IoSlowest(DashboardStatus.NOT_RECORDED, noData(ioKind), profileId(),
                    ioKind, List.of(), notRecordedFollowUp(ioKind), uiLink));
        }

        McpFollowUp followUp = NextSteps.builder(advertised)
                .next(call(FollowUpCalls.IO_ENDPOINTS, ioKind).why(ENDPOINTS_WHY))
                .guidance(NOT_ON_CPU)
                .followUp();
        return McpToolResult.of(new IoSlowest(DashboardStatus.OK, null, profileId(), ioKind,
                operations.stream().map(Operation::of).toList(), followUp, uiLink));
    }

    /** A kind with no events: the other kind, whose events may well be there. */
    private McpFollowUp notRecordedFollowUp(IoKind kind) {
        return NextSteps.builder(advertised)
                .next(call(FollowUpCalls.IO_OVERVIEW, other(kind)).why(OTHER_KIND_WHY))
                .followUp();
    }

    private McpNextTool.Call call(String tool, IoKind kind) {
        return McpNextTool.call(tool)
                .with(FollowUpCalls.PROFILE_ID, profileId())
                .with(FollowUpCalls.KIND, kind);
    }

    /**
     * The schema names the two kinds and the binder refuses anything else, so all that is left to say
     * here is that the argument has to be there at all — it decides which half of the I/O data the
     * answer describes, and there is no sensible default between sockets and files.
     */
    private static IoKind requireKind(IoKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required: one of SOCKET, FILE");
        }
        return kind;
    }

    private static IoKind other(IoKind kind) {
        return kind == IoKind.SOCKET ? IoKind.FILE : IoKind.SOCKET;
    }

    private static String noData(IoKind kind) {
        String name = kind.name().toLowerCase(Locale.ROOT);
        return NO_IO_DATA.formatted(name, name);
    }

    private String link(IoKind kind) {
        return UiLinks.view(profileId(), kind == IoKind.SOCKET ? SOCKET_VIEW : FILE_VIEW);
    }

    private String profileId() {
        return profileManager.info().id();
    }

    /** One kind's totals; the slowest operation's target is null when its event named none. */
    record IoTotals(
            long bytesRead,
            long bytesWritten,
            long opCount,
            long slowestNanos,
            @McpNullable
            String slowestTarget) {

        static IoTotals of(IoOverview overview) {
            return new IoTotals(overview.bytesRead(), overview.bytesWritten(), overview.opCount(),
                    overview.slowestNanos(), overview.slowestTarget());
        }
    }

    /** One target: a host and port for sockets, a path for files, an unknown label when the event named none. */
    record Target(
            String target,
            long opCount,
            long bytes,
            long totalNanos,
            long maxNanos) {

        static Target of(IoEndpoint endpoint) {
            return new Target(endpoint.target(), endpoint.opCount(), endpoint.bytes(), endpoint.totalNanos(),
                    endpoint.maxNanos());
        }
    }

    /** One slow operation; what its event did not name is null. */
    record Operation(
            @McpDescription("Which operation, e.g. 'Socket Read'")
            String kind,
            String target,
            long bytes,
            long durationNanos,
            @McpNullable
            String thread) {

        static Operation of(IoOperation operation) {
            return new Operation(operation.kind(), operation.target(), operation.bytes(), operation.durationNanos(),
                    operation.thread());
        }
    }

    record IoDashboard(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there is no dashboard; null when status is OK")
            String reason,
            String profileId,
            IoKind kind,
            @McpNullable
            @McpDescription("The kind's totals; null when status is NOT_RECORDED")
            IoTotals overview,
            McpFollowUp followUp,
            @McpDescription("The kind's I/O page in the Microscope UI, for the user")
            String uiLink) {
    }

    record IoEndpoints(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no targets; null when status is OK")
            String reason,
            String profileId,
            IoKind kind,
            @McpDescription("The costliest targets, at most 40")
            List<Target> endpoints,
            @McpNullable
            @McpDescription("Targets left out of endpoints by its cap of 40; null when status is NOT_RECORDED")
            Integer omittedEndpoints,
            McpFollowUp followUp,
            @McpDescription("The kind's I/O page in the Microscope UI, for the user")
            String uiLink) {
    }

    record IoSlowest(
            DashboardStatus status,
            @McpNullable
            @McpDescription("Why there are no operations; null when status is OK")
            String reason,
            String profileId,
            IoKind kind,
            @McpDescription("The slowest operations, slowest first, as many as the profile keeps")
            List<Operation> operations,
            McpFollowUp followUp,
            @McpDescription("The kind's I/O page in the Microscope UI, for the user")
            String uiLink) {
    }
}

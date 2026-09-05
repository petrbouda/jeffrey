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
import cafe.jeffrey.microscope.core.mcp.LinkedOutput;
import cafe.jeffrey.microscope.core.mcp.UiLinks;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.model.io.IoEndpoint;
import cafe.jeffrey.profile.manager.model.io.IoKind;
import cafe.jeffrey.profile.manager.model.io.IoOperation;
import cafe.jeffrey.profile.manager.model.io.IoOverview;
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

    private static final String SOCKET_VIEW = "socket-io";
    private static final String FILE_VIEW = "file-io";

    private static final int MAX_ENDPOINTS = 40;

    private static final String NO_IO_DATA =
            "This profile recorded no %s I/O events. That is a profiler-configuration finding rather "
                    + "than evidence the application did no %s work - jdk.SocketRead, jdk.SocketWrite, "
                    + "jdk.FileRead and jdk.FileWrite are threshold-gated, so a recording can also hold "
                    + "none because every operation was faster than the threshold.";

    private static final String STEP_ENDPOINTS =
            "io_endpoints ranks the individual targets - hosts, ports, files - by what they cost.";
    private static final String STEP_SLOWEST =
            "io_slowest lists the individual operations, with the thread that waited.";
    private static final String STEP_NOT_ON_CPU =
            "Time spent here is off-CPU and invisible in jdk.ExecutionSample. The flamegraph that does "
                    + "cover waiting is profiler.WallClockSample, when the recording carries it.";
    private static final String STEP_OTHER_KIND =
            "The other half of I/O - file when this was socket, socket when this was file - is the same "
                    + "tool with the other kind.";

    private final ProfileManager profileManager;

    public IoMcpTools(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    @Tool(description = "The I/O dashboard for one kind: bytes read and written, how many operations, "
            + "and the slowest single one with its target. Answers 'what is this application talking "
            + "to, and how slow is it'. Time spent waiting here never appears in a CPU flamegraph, "
            + "because a blocked thread produces no samples.")
    public String overview(
            @ToolParam(required = true, description = "Which I/O to report: 'SOCKET' for network, 'FILE' for disk")
            @ToolParamValues({"SOCKET", "FILE"})
            String kind) {

        IoKind ioKind = requireKind(kind);
        IoOverview overview = profileManager.ioManager().overview(ioKind);
        if (!overview.hasEvents()) {
            return noData(ioKind);
        }

        return LinkedOutput.json(new IoDashboard(
                ioKind.name(),
                overview,
                NextSteps.builder()
                        .add(STEP_ENDPOINTS)
                        .add(STEP_SLOWEST)
                        .add(STEP_NOT_ON_CPU)
                        .build(),
                UiLinks.view(profileId(), view(ioKind))));
    }

    @Tool(description = "The targets this application did I/O with, ranked by cost: for sockets the "
            + "hosts and ports, for files the paths, each with its operation count, bytes and total "
            + "and maximum time. Use it after io_overview to see which endpoint the time went to.")
    public String endpoints(
            @ToolParam(required = true, description = "Which I/O to report: 'SOCKET' for network, 'FILE' for disk")
            @ToolParamValues({"SOCKET", "FILE"})
            String kind) {

        IoKind ioKind = requireKind(kind);
        List<IoEndpoint> endpoints = profileManager.ioManager().endpoints(ioKind);
        if (endpoints.isEmpty()) {
            return noData(ioKind);
        }

        return LinkedOutput.json(new IoEndpoints(
                ioKind.name(),
                ToolArguments.firstOf(endpoints, MAX_ENDPOINTS),
                NextSteps.builder().add(STEP_SLOWEST).add(STEP_OTHER_KIND).build(),
                UiLinks.view(profileId(), view(ioKind))));
    }

    @Tool(description = "The slowest individual I/O operations, each with its target, the bytes moved "
            + "and the thread that waited. A single pathological operation and a uniformly slow "
            + "endpoint look identical in the totals and different here.")
    public String slowest(
            @ToolParam(required = true, description = "Which I/O to report: 'SOCKET' for network, 'FILE' for disk")
            @ToolParamValues({"SOCKET", "FILE"})
            String kind) {

        IoKind ioKind = requireKind(kind);
        List<IoOperation> operations = profileManager.ioManager().slowestOperations(ioKind);
        if (operations.isEmpty()) {
            return noData(ioKind);
        }

        return LinkedOutput.json(new IoSlowest(
                ioKind.name(),
                operations,
                NextSteps.builder().add(STEP_ENDPOINTS).add(STEP_NOT_ON_CPU).build(),
                UiLinks.view(profileId(), view(ioKind))));
    }

    private static IoKind requireKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("kind is required: one of SOCKET, FILE");
        }
        try {
            return IoKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown I/O kind '" + kind + "'. Valid kinds: SOCKET, FILE");
        }
    }

    private static String noData(IoKind kind) {
        String name = kind.name().toLowerCase(Locale.ROOT);
        return NO_IO_DATA.formatted(name, name);
    }

    private static String view(IoKind kind) {
        return kind == IoKind.SOCKET ? SOCKET_VIEW : FILE_VIEW;
    }

    private String profileId() {
        return profileManager.info().id();
    }

    private record IoDashboard(
            String kind, IoOverview overview, List<String> nextSteps, String uiLink) {
    }

    private record IoEndpoints(
            String kind, List<IoEndpoint> endpoints, List<String> nextSteps, String uiLink) {
    }

    private record IoSlowest(
            String kind, List<IoOperation> operations, List<String> nextSteps, String uiLink) {
    }
}

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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.mcp.tools.CompareMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.BlockingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.GrpcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDiffMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HttpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JdbcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MethodTracingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TraceAttributesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TimelineMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TracesMcpTools;
import cafe.jeffrey.microscope.core.web.controllers.profile.HeapDumpManagerToolsDelegate;
import cafe.jeffrey.profile.ai.duckdb.heapdump.tools.HeapDumpMcpTools;
import cafe.jeffrey.profile.ai.duckdb.jfr.tools.DuckDbMcpTools;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Assembles the tool families the external MCP server advertises.
 * <p>
 * Built once: the specs depend only on the tool classes, not on which profile is being asked about, so
 * a {@code tools/list} costs a field read. The per-profile work happens inside
 * {@link ProfileScopedToolset}, which resolves its target through the {@link McpProfileContextCache}
 * when a call actually arrives.
 * <p>
 * Every family here is scoped to one profile except {@link CompareMcpTools}, which is scoped to a
 * pair: the toolset resolves the {@code profileId} as usual and the tool resolves its
 * {@code baselineProfileId} through the same cache, so both profiles stay pinned for the session
 * rather than the baseline being reopened on every call.
 * <p>
 * {@code JvmMcpTools} is the machine-level family — garbage collection, safepoints, JIT compilation,
 * threads, native memory, the container and the JVM's configuration. Each of its tools renders the
 * manager behind the matching Jeffrey UI page, which is what keeps a subsystem question to one call
 * instead of a handful of invented SQL queries, several of which a reader reliably gets wrong.
 * <p>
 * Every analysis family here is read-only. {@code DuckDbMcpTools} is constructed with its
 * single-argument constructor, which leaves {@code executeModification} refusing — an external client
 * gets to read a profile's data, not to rewrite it.
 * <p>
 * {@link RecordingsMcpTools} is the one exception, and it writes at a different level: it does not
 * change an analysed profile, it creates one, which is what lets a reader analyse a recording without
 * leaving the terminal. It is appended only when ingestion is enabled — an installation that wants the
 * purely read-only server keeps it, minus this family.
 */
public class McpToolsetAssembler {

    private static final String PREFIX_PROFILES = "profiles";
    private static final String PREFIX_JFR = "jfr";
    private static final String PREFIX_FLAMEGRAPH = "flamegraph";
    private static final String PREFIX_COMPARE = "compare";
    private static final String PREFIX_TRACES = "traces";
    private static final String PREFIX_JVM = "jvm";
    private static final String PREFIX_HTTP = "http";
    private static final String PREFIX_JDBC = "jdbc";
    private static final String PREFIX_GRPC = "grpc";
    private static final String PREFIX_METHOD_TRACING = "methodtracing";
    private static final String PREFIX_IO = "io";
    private static final String PREFIX_BLOCKING = "blocking";
    private static final String PREFIX_TIMELINE = "timeline";
    private static final String PREFIX_MEMORY = "memory";
    private static final String PREFIX_HEAP = "heap";
    private static final String PREFIX_RECORDINGS = "recordings";
    private static final String PREFIX_HUBS = "hubs";

    /**
     * The one JFR tool that writes. Left out of the family rather than left in to refuse: an
     * advertised tool that always answers "not enabled" spends a slot in the model's context and
     * invites a call that cannot succeed.
     */
    static final Set<String> WRITE_TOOLS = Set.of("executeModification");

    private final McpToolProvider toolset;

    public McpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            RecordingsMcpTools recordingsMcpTools,
            HubsMcpTools hubsMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            RecordingCommitResolver recordingCommitResolver,
            ExternalMcpProperties properties) {

        List<McpToolProvider> families = new ArrayList<>(List.of(
                new ReflectiveToolset(profilesMcpTools, PREFIX_PROFILES),
                new ProfileScopedToolset<>(ProfileMcpTools.class, PREFIX_PROFILES,
                        profileId -> new ProfileMcpTools(
                                profileManager(contextCache, profileId), recordingCommitResolver)),
                new ProfileScopedToolset<>(DuckDbMcpTools.class, PREFIX_JFR,
                        profileId -> new DuckDbMcpTools(contextCache.context(profileId).dataSource()),
                        WRITE_TOOLS),
                new ProfileScopedToolset<>(FlamegraphMcpTools.class, PREFIX_FLAMEGRAPH,
                        profileId -> new FlamegraphMcpTools(
                                profileManager(contextCache, profileId),
                                jfrPanelProvider,
                                stackSamplePanelProvider)),
                new ProfileScopedToolset<>(CompareMcpTools.class, PREFIX_COMPARE,
                        profileId -> new CompareMcpTools(
                                profileManager(contextCache, profileId),
                                baselineId -> profileManager(contextCache, baselineId))),
                new ProfileScopedToolset<>(TracesMcpTools.class, PREFIX_TRACES,
                        profileId -> new TracesMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(JvmMcpTools.class, PREFIX_JVM,
                        profileId -> new JvmMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(HttpMcpTools.class, PREFIX_HTTP,
                        profileId -> new HttpMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(JdbcMcpTools.class, PREFIX_JDBC,
                        profileId -> new JdbcMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(GrpcMcpTools.class, PREFIX_GRPC,
                        profileId -> new GrpcMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(MethodTracingMcpTools.class, PREFIX_METHOD_TRACING,
                        profileId -> new MethodTracingMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(IoMcpTools.class, PREFIX_IO,
                        profileId -> new IoMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(BlockingMcpTools.class, PREFIX_BLOCKING,
                        profileId -> new BlockingMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(TimelineMcpTools.class, PREFIX_TIMELINE,
                        profileId -> new TimelineMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(MemoryMcpTools.class, PREFIX_MEMORY,
                        profileId -> new MemoryMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(TraceAttributesMcpTools.class, PREFIX_TRACES,
                        profileId -> new TraceAttributesMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(HeapDiffMcpTools.class, PREFIX_HEAP,
                        profileId -> new HeapDiffMcpTools(
                                profileManager(contextCache, profileId),
                                baselineId -> profileManager(contextCache, baselineId))),
                new ProfileScopedToolset<>(HeapDumpMcpTools.class, PREFIX_HEAP,
                        profileId -> heapTools(contextCache, profileId))));

        if (properties.ingestEnabled()) {
            families.add(new ReflectiveToolset(recordingsMcpTools, PREFIX_RECORDINGS));
        }
        if (properties.hubsAdvertised()) {
            families.add(new ReflectiveToolset(hubsMcpTools, PREFIX_HUBS));
        }

        this.toolset = new CompositeToolset(List.copyOf(families));
    }

    public McpToolProvider toolset() {
        return toolset;
    }

    private static ProfileManager profileManager(McpProfileContextCache contextCache, String profileId) {
        return contextCache.profileManager(profileId);
    }

    /**
     * The heap family, refused up front for a profile that has no heap dump to read.
     * <p>
     * Checked once here rather than in each of the twenty heap tools: without it every one of them
     * fails deep inside the engine with a null-dereference message that says nothing about the actual
     * problem, which is that this profile is a JFR recording and the model asked the wrong family.
     */
    private static HeapDumpMcpTools heapTools(McpProfileContextCache contextCache, String profileId) {
        HeapDumpManager heapDumpManager = profileManager(contextCache, profileId).heapDumpManager();
        if (!heapDumpManager.heapDumpExists()) {
            throw new IllegalArgumentException(
                    "Profile " + profileId + " has no heap dump. Use profiles_features to see what a "
                            + "profile can answer; for a JFR recording use the jfr_, flamegraph_ and "
                            + "traces_ tools instead.");
        }
        if (!heapDumpManager.isCacheReady()) {
            throw new IllegalArgumentException(
                    "The heap dump of profile " + profileId + " is still being indexed. Open it once in "
                            + "the Jeffrey UI to build the index, then try again.");
        }
        return new HeapDumpMcpTools(new HeapDumpManagerToolsDelegate(heapDumpManager));
    }
}

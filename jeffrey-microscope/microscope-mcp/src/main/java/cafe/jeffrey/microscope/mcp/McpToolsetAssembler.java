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

package cafe.jeffrey.microscope.mcp;

import cafe.jeffrey.microscope.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.mcp.tools.ProfileMcpTools;
import cafe.jeffrey.microscope.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.mcp.tools.TracesMcpTools;
import cafe.jeffrey.profile.mcp.tools.heapdump.HeapDumpMcpTools;
import cafe.jeffrey.profile.mcp.tools.jfr.DuckDbMcpTools;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;

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
 * Every family here is read-only. {@code DuckDbMcpTools} is constructed with its single-argument
 * constructor, which leaves {@code executeModification} refusing — an external client gets to read
 * Jeffrey's data, not to rewrite it.
 */
public class McpToolsetAssembler {

    private static final String PREFIX_PROFILES = "profiles";
    private static final String PREFIX_JFR = "jfr";
    private static final String PREFIX_FLAMEGRAPH = "flamegraph";
    private static final String PREFIX_TRACES = "traces";
    private static final String PREFIX_HEAP = "heap";

    /**
     * The one JFR tool that writes. Left out of the family rather than left in to refuse: an
     * advertised tool that always answers "not enabled" spends a slot in the model's context and
     * invites a call that cannot succeed.
     */
    private static final Set<String> WRITE_TOOLS = Set.of("executeModification");

    private final McpToolProvider toolset;

    public McpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider panelProvider) {

        this.toolset = new CompositeToolset(List.of(
                new ReflectiveToolset(profilesMcpTools, PREFIX_PROFILES),
                new ProfileScopedToolset<>(ProfileMcpTools.class, PREFIX_PROFILES,
                        profileId -> new ProfileMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(DuckDbMcpTools.class, PREFIX_JFR,
                        profileId -> new DuckDbMcpTools(contextCache.context(profileId).dataSource()),
                        WRITE_TOOLS),
                new ProfileScopedToolset<>(FlamegraphMcpTools.class, PREFIX_FLAMEGRAPH,
                        profileId -> new FlamegraphMcpTools(profileManager(contextCache, profileId), panelProvider)),
                new ProfileScopedToolset<>(TracesMcpTools.class, PREFIX_TRACES,
                        profileId -> new TracesMcpTools(profileManager(contextCache, profileId))),
                new ProfileScopedToolset<>(HeapDumpMcpTools.class, PREFIX_HEAP,
                        profileId -> heapTools(contextCache, profileId))));
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

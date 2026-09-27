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

package cafe.jeffrey.microscope.core.mcp;

import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.mcp.tools.BlockingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.BoundedOperation;
import cafe.jeffrey.microscope.core.mcp.tools.CompareMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.EventTypeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.GrpcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapComputeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDiffMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapOqlMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HttpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsArtifactsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IdeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JdbcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MethodTracingMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.OperationAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.OperationKind;
import cafe.jeffrey.microscope.core.mcp.tools.OperationsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileEvidenceMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindingsReader;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileSchemaReader;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TimelineMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TraceAttributesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.TracesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.jvm.AutoAnalysisSection.AutoAnalysisDashboard;
import cafe.jeffrey.microscope.core.web.controllers.profile.HeapDumpManagerToolsDelegate;
import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpToolAnnotations;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.mcp.McpToolNames;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
 * Every analysis family here is read-only, the {@link DuckDbMcpTools} SQL family included — an
 * external client gets to read a profile's data, not to rewrite it.
 * <p>
 * {@link RecordingsMcpTools} is the one exception, and it writes at a different level: it does not
 * change an analysed profile, it creates one, which is what lets a reader analyse a recording without
 * leaving the terminal. {@link HeapComputeMcpTools} is the same shape: it builds the index and the
 * dominator tree the reading heap tools need, rather than altering anything already analysed. Both are
 * marked {@link McpToolAnnotations#CREATES} so a client can tell them apart from their neighbours.
 * <p>
 * Two reads that can run for minutes — {@code jvm_autoAnalysis} with {@code compute} and
 * {@code heap_oql} with {@code includeRetainedSize} — run as operations the way the writers' long
 * work does, through one {@link BoundedOperation} each shared by every call, and say so per tool with
 * {@code @McpToolHints}: they fill a cache rather than only reading one.
 * <p>
 * {@link HubsMcpTools} and {@link IdeMcpTools} are the two families gated by configuration, because
 * they are the two that reach outside this server: the hub family leaves the machine altogether, and
 * the IDE family reaches into another process on it and can put a file on the developer's screen.
 */
public class McpToolsetAssembler {

    private static final String PREFIX_PROFILES = "profiles";
    private static final String PREFIX_OPERATIONS = "operations";
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
    private static final String PREFIX_IDE = "ide";

    private final McpToolProvider toolset;
    private final McpProfileDocuments documents;

    public McpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            RecordingsMcpTools recordingsMcpTools,
            HubsMcpTools hubsMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            RecordingCommitResolver recordingCommitResolver,
            HeapDumpInitService heapDumpInitService,
            IdeBridge ideBridge,
            ExternalMcpProperties properties,
            AdvertisedFamilies advertised,
            HubsArtifactsMcpTools hubsArtifactsMcpTools,
            McpOperationRegistry operations,
            OperationAnswers answers,
            Clock clock) {

        // Shared by every call rather than built per call, so a second request joins the run in flight.
        BoundedOperation<AutoAnalysisDashboard> autoAnalysisRuns =
                BoundedOperation.standard(OperationKind.JVM_AUTO_ANALYSIS, operations, answers, clock);
        BoundedOperation<OQLQueryResult> retainedOqlRuns =
                BoundedOperation.standard(OperationKind.HEAP_OQL, operations, answers, clock);

        List<McpToolProvider> families = new ArrayList<>(List.of(
                new ReflectiveToolset(profilesMcpTools, PREFIX_PROFILES),
                new ReflectiveToolset(new OperationsMcpTools(operations, kind -> reachable(kind, properties)), PREFIX_OPERATIONS),
                ProfileScopedToolset.leased(ProfileEvidenceMcpTools.class, PREFIX_PROFILES,
                        profileId -> scoped(contextCache, profileId, scope -> new ProfileEvidenceMcpTools(
                                scope.profileManager(), recordingCommitResolver, jfrPanelProvider,
                                stackSamplePanelProvider, clock, advertised))),
                ProfileScopedToolset.leased(ProfileMcpTools.class, PREFIX_PROFILES,
                        profileId -> scoped(contextCache, profileId, scope -> new ProfileMcpTools(
                                scope.profileManager(),
                                recordingCommitResolver,
                                jfrPanelProvider,
                                stackSamplePanelProvider,
                                advertised))),
                ProfileScopedToolset.leased(EventTypeMcpTools.class, PREFIX_JFR,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new EventTypeMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(DuckDbMcpTools.class, PREFIX_JFR,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new DuckDbMcpTools(scope.dataSource(), profileId, advertised))),
                ProfileScopedToolset.leased(FlamegraphMcpTools.class, PREFIX_FLAMEGRAPH,
                        profileId -> scoped(contextCache, profileId, scope -> new FlamegraphMcpTools(
                                scope.profileManager(),
                                jfrPanelProvider,
                                stackSamplePanelProvider,
                                advertised))),
                ProfileScopedToolset.leased(CompareMcpTools.class, PREFIX_COMPARE,
                        profileId -> scoped(contextCache, profileId, scope -> new CompareMcpTools(
                                scope.profileManager(), scope::profileManager, clock, advertised))),
                ProfileScopedToolset.leased(TracesMcpTools.class, PREFIX_TRACES,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new TracesMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(JvmMcpTools.class, PREFIX_JVM,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new JvmMcpTools(scope.profileManager(), autoAnalysisRuns,
                                        () -> contextCache.acquire(profileId), advertised))),
                ProfileScopedToolset.leased(HttpMcpTools.class, PREFIX_HTTP,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new HttpMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(JdbcMcpTools.class, PREFIX_JDBC,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new JdbcMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(GrpcMcpTools.class, PREFIX_GRPC,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new GrpcMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(MethodTracingMcpTools.class, PREFIX_METHOD_TRACING,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new MethodTracingMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(IoMcpTools.class, PREFIX_IO,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new IoMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(BlockingMcpTools.class, PREFIX_BLOCKING,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new BlockingMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(TimelineMcpTools.class, PREFIX_TIMELINE,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new TimelineMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(MemoryMcpTools.class, PREFIX_MEMORY,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new MemoryMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(TraceAttributesMcpTools.class, PREFIX_TRACES,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new TraceAttributesMcpTools(scope.profileManager(), advertised))),
                ProfileScopedToolset.leased(HeapDiffMcpTools.class, PREFIX_HEAP,
                        profileId -> scoped(contextCache, profileId, scope -> new HeapDiffMcpTools(
                                scope.profileManager(), scope::profileManager, advertised))),
                ProfileScopedToolset.leased(HeapOqlMcpTools.class, PREFIX_HEAP,
                        profileId -> scoped(contextCache, profileId, scope -> {
                            // OQL reads the same index, so it is refused on the same terms.
                            requireIndexedHeapDump(scope.profileManager(), profileId);
                            return new HeapOqlMcpTools(scope.profileManager(), retainedOqlRuns,
                                    () -> contextCache.acquire(profileId), advertised);
                        })),
                ProfileScopedToolset.leased(HeapDumpMcpTools.class, PREFIX_HEAP,
                        profileId -> scoped(contextCache, profileId,
                                scope -> heapTools(scope.profileManager(), profileId, advertised))),
                ProfileScopedToolset.leased(HeapComputeMcpTools.class, PREFIX_HEAP,
                        profileId -> scoped(contextCache, profileId,
                                scope -> new HeapComputeMcpTools(
                                        scope.profileManager(),
                                        heapDumpInitService,
                                        () -> contextCache.acquire(profileId), operations, answers, advertised)),
                        McpToolAnnotations.CREATES),
                new ReflectiveToolset(
                        recordingsMcpTools, PREFIX_RECORDINGS, McpToolAnnotations.CREATES)));

        if (properties.ideEnabled()) {
            // Read-only as a family: three of its five tools observe. The two that do not — linking
            // a window and opening a file — say so themselves with @McpToolHints, which is what that
            // annotation is for.
            families.add(ProfileScopedToolset.leased(IdeMcpTools.class, PREFIX_IDE,
                    profileId -> scoped(contextCache, profileId, scope -> new IdeMcpTools(
                            ideBridge,
                            scope.profileManager(),
                            recordingCommitResolver,
                            profileId,
                            advertised)),
                    McpToolAnnotations.READS_REMOTE));
        }

        if (properties.hubsEnabled()) {
            families.add(new ReflectiveToolset(
                    hubsMcpTools, PREFIX_HUBS, McpToolAnnotations.READS_REMOTE));
            // Read-only as a family: hubs_files observes. hubs_fetchFile puts a file on this disk and
            // says so itself with @McpToolHints, the way hubs_download does.
            families.add(new ReflectiveToolset(
                    hubsArtifactsMcpTools, PREFIX_HUBS, McpToolAnnotations.READS_REMOTE));
        }

        this.toolset = new CompositeToolset(retained(families, properties));
        // Read through a call scope like any tool call, so a document read pins the profile for as
        // long as it runs and releases it after, and an unknown id is the same not-found a tool gets.
        this.documents = new McpProfileDocuments(
                profileId -> read(contextCache, profileId,
                        scope -> new ProfileSchemaReader(scope.dataSource()).read(profileId)),
                profileId -> read(contextCache, profileId,
                        scope -> new ProfileFindingsReader(scope.profileManager(), jfrPanelProvider,
                                stackSamplePanelProvider, advertised).read()));
    }

    public McpToolProvider toolset() {
        return toolset;
    }

    /**
     * The per-profile documents {@code McpResources} serves beside the tool-backed resources. Whether
     * each is offered is the resources' decision, made on the families the toolset serves.
     */
    public McpProfileDocuments documents() {
        return documents;
    }

    /** One read of one profile, holding its lease for exactly as long as the read runs. */
    private static <T> T read(
            McpProfileContextCache contextCache, String profileId, Function<ProfileCallScope, T> read) {
        try (ProfileCallScope scope = new ProfileCallScope(contextCache, profileId)) {
            return read.apply(scope);
        }
    }

    /**
     * Whether {@code operations_status} and {@code operations_cancel} -- and the tasks extension, which
     * reads the same operations -- may reach an operation of this kind: only when the family that
     * started it is served, and -- for the two hub kinds -- only when the hub switch is on, since the
     * family filter alone does not know about that switch.
     */
    public static boolean reachable(OperationKind kind, ExternalMcpProperties properties) {
        if (kind.reachesHub() && !properties.hubsEnabled()) {
            return false;
        }
        return properties.advertises(kind.family());
    }

    /**
     * The families this installation actually serves.
     * <p>
     * Filtering happens here, on assembled families, rather than at each registration: the list above
     * reads as the whole surface, and what a particular installation withholds is one decision applied
     * once. Unknown family and preset names are rejected by the properties at startup; the independent
     * hub and IDE switches may still withhold a known family selected by a preset or explicit list.
     */
    private static List<McpToolProvider> retained(
            List<McpToolProvider> families, ExternalMcpProperties properties) {
        List<McpToolProvider> retained = new ArrayList<>();
        for (McpToolProvider family : families) {
            if (properties.advertises(prefixOf(family))) {
                retained.add(family);
            }
        }
        return List.copyOf(retained);
    }

    /**
     * The family name a provider answers to, taken from the first tool it advertises — every tool in a
     * family carries the prefix, so the first one names the family.
     */
    private static String prefixOf(McpToolProvider family) {
        List<McpToolSpec> specs = family.specs();
        if (specs.isEmpty()) {
            return "";
        }
        return McpToolNames.familyOf(specs.getFirst().name());
    }

    private static <T> ProfileScopedToolset.ScopedTarget<T> scoped(
            McpProfileContextCache contextCache,
            String profileId,
            Function<ProfileCallScope, T> targetFactory) {
        ProfileCallScope scope = new ProfileCallScope(contextCache, profileId);
        try {
            T target = targetFactory.apply(scope);
            return new ProfileScopedToolset.ScopedTarget<>() {
                @Override
                public T target() {
                    return target;
                }

                @Override
                public void close() {
                    scope.close();
                }
            };
        } catch (RuntimeException | Error e) {
            scope.close();
            throw e;
        }
    }

    /**
     * The heap family, refused up front for a profile that has no heap dump to read.
     * <p>
     * Checked once here rather than in each of the twenty heap tools: without it every one of them
     * fails deep inside the engine with a null-dereference message that says nothing about the actual
     * problem, which is that this profile is a JFR recording and the model asked the wrong family.
     */
    private static HeapDumpMcpTools heapTools(
            ProfileManager profileManager, String profileId, AdvertisedFamilies advertised) {
        return new HeapDumpMcpTools(new HeapDumpManagerToolsDelegate(
                requireIndexedHeapDump(profileManager, profileId)), profileId, advertised);
    }

    /**
     * The heap dump the {@code heap_} reading tools — {@link HeapDumpMcpTools} and
     * {@link HeapOqlMcpTools} alike — answer from, refused for a JFR recording or a dump not yet indexed.
     */
    private static HeapDumpManager requireIndexedHeapDump(ProfileManager profileManager, String profileId) {
        HeapDumpManager heapDumpManager = profileManager.heapDumpManager();
        if (!heapDumpManager.heapDumpExists()) {
            throw new IllegalArgumentException(
                    "Profile " + profileId + " has no heap dump. Use profiles_features to see what a "
                            + "profile can answer; for a JFR recording use the jfr_, flamegraph_ and "
                            + "traces_ tools instead.");
        }
        if (!heapDumpManager.isCacheReady()) {
            throw new IllegalArgumentException(
                    "The heap dump of profile " + profileId + " is not indexed yet, and the analysis "
                            + "tools read the index rather than the dump. Call heap_prepare to build it "
                            + "and heap_status to follow it; the tools answer once it reports ready.");
        }
        return heapDumpManager;
    }

    static final class ProfileCallScope implements AutoCloseable {

        private final McpProfileContextCache contextCache;
        private final List<McpProfileContextCache.Lease> leases = new ArrayList<>();
        private final McpProfileContextCache.Lease primary;

        ProfileCallScope(McpProfileContextCache contextCache, String profileId) {
            this.contextCache = contextCache;
            this.primary = acquire(profileId);
        }

        ProfileManager profileManager() {
            return primary.profileManager();
        }

        ProfileManager profileManager(String profileId) {
            return acquire(profileId).profileManager();
        }

        private DataSource dataSource() {
            return primary.dataSource();
        }

        private McpProfileContextCache.Lease acquire(String profileId) {
            McpProfileContextCache.Lease lease = contextCache.acquire(profileId);
            leases.add(lease);
            return lease;
        }

        @Override
        public void close() {
            Throwable failure = null;
            for (int i = leases.size() - 1; i >= 0; i--) {
                try {
                    leases.get(i).close();
                } catch (RuntimeException | Error e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof Error error) {
                throw error;
            }
        }
    }
}

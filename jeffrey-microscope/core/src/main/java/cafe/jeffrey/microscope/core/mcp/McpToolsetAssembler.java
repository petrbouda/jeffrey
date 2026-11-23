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
import cafe.jeffrey.microscope.core.mcp.tools.CodeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.CodeSurfaceMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.CompareMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.EventTypeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.FlamegraphMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.GrpcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapComputeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDiffMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpProfiles;
import cafe.jeffrey.microscope.core.mcp.tools.HeapOqlMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HttpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ConnectorArtifactsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ConnectorMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IdeMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.IoMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JdbcMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.JvmMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MemoryMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.MethodTracingMcpTools;
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
import cafe.jeffrey.microscope.core.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.microscope.core.profile.manager.ProfileManager;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.microscope.core.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.microscope.mcp.server.McpToolNames;
import cafe.jeffrey.microscope.mcp.server.ProfileScopedToolset;
import cafe.jeffrey.microscope.mcp.server.ReflectiveToolset;
import cafe.jeffrey.microscope.core.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.microscope.core.profile.panel.StackSampleFlamegraphPanelProvider;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.BLOCKING;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.CODE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.COMPARE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.CONNECTOR;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.FLAMEGRAPH;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.GRPC;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.HEAP;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.HTTP;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.IDE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.IO;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JDBC;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JFR;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.JVM;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.MEMORY;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.METHOD_TRACING;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.PROFILES;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.RECORDINGS;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.TIMELINE;
import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamilies.TRACES;

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
 * {@code heap_oql} with {@code includeRetainedSize} — run synchronously on the call and stream
 * {@code notifications/progress} the way the writers' long work does, and say so per tool with
 * {@code @McpToolHints}: they fill a cache rather than only reading one.
 * <p>
 * {@link ConnectorMcpTools} and {@link IdeMcpTools} are the two families gated by configuration, because
 * they are the two that reach outside this server: the connector family leaves the machine altogether, and
 * the IDE family reaches into another process on it and can put a file on the developer's screen.
 */
public class McpToolsetAssembler {


    private final McpToolProvider toolset;
    private final McpProfileDocuments documents;

    public McpToolsetAssembler(
            ProfilesMcpTools profilesMcpTools,
            RecordingsMcpTools recordingsMcpTools,
            ConnectorMcpTools connectorMcpTools,
            McpProfileContextCache contextCache,
            JfrFlamegraphPanelProvider jfrPanelProvider,
            StackSampleFlamegraphPanelProvider stackSamplePanelProvider,
            RecordingCommitResolver recordingCommitResolver,
            HeapDumpInitService heapDumpInitService,
            IdeBridge ideBridge,
            ExternalMcpProperties properties,
            AdvertisedFamilies advertised,
            ConnectorArtifactsMcpTools connectorArtifactsMcpTools,
            Clock clock) {

        ProfileFamilies onProfile = new ProfileFamilies(contextCache, advertised);
        List<McpToolProvider> families = new ArrayList<>(List.of(
                new ReflectiveToolset(profilesMcpTools, PROFILES),
                onProfile.of(ProfileEvidenceMcpTools.class, PROFILES, (scope, profileId) -> new ProfileEvidenceMcpTools(
                        scope.profileManager(), recordingCommitResolver, jfrPanelProvider,
                        stackSamplePanelProvider, clock, advertised)),
                onProfile.of(ProfileMcpTools.class, PROFILES, (scope, profileId) -> new ProfileMcpTools(
                        scope.profileManager(), recordingCommitResolver, jfrPanelProvider,
                        stackSamplePanelProvider, advertised)),
                onProfile.onManager(EventTypeMcpTools.class, JFR, EventTypeMcpTools::new),
                onProfile.of(DuckDbMcpTools.class, JFR,
                        (scope, profileId) -> new DuckDbMcpTools(scope.dataSource(), profileId, advertised)),
                onProfile.of(FlamegraphMcpTools.class, FLAMEGRAPH, (scope, profileId) -> new FlamegraphMcpTools(
                        scope.profileManager(), jfrPanelProvider, stackSamplePanelProvider, advertised)),
                onProfile.onManager(CodeMcpTools.class, CODE, CodeMcpTools::new),
                onProfile.onManager(CodeSurfaceMcpTools.class, CODE, CodeSurfaceMcpTools::new),
                onProfile.of(CompareMcpTools.class, COMPARE, (scope, profileId) -> new CompareMcpTools(
                        scope.profileManager(), scope::profileManager, clock, advertised)),
                onProfile.onManager(TracesMcpTools.class, TRACES, TracesMcpTools::new),
                onProfile.of(JvmMcpTools.class, JVM, (scope, profileId) -> new JvmMcpTools(
                        scope.profileManager(), advertised)),
                onProfile.onManager(HttpMcpTools.class, HTTP, HttpMcpTools::new),
                onProfile.onManager(JdbcMcpTools.class, JDBC, JdbcMcpTools::new),
                onProfile.onManager(GrpcMcpTools.class, GRPC, GrpcMcpTools::new),
                onProfile.onManager(MethodTracingMcpTools.class, METHOD_TRACING, MethodTracingMcpTools::new),
                onProfile.onManager(IoMcpTools.class, IO, IoMcpTools::new),
                onProfile.onManager(BlockingMcpTools.class, BLOCKING, BlockingMcpTools::new),
                onProfile.onManager(TimelineMcpTools.class, TIMELINE, TimelineMcpTools::new),
                onProfile.onManager(MemoryMcpTools.class, MEMORY, MemoryMcpTools::new),
                onProfile.onManager(TraceAttributesMcpTools.class, TRACES, TraceAttributesMcpTools::new),
                onProfile.of(HeapDiffMcpTools.class, HEAP, (scope, profileId) -> new HeapDiffMcpTools(
                        scope.profileManager(), scope::profileManager, advertised)),
                onProfile.of(HeapOqlMcpTools.class, HEAP, (scope, profileId) -> {
                    // OQL reads the same index, so it is refused on the same terms.
                    requireIndexedHeapDump(scope.profileManager(), profileId);
                    return new HeapOqlMcpTools(scope.profileManager(), advertised);
                }),
                onProfile.of(HeapDumpMcpTools.class, HEAP,
                        (scope, profileId) -> heapTools(scope.profileManager(), profileId, advertised)),
                onProfile.of(HeapComputeMcpTools.class, HEAP, (scope, profileId) -> new HeapComputeMcpTools(
                                scope.profileManager(), heapDumpInitService, advertised),
                        McpToolAnnotations.CREATES),
                new ReflectiveToolset(recordingsMcpTools, RECORDINGS, McpToolAnnotations.CREATES)));

        if (properties.ideEnabled()) {
            // Read-only as a family: three of its five tools observe. The two that do not — linking
            // a window and opening a file — say so themselves with @McpToolHints, which is what that
            // annotation is for.
            families.add(onProfile.of(IdeMcpTools.class, IDE, (scope, profileId) -> new IdeMcpTools(
                            ideBridge, scope.profileManager(), recordingCommitResolver, profileId, advertised),
                    McpToolAnnotations.READS_OUTSIDE));
        }

        if (properties.connectorEnabled()) {
            families.add(new ReflectiveToolset(
                    connectorMcpTools, CONNECTOR, McpToolAnnotations.READS_OUTSIDE));
            // Read-only as a family: connector_files observes. connector_fetchFile puts a file on this disk and
            // says so itself with @McpToolHints, the way connector_download does.
            families.add(new ReflectiveToolset(
                    connectorArtifactsMcpTools, CONNECTOR, McpToolAnnotations.READS_OUTSIDE));
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
     * The families this installation actually serves.
     * <p>
     * Filtering happens here, on assembled families, rather than at each registration: the list above
     * reads as the whole surface, and what a particular installation withholds is one decision applied
     * once. Unknown family and preset names are rejected by the properties at startup; the independent
     * connector and IDE switches may still withhold a known family selected by a preset or explicit list.
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
     * {@link HeapOqlMcpTools} alike — answer from, refused for a profile that is not a heap dump or a
     * dump not yet indexed.
     */
    private static HeapDumpManager requireIndexedHeapDump(ProfileManager profileManager, String profileId) {
        HeapDumpManager heapDumpManager = HeapDumpProfiles.require(profileManager);
        if (!heapDumpManager.isCacheReady()) {
            throw new IllegalArgumentException(
                    "The heap dump of profile " + profileId + " is not indexed yet, and the analysis "
                            + "tools read the index rather than the dump. Call heap_prepare to build it "
                            + "and heap_status to follow it; the tools answer once it reports ready.");
        }
        return heapDumpManager;
    }

    /**
     * Builds the families that read one profile: every call leases the profile for as long as it runs and
     * builds the family's tool object over it.
     */
    private record ProfileFamilies(McpProfileContextCache contextCache, AdvertisedFamilies advertised) {

        <T> ProfileScopedToolset<T> of(Class<T> type, String family, ProfileToolFactory<T> factory) {
            return ProfileScopedToolset.leased(type, family, resolver(factory));
        }

        <T> ProfileScopedToolset<T> of(
                Class<T> type, String family, ProfileToolFactory<T> factory, McpToolAnnotations annotations) {
            return ProfileScopedToolset.leased(type, family, resolver(factory), annotations);
        }

        /** A family whose tools read nothing but the profile's manager and the families advertised. */
        <T> ProfileScopedToolset<T> onManager(
                Class<T> type, String family, BiFunction<ProfileManager, AdvertisedFamilies, T> constructor) {
            return of(type, family, (scope, profileId) -> constructor.apply(scope.profileManager(), advertised));
        }

        private <T> ProfileScopedToolset.ScopedTargetResolver<T> resolver(ProfileToolFactory<T> factory) {
            return profileId -> scoped(contextCache, profileId, scope -> factory.create(scope, profileId));
        }
    }

    /** The tool object of a family, built over the scope of one call on one profile. */
    @FunctionalInterface
    private interface ProfileToolFactory<T> {
        T create(ProfileCallScope scope, String profileId);
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

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

import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsReplayMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolsetAssemblerTest {

    @Mock
    MicroscopeCoreRepositories coreRepositories;

    @Mock
    RecordingsManager recordingsManager;

    @Mock
    McpProfileContextCache contextCache;

    @Mock
    JfrFlamegraphPanelProvider jfrPanelProvider;

    @Mock
    StackSampleFlamegraphPanelProvider stackSamplePanelProvider;

    @Mock
    RecordingCommitResolver recordingCommitResolver;

    @Mock
    HubsManager hubsManager;

    @Mock
    ProjectManagerResolver projectManagerResolver;

    @Mock
    IdeBridge ideBridge;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private McpToolsetAssembler assembler(boolean hubsEnabled) {
        return assembler(new ExternalMcpProperties(true, hubsEnabled, true, Set.of()));
    }

    private McpToolsetAssembler assembler(ExternalMcpProperties properties) {
        return new McpToolsetAssembler(
                new ProfilesMcpTools(coreRepositories),
                new RecordingsMcpTools(recordingsManager, new PipelineRunRegistry<>(
                        ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK)),
                new HubsMcpTools(hubsManager, projectManagerResolver, recordingsManager, CLOCK),
                contextCache,
                jfrPanelProvider,
                stackSamplePanelProvider,
                recordingCommitResolver,
                new HeapDumpInitService(CLOCK),
                ideBridge,
                properties, new HubsReplayMcpTools(projectManagerResolver, new McpOperationRegistry(CLOCK)), new McpOperationRegistry(CLOCK));
    }

    private List<String> toolNames(boolean hubsEnabled) {
        return assembler(hubsEnabled).toolset().specs().stream()
                .map(McpToolSpec::name)
                .toList();
    }

    @Test
    void callScopePinsThePrimaryAndBaselineUntilTheInvocationEnds() {
        ProfileManager primaryManager = mock(ProfileManager.class);
        ProfileManager baselineManager = mock(ProfileManager.class);
        McpProfileContextCache.Lease primary = mock(McpProfileContextCache.Lease.class);
        McpProfileContextCache.Lease baseline = mock(McpProfileContextCache.Lease.class);
        when(primary.profileManager()).thenReturn(primaryManager);
        when(baseline.profileManager()).thenReturn(baselineManager);
        when(contextCache.acquire("primary")).thenReturn(primary);
        when(contextCache.acquire("baseline")).thenReturn(baseline);

        McpToolsetAssembler.ProfileCallScope scope =
                new McpToolsetAssembler.ProfileCallScope(contextCache, "primary");

        assertSame(primaryManager, scope.profileManager());
        assertSame(baselineManager, scope.profileManager("baseline"));
        verify(primary, never()).close();
        verify(baseline, never()).close();

        scope.close();

        verify(primary).close();
        verify(baseline).close();
    }

    @Test
    void callScopeReleasesEveryLeaseWhenMoreThanOneReleaseFails() {
        McpProfileContextCache.Lease primary = mock(McpProfileContextCache.Lease.class);
        McpProfileContextCache.Lease baseline = mock(McpProfileContextCache.Lease.class);
        when(contextCache.acquire("primary")).thenReturn(primary);
        when(contextCache.acquire("baseline")).thenReturn(baseline);
        doThrow(new IllegalStateException("primary release failed")).when(primary).close();
        doThrow(new IllegalStateException("baseline release failed")).when(baseline).close();
        McpToolsetAssembler.ProfileCallScope scope =
                new McpToolsetAssembler.ProfileCallScope(contextCache, "primary");
        scope.profileManager("baseline");

        IllegalStateException failure = assertThrows(IllegalStateException.class, scope::close);

        assertEquals("baseline release failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("primary release failed", failure.getSuppressed()[0].getMessage());
        verify(primary).close();
        verify(baseline).close();
    }

    /**
     * Everything except the {@code hubs_} family is advertised unconditionally. Ingestion and the
     * heap-compute tools used to have switches of their own; both were dropped because neither bounded
     * what it claimed to — an installation could still spend a core on {@code jfr_executeQuery} — while
     * withholding compute left the heap family telling the reader to go and use the browser.
     */
    @Nested
    class AlwaysAdvertised {

        @Test
        void advertisesTheHeapComputeTools() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("heap_prepare"));
            assertTrue(names.contains("heap_status"));
        }

        @Test
        void advertisesTheRecordingsFamily() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("recordings_analyzeFile"));
            assertTrue(names.contains("recordings_analyzeRecording"));
            assertTrue(names.contains("recordings_list"));
        }

        @Test
        void keepsEveryReadOnlyFamily() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("profiles_list"));
            assertTrue(names.contains("flamegraph_list"));
            assertTrue(names.contains("flamegraph_export"));
            assertTrue(names.contains("traces_notifications"));
            assertTrue(names.contains("heap_getHeapSummary"));
        }

        /**
         * The machine-level family is the only route to garbage collection, safepoints, JIT
         * compilation, threads, native memory and the container: no other family covers them, and
         * without these tools the questions fall back to hand-written SQL.
         */
        @Test
        void advertisesTheMachineLevelFamily() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("jvm_sections"));
            assertTrue(names.contains("jvm_autoAnalysis"));
            assertTrue(names.contains("jvm_gc"));
            assertTrue(names.contains("jvm_safepoints"));
            assertTrue(names.contains("jvm_jit"));
            assertTrue(names.contains("jvm_threads"));
            assertTrue(names.contains("jvm_nativeMemory"));
            assertTrue(names.contains("jvm_container"));
            assertTrue(names.contains("jvm_configuration"));
        }
    }

    @Nested
    class HubsEnabled {

        @Test
        void advertisesTheHubsFamily() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("hubs_list"));
            assertTrue(names.contains("hubs_sessions"));
            assertTrue(names.contains("hubs_download"));
            assertTrue(names.contains("hubs_eventActivity"));
            assertTrue(names.contains("hubs_activityStatus"));
            assertTrue(names.contains("hubs_activityCancel"));
        }

        @Test
        void keepsEveryOtherFamily() {
            List<String> names = toolNames(true);

            assertTrue(names.contains("profiles_list"));
            assertTrue(names.contains("recordings_list"));
            assertTrue(names.contains("heap_getHeapSummary"));
        }
    }

    @Nested
    class HubsDisabled {

        @Test
        void advertisesNoHubsTool() {
            List<String> names = toolNames(false);

            assertTrue(names.stream().noneMatch(name -> name.startsWith("hubs_")), names.toString());
        }

        @Test
        void andRefusesOneCalledByName() {
            McpToolsetAssembler assembler = assembler(false);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> assembler.toolset().call("hubs_sessions", null));
        }

        @Test
        void leavesTheRecordingsFamilyUntouched() {
            List<String> names = toolNames(false);

            assertTrue(names.contains("recordings_analyzeFile"));
            assertTrue(names.contains("recordings_list"));
        }
    }

    /**
     * The routing this server carries is only as good as the names in it. A description or a
     * nextSteps line that points at a tool which does not exist sends the reader nowhere and looks
     * exactly like one that works — three such references survived in the pre-prefix SQL families
     * until this test was written, and a rename would have created more.
     */
    @Nested
    class ToolReferences {

        /** {@code family_toolName} as it appears inside prose. */
        private static final Pattern REFERENCE = Pattern.compile("\\b([a-z][a-z]*_[a-zA-Z][a-zA-Z0-9]*)\\b");

        /** Names that look like a tool reference and are not: deliberate counter-examples. */
        private static final Set<String> NOT_REFERENCES = Set.of(
                "jfr_list_tables", "heap_get_leak_suspects", "compare_movements_list");

        private List<McpToolSpec> specs() {
            return assembler(true).toolset().specs();
        }

        @Test
        void everyToolNamedInADescriptionIsAToolThatExists() {
            List<McpToolSpec> specs = specs();
            Set<String> registered = specs.stream().map(McpToolSpec::name).collect(Collectors.toSet());
            Set<String> prefixes = registered.stream()
                    .map(name -> name.substring(0, name.indexOf('_')))
                    .collect(Collectors.toSet());

            List<String> dangling = new ArrayList<>();
            for (McpToolSpec spec : specs) {
                Matcher matcher = REFERENCE.matcher(spec.description());
                while (matcher.find()) {
                    String reference = matcher.group(1);
                    if (NOT_REFERENCES.contains(reference) || registered.contains(reference)) {
                        continue;
                    }
                    // Only a token whose prefix is a real family is claiming to be a tool; anything
                    // else is ordinary prose that happens to contain an underscore.
                    if (prefixes.contains(reference.substring(0, reference.indexOf('_')))) {
                        dangling.add(spec.name() + " -> " + reference);
                    }
                }
            }

            assertTrue(dangling.isEmpty(), "Descriptions naming tools that do not exist: " + dangling);
        }

        /**
         * Every family the assembler registers has to be reachable from the schema too: a tool whose
         * arguments are undocumented is as unusable as one that does not exist.
         */
        @Test
        void everyRegisteredToolCarriesADescription() {
            List<String> undescribed = specs().stream()
                    .filter(spec -> spec.description() == null || spec.description().isBlank())
                    .map(McpToolSpec::name)
                    .toList();

            assertTrue(undescribed.isEmpty(), "Tools without a description: " + undescribed);
        }
    }

    /**
     * What each tool tells a client it will do. This is the contract a person reads in an approval
     * prompt before they say yes, so it is the one place where a wrong answer costs more than a
     * confusing one — and it was wrong: {@code hubs_download} inherited its family's read-only hint
     * and offered a multi-gigabyte cross-machine transfer as a safe read.
     */
    @Nested
    class Annotations {

        /**
         * Everything that does not only read. Asserted as a set rather than one tool at a time, so a
         * <em>new</em> write tool that forgets its {@code @McpToolHints} fails here too — the way
         * this family did.
         */
        private static final Set<String> WRITES = Set.of(
                "recordings_analyzeFile",
                "recordings_analyzeRecording",
                "heap_prepare",
                "hubs_download",
                "hubs_eventActivity",
                "hubs_activityCancel",
                "operations_cancel",
                "ide_link",
                "ide_open");

        /** The two families that reach past this installation: another machine, and the editor beside it. */
        private static final Set<String> REMOTE_PREFIXES = Set.of("hubs", "ide");

        private List<McpToolSpec> specs() {
            return assembler(true).toolset().specs();
        }

        @Test
        void exactlyTheToolsThatWriteSayTheyWrite() {
            Set<String> declared = specs().stream()
                    .filter(spec -> !spec.annotations().readOnly())
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());

            assertEquals(WRITES, declared);
        }

        /**
         * {@code openWorldHint} is the other half of what a client shows: not "does this change
         * something" but "does it leave this machine".
         */
        @Test
        void exactlyTheRemoteFamiliesSayTheyReachOutside() {
            Set<String> declared = specs().stream()
                    .filter(spec -> spec.annotations().openWorld())
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());

            Set<String> remote = specs().stream()
                    .map(McpToolSpec::name)
                    .filter(name -> REMOTE_PREFIXES.contains(name.substring(0, name.indexOf('_'))))
                    .collect(Collectors.toSet());

            remote.add("operations_cancel");
            remote.add("operations_status");
            assertEquals(remote, declared);
        }

        /**
         * The documentation says it in as many words: nothing here deletes a profile, a recording or
         * a dump. A tool that ever needs to would have to change that sentence as well as this test.
         */
        @Test
        void nothingIsDestructive() {
            List<String> destructive = specs().stream()
                    .filter(spec -> spec.annotations().destructive())
                    .map(McpToolSpec::name)
                    .toList();

            assertTrue(destructive.isEmpty(), "Tools claiming to be destructive: " + destructive);
        }

        /**
         * A family switched off cannot mislead anyone, but the tools that remain still have to be
         * described correctly — the filter must not take the hints with it.
         */
        @Test
        void keepsTheHintsWhenAFamilyIsSwitchedOff() {
            Set<String> declared = assembler(false).toolset().specs().stream()
                    .filter(spec -> !spec.annotations().readOnly())
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());

            assertFalse(declared.contains("hubs_download"));
            assertTrue(declared.contains("heap_prepare"));
            assertTrue(declared.contains("ide_open"));
        }
    }

    @Nested
    class FamilyFilter {

        /**
         * A client that pays for every schema on every turn can be given only what it uses.
         */
        @Test
        void advertisesOnlyTheNamedFamilies() {
            McpToolsetAssembler assembler = assembler(new ExternalMcpProperties(
                    true, true, true, Set.of("profiles", "flamegraph")));

            Set<String> prefixes = assembler.toolset().specs().stream()
                    .map(spec -> spec.name().substring(0, spec.name().indexOf('_')))
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(Set.of("profiles", "flamegraph"), prefixes);
        }

        @Test
        void anEmptyFilterKeepsEverything() {
            McpToolsetAssembler filtered = assembler(
                    new ExternalMcpProperties(true, true, true, Set.of()));

            assertEquals(assembler(true).toolset().specs().size(),
                    filtered.toolset().specs().size());
        }
    }

    @Nested
    class Presets {

        @Test
        void defaultAllRetainsEveryFamilyAndEachPresetKeepsDiscovery() {
            Set<String> all = families(assembler(true));
            assertEquals(Set.of("profiles", "recordings", "jfr", "flamegraph", "compare", "traces",
                    "jvm", "http", "jdbc", "grpc", "methodtracing", "io", "blocking", "timeline",
                    "memory", "heap", "hubs", "ide", "operations"), all);
            assertEquals(all, families(preset("all")));
            assertEquals(Set.of("profiles", "recordings", "jfr", "flamegraph", "jvm", "compare", "operations"),
                    families(preset("jfr")));
            assertEquals(Set.of("profiles", "recordings", "heap", "operations"), families(preset("heap")));
            assertEquals(Set.of("profiles", "recordings", "hubs", "operations"), families(preset("hub")));
        }

        /**
         * The default serves whatever the assembler builds, so a family missing from the registry is
         * still advertised -- it just cannot be named or reached through a preset. Nothing about the
         * served tool list would look wrong, which is why the two sets are compared here instead.
         */
        @Test
        void everyBuiltFamilyIsANameAReaderCanSelect() {
            assertEquals(ExternalMcpProperties.knownFamilies(), families(assembler(true)),
                    "a family the assembler builds must also be registered in ExternalMcpProperties, "
                            + "or no preset can include it and naming it is rejected as unknown");
        }

        @Test
        void explicitFamiliesOverridePresetAndSwitchesStillConstrainThem() {
            McpToolsetAssembler filtered = assembler(new ExternalMcpProperties(
                    true, false, false, Set.of("jfr", "ide", "hubs"), "heap"));
            assertEquals(Set.of("jfr"), families(filtered));
            assertThrows(IllegalArgumentException.class,
                    () -> filtered.toolset().call("heap_status", Json.createObject()));
        }

        @Test
        void reportsActualToolListWireBytesAndCountsForEveryPreset() {
            int defaultBytes = toolListBytes("all");
            for (String preset : List.of("jfr", "heap", "hub")) {
                assertTrue(toolListBytes(preset) < defaultBytes, preset + " should reduce tools/list bytes");
            }
        }

        private int toolListBytes(String preset) {
            ExternalMcpProperties properties = new ExternalMcpProperties(
                    true, true, true, Set.of(), preset);
            McpToolsetAssembler assembler = assembler(properties);
            ExternalMcpController controller = new ExternalMcpController(
                    assembler, properties, new McpRequestGuard(), new McpPromptRegistry(), mock(McpDiagnostics.class));
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("MCP-Protocol-Version", "2025-11-25");
            JsonNode response = controller.handle(Json.readTree(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"),
                    request).getBody();
            assertTrue(response.path("result").path("tools").isArray());
            int count = response.path("result").path("tools").size();
            assertEquals(assembler.toolset().specs().size(), count);
            int bytes = response.toString().getBytes(StandardCharsets.UTF_8).length;
            System.out.printf("MCP preset=%s tools=%d tools/list UTF-8 bytes=%d%n", preset, count, bytes);
            return bytes;
        }

        private McpToolsetAssembler preset(String name) {
            return assembler(new ExternalMcpProperties(true, true, true, Set.of(), name));
        }

        private Set<String> families(McpToolsetAssembler assembler) {
            return assembler.toolset().specs().stream()
                    .map(spec -> spec.name().substring(0, spec.name().indexOf('_')))
                    .collect(Collectors.toSet());
        }
    }

}

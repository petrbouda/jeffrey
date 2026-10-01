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

import cafe.jeffrey.flamegraph.diff.ProfileComparison;
import cafe.jeffrey.flamegraph.export.AiExportConfig;
import cafe.jeffrey.flamegraph.export.FlamegraphAiMarkdownBuilder;
import cafe.jeffrey.frameir.DiffTreeGenerator;
import cafe.jeffrey.frameir.Frame;
import cafe.jeffrey.microscope.core.manager.hub.HubsManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeBridge;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.AutoAnalysisStatus;
import cafe.jeffrey.microscope.core.mcp.tools.HeapDumpMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HeapOqlMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.HubsArtifactsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.OperationKind;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindings;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileManagerFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ToolFixtures;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.mcp.protocol.McpPrompt;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpToolSpec;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.model.SpanInterval;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.model.FrameType;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.heapdump.model.DominatorNode;
import cafe.jeffrey.profile.heapdump.model.DominatorTreeResponse;
import cafe.jeffrey.profile.manager.DifferentialFlamegraphManager;
import cafe.jeffrey.profile.manager.FlamegraphManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.manager.TraceManager;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpManager;
import cafe.jeffrey.profile.manager.model.trace.TraceContext;
import cafe.jeffrey.profile.manager.model.trace.TraceContextSlice;
import cafe.jeffrey.profile.manager.model.trace.TraceDetail;
import cafe.jeffrey.profile.manager.model.trace.TraceExportSource;
import cafe.jeffrey.profile.manager.model.trace.TraceWindow;
import cafe.jeffrey.profile.manager.model.trace.TraceExceptionRow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationGroupRow;
import cafe.jeffrey.profile.manager.model.trace.TraceNotificationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSpanRow;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationSummary;
import cafe.jeffrey.profile.manager.model.trace.TraceOperationThreads;
import cafe.jeffrey.profile.manager.model.trace.TracePause;
import cafe.jeffrey.profile.manager.model.trace.TraceRow;
import cafe.jeffrey.profile.manager.model.trace.TraceSpanRow;
import cafe.jeffrey.profile.mcp.JeffreyMetaKeys;
import cafe.jeffrey.profile.mcp.McpToolCost;
import cafe.jeffrey.profile.mcp.McpToolNames;
import cafe.jeffrey.profile.mcp.McpToolOutput;
import cafe.jeffrey.profile.mcp.McpToolRequirement;
import cafe.jeffrey.profile.mcp.ProfileScopedToolset;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.profile.trace.export.TraceAiMarkdownBuilder;
import cafe.jeffrey.profile.trace.export.TraceOperationAiMarkdownBuilder;
import cafe.jeffrey.provider.profile.api.TraceNotificationListQuery;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyClientException;
import cafe.jeffrey.test.DuckDBTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import javax.sql.DataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
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
        return assembler(McpTestProperties.of(true, hubsEnabled, true, Set.of()));
    }

    private McpToolsetAssembler assembler(ExternalMcpProperties properties) {
        McpOperationRegistry operations = new McpOperationRegistry(CLOCK);
        return new McpToolsetAssembler(
                new ProfilesMcpTools(coreRepositories, EVERY_FAMILY),
                RecordingsMcpToolsFixture.of(recordingsManager, new PipelineRunRegistry<>(
                        ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), CLOCK), operations, CLOCK).build(),
                HubsMcpToolsFixture.of(hubsManager, projectManagerResolver, recordingsManager, CLOCK)
                        .withOperations(operations).build(),
                contextCache,
                jfrPanelProvider,
                stackSamplePanelProvider,
                recordingCommitResolver,
                new HeapDumpInitService(CLOCK),
                ideBridge,
                properties,
                AdvertisedFamilies.of(properties),
                HubsArtifactsMcpToolsFixture.of(projectManagerResolver, recordingsManager, Path.of("artifacts"),
                        Path.of("profiles"), operations, CLOCK, EVERY_FAMILY).build(),
                operations, ToolFixtures.answers(), CLOCK);
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
     * The two profile documents the resources serve read one profile each through the same context
     * cache the tools lease it from, and give the lease back when the read is done.
     */
    @Nested
    class ProfileDocuments {

        @Test
        void readTheFindingsThroughALeaseAndReleaseIt() {
            ProfileManager profile = ProfileManagerFixture.jfrProfile("p-1");
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            when(lease.profileManager()).thenReturn(profile);
            when(contextCache.acquire("p-1")).thenReturn(lease);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                ProfileFindings findings = assembler(false).documents().findingsOf().apply("p-1");

                assertEquals("p-1", findings.profileId());
                assertEquals(AutoAnalysisStatus.CANNOT_COMPUTE, findings.status());
                verify(lease).close();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        /**
         * An id no profile has is the same not-found a tool call gets, so the envelope answers it -32602
         * with the sentence naming it, rather than a document for nothing.
         */
        @Test
        void passOnTheNotFoundOfAnUnknownProfile() {
            JeffreyClientException notFound = Exceptions.profileNotFound("missing");
            when(contextCache.acquire("missing")).thenThrow(notFound);
            McpProfileDocuments documents = assembler(false).documents();

            assertSame(notFound, assertThrows(JeffreyClientException.class,
                    () -> documents.schemaOf().apply("missing")));
            assertSame(notFound, assertThrows(JeffreyClientException.class,
                    () -> documents.findingsOf().apply("missing")));
        }

        @Test
        void releaseTheLeaseWhenTheReadFails() throws SQLException {
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            DataSource broken = mock(DataSource.class);
            when(lease.dataSource()).thenReturn(broken);
            when(contextCache.acquire("p-1")).thenReturn(lease);
            when(broken.getConnection()).thenThrow(new SQLException("gone"));

            assertThrows(RuntimeException.class, () -> assembler(false).documents().schemaOf().apply("p-1"));

            verify(lease).close();
        }
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

    /**
     * OQL reads the same index as the rest of the heap family, so a JFR recording asked an OQL
     * question gets the same refusal naming the families to use instead, rather than a failure from
     * deep inside the engine.
     */
    @Nested
    class HeapGate {

        private Exception oqlOn(boolean heapDumpExists, boolean indexed) {
            ProfileManager profileManager = mock(ProfileManager.class);
            HeapDumpManager heapDumpManager = mock(HeapDumpManager.class);
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            when(lease.profileManager()).thenReturn(profileManager);
            when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
            when(heapDumpManager.heapDumpExists()).thenReturn(heapDumpExists);
            if (heapDumpExists) {
                when(heapDumpManager.isCacheReady()).thenReturn(indexed);
            }
            when(contextCache.acquire("jfr-only")).thenReturn(lease);

            return assertThrows(Exception.class, () -> assembler(true).toolset().call("heap_oql",
                    Json.createObject().put("profileId", "jfr-only").put("query", "SELECT * FROM java.lang.String")));
        }

        @Test
        void refusesOqlOnAProfileWithoutAHeapDump() {
            Exception refusal = oqlOn(false, false);

            assertTrue(refusal.getMessage().contains("has no heap dump"), refusal.getMessage());
            assertTrue(refusal.getMessage().contains("profiles_features"), refusal.getMessage());
        }

        @Test
        void refusesOqlOnADumpNotYetIndexed() {
            Exception refusal = oqlOn(true, false);

            assertTrue(refusal.getMessage().contains("heap_prepare"), refusal.getMessage());
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
            assertTrue(names.contains("hubs_files"));
            assertTrue(names.contains("hubs_fetchFile"));
        }

        @Test
        void theFileToolsGoWithTheHubSwitch() {
            List<String> names = toolNames(false);

            assertFalse(names.contains("hubs_files"));
            assertFalse(names.contains("hubs_fetchFile"));
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

        /** Names that look like tool references: deliberate counter-examples and a SQL alias. */
        private static final Set<String> NOT_REFERENCES = Set.of(
                "jfr_list_tables", "heap_get_leak_suspects", "compare_movements_list", "hubs_list_sessions", "heap_used");

        private List<McpToolSpec> specs() {
            return assembler(true).toolset().specs();
        }

        @Test
        void pluginSkillsNameToolsThatExist() {
            Set<String> registered = Set.copyOf(toolNames(true));
            Set<String> prefixes = registered.stream()
                    .map(McpToolNames::familyOf)
                    .collect(Collectors.toSet());
            List<String> dangling = new ArrayList<>();
            for (McpPrompt prompt : new McpPromptRegistry(McpSkillCatalogue.fromClasspath()).prompts()) {
                Matcher matcher = REFERENCE.matcher(prompt.text());
                while (matcher.find()) {
                    String reference = matcher.group(1);
                    if (!registered.contains(reference) && !NOT_REFERENCES.contains(reference)
                            && prefixes.contains(McpToolNames.familyOf(reference))) {
                        dangling.add(prompt.name() + " -> " + reference);
                    }
                }
            }
            assertTrue(dangling.isEmpty(), "Skills naming tools that do not exist: " + dangling);
        }

        @Test
        void everyToolNamedInADescriptionIsAToolThatExists() {
            List<McpToolSpec> specs = specs();
            Set<String> registered = specs.stream().map(McpToolSpec::name).collect(Collectors.toSet());
            Set<String> prefixes = registered.stream()
                    .map(McpToolNames::familyOf)
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
                    if (prefixes.contains(McpToolNames.familyOf(reference))) {
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
     * Claude Code cuts a tool description at 2048 characters without saying so, and whatever falls
     * past the cut — usually the "for X use Y instead" that decides between two tools — is simply
     * gone. The limits sit well below the cut so that a description is written to be read, not
     * trimmed until it fits: what the tool does, what it returns, what it is not for. How to order
     * calls lives in {@link McpInstructions} and the skills, not here.
     */
    @Nested
    class DescriptionSize {

        /** Longest tool description allowed; Claude Code silently truncates at 2048. */
        private static final int MAX_TOOL_DESCRIPTION_CHARS = 1_500;

        /** Longest parameter description allowed. */
        private static final int MAX_PARAMETER_DESCRIPTION_CHARS = 400;

        private static final String PROPERTIES = "properties";
        private static final String DESCRIPTION = "description";

        @Test
        void noDescriptionExceedsTheHostCutoff() {
            List<String> tooLong = new ArrayList<>();
            for (McpToolSpec spec : assembler(true).toolset().specs()) {
                int length = spec.description().length();
                if (length > MAX_TOOL_DESCRIPTION_CHARS) {
                    tooLong.add(spec.name() + " (" + length + ")");
                }
                JsonNode properties = spec.inputSchema().path(PROPERTIES);
                for (Map.Entry<String, JsonNode> property : properties.properties()) {
                    int parameterLength = property.getValue().path(DESCRIPTION).asString("").length();
                    if (parameterLength > MAX_PARAMETER_DESCRIPTION_CHARS) {
                        tooLong.add(spec.name() + "." + property.getKey() + " (" + parameterLength + ")");
                    }
                }
            }

            assertTrue(tooLong.isEmpty(), "Descriptions longer than the limit: " + tooLong);
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
                "recordings_delete",
                "heap_prepare",
                "hubs_download",
                "hubs_fetchFile",
                "operations_cancel",
                "ide_link",
                "ide_open",
                "jvm_autoAnalysis",
                "heap_oql");

        /** The two families that reach past this installation: another machine, and the editor beside it. */
        private static final Set<String> REMOTE_PREFIXES = Set.of("hubs", "ide");

        private List<McpToolSpec> specs() {
            return assembler(true).toolset().specs();
        }

        /**
         * How many tools this server advertises, and how they split.
         *
         * <p>Pinned because the figures are repeated in prose that no compiler reads: the MCP
         * documentation states them on {@code McpOverviewPage.vue}, {@code McpToolsPage.vue},
         * {@code McpClaudeCodePage.vue}, {@code McpCodexPage.vue}, {@code McpGeminiPage.vue},
         * {@code McpOtherClientsPage.vue} and {@code DocsIndexPage.vue}, and {@code CLAUDE.md} and
         * {@code McpInstructions} repeat the writer count again. Twice in two weeks a family was added
         * and those numbers silently stopped being true. Adding a tool should fail here, with this
         * list in front of whoever added it.
         */
        private static final int ADVERTISED_TOOLS = 111;

        @Test
        void advertisesTheDocumentedNumberOfTools() {
            List<McpToolSpec> specs = specs();
            long readOnly = specs.stream().filter(spec -> spec.annotations().readOnly()).count();

            assertEquals(ADVERTISED_TOOLS, specs.size(),
                    "The tool count changed. Update the figure in the MCP documentation pages named on "
                            + "ADVERTISED_TOOLS, and in the family map on McpToolsPage.vue.");
            assertEquals(WRITES.size(), specs.size() - readOnly);
            assertEquals(ADVERTISED_TOOLS - WRITES.size(), readOnly,
                    "The read-only count changed; DocsIndexPage.vue states it.");
        }

        /**
         * The tools whose ordinary answer can pass Claude Code's inline budget, and so tell the host
         * up front how large an answer to keep inline rather than spill to a file.
         */
        private static final Set<String> LARGE_RESULTS = Set.of(
                "flamegraph_export",
                "compare_flamegraph",
                "traces_traceExport",
                "traces_operationExport",
                "traces_spanFlamegraphExport",
                "jfr_executeQuery",
                "heap_getDominatorTreeRoots");

        private static final String MAX_RESULT_SIZE_KEY = "anthropic/maxResultSizeChars";

        @Test
        void exactlyTheLargeAnswersDeclareTheirSizeToTheHost() {
            Set<String> declared = specs().stream()
                    .filter(spec -> spec.meta().containsKey(MAX_RESULT_SIZE_KEY))
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());

            assertEquals(LARGE_RESULTS, declared);
            for (McpToolSpec spec : specs()) {
                if (LARGE_RESULTS.contains(spec.name())) {
                    assertEquals(McpToolOutput.MAX_CHARS, spec.meta().get(MAX_RESULT_SIZE_KEY).asInt(), spec.name());
                }
            }
        }

        /** A display name for every tool, so a client has something to show that is not the raw name. */
        @Test
        void everyToolCarriesATitle() {
            for (McpToolSpec spec : specs()) {
                assertNotNull(spec.title(), spec.name() + " has no title");
                assertFalse(spec.title().isBlank(), spec.name() + " has a blank title");
            }
        }

        /** The handshake repeats the writer set in prose, which no compiler reads. */
        @Test
        void theInstructionsNameEveryToolThatWrites() {
            String text = McpInstructions.text(
                    AdvertisedFamilies.of(McpTestProperties.of(true, true, true, Set.of())));
            assertTrue(text.contains("Eleven tools are not read-only"), text);
            for (String writer : WRITES) {
                assertTrue(text.contains(writer), writer + " is missing from McpInstructions");
            }
            assertTrue(text.contains("wait up to 45 s, then answer with an operationId"), text);
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
                    .filter(name -> REMOTE_PREFIXES.contains(McpToolNames.familyOf(name)))
                    .collect(Collectors.toSet());

            remote.add("operations_cancel");
            remote.add("operations_status");
            assertEquals(remote, declared);
        }

        /**
         * The documentation says it in as many words: one tool deletes, {@code recordings_delete},
         * and nothing else takes a profile, a recording or a dump away. A second one would have to
         * change that sentence as well as this test.
         */
        @Test
        void onlyRecordingsDeleteIsDestructive() {
            List<String> destructive = specs().stream()
                    .filter(spec -> spec.annotations().destructive())
                    .map(McpToolSpec::name)
                    .toList();

            assertEquals(List.of("recordings_delete"), destructive);
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

    /**
     * What each tool tells a host a call costs and needs, in {@code _meta["jeffrey/cost"]} and
     * {@code _meta["jeffrey/requires"]}. Both are claims about the code, so each is held to it: the slow
     * tools are the ones that start an {@link OperationKind}, and a requirement is declared exactly where
     * the code checks it.
     */
    @Nested
    class Hints {

        /**
         * The tool that starts each kind of operation. Keyed by the enum so a new kind without an entry
         * here fails, rather than a new slow tool going unnoticed.
         */
        private static final Map<OperationKind, String> OPERATION_TOOLS = Map.of(
                OperationKind.RECORDING_IMPORT, "recordings_analyzeFile",
                OperationKind.RECORDING_ANALYSIS, "recordings_analyzeRecording",
                OperationKind.HUB_DOWNLOAD, "hubs_download",
                OperationKind.HUB_FETCH, "hubs_fetchFile",
                OperationKind.HEAP_PREPARE, "heap_prepare",
                OperationKind.HEAP_OQL, "heap_oql",
                OperationKind.JVM_AUTO_ANALYSIS, "jvm_autoAnalysis");

        /** The status a heap report tool answers with until heap_prepare has computed its report. */
        private static final String NOT_RUN_YET = "NOT_RUN_YET";
        private static final String STATUS = "status";

        /** The one heap_ tool outside the index gate that checks the index itself, on both dumps. */
        private static final String HEAP_DIFF = "heap_diff";

        private static final String HEAP_PREPARE = "heap_prepare";
        private static final String AUTO_ANALYSIS = "jvm_autoAnalysis";
        private static final String TRACES_OVERVIEW = "traces_overview";
        private static final Set<String> IDE_RUNNING_TOOLS = Set.of("ide_link");

        /**
         * The tools that find out whether a requirement holds. A probe answers either way, so it does
         * not declare what it probes.
         */
        private static final Set<String> PROBES = Set.of("traces_overview", "hubs_list", "ide_windows");

        private static final String HEAP_FAMILY = "heap";
        private static final String TRACES_FAMILY = "traces";
        private static final String HUBS_FAMILY = "hubs";
        private static final String IDE_FAMILY = "ide";

        private static final String UNINDEXED_PROFILE = "unindexed";
        private static final String PROFILE_ID = "profileId";
        private static final String PROPERTIES = "properties";
        private static final String REQUIRED = "required";
        private static final String TYPE = "type";
        private static final String ENUM = "enum";
        private static final String ARRAY = "array";
        private static final String SAMPLE_TEXT = "1";

        /** A value of each JSON type that any argument of that type accepts at binding. */
        private static final Map<String, JsonNode> SAMPLE_VALUES = Map.of(
                "string", StringNode.valueOf(SAMPLE_TEXT),
                "integer", IntNode.valueOf(1),
                "number", IntNode.valueOf(1),
                "boolean", BooleanNode.FALSE);

        private List<McpToolSpec> specs() {
            return assembler(true).toolset().specs();
        }

        private static Set<String> costed(List<McpToolSpec> specs, McpToolCost cost) {
            return specs.stream()
                    .filter(spec -> cost.name().equals(spec.meta().get(JeffreyMetaKeys.COST).asString()))
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());
        }

        private static List<String> requirements(McpToolSpec spec) {
            JsonNode requires = spec.meta().get(JeffreyMetaKeys.REQUIRES);
            if (requires == null) {
                return List.of();
            }
            return requires.valueStream().map(JsonNode::asString).toList();
        }

        private static Set<String> requiring(List<McpToolSpec> specs, McpToolRequirement requirement) {
            return specs.stream()
                    .filter(spec -> requirements(spec).contains(requirement.name()))
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());
        }

        /** The tools a family class advertises, read from the class alone, with no profile behind it. */
        private static Set<String> toolsOf(Class<?> family) {
            return ProfileScopedToolset.leased(family, HEAP_FAMILY, profileId -> {
                        throw new AssertionError("not called");
                    }).specs().stream()
                    .map(McpToolSpec::name)
                    .collect(Collectors.toSet());
        }

        @Test
        void everyToolDeclaresItsCost() {
            Set<String> names = Arrays.stream(McpToolCost.values()).map(Enum::name).collect(Collectors.toSet());
            List<String> undeclared = specs().stream()
                    .filter(spec -> !spec.meta().containsKey(JeffreyMetaKeys.COST)
                            || !names.contains(spec.meta().get(JeffreyMetaKeys.COST).asString()))
                    .map(McpToolSpec::name)
                    .toList();

            assertTrue(undeclared.isEmpty(), "Tools without a jeffrey/cost: " + undeclared);
        }

        @Test
        void everyOperationKindIsStartedByATool() {
            assertEquals(EnumSet.allOf(OperationKind.class), EnumSet.copyOf(OPERATION_TOOLS.keySet()));
            assertTrue(Set.copyOf(toolNames(true)).containsAll(OPERATION_TOOLS.values()), OPERATION_TOOLS.toString());
        }

        /** SLOW means "can hand back an operation or a task", which only the operation starters can. */
        @Test
        void slowIsExactlyTheToolsThatStartAnOperation() {
            assertEquals(Set.copyOf(OPERATION_TOOLS.values()), costed(specs(), McpToolCost.SLOW));
        }

        @Test
        void requirementsAreSortedAndNamedOnce() {
            for (McpToolSpec spec : specs()) {
                List<String> declared = requirements(spec);
                assertEquals(declared.stream().sorted().distinct().toList(), declared, spec.name());
                assertFalse(spec.meta().containsKey(JeffreyMetaKeys.REQUIRES) && declared.isEmpty(), spec.name());
            }
        }

        /**
         * Every tool the assembler's index gate serves declares the indexed dump it is refused without,
         * and so does heap_diff, which checks both of its dumps itself; nothing else does.
         */
        @Test
        void anIndexedHeapDumpIsRequiredExactlyWhereTheGateOrTheToolChecksIt() {
            Set<String> expected = new TreeSet<>(toolsOf(HeapDumpMcpTools.class));
            expected.addAll(toolsOf(HeapOqlMcpTools.class));
            expected.add(HEAP_DIFF);

            assertEquals(expected, new TreeSet<>(requiring(specs(), McpToolRequirement.HEAP_DUMP_INDEXED)));
        }

        /**
         * The claim, called: on a heap dump that exists but is not indexed, every tool declaring
         * HEAP_DUMP_INDEXED answers that it is not indexed and names heap_prepare.
         */
        @Test
        void everyToolRequiringAnIndexedDumpIsRefusedWithoutOne() {
            ProfileManager profileManager = mock(ProfileManager.class);
            HeapDumpManager heapDumpManager = mock(HeapDumpManager.class);
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            when(lease.profileManager()).thenReturn(profileManager);
            when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(false);
            when(profileManager.info()).thenReturn(heapDumpProfile());
            when(contextCache.acquire(UNINDEXED_PROFILE)).thenReturn(lease);

            McpToolsetAssembler assembler = assembler(true);
            List<McpToolSpec> gated = assembler.toolset().specs().stream()
                    .filter(spec -> requirements(spec).contains(McpToolRequirement.HEAP_DUMP_INDEXED.name()))
                    .toList();
            assertFalse(gated.isEmpty());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                for (McpToolSpec spec : gated) {
                    String answer;
                    try {
                        answer = assembler.toolset().call(spec.name(), minimalArguments(spec));
                    } catch (RuntimeException refusal) {
                        answer = refusal.getMessage();
                    }
                    assertTrue(answer.contains("indexed") && answer.contains(HEAP_PREPARE), spec.name() + ": " + answer);
                }
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        /** The heap dump a heap tool's answer names and links, as the profile reports it. */
        private static ProfileInfo heapDumpProfile() {
            return new ProfileInfo(UNINDEXED_PROFILE, "project-1", "workspace-1", "Heap dump",
                    RecordingEventSource.HEAP_DUMP, Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH,
                    true, false, "recording-1");
        }

        /**
         * The claim, called: on an indexed dump whose reports were never computed, exactly the tools
         * declaring HEAP_REPORTS answer NOT_RUN_YET.
         */
        @Test
        void heapReportsAreRequiredByExactlyTheToolsThatAnswerNotRunYet() {
            ProfileManager profileManager = mock(ProfileManager.class);
            HeapDumpManager heapDumpManager = mock(HeapDumpManager.class);
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            when(lease.profileManager()).thenReturn(profileManager);
            when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
            when(heapDumpManager.heapDumpExists()).thenReturn(true);
            when(heapDumpManager.isCacheReady()).thenReturn(true);
            when(profileManager.info()).thenReturn(heapDumpProfile());
            when(contextCache.acquire(UNINDEXED_PROFILE)).thenReturn(lease);

            McpToolsetAssembler assembler = assembler(true);
            List<McpToolSpec> specs = assembler.toolset().specs();
            Set<String> notRunYet = new TreeSet<>();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                collectNotRunYet(assembler, specs, notRunYet);
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }

            assertFalse(notRunYet.isEmpty());
            assertEquals(notRunYet, new TreeSet<>(requiring(specs, McpToolRequirement.HEAP_REPORTS)));
        }

        private void collectNotRunYet(McpToolsetAssembler assembler, List<McpToolSpec> specs, Set<String> notRunYet) {
            for (McpToolSpec spec : specs) {
                if (!requirements(spec).contains(McpToolRequirement.HEAP_DUMP_INDEXED.name())) {
                    continue;
                }
                JsonNode status;
                try {
                    McpToolResult answer = assembler.toolset().callResult(spec.name(), minimalArguments(spec));
                    status = answer.hasStructuredContent() ? answer.structuredContent().path(STATUS) : null;
                } catch (RuntimeException failure) {
                    // The rest of the family reads a manager with nothing behind it; how it fails is
                    // not the question, only whether it says a report is missing.
                    status = null;
                }
                if (status != null && NOT_RUN_YET.equals(status.asString())) {
                    notRunYet.add(spec.name());
                }
            }
        }

        /** Each of the other requirements, where the tool family checks it. */
        @Test
        void everyOtherRequirementIsDeclaredWhereTheCodeChecksIt() {
            List<McpToolSpec> specs = specs();
            Set<String> names = specs.stream().map(McpToolSpec::name).collect(Collectors.toSet());

            assertEquals(Set.of(HEAP_PREPARE), requiring(specs, McpToolRequirement.HEAP_DUMP));
            assertEquals(Set.of(AUTO_ANALYSIS), requiring(specs, McpToolRequirement.AUTO_ANALYSIS));
            assertEquals(inFamily(names, TRACES_FAMILY).stream()
                            .filter(name -> !TRACES_OVERVIEW.equals(name))
                            .collect(Collectors.toSet()),
                    requiring(specs, McpToolRequirement.TRACES));
            Set<String> hubs = new TreeSet<>(inFamily(names, HUBS_FAMILY));
            hubs.removeAll(PROBES);
            assertEquals(hubs, new TreeSet<>(requiring(specs, McpToolRequirement.HUB)));
            assertEquals(IDE_RUNNING_TOOLS, requiring(specs, McpToolRequirement.IDE_RUNNING));
            Set<String> linked = new TreeSet<>(inFamily(names, IDE_FAMILY));
            linked.removeAll(IDE_RUNNING_TOOLS);
            linked.removeAll(PROBES);
            assertEquals(linked, new TreeSet<>(requiring(specs, McpToolRequirement.IDE_LINKED)));
        }

        /** A probe answers whether the requirement holds, so declaring it would forbid the question. */
        @Test
        void aProbeDeclaresNoRequirement() {
            for (McpToolSpec spec : specs()) {
                if (PROBES.contains(spec.name())) {
                    assertEquals(List.of(), requirements(spec), spec.name());
                }
            }
        }

        /**
         * The instructions give examples of the hints only where an advertised tool carries them:
         * SLOW only for a family with a SLOW tool, and a requirement only for a family that declares
         * it. Checked one family at a time, against what that family's tools actually declare.
         */
        @Test
        void theInstructionsNameOnlyTheHintsTheAdvertisedToolsCarry() {
            List<McpToolSpec> specs = specs();
            for (String family : ExternalMcpProperties.knownFamilies()) {
                List<McpToolSpec> own = specs.stream()
                        .filter(spec -> family.equals(McpToolNames.familyOf(spec.name())))
                        .toList();
                String text = McpInstructions.text(new AdvertisedFamilies(Set.of(family)));

                boolean slow = !costed(own, McpToolCost.SLOW).isEmpty();
                assertEquals(slow, Pattern.compile("\\b" + McpToolCost.SLOW.name() + "\\b").matcher(text).find(),
                        family + ": " + text);
                for (McpToolRequirement requirement : McpToolRequirement.values()) {
                    boolean named = Pattern.compile("\\b" + requirement.name() + "\\b").matcher(text).find();
                    if (named) {
                        assertFalse(requiring(own, requirement).isEmpty(), family + " names " + requirement);
                    }
                }
                boolean anyRequirement = own.stream().anyMatch(spec -> !requirements(spec).isEmpty());
                assertEquals(anyRequirement, text.contains(JeffreyMetaKeys.REQUIRES), family + ": " + text);
            }
        }

        private static Set<String> inFamily(Set<String> names, String family) {
            return names.stream()
                    .filter(name -> family.equals(McpToolNames.familyOf(name)))
                    .collect(Collectors.toSet());
        }

        /** The required arguments of a tool, each with a value its type accepts, for the unindexed profile. */
        private static ObjectNode minimalArguments(McpToolSpec spec) {
            ObjectNode arguments = Json.createObject().put(PROFILE_ID, UNINDEXED_PROFILE);
            JsonNode properties = spec.inputSchema().path(PROPERTIES);
            for (JsonNode required : spec.inputSchema().path(REQUIRED)) {
                String name = required.asString();
                if (arguments.has(name)) {
                    continue;
                }
                JsonNode property = properties.path(name);
                if (property.has(ENUM)) {
                    arguments.set(name, property.get(ENUM).get(0));
                } else if (ARRAY.equals(property.path(TYPE).asString())) {
                    arguments.putArray(name).add(SAMPLE_TEXT);
                } else {
                    arguments.set(name, SAMPLE_VALUES.get(property.path(TYPE).asString()));
                }
            }
            return arguments;
        }
    }

    /**
     * What the tools that declare a large result size answer when called with nothing but their
     * required arguments. Claude Code keeps a declared tool's answer inline up to the envelope cap, but
     * a default answer the size of that cap would still cost a turn most of its context; the defaults
     * are meant to stay well under it. Each call runs through the assembled toolset with the tool's
     * real code; only the profile underneath is a fixture.
     * <p>
     * No imported profile exists among the unit tests, so the fixture is synthetic: a call tree the
     * shape of a web server's — a shared framework trunk, a spread of handlers of falling weight, each
     * fanning out into many small leaves — large enough that its unpruned export runs past the budget
     * several times over, a heap with far more dominator roots than any default asks for, and an
     * events table wider than the query cap. The export stub applies the configured default threshold
     * when the tool passes none, as {@code FlamegraphManager} does.
     * <p>
     * The comparison diffs that tree against a baseline of the same shape in which one handler did
     * half the work and every leaf moved a little, so no subtree is free of movement. The traces are
     * an N+1 request — one query per order line, past the span cap of the trace export — and an
     * operation whose span names, notification kinds and traces each outnumber what its export asks
     * the manager for; the manager stubs honour the limits they are given, as the repository does.
     */
    @Nested
    @DuckDBTest
    class DefaultAnswerBudget {

        /** Well under the envelope's {@link McpToolOutput#MAX_CHARS}, with room for a wrapper and links. */
        private static final int BUDGET_CHARS = 100_000;

        /** {@code jeffrey.microscope.ai-export.flamegraph.min-frame-threshold-pct} when not configured. */
        private static final double CONFIGURED_THRESHOLD_PCT = 2.0;

        /** Fine enough that the fixture's whole tree survives, to show the fixture is not small. */
        private static final double UNPRUNED_THRESHOLD_PCT = 0.001;

        private static final String PROFILE_ID = "profile-1";
        private static final String TRACE_ID = "00000000000000ab";
        private static final String SPAN_ID = "00000000000000cd";
        private static final int TRUNK_DEPTH = 25;
        private static final int HANDLERS = 16;
        private static final int HANDLER_DEPTH = 25;
        private static final int LEAVES_PER_HANDLER = 20;
        private static final int LEAF_DEPTH = 8;
        private static final int DOMINATOR_ROOTS_IN_HEAP = 5_000;
        private static final int EVENT_ROWS = 20_000;

        private static final String BASELINE_PROFILE_ID = "profile-0";
        /** The length both fixture profiles report, from their {@code ProfileInfo}. */
        private static final Duration RECORDING_LENGTH = Duration.ofSeconds(600);
        /** The handler the baseline did half the work in: the regression the diff has to find. */
        private static final int REGRESSED_HANDLER = 3;
        private static final long LEAF_NOISE_SAMPLES = 5L;
        private static final int LEAF_NOISE_STEPS = 3;

        private static final String OPERATION_NAME = "GET /orders/{id}";
        private static final String OPERATION_KIND = "SERVER";
        private static final String OPERATION_EVENT_TYPE = "jeffrey.HttpServerExchange";
        private static final String QUERY_EVENT_TYPE = "jeffrey.JdbcStatement";
        private static final String QUERY_NAME = "SELECT * FROM order_line WHERE order_id = ? AND line_no = ?";
        private static final String SOCKET_READ_EVENT_TYPE = "jdk.SocketRead";
        private static final String SOCKET_READ_FIELDS =
                "{\"host\":\"orders-db.internal\",\"port\":5432,\"bytesRead\":65536}";
        private static final long MS = 1_000_000L;
        /** One query per order line: more recorded spans than the trace export gives a line each. */
        private static final int QUERIES_IN_TRACE = 500;
        private static final long QUERY_NANOS = 1_200_000L;
        private static final long SLOW_QUERY_NANOS = 26 * MS;
        private static final long SLOW_READ_NANOS = 24 * MS;
        /** Every this many queries waits on a socket read long enough for JFR to record it. */
        private static final int SLOW_QUERY_EVERY = 25;
        private static final int FAILED_QUERY_EVERY = 100;
        private static final int WAITING_QUERY_EVERY = 20;
        private static final int NOTIFICATIONS_IN_TRACE = 12;
        private static final int SPAN_NAMES_IN_OPERATION = 300;
        private static final int NOTIFICATION_KINDS_IN_OPERATION = 200;
        private static final int TRACES_OF_OPERATION = 5_000;

        private final Frame tree = webServerTree();
        private final Frame baselineTree = webServerTree(DefaultAnswerBudget::baselineSamples);
        private final ProfileManager profileManager = mock(ProfileManager.class);
        private final FlamegraphManager flamegraphManager = mock(FlamegraphManager.class);

        @Test
        void noDefaultCallOfALargeResultToolPassesTheBudget(DataSource dataSource) throws SQLException {
            seedEvents(dataSource);
            stubProfile(dataSource);
            ObjectNode profile = Json.createObject().put("profileId", PROFILE_ID);

            Map<String, JsonNode> defaultCalls = new LinkedHashMap<>();
            defaultCalls.put("flamegraph_export", profile.deepCopy().put("eventType", "jdk.ExecutionSample"));
            defaultCalls.put("traces_spanFlamegraphExport", profile.deepCopy()
                    .put("traceId", TRACE_ID).put("spanId", SPAN_ID).put("eventType", "jdk.ExecutionSample"));
            defaultCalls.put("jfr_executeQuery", profile.deepCopy().put("query", "SELECT * FROM events"));
            defaultCalls.put("heap_getDominatorTreeRoots", profile.deepCopy());
            defaultCalls.put("compare_flamegraph", profile.deepCopy()
                    .put("baselineProfileId", BASELINE_PROFILE_ID).put("eventType", "jdk.ExecutionSample"));
            defaultCalls.put("traces_traceExport", profile.deepCopy().put("traceId", TRACE_ID));
            defaultCalls.put("traces_operationExport", profile.deepCopy()
                    .put("name", OPERATION_NAME).put("kind", OPERATION_KIND).put("eventType", OPERATION_EVENT_TYPE));

            Map<String, Integer> sizes = callAll(defaultCalls);

            for (Map.Entry<String, Integer> size : sizes.entrySet()) {
                System.out.printf("MCP default call tool=%s chars=%d%n", size.getKey(), size.getValue());
                assertTrue(size.getValue() < BUDGET_CHARS, size.getKey() + " answered " + size.getValue()
                        + " chars by default; the budget is " + BUDGET_CHARS);
            }
        }

        @Test
        void theSummaryIsAFractionOfTheDefaultTree(DataSource dataSource) throws SQLException {
            stubProfile(dataSource);
            ObjectNode call = Json.createObject().put("profileId", PROFILE_ID).put("eventType", "jdk.ExecutionSample");

            Map<String, Integer> sizes = callAll(Map.of(
                    "standard", call.deepCopy(),
                    "summary", call.deepCopy().put("detail", "summary")), "flamegraph_export");

            System.out.printf("MCP flamegraph_export standard=%d summary=%d chars%n",
                    sizes.get("standard"), sizes.get("summary"));
            assertTrue(sizes.get("summary") * 4 < sizes.get("standard"), sizes.toString());
        }

        /** Without this the budget above would hold for any fixture small enough to fit anyway. */
        @Test
        void theFixtureTreeUnprunedIsFarPastTheBudget() {
            String unpruned = new FlamegraphAiMarkdownBuilder(
                    Type.EXECUTION_SAMPLE, new AiExportConfig(UNPRUNED_THRESHOLD_PCT)).build(tree);

            assertTrue(unpruned.length() > 3 * BUDGET_CHARS, "fixture export chars: " + unpruned.length());
        }

        /** The same for the comparison: every subtree moved, so an unpruned diff keeps all of them. */
        @Test
        void theFixtureDiffUnprunedIsFarPastTheBudget() {
            String unpruned = diffExport(new AiExportConfig(UNPRUNED_THRESHOLD_PCT));

            assertTrue(unpruned.length() > 3 * BUDGET_CHARS, "fixture diff chars: " + unpruned.length());
        }

        /** The fixture trace has more spans than the export lists, so its span cap is what is tested. */
        @Test
        void theFixtureTraceRunsPastTheExportsSpanCap() {
            String export = new TraceAiMarkdownBuilder(
                    new TraceExportSource(traceDetail(), traceContext(), List.of())).build();

            assertTrue(export.contains("(truncated: "), "the fixture trace fits the export whole");
        }

        /** The operation's population, handed to the export uncapped, is far past the budget. */
        @Test
        void theFixtureOperationUncappedIsFarPastTheBudget() {
            String uncapped = new TraceOperationAiMarkdownBuilder(
                    operationRow(),
                    operationSummary(SPAN_NAMES_IN_OPERATION),
                    notificationKinds(NOTIFICATION_KINDS_IN_OPERATION),
                    tracesOfOperation(TRACES_OF_OPERATION))
                    .build();

            assertTrue(uncapped.length() > 3 * BUDGET_CHARS, "fixture operation chars: " + uncapped.length());
        }

        private Map<String, Integer> callAll(Map<String, JsonNode> calls) {
            return callAll(calls, null);
        }

        /**
         * @param tool the one tool every call goes to, or null when each key names its tool
         */
        private Map<String, Integer> callAll(Map<String, JsonNode> calls, String tool) {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                McpToolsetAssembler assembler = assembler(true);
                Map<String, Integer> sizes = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> call : calls.entrySet()) {
                    String name = tool == null ? call.getKey() : tool;
                    sizes.put(call.getKey(), assembler.toolset().call(name, call.getValue()).length());
                }
                return sizes;
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        private void stubProfile(DataSource dataSource) {
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            lenient().when(lease.profileManager()).thenReturn(profileManager);
            lenient().when(lease.dataSource()).thenReturn(dataSource);
            lenient().when(contextCache.acquire(PROFILE_ID)).thenReturn(lease);
            lenient().when(profileManager.info()).thenReturn(new ProfileInfo(
                    PROFILE_ID, "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                    Instant.EPOCH, Instant.EPOCH.plusSeconds(600), Instant.EPOCH, true, false, "recording-1"));

            lenient().when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            lenient().when(flamegraphManager.generateAiExport(any(), any())).thenAnswer(invocation -> {
                AiExportConfig config = invocation.getArgument(1);
                return export(config == null ? new AiExportConfig(CONFIGURED_THRESHOLD_PCT) : config);
            });
            lenient().when(flamegraphManager.generateAiExport(any()))
                    .thenAnswer(invocation -> export(new AiExportConfig(CONFIGURED_THRESHOLD_PCT)));

            TraceManager traceManager = mock(TraceManager.class);
            lenient().when(profileManager.traceManager()).thenReturn(traceManager);
            lenient().when(traceManager.spanIntervals(anyLong(), anyLong(), anyBoolean()))
                    .thenReturn(List.of(new SpanInterval(1L, 0L, 60_000L)));
            stubTraces(traceManager);
            stubBaseline();

            HeapDumpManager heapDumpManager = mock(HeapDumpManager.class);
            lenient().when(profileManager.heapDumpManager()).thenReturn(heapDumpManager);
            lenient().when(heapDumpManager.heapDumpExists()).thenReturn(true);
            lenient().when(heapDumpManager.isCacheReady()).thenReturn(true);
            lenient().when(heapDumpManager.getDominatorTreeRoots(anyInt())).thenAnswer(invocation -> {
                int limit = invocation.getArgument(0);
                return new DominatorTreeResponse(dominatorRoots(Math.min(limit, DOMINATOR_ROOTS_IN_HEAP)),
                        1L << 34, true, limit < DOMINATOR_ROOTS_IN_HEAP);
            });
        }

        private String export(AiExportConfig config) {
            return new FlamegraphAiMarkdownBuilder(Type.EXECUTION_SAMPLE, config).build(tree);
        }

        /**
         * The baseline profile resolved through the same cache, and the pair's diff manager rendering
         * the real comparison of the two fixture trees, with the configured default threshold when
         * the tool passes none, as {@code DiffFlamegraphManagerImpl} does.
         */
        private void stubBaseline() {
            ProfileManager baselineManager = mock(ProfileManager.class);
            McpProfileContextCache.Lease baselineLease = mock(McpProfileContextCache.Lease.class);
            lenient().when(baselineLease.profileManager()).thenReturn(baselineManager);
            lenient().when(contextCache.acquire(BASELINE_PROFILE_ID)).thenReturn(baselineLease);
            // The answer names the baseline it compared against, and links the pair's page with it.
            lenient().when(baselineManager.info()).thenReturn(new ProfileInfo(
                    BASELINE_PROFILE_ID, "project-1", "workspace-1", "Baseline", RecordingEventSource.JDK,
                    Instant.EPOCH, Instant.EPOCH.plus(RECORDING_LENGTH), Instant.EPOCH, true, false, "recording-0"));

            DifferentialFlamegraphManager diffManager = mock(DifferentialFlamegraphManager.class);
            lenient().when(profileManager.diffFlamegraphManager(baselineManager)).thenReturn(diffManager);
            lenient().when(diffManager.generateAiExport(any(), any())).thenAnswer(invocation -> {
                AiExportConfig config = invocation.getArgument(1);
                return diffExport(config == null ? new AiExportConfig(CONFIGURED_THRESHOLD_PCT) : config);
            });
        }

        private String diffExport(AiExportConfig config) {
            return ProfileComparison.treeMarkdown(Type.EXECUTION_SAMPLE,
                    new DiffTreeGenerator(tree, baselineTree).generate(),
                    RECORDING_LENGTH, RECORDING_LENGTH, config, false);
        }

        private void stubTraces(TraceManager traceManager) {
            lenient().when(traceManager.trace(anyLong())).thenReturn(Optional.of(traceDetail()));
            lenient().when(traceManager.context(anyLong())).thenReturn(traceContext());
            lenient().when(traceManager.export(anyLong())).thenReturn(
                    Optional.of(new TraceExportSource(traceDetail(), traceContext(), List.of())));
            lenient().when(traceManager.operation(any())).thenReturn(Optional.of(operationRow()));
            lenient().when(traceManager.operationSummary(any(), anyInt()))
                    .thenAnswer(invocation -> operationSummary(invocation.getArgument(1)));
            lenient().when(traceManager.notifications(any())).thenAnswer(invocation ->
                    notificationKinds(invocation.<TraceNotificationListQuery>getArgument(0).limit()));
            lenient().when(traceManager.slowestTracesOfOperation(any(), anyInt()))
                    .thenAnswer(invocation -> tracesOfOperation(invocation.getArgument(1)));
        }

        /**
         * One request of the operation: the controller and the service, then one query per order
         * line. Every {@value #SLOW_QUERY_EVERY}th query waited on a socket read long enough for JFR
         * to record it, which the derivation promotes into a leaf under the query; every
         * {@value #FAILED_QUERY_EVERY}th failed; the application raised a handful of notifications.
         */
        private static TraceDetail traceDetail() {
            List<TraceSpanRow> spans = new ArrayList<>();
            List<TraceNotificationRow> notifications = new ArrayList<>();
            List<TraceExceptionRow> exceptions = new ArrayList<>();
            long queriesNanos = 0;
            int nextSpan = 4;
            long startMillis = 3;
            for (int query = 0; query < QUERIES_IN_TRACE; query++) {
                boolean slow = query % SLOW_QUERY_EVERY == 0;
                boolean failed = query % FAILED_QUERY_EVERY == FAILED_QUERY_EVERY - 1;
                long nanos = slow ? SLOW_QUERY_NANOS : QUERY_NANOS;
                String spanId = spanId(nextSpan++);
                spans.add(span(spanId, spanId(3), QUERY_NAME, "CLIENT", failed, startMillis, nanos,
                        slow ? nanos - SLOW_READ_NANOS : nanos, slow ? nanos : 0, 3, QUERY_EVENT_TYPE, null, false));
                if (slow) {
                    spans.add(span(spanId(nextSpan++), spanId, "Socket read", "CLIENT", false, startMillis,
                            SLOW_READ_NANOS, SLOW_READ_NANOS, SLOW_READ_NANOS, 4, SOCKET_READ_EVENT_TYPE,
                            SOCKET_READ_FIELDS, true));
                }
                if (failed) {
                    exceptions.add(new TraceExceptionRow(spanId, "ex-" + query, startMillis, startMillis * 1_000L,
                            "jdk.JavaExceptionThrow", "java.sql.SQLTransientConnectionException",
                            "HikariPool-1 - Connection is not available, request timed out after 30000ms.",
                            false, "stacktrace-" + query, "3001"));
                }
                if (notifications.size() < NOTIFICATIONS_IN_TRACE && slow) {
                    notifications.add(new TraceNotificationRow(spanId, "n-" + query, startMillis,
                            startMillis * 1_000L, "CONNECTION_POOL_NEAR_EXHAUSTION",
                            "HikariPool-1 has 1 of 10 connections idle and 14 threads waiting",
                            "HIGH", "RESOURCE", "hikari", null, "3001"));
                }
                queriesNanos += nanos;
                startMillis += Math.max(1, nanos / MS);
            }
            long total = queriesNanos + 40 * MS;
            List<TraceSpanRow> trunk = List.of(
                    span(spanId(1), null, OPERATION_NAME, OPERATION_KIND, true, 0, total, 8 * MS, total, 0,
                            OPERATION_EVENT_TYPE, null, false),
                    span(spanId(2), spanId(1), "OrderController.lines", "INTERNAL", true, 1, total - 10 * MS,
                            12 * MS, total - 10 * MS, 1, "jeffrey.TraceSpan", null, false),
                    span(spanId(3), spanId(2), "OrderService.loadLines", "INTERNAL", true, 2, queriesNanos + 20 * MS,
                            20 * MS, queriesNanos + 20 * MS, 2, "jeffrey.TraceSpan", null, false));
            List<TraceSpanRow> all = new ArrayList<>(trunk);
            all.addAll(spans);
            TraceRow trace = new TraceRow(TRACE_ID, OPERATION_NAME, OPERATION_KIND, OPERATION_EVENT_TYPE,
                    120_000L, 1_767_261_720_000L, total, all.size(), exceptions.size(), true);
            return new TraceDetail(trace, new TraceWindow(0, total / 1_000L), all, List.of(), 1,
                    notifications, exceptions, Map.of());
        }

        private static TraceSpanRow span(String spanId, String parentSpanId, String name, String kind, boolean failed,
                long startMillis, long nanos, long selfNanos, long criticalNanos, int depth, String eventType,
                String eventFields, boolean synthesized) {
            return new TraceSpanRow(spanId, parentSpanId, name, kind, failed ? "ERROR" : "UNSET",
                    failed ? "SQLTransientConnectionException" : null, startMillis, startMillis * 1_000L,
                    nanos, selfNanos, criticalNanos, depth, "3001", "http-nio-8080-exec-7", false,
                    eventType, null, eventFields, synthesized, null);
        }

        /** Two collections during the request, and a small wait under every few queries. */
        private static TraceContext traceContext() {
            Map<String, List<TraceContextSlice>> waits = new LinkedHashMap<>();
            for (int query = 0; query < QUERIES_IN_TRACE; query += WAITING_QUERY_EVERY) {
                waits.put(spanId(4 + query + query / SLOW_QUERY_EVERY),
                        List.of(new TraceContextSlice("ALLOCATION_REQUIRING_GC", 300_000L, 1)));
            }
            return new TraceContext(
                    List.of(new TracePause("GC_PAUSE", "G1 Evacuation Pause", 1_767_261_720_100_000L, 14 * MS, false),
                            new TracePause("GC_PAUSE", "G1 Evacuation Pause", 1_767_261_720_600_000L, 11 * MS, false)),
                    List.of(),
                    waits,
                    List.of(new TraceContextSlice("SOCKET_IO", 20 * SLOW_READ_NANOS, 20),
                            new TraceContextSlice("GC_PAUSE", 25 * MS, 2),
                            new TraceContextSlice("ALLOCATION_REQUIRING_GC", 25 * 300_000L, 25),
                            new TraceContextSlice(TraceContextSlice.OWN_WORK, 590 * MS, 0)));
        }

        private static String spanId(int number) {
            return String.format("%016x", 0x5eed_0000L + number);
        }

        private static TraceOperationRow operationRow() {
            return new TraceOperationRow(OPERATION_NAME, OPERATION_KIND, OPERATION_EVENT_TYPE,
                    TRACES_OF_OPERATION, 40, 600, 120, 2_500_000L, TRACES_OF_OPERATION * 180 * MS,
                    90 * MS, 400 * MS, 1_100 * MS, 2_400 * MS);
        }

        private static TraceOperationSummary operationSummary(int spanLimit) {
            List<TraceOperationSpanRow> spans = new ArrayList<>();
            for (int i = 0; i < Math.min(spanLimit, SPAN_NAMES_IN_OPERATION); i++) {
                long p50 = (SPAN_NAMES_IN_OPERATION - i) * 10_000L;
                spans.add(new TraceOperationSpanRow(
                        "SELECT * FROM order_line_" + i + " WHERE order_id = ? AND region = ?",
                        QUERY_EVENT_TYPE, 40_000L - i, 4_000L, p50 * 40_000L, p50 * 30_000L,
                        p50, p50 * 3 / 4, p50 * 20, p50 * 15, p50 * 40));
            }
            return new TraceOperationSummary(spans, new TraceOperationThreads(200, 2_000_000L, 500_000L, 0));
        }

        private static List<TraceNotificationGroupRow> notificationKinds(int limit) {
            List<TraceNotificationGroupRow> kinds = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, NOTIFICATION_KINDS_IN_OPERATION); i++) {
                kinds.add(new TraceNotificationGroupRow("SLOW_DOWNSTREAM_" + i, i < 20 ? "HIGH" : "MEDIUM",
                        "PERFORMANCE", "orders-client-" + i,
                        "Downstream inventory-service-" + i + " answered in 1840 ms, over the 500 ms budget "
                                + "for region eu-west-1; the request continued with cached stock levels",
                        1_000L - i, 400L - i, 1_000L, 590_000L,
                        List.of(spanId(10_000 + i), spanId(20_000 + i), spanId(30_000 + i))));
            }
            return kinds;
        }

        private static List<TraceRow> tracesOfOperation(int limit) {
            List<TraceRow> traces = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, TRACES_OF_OPERATION); i++) {
                traces.add(new TraceRow(spanId(100_000 + i), OPERATION_NAME, OPERATION_KIND, OPERATION_EVENT_TYPE,
                        1_000L + i, 1_767_261_600_000L + i, (2_400L - i % 2_000) * MS, 500, i % 3, true));
            }
            return traces;
        }

        private static List<DominatorNode> dominatorRoots(int count) {
            List<DominatorNode> nodes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                nodes.add(new DominatorNode(i, "com.example.cache.internal.SegmentedLruCache$Segment" + i,
                        Map.of(), null, 64, 1_000_000L - i, 0.1, true, null, List.of()));
            }
            return nodes;
        }

        private static void seedEvents(DataSource dataSource) throws SQLException {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE events AS SELECT 'jdk.ExecutionSample' AS event_type, i AS "
                        + "start_timestamp, i * 7 AS duration, repeat('com.example.checkout.Pricing', 3) AS "
                        + "frame FROM generate_series(1, " + EVENT_ROWS + ") AS t(i)");
            }
        }

        /**
         * A framework trunk shared by every sample; below it handlers whose weights fall from about
         * twelve percent to under one, each a chain of its own frames; below each handler many leaves
         * of equal weight, each well under the default threshold.
         */
        private static Frame webServerTree() {
            return webServerTree((handler, leaf) -> handlerSamples(handler));
        }

        private static long handlerSamples(int handler) {
            return 100L * (HANDLERS - handler);
        }

        /**
         * The baseline of the comparison: the regressed handler did half the work, and every leaf a
         * few samples less, so no subtree of the diff is free of movement.
         */
        private static long baselineSamples(int handler, int leaf) {
            long samples = handler == REGRESSED_HANDLER ? handlerSamples(handler) / 2 : handlerSamples(handler);
            return samples - LEAF_NOISE_SAMPLES * (1 + leaf % LEAF_NOISE_STEPS);
        }

        private static Frame webServerTree(LeafSamples leafSamples) {
            Frame root = Frame.emptyFrame();
            for (int handler = 0; handler < HANDLERS; handler++) {
                for (int leaf = 0; leaf < LEAVES_PER_HANDLER; leaf++) {
                    long samples = leafSamples.of(handler, leaf);
                    List<String> path = new ArrayList<>();
                    for (int level = 0; level < TRUNK_DEPTH; level++) {
                        path.add("org.springframework.web.servlet.FrameworkServlet.service" + level);
                    }
                    for (int level = 0; level < HANDLER_DEPTH; level++) {
                        path.add("com.example.shop.web.Handler" + handler + ".stage" + level);
                    }
                    for (int level = 0; level < LEAF_DEPTH; level++) {
                        path.add("com.example.shop.domain.Handler" + handler + "Leaf" + leaf + ".step" + level);
                    }
                    addPath(root, path, samples);
                }
            }
            return root;
        }

        /** How many samples one leaf of the fixture tree carries. */
        private interface LeafSamples {
            long of(int handler, int leaf);
        }

        private static void addPath(Frame root, List<String> path, long samples) {
            root.increment(FrameType.NATIVE, 0, samples, false);
            Frame current = root;
            for (int i = 0; i < path.size(); i++) {
                String name = path.get(i);
                Frame next = current.get(name);
                if (next == null) {
                    next = new Frame(current, name, 0, 0);
                    current.put(name, next);
                }
                next.increment(FrameType.JIT_COMPILED, 0, samples, i == path.size() - 1);
                current = next;
            }
        }
    }

    @Nested
    class FamilyFilter {

        /**
         * A client that pays for every schema on every turn can be given only what it uses.
         */
        @Test
        void advertisesOnlyTheNamedFamilies() {
            McpToolsetAssembler assembler = assembler(McpTestProperties.of(
                    true, true, true, Set.of("profiles", "flamegraph")));

            Set<String> prefixes = assembler.toolset().specs().stream()
                    .map(spec -> McpToolNames.familyOf(spec.name()))
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(Set.of("profiles", "flamegraph"), prefixes);
        }

        /**
         * The next-step gate and the assembler read a tool's family the same way: every tool the
         * assembler serves is one {@code servesTool} keeps, and every tool it withholds is one
         * {@code servesTool} drops — under each preset, an explicit list and the two switches.
         */
        @Test
        void theNextStepGateAgreesWithTheFamilyEveryToolIsServedUnder() {
            Set<String> every = assembler(true).toolset().specs().stream()
                    .map(McpToolSpec::name)
                    .collect(Collectors.toUnmodifiableSet());
            List<ExternalMcpProperties> configurations = List.of(
                    McpTestProperties.of(true, true, true, Set.of()),
                    McpTestProperties.of(true, false, false, Set.of()),
                    McpTestProperties.of(true, true, true, Set.of(), "heap"),
                    McpTestProperties.of(true, true, true, Set.of(), "jfr"),
                    McpTestProperties.of(true, true, true, Set.of(), "hub"),
                    McpTestProperties.of(true, false, false, Set.of("jfr", "ide", "hubs", "recordings", "operations"), "heap"),
                    McpTestProperties.of(true, true, true, Set.of("profiles", "flamegraph", "hubs", "recordings", "operations")));
            for (ExternalMcpProperties properties : configurations) {
                Set<String> served = assembler(properties).toolset().specs().stream()
                        .map(McpToolSpec::name)
                        .collect(Collectors.toUnmodifiableSet());
                AdvertisedFamilies advertised = AdvertisedFamilies.of(properties);
                for (String tool : every) {
                    assertEquals(served.contains(tool), advertised.servesTool(tool), tool + " under " + properties);
                }
            }
        }

        @Test
        void anEmptyFilterKeepsEverything() {
            McpToolsetAssembler filtered = assembler(
                    McpTestProperties.of(true, true, true, Set.of()));

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
            assertEquals(Set.of("profiles", "recordings", "jfr", "flamegraph", "jvm", "compare", "traces",
                    "http", "jdbc", "grpc", "methodtracing", "io", "blocking", "timeline", "memory", "operations"),
                    families(preset("jfr")));
            assertEquals(Set.of("profiles", "recordings", "heap", "operations"), families(preset("heap")));
            assertEquals(Set.of("profiles", "recordings", "hubs", "operations"), families(preset("hub")));
        }

        /**
         * The default serves whatever the assembler builds, so a family missing from the registry is
         * still advertised -- it just cannot be named or reached through a preset. Nothing about the
         * served tool list would look wrong, which is why the two sets are compared here instead.
         */
        /**
         * The operations family gates each operation by the family that started it, so a kind whose
         * family is not a name the properties know would be reachable by nobody and unreachable for no
         * reason anyone could read off the configuration.
         */
        @Test
        void everyOperationKindBelongsToAKnownFamily() {
            for (OperationKind kind : OperationKind.values()) {
                assertTrue(ExternalMcpProperties.knownFamilies().contains(kind.family()),
                        kind + " names family " + kind.family() + ", which ExternalMcpProperties does not know");
            }
        }

        @Test
        void everyBuiltFamilyIsANameAReaderCanSelect() {
            assertEquals(ExternalMcpProperties.knownFamilies(), families(assembler(true)),
                    "a family the assembler builds must also be registered in ExternalMcpProperties, "
                            + "or no preset can include it and naming it is rejected as unknown");
        }

        @Test
        void explicitFamiliesOverridePresetAndSwitchesStillConstrainThem() {
            McpToolsetAssembler filtered = assembler(McpTestProperties.of(
                    true, false, false, Set.of("jfr", "ide", "hubs", "recordings", "operations"), "heap"));
            assertEquals(Set.of("jfr", "recordings", "operations"), families(filtered));
            assertThrows(IllegalArgumentException.class,
                    () -> filtered.toolset().call("heap_status", Json.createObject()));
        }

        /**
         * The jfr preset now serves timeline_ and traces_, so an explicit list is what withholds them:
         * a flamegraph answer must then stop routing to timeline_hotWindows and compare_movements.
         */
        @Test
        void aHintToAWithheldFamilyIsLeftOutOfTheAnswer() {
            String trimmed = flamegraphExportUnder(Set.of("profiles", "flamegraph", "jvm", "operations"));

            assertTrue(trimmed.contains("jvm_threads"), trimmed);
            assertFalse(trimmed.contains("timeline_"), trimmed);
            assertFalse(trimmed.contains("compare_"), trimmed);
        }

        @Test
        void aHintToAnAdvertisedFamilyStays() {
            String full = flamegraphExportUnder(Set.of());

            assertTrue(full.contains("timeline_hotWindows"), full);
            assertTrue(full.contains("compare_movements"), full);
        }

        /** The answer's follow-up, where the calls and the advice that route onward now travel. */
        private String flamegraphExportUnder(Set<String> families) {
            ProfileManager profileManager = mock(ProfileManager.class);
            FlamegraphManager flamegraphManager = mock(FlamegraphManager.class);
            McpProfileContextCache.Lease lease = mock(McpProfileContextCache.Lease.class);
            when(lease.profileManager()).thenReturn(profileManager);
            when(profileManager.info()).thenReturn(new ProfileInfo(
                    "profile-1", "project-1", "workspace-1", "Profile", RecordingEventSource.JDK,
                    Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, "recording-1"));
            when(profileManager.flamegraphManager()).thenReturn(flamegraphManager);
            when(flamegraphManager.generateAiExport(any(), any())).thenReturn("tree");
            when(contextCache.acquire("profile-1")).thenReturn(lease);

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                return assembler(McpTestProperties.of(true, true, true, families, "jfr")).toolset()
                        .callResult("flamegraph_export", Json.createObject()
                                .put("profileId", "profile-1")
                                .put("eventType", "jdk.ExecutionSample"))
                        .structuredContent().get("followUp").toString();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }

        @Test
        void reportsActualToolListWireBytesAndCountsForEveryPreset() {
            int defaultBytes = toolListBytes("all");
            for (String preset : List.of("jfr", "heap", "hub")) {
                assertTrue(toolListBytes(preset) < defaultBytes, preset + " should reduce tools/list bytes");
            }
        }

        private int toolListBytes(String preset) {
            ExternalMcpProperties properties = McpTestProperties.of(
                    true, true, true, Set.of(), preset);
            McpToolsetAssembler assembler = assembler(properties);
            ExternalMcpController controller = new ExternalMcpController(
                    assembler, properties, McpTestGuards.loopback(),
                    new McpPromptRegistry(McpSkillCatalogue.fromClasspath()), mock(McpDiagnostics.class),
                    AdvertisedFamilies.of(properties), McpTaskProvider.NONE, McpSkillProvider.NONE);
            McpTestRequests.Request toolsList = McpTestRequests.request("tools/list");
            MockHttpServletRequest request = new MockHttpServletRequest();
            toolsList.httpHeaders().forEach(request::addHeader);
            JsonNode response = controller.handle(toolsList.body(), request).getBody();
            assertTrue(response.path("result").path("tools").isArray());
            int count = response.path("result").path("tools").size();
            assertEquals(assembler.toolset().specs().size(), count);
            int bytes = response.toString().getBytes(StandardCharsets.UTF_8).length;
            System.out.printf("MCP preset=%s tools=%d tools/list UTF-8 bytes=%d%n", preset, count, bytes);
            return bytes;
        }

        private McpToolsetAssembler preset(String name) {
            return assembler(McpTestProperties.of(true, true, true, Set.of(), name));
        }

        private Set<String> families(McpToolsetAssembler assembler) {
            return assembler.toolset().specs().stream()
                    .map(spec -> McpToolNames.familyOf(spec.name()))
                    .collect(Collectors.toSet());
        }
    }

}

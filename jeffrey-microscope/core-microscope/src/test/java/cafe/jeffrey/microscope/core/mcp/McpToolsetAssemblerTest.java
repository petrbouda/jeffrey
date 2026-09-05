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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingCommitResolver;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.manager.server.HubsManager;
import cafe.jeffrey.microscope.core.mcp.tools.HubsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.heapdump.HeapDumpInitService;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);

    private McpToolsetAssembler assembler(boolean hubsEnabled) {
        return assembler(new ExternalMcpProperties(true, hubsEnabled, Set.of()));
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
                properties);
    }

    private List<String> toolNames(boolean hubsEnabled) {
        return assembler(hubsEnabled).toolset().specs().stream()
                .map(McpToolSpec::name)
                .toList();
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

        /**
         * The one JFR tool that writes inside a profile stays out either way — ingestion creates
         * profiles, it does not open the door to rewriting one.
         */
        @Test
        void stillLeavesTheJfrWriteToolOut() {
            assertFalse(toolNames(true).contains("jfr_executeModification"));
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

        /**
         * Names that look like a tool reference and are not: deliberate counter-examples, and the one
         * write tool the external server excludes on purpose.
         */
        private static final Set<String> NOT_REFERENCES = Set.of(
                "jfr_list_tables", "heap_get_leak_suspects", "compare_movements_list",
                "jfr_executeModification");

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

    @Nested
    class FamilyFilter {

        /**
         * A client that pays for every schema on every turn can be given only what it uses.
         */
        @Test
        void advertisesOnlyTheNamedFamilies() {
            McpToolsetAssembler assembler = assembler(new ExternalMcpProperties(
                    true, true, Set.of("profiles", "flamegraph")));

            Set<String> prefixes = assembler.toolset().specs().stream()
                    .map(spec -> spec.name().substring(0, spec.name().indexOf('_')))
                    .collect(Collectors.toUnmodifiableSet());

            assertEquals(Set.of("profiles", "flamegraph"), prefixes);
        }

        @Test
        void anEmptyFilterKeepsEverything() {
            McpToolsetAssembler filtered = assembler(
                    new ExternalMcpProperties(true, true, Set.of()));

            assertEquals(assembler(true).toolset().specs().size(),
                    filtered.toolset().specs().size());
        }
    }

}

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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.ProfilesMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.profile.mcp.McpToolSpec;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class McpToolsetAssemblerTest {

    private static final String INGEST_PREFIX = "recordings_";

    @Mock
    MicroscopeCoreRepositories coreRepositories;

    @Mock
    RecordingsManager recordingsManager;

    @Mock
    McpProfileContextCache contextCache;

    @Mock
    JfrFlamegraphPanelProvider panelProvider;

    @Mock
    RecordingTagsRepository recordingTagsRepository;

    private List<String> toolNamesWithIngest(boolean ingestEnabled) {
        McpToolsetAssembler assembler = new McpToolsetAssembler(
                new ProfilesMcpTools(coreRepositories),
                new RecordingsMcpTools(recordingsManager),
                contextCache,
                panelProvider,
                recordingTagsRepository,
                new ExternalMcpProperties(true, ingestEnabled));

        return assembler.toolset().specs().stream().map(McpToolSpec::name).toList();
    }

    @Nested
    class IngestEnabled {

        @Test
        void advertisesTheRecordingsFamily() {
            List<String> names = toolNamesWithIngest(true);

            assertTrue(names.contains("recordings_analyzeFile"));
            assertTrue(names.contains("recordings_analyzeRecording"));
            assertTrue(names.contains("recordings_list"));
        }

        @Test
        void keepsEveryReadOnlyFamily() {
            List<String> names = toolNamesWithIngest(true);

            assertTrue(names.contains("profiles_list"));
            assertTrue(names.contains("flamegraph_export"));
            assertTrue(names.contains("heap_getHeapSummary"));
            assertTrue(names.contains("profiles_buildInfo"));
        }

        /**
         * The one JFR tool that writes inside a profile stays out either way — ingestion creates
         * profiles, it does not open the door to rewriting one.
         */
        @Test
        void stillLeavesTheJfrWriteToolOut() {
            assertFalse(toolNamesWithIngest(true).contains("jfr_executeModification"));
        }
    }

    @Nested
    class IngestDisabled {

        /**
         * Not advertised rather than advertised-and-refusing: a tool that can never succeed spends a
         * slot in the model's context and invites a call whose failure explains nothing.
         */
        @Test
        void advertisesNoIngestTool() {
            assertFalse(toolNamesWithIngest(false).stream().anyMatch(name -> name.startsWith(INGEST_PREFIX)));
        }

        @Test
        void andRefusesOneCalledByName() {
            McpToolsetAssembler assembler = new McpToolsetAssembler(
                    new ProfilesMcpTools(coreRepositories),
                    new RecordingsMcpTools(recordingsManager),
                    contextCache,
                    panelProvider,
                    recordingTagsRepository,
                    new ExternalMcpProperties(true, false));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> assembler.toolset().call("recordings_list", null));
        }

        @Test
        void leavesTheReadOnlyFamiliesUntouched() {
            List<String> names = toolNamesWithIngest(false);

            assertTrue(names.contains("profiles_list"));
            assertTrue(names.contains("heap_getHeapSummary"));
            assertTrue(names.contains("profiles_buildInfo"));
        }
    }
}

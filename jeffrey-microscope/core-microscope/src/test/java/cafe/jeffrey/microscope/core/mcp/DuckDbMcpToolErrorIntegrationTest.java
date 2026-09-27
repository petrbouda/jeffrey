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

import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.shared.common.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.Set;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DuckDbMcpToolErrorIntegrationTest {

    @Mock
    McpToolsetAssembler assembler;

    @Mock
    DataSource dataSource;

    @Test
    void databaseFailureCrossesTheReflectiveAdapterAsAnMcpToolError(@TempDir Path directory) throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("database unavailable"));
        when(assembler.toolset()).thenReturn(new ReflectiveToolset(new DuckDbMcpTools(dataSource, "p-1", EVERY_FAMILY), "jfr"));
        MockMvcTester mvc = mockMvcTesterFor(new ExternalMcpController(
                assembler,
                McpTestProperties.of(true, true, true, Set.of()),
                McpTestGuards.loopback(),
                new McpPromptRegistry(McpSkillCatalogue.fromClasspath()), mock(McpDiagnostics.class), EVERY_FAMILY,
                McpTaskProvider.NONE, McpSkillProvider.NONE));

        McpTestRequests.Request request = McpTestRequests.toolCall("jfr_listTables", Json.createObject());

        Path recordingPath = directory.resolve("tool-failure.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TraceSpanEvent.class);
            recording.start();
            assertThat(ExternalMcpControllerTest.post(mvc, ExternalMcpController.PATH, request))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.isError").isEqualTo(true);
            recording.stop();
            recording.dump(recordingPath);
        }
        var toolSpans = RecordingFile.readAllEvents(recordingPath).stream()
                .filter(event -> event.getEventType().getName().equals(TraceSpanEvent.NAME))
                .filter(event -> event.getString("name").equals("jfr_listTables"))
                .toList();
        assertThat(toolSpans).hasSize(1);
        assertThat(toolSpans.getFirst().getString("status")).isEqualTo("ERROR");
    }
}

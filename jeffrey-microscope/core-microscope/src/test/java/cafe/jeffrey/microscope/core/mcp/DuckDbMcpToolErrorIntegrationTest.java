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

import cafe.jeffrey.microscope.core.mcp.tools.DuckDbMcpTools;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.Set;

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
        when(assembler.toolset()).thenReturn(new ReflectiveToolset(new DuckDbMcpTools(dataSource), "jfr"));
        MockMvcTester mvc = mockMvcTesterFor(new ExternalMcpController(
                assembler,
                new ExternalMcpProperties(true, true, true, Set.of()),
                new McpRequestGuard(),
                new McpPromptRegistry(), mock(McpDiagnostics.class)));

        String request = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call",
                 "params":{"name":"jfr_listTables","arguments":{}}}""";

        Path recordingPath = directory.resolve("tool-failure.jfr");
        try (Recording recording = new Recording()) {
            recording.enable(TraceSpanEvent.class);
            recording.start();
            assertThat(mvc.post().uri(ExternalMcpController.PATH)
                    .contentType(MediaType.APPLICATION_JSON).content(request))
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

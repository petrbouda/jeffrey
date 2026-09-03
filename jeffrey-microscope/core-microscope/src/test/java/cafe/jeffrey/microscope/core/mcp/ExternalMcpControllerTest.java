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

import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalMcpControllerTest {

    private static final String URI = "/api/internal/mcp";
    private static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON;

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},
                       "clientInfo":{"name":"claude","version":"1"}}}""";
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";
    private static final String TOOLS_CALL = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call",
             "params":{"name":"sample_ping","arguments":{}}}""";

    @Mock
    McpToolsetAssembler assembler;

    private MockMvcTester mvcWith(boolean enabled) {
        return mockMvcTesterFor(new ExternalMcpController(assembler, new ExternalMcpProperties(enabled)));
    }

    @Nested
    class Disabled {

        /**
         * A disabled server should look like no server at all, so a client pointed at a Jeffrey that
         * turned the endpoint off gets a clean "no such endpoint" rather than a refusal it might retry.
         */
        @Test
        void answers404() {
            assertThat(mvcWith(false).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatus(404);
        }

        @Test
        void doesNotBuildTheToolset() {
            mvcWith(false).post().uri(URI).contentType(APPLICATION_JSON).content(TOOLS_LIST).exchange();
            verifyNoInteractions(assembler);
        }
    }

    @Nested
    class Enabled {

        @Test
        void reportsItselfOnInitialize() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.serverInfo.name").asString().isEqualTo("jeffrey");
        }

        @Test
        void echoesTheClientsProtocolVersion() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.protocolVersion").asString().isEqualTo("2025-06-18");
        }

        @Test
        void listsTheAssembledTools() {
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new SampleTools(), "sample"));

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(TOOLS_LIST))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.tools[0].name").asString().isEqualTo("sample_ping");
        }

        @Test
        void callsATool() {
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new SampleTools(), "sample"));

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(TOOLS_CALL))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.content[0].text").asString().isEqualTo("pong");
        }

        /**
         * A failing tool is reported inside the result rather than as a transport error: the model is
         * meant to read what went wrong and try something else.
         */
        @Test
        void reportsAFailingToolAsAToolError() {
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new SampleTools(), "sample"));

            String failing = """
                    {"jsonrpc":"2.0","id":4,"method":"tools/call",
                     "params":{"name":"sample_boom","arguments":{}}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(failing))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.isError").isEqualTo(true);
        }

        @Test
        void rejectsAnUnknownMethod() {
            String unknown = """
                    {"jsonrpc":"2.0","id":5,"method":"resources/list"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(unknown))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32601);
        }

        /**
         * Notifications carry no id and expect no body — answering one with a result would leave the
         * client correlating a response to a request it never made.
         */
        @Test
        void acknowledgesNotificationsWithoutABody() {
            String notification = """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(notification))
                    .hasStatus(202);
        }
    }

    public static class SampleTools {

        @Tool(description = "Ping")
        public String ping() {
            return "pong";
        }

        @Tool(description = "Always fails")
        public String boom() {
            throw new IllegalStateException("nope");
        }
    }
}

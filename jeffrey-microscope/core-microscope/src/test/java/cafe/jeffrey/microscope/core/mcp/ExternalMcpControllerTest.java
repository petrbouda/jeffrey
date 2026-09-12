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

import cafe.jeffrey.profile.mcp.CompositeToolset;
import cafe.jeffrey.profile.mcp.McpToolProvider;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ExternalMcpControllerTest {

    private static final String URI = ExternalMcpController.PATH;
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

    /**
     * The toolset the endpoint serves in these tests. A composite rather than one family, because the
     * resources are backed by real tool names — a fixture without them would let a resource be
     * advertised that nothing could read.
     */
    private static McpToolProvider toolset() {
        return new CompositeToolset(List.of(
                new ReflectiveToolset(new SampleTools(), "sample"),
                new ReflectiveToolset(new ProfilesTools(), "profiles"),
                new ReflectiveToolset(new FlamegraphTools(), "flamegraph")));
    }

    private MockMvcTester mvcWith(boolean enabled) {
        return mockMvcTesterFor(new ExternalMcpController(
                assembler,
                new ExternalMcpProperties(enabled, true, true, Set.of()),
                new McpRequestGuard(),
                new McpPromptRegistry()));
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
    class MalformedJson {

        @Test
        void reportsAJsonRpcParseErrorWithoutInvokingTools() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content("{invalid"))
                    .hasStatus(400)
                    .bodyJson()
                    .extractingPath("$.error.code").asNumber().isEqualTo(-32700);
            verifyNoInteractions(assembler);
        }

        @Test
        void keepsADisabledEndpointHiddenForMalformedJson() {
            assertThat(mvcWith(false).post().uri(URI).contentType(APPLICATION_JSON).content("{invalid"))
                    .hasStatus(404);
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

        /**
         * Names rather than positions: the order a family is listed in is stable (the index sorts by
         * method name), but what this asserts is that both tools reached the client.
         */
        @Test
        void listsTheAssembledTools() {
            when(assembler.toolset()).thenReturn(toolset());

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(TOOLS_LIST))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.tools[*].name").asArray()
                    .containsExactly(
                            "sample_boom", "sample_ping",
                            "profiles_list", "profiles_summary",
                            "flamegraph_export");
        }

        @Test
        void callsATool() {
            when(assembler.toolset()).thenReturn(toolset());

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
            when(assembler.toolset()).thenReturn(toolset());

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
                    {"jsonrpc":"2.0","id":5,"method":"completion/complete"}""";

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

    @Nested
    class LegacyPath {

        /**
         * The endpoint moved out of {@code /api/internal/**}, but its old address is written down in
         * every client that was configured before the move — a plugin's own settings, a hand-added
         * server — where this repository cannot reach it. So the old path keeps answering, and keeps
         * answering the same thing rather than redirecting: a JSON-RPC client that follows a redirect
         * on a POST is not something to rely on.
         */
        @Test
        void stillServesClientsConfiguredBeforeTheMove() {
            assertThat(mvcWith(true).post().uri(ExternalMcpController.LEGACY_PATH)
                    .contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.serverInfo.name").asString().isEqualTo("jeffrey");
        }

        @Test
        void answers404OnTheOldPathWhenTheServerIsOff() {
            assertThat(mvcWith(false).post().uri(ExternalMcpController.LEGACY_PATH)
                    .contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatus(404);
        }
    }

    @Nested
    class Guarding {

        /**
         * A CLI client sends no Origin at all, which is what makes refusing a foreign one free.
         */
        @Test
        void servesARequestThatCarriesNoOrigin() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk();
        }

        @Test
        void refusesARequestFromAForeignOrigin() {
            assertThat(mvcWith(true).post().uri(URI)
                    .header("Origin", "http://evil.example")
                    .contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    class ProtocolVersion {

        @Test
        void answersAnUnknownProtocolVersionWithTheOneItSpeaks() {
            String future = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2099-01-01","capabilities":{}}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(future))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.protocolVersion").asString().isEqualTo("2025-11-25");
        }

        /**
         * The header is the strict half of the same question, and the refusal is what a client that
         * speaks the current revision reads before falling back to the handshake above. Over real
         * HTTP because it is the header rather than the body that decides, and a controller that
         * stopped passing it would look fine everywhere else.
         */
        @Test
        void refusesTheCurrentRevisionInTheHeaderAndSaysWhatItSpeaks() {
            assertThat(mvcWith(true).post().uri(URI)
                    .header("MCP-Protocol-Version", "2026-07-28")
                    .contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.error.message").asString().contains("2025-11-25");
        }

        @Test
        void servesTheNewestRevisionItImplements() {
            assertThat(mvcWith(true).post().uri(URI)
                    .header("MCP-Protocol-Version", "2025-11-25")
                    .contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk();
        }

        @Test
        void keepsAnOlderVersionItStillSpeaks() {
            String older = """
                    {"jsonrpc":"2.0","id":1,"method":"initialize",
                     "params":{"protocolVersion":"2024-11-05","capabilities":{}}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(older))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.protocolVersion").asString().isEqualTo("2024-11-05");
        }
    }

    /**
     * The workflows ship as plugin skills, which a client that cannot install a plugin cannot read.
     * Serving them as prompts is how Cursor, VS Code and Kiro get them.
     */
    @Nested
    class Prompts {

        @Test
        void advertisesThePromptCapability() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.prompts").isNotNull();
        }

        @Test
        void listsTheSkillsAsPrompts() {
            String list = """
                    {"jsonrpc":"2.0","id":6,"method":"prompts/list"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(list))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.prompts[*].name").asArray()
                    .contains("analyze-jfr", "analyze-heap", "compare-jfr", "report");
        }

        @Test
        void handsBackTheSkillBody() {
            String get = """
                    {"jsonrpc":"2.0","id":7,"method":"prompts/get",
                     "params":{"name":"analyze-jfr"}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(get))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.messages[0].content.text").asString()
                    .contains("profiles_list");
        }

        @Test
        void refusesAPromptItDoesNotHave() {
            String get = """
                    {"jsonrpc":"2.0","id":8,"method":"prompts/get",
                     "params":{"name":"nonsense"}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(get))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }
    }

    @Nested
    class Resources {

        @Test
        void advertisesTheResourceCapability() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.resources").isNotNull();
        }

        @Test
        void listsTheCatalogue() {
            when(assembler.toolset()).thenReturn(toolset());
            String list = """
                    {"jsonrpc":"2.0","id":9,"method":"resources/list"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(list))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resources[0].uri").asString().isEqualTo("jeffrey://profiles");
        }

        /**
         * A template carries placeholders and is not itself fetchable, so it goes under uriTemplate —
         * a client that reads it as a uri will try to fetch it.
         */
        @Test
        void listsThePerProfileTemplatesSeparately() {
            when(assembler.toolset()).thenReturn(toolset());
            String list = """
                    {"jsonrpc":"2.0","id":10,"method":"resources/templates/list"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(list))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resourceTemplates[*].uriTemplate").asArray()
                    .contains("jeffrey://profile/{profileId}/summary");
        }

        /**
         * Reading a resource runs a tool, so an installation narrowed to one family must not advertise
         * a resource whose tool it left out — the client would be told the catalogue exists and then
         * that the tool behind it does not.
         */
        @Test
        void advertisesNoResourceWhoseToolThisInstallationLeftOut() {
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new SampleTools(), "sample"));
            String list = """
                    {"jsonrpc":"2.0","id":12,"method":"resources/list"}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(list))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resources").asArray().isEmpty();
        }

        @Test
        void refusesAUriItDoesNotServe() {
            when(assembler.toolset()).thenReturn(toolset());
            String read = """
                    {"jsonrpc":"2.0","id":11,"method":"resources/read",
                     "params":{"uri":"jeffrey://nonsense"}}""";

            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(read))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }
    }

    /**
         * Echoing a version the server may not speak promises something it cannot keep, so an
         * unrecognised one is answered with what this server does implement.
         */
    /**
     * Stands in for the profiles family, so {@code jeffrey://profiles} and the summary template have
     * the tools behind them that the real assembler would provide.
     */
    public static class ProfilesTools {

        @Tool(description = "Every analysed profile")
        public String list() {
            return "| id |\n| -- |\n| p-1 |";
        }

        @Tool(description = "What one profile holds")
        public String summary(
                @ToolParam(required = false, description = "profile") String profileId) {
            return "{\"profileId\":\"" + profileId + "\"}";
        }
    }

    public static class FlamegraphTools {

        @Tool(description = "The call tree of one event type")
        public String export(
                @ToolParam(required = false, description = "profile") String profileId,
                @ToolParam(required = false, description = "event type") String eventType) {
            return "flames for " + eventType;
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

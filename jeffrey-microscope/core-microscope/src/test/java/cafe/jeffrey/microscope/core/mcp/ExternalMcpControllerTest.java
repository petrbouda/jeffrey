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

import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.mcp.tools.BoundedJobs;
import cafe.jeffrey.microscope.core.mcp.tools.McpOperationRegistry;
import cafe.jeffrey.microscope.core.mcp.tools.OperationAnswers;
import cafe.jeffrey.microscope.core.mcp.tools.OperationKind;
import cafe.jeffrey.microscope.core.mcp.tools.OperationResults;
import cafe.jeffrey.microscope.core.mcp.tools.OperationTasks;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindings;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileFindingsReader;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileManagerFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileSchema;
import cafe.jeffrey.microscope.core.mcp.tools.ProfileSchemaReader;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpTools;
import cafe.jeffrey.microscope.core.mcp.tools.RecordingsMcpToolsFixture;
import cafe.jeffrey.microscope.core.mcp.tools.ToolFixtures;
import cafe.jeffrey.microscope.mcp.protocol.CompositeToolset;
import cafe.jeffrey.microscope.mcp.protocol.McpClientCapabilities;
import cafe.jeffrey.microscope.mcp.protocol.McpSchemaGenerator;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillFile;
import cafe.jeffrey.microscope.mcp.protocol.McpSkillProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpTaskProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolOutcome;
import cafe.jeffrey.microscope.mcp.protocol.McpToolProvider;
import cafe.jeffrey.microscope.mcp.protocol.McpToolResult;
import cafe.jeffrey.microscope.mcp.protocol.McpTransportHeaders;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpSchemaConformance;
import cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests;
import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.profile.ProfileInitStages;
import cafe.jeffrey.profile.common.operation.OperationHandle;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.manager.AutoAnalysisManager;
import cafe.jeffrey.profile.manager.ProfileManager;
import cafe.jeffrey.profile.mcp.ReflectiveToolset;
import cafe.jeffrey.profile.panel.JfrFlamegraphPanelProvider;
import cafe.jeffrey.profile.panel.StackSampleFlamegraphPanelProvider;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.storage.recording.api.file.Recording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import static cafe.jeffrey.microscope.core.mcp.AdvertisedFamiliesFixture.EVERY_FAMILY;
import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.request;
import static cafe.jeffrey.microscope.mcp.protocol.testing.McpTestRequests.toolCall;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

@ExtendWith(MockitoExtension.class)
class ExternalMcpControllerTest {

    private static final String URI = ExternalMcpController.PATH;
    private static final String TOKEN = "s3cret-token";
    private static final MediaType APPLICATION_JSON = MediaType.APPLICATION_JSON;
    private static final String SERVER_NAME_PATH = "$.result._meta['io.modelcontextprotocol/serverInfo'].name";
    private static final String RECORDING_ID = "rec-1";
    private static final String PROFILE_ID = "prof-1";
    private static final Duration SHORT_TASK_WAIT = Duration.ofMillis(100);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    private static final McpTestRequests.Request DISCOVER = request("server/discover");
    private static final McpTestRequests.Request TOOLS_LIST = request("tools/list");
    private static final McpTestRequests.Request TOOLS_CALL = toolCall("sample_ping", Json.createObject());

    /** What a client from the handshake era opens with: no _meta, no headers. */
    private static final String LEGACY_INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{},
                       "clientInfo":{"name":"claude","version":"1"}}}""";

    @Mock
    McpToolsetAssembler assembler;

    /**
     * The toolset the endpoint serves in these tests. A composite rather than one family, because the
     * resources are backed by real tool names — a fixture without them would let a resource be
     * advertised that nothing could read.
     */
    private static final String SCHEMA_TEMPLATE = "jeffrey://profile/{profileId}/schema";
    private static final String FINDINGS_TEMPLATE = "jeffrey://profile/{profileId}/findings";

    /** The usual toolset, plus the SQL catalogue and the jvm family the two profile documents are gated on. */
    private static McpToolProvider documentToolset() {
        return new CompositeToolset(List.of(
                toolset(),
                new ReflectiveToolset(new McpResourcesTest.SqlTools(), "jfr"),
                new ReflectiveToolset(new McpResourcesTest.JvmTools(), "jvm")));
    }

    private static McpToolProvider toolset() {
        return new CompositeToolset(List.of(
                new ReflectiveToolset(new SampleTools(), "sample"),
                new ReflectiveToolset(new ProfilesTools(), "profiles"),
                new ReflectiveToolset(new FlamegraphTools(), "flamegraph")));
    }

    private MockMvcTester mvcWith(boolean enabled) {
        return mockMvcTesterFor(new ExternalMcpController(
                assembler,
                McpTestProperties.of(enabled, true, true, Set.of()),
                McpTestGuards.loopback(),
                new McpPromptRegistry(McpSkillCatalogue.fromClasspath()), mock(McpDiagnostics.class), EVERY_FAMILY,
                McpTaskProvider.NONE, McpSkillProvider.NONE));
    }

    /** A POST of one modern request: its body and the three headers that travel with it. */
    static MockMvcTester.MockMvcRequestBuilder post(MockMvcTester mvc, String uri, McpTestRequests.Request request) {
        MockMvcTester.MockMvcRequestBuilder builder = mvc.post().uri(uri)
                .contentType(APPLICATION_JSON)
                .content(request.json());
        request.httpHeaders().forEach((name, value) -> builder.header(name, value));
        return builder;
    }

    private static MockMvcTester.MockMvcRequestBuilder post(MockMvcTester mvc, McpTestRequests.Request request) {
        return post(mvc, URI, request);
    }

    @Nested
    class Disabled {

        /**
         * A disabled server should look like no server at all, so a client pointed at a Jeffrey that
         * turned the endpoint off gets a clean "no such endpoint" rather than a refusal it might retry.
         */
        @Test
        void answers404() {
            assertThat(post(mvcWith(false), DISCOVER)).hasStatus(404);
        }

        @Test
        void doesNotBuildTheToolset() {
            post(mvcWith(false), TOOLS_LIST).exchange();
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
        void reportsItselfOnDiscover() {
            assertThat(post(mvcWith(true), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath(SERVER_NAME_PATH).asString().isEqualTo("jeffrey");
        }

        @Test
        void discoversWithoutBuildingTheToolset() {
            assertThat(post(mvcWith(true), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.supportedVersions[0]").asString().isEqualTo("2026-07-28");
            verifyNoInteractions(assembler);
        }

        /**
         * Names rather than positions: the order a family is listed in is stable (the index sorts by
         * method name), but what this asserts is that both tools reached the client.
         */
        @Test
        void listsTheAssembledTools() {
            when(assembler.toolset()).thenReturn(toolset());

            assertThat(post(mvcWith(true), TOOLS_LIST))
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

            assertThat(post(mvcWith(true), TOOLS_CALL))
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

            assertThat(post(mvcWith(true), toolCall("sample_boom", Json.createObject())))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.isError").isEqualTo(true);
        }

        /**
         * A real protocol method this server does not implement, and does not advertise: it declares
         * no logging capability, so a client asking to set a level is asking for something that is not
         * here. An unknown method is a 404 in this revision.
         */
        @Test
        void rejectsAnUnknownMethod() {
            assertThat(post(mvcWith(true), request("logging/setLevel")))
                    .hasStatus(HttpStatus.NOT_FOUND)
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32601);
        }

        /**
         * Notifications carry no id and expect no body — answering one with a result would leave the
         * client correlating a response to a request it never made.
         */
        @Test
        void acknowledgesNotificationsWithoutABody() {
            assertThat(post(mvcWith(true),
                    McpTestRequests.notification("notifications/cancelled", Json.createObject())))
                    .hasStatus(202);
        }
    }

    @Nested
    class Guarding {

        /**
         * A CLI client sends no Origin at all, which is what makes refusing a foreign one free.
         */
        @Test
        void servesARequestThatCarriesNoOrigin() {
            assertThat(post(mvcWith(true), DISCOVER)).hasStatusOk();
        }

        @Test
        void refusesARequestFromAForeignOrigin() {
            assertThat(post(mvcWith(true), DISCOVER).header("Origin", "http://evil.example"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        /**
         * The hook tells a 403 from a 401 by status, and quotes the body of a 403 to the reader, so
         * the body has to name the property that fixes it.
         */
        @Test
        void answersAnUntrustedHostWith403NamingTheProperty() {
            assertThat(post(mvcWith(true), DISCOVER)
                    .with(request -> {
                        request.setServerName("host.docker.internal");
                        return request;
                    }))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .doesNotContainHeader(HttpHeaders.WWW_AUTHENTICATE)
                    .bodyJson()
                    .extractingPath("$.error").asString()
                    .contains(McpRequestGuard.ALLOWED_HOSTS_PROPERTY, "host.docker.internal");
        }

        @Test
        void answersAMissingTokenWith401AndABearerChallenge() {
            assertThat(post(tokenGuarded(), DISCOVER))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .bodyJson()
                    .extractingPath("$.error").asString()
                    .contains(McpRequestGuard.TOKEN_PROPERTY, McpRequestGuard.TOKEN_ENV_VAR);
        }

        @Test
        void answersAWrongTokenWith401() {
            assertThat(post(tokenGuarded(), DISCOVER).header(HttpHeaders.AUTHORIZATION, "Bearer wrong"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        @Test
        void servesTheRightToken() {
            assertThat(post(tokenGuarded(), DISCOVER).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .hasStatusOk();
        }

        /** The guard runs before the protocol: a legacy request without the token learns nothing either. */
        @Test
        void refusesALegacyRequestWithoutTheTokenWith401() {
            assertThat(tokenGuarded().post().uri(URI).contentType(APPLICATION_JSON).content(LEGACY_INITIALIZE))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        /**
         * The same refusal covers a body that never parsed: a request without the token learns
         * nothing about the endpoint, not even that it speaks JSON-RPC.
         */
        @Test
        void refusesMalformedJsonWithoutTheTokenWith401() {
            assertThat(tokenGuarded().post().uri(URI).contentType(APPLICATION_JSON).content("{invalid"))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
            verifyNoInteractions(assembler);
        }

        private MockMvcTester tokenGuarded() {
            return mockMvcTesterFor(new ExternalMcpController(
                    assembler,
                    McpTestProperties.of(true, true, true, Set.of()),
                    new McpRequestGuard(Set.of("localhost"), false, TOKEN),
                    new McpPromptRegistry(McpSkillCatalogue.fromClasspath()), mock(McpDiagnostics.class), EVERY_FAMILY,
                    McpTaskProvider.NONE, McpSkillProvider.NONE));
        }
    }

    /**
     * The plugin's skills over the skills extension, from the catalogue the build copied onto the
     * classpath, over real HTTP.
     */
    @Nested
    class Skills {

        private static final String REPORT_URI = "skill://report/SKILL.md";

        private final McpSkillCatalogue catalogue = McpSkillCatalogue.fromClasspath();

        private MockMvcTester skilled() {
            return mockMvcTesterFor(new ExternalMcpController(
                    assembler,
                    McpTestProperties.of(true, true, true, Set.of()),
                    McpTestGuards.loopback(),
                    new McpPromptRegistry(catalogue), mock(McpDiagnostics.class), EVERY_FAMILY,
                    McpTaskProvider.NONE, catalogue));
        }

        private JsonNode result(McpTestRequests.Request request) throws Exception {
            MvcTestResult response = post(skilled(), request).exchange();
            assertThat(response).hasStatusOk();
            return Json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("result");
        }

        @Test
        void declaresTheExtensionAtDiscover() {
            assertThat(post(skilled(), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.extensions['io.modelcontextprotocol/skills']")
                    .isEqualTo(Map.of());
            verifyNoInteractions(assembler);
        }

        @Test
        void listsTheTenPluginSkills() throws Exception {
            JsonNode skills = result(request("skills/list")).path("skills");

            assertThat(skills.size()).isEqualTo(10);
            assertThat(skills.get(0).path("uri").asString()).isEqualTo("skill://advise-jfr/SKILL.md");
            assertThat(skills.get(0).path("frontmatter").path("name").asString()).isEqualTo("advise-jfr");
            verifyNoInteractions(assembler);
        }

        /** A host verifies what it read against the manifest; the bytes must hash to the digest. */
        @Test
        void readsBytesThatMatchTheDigestInTheManifest() throws Exception {
            JsonNode manifest = result(request("skills/get", Json.createObject().put("uri", REPORT_URI)))
                    .path("skill").path("resources");

            assertThat(manifest.size()).isEqualTo(2);
            for (JsonNode entry : manifest) {
                String uri = entry.path("uri").asString();
                JsonNode contents = result(request("resources/read", Json.createObject().put("uri", uri)))
                        .path("contents").get(0);
                byte[] read = contents.path("text").asString().getBytes(StandardCharsets.UTF_8);
                McpSkillFile file = new McpSkillFile(uri, contents.path("mimeType").asString(), read);

                assertThat(file.digest()).as(uri).isEqualTo(entry.path("digest").asString());
                assertThat(file.size()).as(uri).isEqualTo(entry.path("size").asInt());
                assertThat(file.mimeType()).isEqualTo("text/markdown");
            }
            verifyNoInteractions(assembler);
        }

        @Test
        void answersAnUnknownSkillWithInvalidParams() {
            assertThat(post(skilled(), request("skills/get",
                    Json.createObject().put("uri", "skill://no-such-skill/SKILL.md"))))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }

        @Test
        void answersAnUnknownSkillFileWithInvalidParams() {
            assertThat(post(skilled(), request("resources/read",
                    Json.createObject().put("uri", "skill://report/references/missing.md"))))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }

        /**
         * A read names a file by its exact URI in a manifest: a URI that climbs out of a skill, encodes the
         * climb, has no skill at all or an empty segment matches none, and is refused like any other.
         */
        @Test
        void refusesAUriThatLeavesASkill() throws Exception {
            for (String uri : List.of("skill://report/../advise-jfr/SKILL.md", "skill://report/%2e%2e/advise-jfr/SKILL.md",
                    "skill:///etc/passwd", "skill://report//SKILL.md")) {
                assertThat(post(skilled(), request("resources/read", Json.createObject().put("uri", uri))))
                        .as(uri)
                        .hasStatusOk()
                        .bodyJson()
                        .extractingPath("$.error.code").isEqualTo(-32602);
            }
            verifyNoInteractions(assembler);
        }

        /** Discovery is {@code skills/list}; the resource list stays the profile catalogue and the server. */
        @Test
        void doesNotListSkillFilesAsResources() throws Exception {
            when(assembler.toolset()).thenReturn(toolset());

            JsonNode resources = result(request("resources/list")).path("resources");

            assertThat(resources.size()).isPositive();
            assertThat(resources.toString()).doesNotContain("skill://");
        }

        /** The prompts come from the same catalogue, one per skill. */
        @Test
        void servesTheSameSkillsAsPrompts() throws Exception {
            JsonNode prompts = result(request("prompts/list")).path("prompts");

            assertThat(prompts.size()).isEqualTo(10);
        }
    }

    /**
     * The server speaks {@code 2026-07-28} only. Over real HTTP because the headers decide as much as
     * the body does, and a controller that stopped passing them would look fine everywhere else.
     */
    @Nested
    class ProtocolVersion {

        /**
         * A client from the handshake era sends no per-request {@code _meta}: a request missing a
         * required field, {@code -32602} with a 400. It cannot fall forward on its own, so the refusal
         * names the revision it would have to speak, in the message and in {@code data.supported}.
         */
        @Test
        void refusesAnInitializeAsMalformedNamingTheRevisionItSpeaks() {
            MvcTestResult result = mvcWith(true).post().uri(URI)
                    .contentType(APPLICATION_JSON).content(LEGACY_INITIALIZE).exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$.error.code").isEqualTo(-32602);
            assertThat(result).bodyJson().extractingPath("$.error.message").asString().contains("2026-07-28");
            assertThat(result).bodyJson().extractingPath("$.error.data.supported").asArray()
                    .containsExactly("2026-07-28");
            verifyNoInteractions(assembler);
        }

        @Test
        void servesAModernRequestCarryingAllThreeHeaders() {
            when(assembler.toolset()).thenReturn(toolset());

            assertThat(TOOLS_CALL.httpHeaders()).containsOnlyKeys(
                    McpTransportHeaders.PROTOCOL_VERSION_HEADER,
                    McpTransportHeaders.METHOD_HEADER,
                    McpTransportHeaders.NAME_HEADER);
            assertThat(post(mvcWith(true), TOOLS_CALL))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resultType").asString().isEqualTo("complete");
        }

        /**
         * The conformance case over real HTTP: a supported {@code MCP-Protocol-Version} header over a
         * different {@code _meta} version is a header mismatch.
         */
        @Test
        void refusesASupportedHeaderOverADifferentMetaVersion() {
            McpTestRequests.Request mismatched = DISCOVER.editMeta(meta ->
                    meta.put("io.modelcontextprotocol/protocolVersion", "2025-11-25"));

            assertThat(post(mvcWith(true), mismatched))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32020);
        }

        /** The body alone is not enough: the controller hands the envelope the headers too. */
        @Test
        void refusesAModernBodySentWithoutItsHeaders() {
            assertThat(mvcWith(true).post().uri(URI).contentType(APPLICATION_JSON).content(DISCOVER.json()))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32020);
        }

        @Test
        void refusesANameHeaderThatDisagreesWithTheBody() {
            assertThat(post(mvcWith(true), TOOLS_CALL.withHeaders(
                    new McpTransportHeaders("2026-07-28", "tools/call", "sample_boom"))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32020);
            verifyNoInteractions(assembler);
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
            assertThat(post(mvcWith(true), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.prompts").isNotNull();
        }

        @Test
        void listsTheSkillsAsPrompts() {
            assertThat(post(mvcWith(true), request("prompts/list")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.prompts[*].name").asArray()
                    .contains("analyze-jfr", "analyze-heap", "compare-jfr", "report");
        }

        @Test
        void handsBackTheSkillBody() {
            assertThat(post(mvcWith(true), request("prompts/get", "{\"name\":\"analyze-jfr\"}")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.messages[0].content.text").asString()
                    .contains("profiles_list");
        }

        @Test
        void refusesAPromptItDoesNotHave() {
            assertThat(post(mvcWith(true), request("prompts/get", "{\"name\":\"nonsense\"}")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }
    }

    @Nested
    class Resources {

        @Test
        void advertisesTheResourceCapability() {
            assertThat(post(mvcWith(true), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.resources").isNotNull();
        }

        @Test
        void listsTheCatalogue() {
            when(assembler.toolset()).thenReturn(toolset());

            assertThat(post(mvcWith(true), request("resources/list")))
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

            assertThat(post(mvcWith(true), request("resources/templates/list")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resourceTemplates[*].uriTemplate").asArray()
                    .contains("jeffrey://profile/{profileId}/summary");
        }

        /**
         * An installation narrowed to one family must omit resources backed by missing tools.
         * Server diagnostics remain available independently of the selected tools.
         */
        @Test
        void advertisesNoResourceWhoseToolThisInstallationLeftOut() {
            when(assembler.toolset()).thenReturn(new ReflectiveToolset(new SampleTools(), "sample"));

            assertThat(post(mvcWith(true), request("resources/list")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resources[*].uri").asArray()
                    .containsExactly("jeffrey://server", "jeffrey://diagnostics");
        }

        /**
         * A URI this server does not serve is answered -32602 with a sentence naming it;
         * {@code 2026-07-28} forbids the older -32002.
         */
        @Test
        void refusesAUriItDoesNotServe() {
            when(assembler.toolset()).thenReturn(toolset());

            assertThat(post(mvcWith(true), request("resources/read", "{\"uri\":\"jeffrey://nonsense\"}")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }

        @Test
        void readsTheSummaryOfAProfileItHas() {
            when(assembler.toolset()).thenReturn(toolset());

            MvcTestResult result = post(mvcWith(true),
                    request("resources/read", "{\"uri\":\"jeffrey://profile/p-1/summary\"}")).exchange();

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.result.contents[0].text").asString()
                    .contains("p-1");
            assertThat(result).bodyJson().extractingPath("$.result.cacheScope").asString().isEqualTo("private");
        }

        /**
         * The summary is read by running {@code profiles_summary}, whose not-found arrives wrapped in
         * the tool-execution failure. The client must still get the tool's own sentence under
         * -32602 — not "-32603 Internal error", which tells it the server broke.
         */
        @Test
        void answersAnUnknownProfileWithTheToolsOwnSentence() {
            when(assembler.toolset()).thenReturn(toolset());

            MvcTestResult result = post(mvcWith(true),
                    request("resources/read", "{\"uri\":\"jeffrey://profile/p-404/summary\"}")).exchange();

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.error.code").isEqualTo(-32602);
            assertThat(result).bodyJson().extractingPath("$.error.message").asString()
                    .isEqualTo("Profile not found: p-404");
        }

        /**
         * The two templates that are not one tool's answer are listed where their families are served,
         * the way the tool-backed ones are.
         */
        @Test
        void listsTheSchemaAndFindingsTemplatesWhereTheirFamiliesAreServed() {
            when(assembler.toolset()).thenReturn(documentToolset());

            assertThat(post(mvcWith(true), request("resources/templates/list")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.resourceTemplates[*].uriTemplate").asArray()
                    .contains(SCHEMA_TEMPLATE, FINDINGS_TEMPLATE);
        }

        /**
         * The schema read end to end over a real DuckDB: the view is there as a view, the answer is the
         * record's JSON, and its link is built on the host the client reached.
         */
        @Test
        void readsTheSchemaOfAProfile() throws Exception {
            when(assembler.toolset()).thenReturn(documentToolset());
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:duckdb:", true);
            try {
                try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TABLE event_types (name VARCHAR, label VARCHAR, description VARCHAR)");
                    stmt.execute("CREATE TABLE events_raw (event_type VARCHAR, samples BIGINT, fields JSON)");
                    stmt.execute("CREATE VIEW events AS SELECT * FROM events_raw");
                    stmt.execute("INSERT INTO event_types VALUES ('jdk.ExecutionSample', 'Execution', NULL)");
                    stmt.execute("INSERT INTO events_raw VALUES ('jdk.ExecutionSample', 1, '{}')");
                }
                when(assembler.documents()).thenReturn(new McpProfileDocuments(
                        profileId -> new ProfileSchemaReader(dataSource).read(profileId),
                        profileId -> {
                            throw new AssertionError("the schema read reads no findings");
                        }));

                MvcTestResult result = post(mvcWith(true),
                        request("resources/read", "{\"uri\":\"jeffrey://profile/p-1/schema\"}")).exchange();

                JsonNode contents = contentsOf(result);
                assertThat(contents.path("mimeType").asString()).isEqualTo("application/json");
                JsonNode schema = Json.readTree(contents.path("text").asString());
                McpSchemaConformance.assertConforms(schema, McpSchemaGenerator.schemaOf(ProfileSchema.class));
                List<String> relations = new ArrayList<>();
                for (JsonNode relation : schema.path("relations")) {
                    String kind = relation.path("view").asBoolean() ? " (view)" : "";
                    relations.add(relation.path("name").asString() + kind);
                }
                assertThat(relations).contains("events (view)", "events_raw", "event_types");
                assertThat(schema.path("eventTypes").get(0).path("count").asLong()).isEqualTo(1);
                assertThat(schema.path("uiLink").asString()).isEqualTo("http://localhost/profiles/p-1/event-types");
                assertThat(result).bodyJson().extractingPath("$.result.cacheScope").asString().isEqualTo("private");
            } finally {
                dataSource.destroy();
            }
        }

        /**
         * The findings read end to end: a profile whose rules have not run answers NOT_COMPUTED with the
         * call that would run them, and the read itself runs nothing.
         */
        @Test
        void readsTheFindingsOfAProfileWithoutComputingThem() throws Exception {
            when(assembler.toolset()).thenReturn(documentToolset());
            ProfileManager manager = ProfileManagerFixture.jfrProfile("p-1");
            AutoAnalysisManager autoAnalysis = mock(AutoAnalysisManager.class);
            when(manager.autoAnalysisManager()).thenReturn(autoAnalysis);
            when(autoAnalysis.canGenerate()).thenReturn(true);
            when(assembler.documents()).thenReturn(new McpProfileDocuments(
                    profileId -> {
                        throw new AssertionError("the findings read reads no schema");
                    },
                    profileId -> new ProfileFindingsReader(manager, mock(JfrFlamegraphPanelProvider.class),
                            mock(StackSampleFlamegraphPanelProvider.class), EVERY_FAMILY).read()));

            MvcTestResult result = post(mvcWith(true),
                    request("resources/read", "{\"uri\":\"jeffrey://profile/p-1/findings\"}")).exchange();

            JsonNode contents = contentsOf(result);
            assertThat(contents.path("mimeType").asString()).isEqualTo("application/json");
            JsonNode findings = Json.readTree(contents.path("text").asString());
            McpSchemaConformance.assertConforms(findings, McpSchemaGenerator.schemaOf(ProfileFindings.class));
            assertThat(findings.path("status").asString()).isEqualTo("NOT_COMPUTED");
            assertThat(findings.path("followUp").path("nextTools").get(0).path("tool").asString())
                    .isEqualTo("jvm_autoAnalysis");
            assertThat(findings.path("uiLink").asString()).isEqualTo("http://localhost/profiles/p-1/auto-analysis");
            verify(autoAnalysis, never()).generate();
        }

        /** A document URI with nothing where the profile id goes is refused -32602, like every unknown URI. */
        @Test
        void refusesADocumentUriWithoutAProfile() {
            when(assembler.toolset()).thenReturn(documentToolset());

            assertThat(post(mvcWith(true), request("resources/read", "{\"uri\":\"jeffrey://profile//findings\"}")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }

        private static JsonNode contentsOf(MvcTestResult result) throws Exception {
            assertThat(result).hasStatusOk();
            return Json.readTree(result.getResponse().getContentAsString()).path("result").path("contents").get(0);
        }

        @Test
        void answersACursorTheCatalogueCannotReadAsInvalidParams() {
            when(assembler.toolset()).thenReturn(toolset());

            MvcTestResult result = post(mvcWith(true),
                    request("resources/read", "{\"uri\":\"jeffrey://profiles?cursor=garbage\"}")).exchange();

            assertThat(result).hasStatusOk().bodyJson().extractingPath("$.error.code").isEqualTo(-32602);
            assertThat(result).bodyJson().extractingPath("$.error.message").asString()
                    .isEqualTo("Invalid cursor: garbage");
        }
    }

    /**
     * The tasks extension over real HTTP: a call that defers hands back a task, and the task is then
     * followed, cancelled and refused through the same operation registry {@code operations_*} reads.
     * Driven by a fixture tool that defers, and by recordings_analyzeFile, a production tool that
     * defers after the short wait.
     */
    @Nested
    class Tasks {

        private final McpOperationRegistry registry = new McpOperationRegistry(FIXED_CLOCK);
        private final TaskTools taskTools = new TaskTools(registry);

        private MockMvcTester following(Predicate<OperationKind> reachable) {
            return mockMvcTesterFor(new ExternalMcpController(
                    assembler,
                    McpTestProperties.of(true, true, true, Set.of()),
                    McpTestGuards.loopback(),
                    new McpPromptRegistry(McpSkillCatalogue.fromClasspath()), mock(McpDiagnostics.class), EVERY_FAMILY,
                    OperationTasks.of(registry, reachable), McpSkillProvider.NONE));
        }

        private MockMvcTester following() {
            return following(kind -> true);
        }

        private void servingTheTaskTools() {
            when(assembler.toolset()).thenReturn(new CompositeToolset(List.of(new ReflectiveToolset(taskTools, "task"))));
        }

        private McpTestRequests.Request tasks(String method, String taskId) {
            return request(method, Json.createObject().put("taskId", taskId), McpTestRequests.tasksClient());
        }

        private JsonNode result(MockMvcTester mvc, McpTestRequests.Request request) {
            MvcTestResult result = post(mvc, request).exchange();
            assertThat(result).hasStatusOk();
            return Json.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                    .path("result");
        }

        @Test
        void discoverDeclaresTheExtension() {
            assertThat(post(following(), DISCOVER))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.capabilities.extensions").asMap()
                    .containsKey(McpClientCapabilities.TASKS_EXTENSION);
        }

        @Test
        void followsADeferredCallToTheAnswerAWaitingCallWouldHaveGot() {
            servingTheTaskTools();
            MockMvcTester mvc = following();
            String synchronous = result(mvc, toolCall("task_now", Json.createObject()))
                    .path("content").get(0).path("text").asString();

            JsonNode created = result(mvc, toolCall("task_start", Json.createObject(), McpTestRequests.tasksClient()));

            assertThat(created.path("resultType").asString()).isEqualTo("task");
            assertThat(created.path("status").asString()).isEqualTo("working");
            assertThat(created.path("ttlMs").asLong()).isEqualTo(McpOperationRegistry.RETENTION.toMillis());
            assertThat(created.path("pollIntervalMs").asLong())
                    .isEqualTo(McpOperationRegistry.TASK_POLL_INTERVAL.toMillis());
            String taskId = created.path("taskId").asString();
            assertThat(result(mvc, tasks("tasks/get", taskId)).path("status").asString()).isEqualTo("working");

            taskTools.release();

            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertThat(result(mvc, tasks("tasks/get", taskId)).path("status").asString()).isEqualTo("completed"));
            JsonNode finished = result(mvc, tasks("tasks/get", taskId));
            assertThat(finished.path("resultType").asString()).isEqualTo("complete");
            assertThat(finished.path("result").path("isError").asBoolean()).isFalse();
            assertThat(finished.path("result").path("content").get(0).path("text").asString()).isEqualTo(synchronous);
        }

        /**
         * A real tool, not a fixture: recordings_analyzeFile run for a client that declared the
         * extension hands back a task after the task budget, the task completes once the analysis
         * does, and its result is the text a client that waited is given for the same operation.
         */
        @Test
        void followsARealImportFromTheCallToTheProfileAWaitingCallerIsGiven(@TempDir Path dir) throws Exception {
            Path file = Files.writeString(dir.resolve("app.jfr"), "not really a recording, but a real file");
            RecordingsManager recordings = mock(RecordingsManager.class);
            CountDownLatch release = new CountDownLatch(1);
            when(recordings.importRecordingFromPath(file)).thenReturn(RECORDING_ID);
            when(recordings.analyzeRecording(RECORDING_ID)).thenAnswer(invocation -> {
                assertThat(release.await(60, SECONDS)).isTrue();
                return PROFILE_ID;
            });
            when(recordings.findRecording(RECORDING_ID)).thenReturn(Optional.of(new Recording(
                    RECORDING_ID, "app.jfr", null, RecordingEventSource.JDK, Instant.EPOCH, Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(60), true, PROFILE_ID, "app.jfr", List.of())));
            ProfileManager profile = mock(ProfileManager.class);
            when(profile.info()).thenReturn(new ProfileInfo(
                    PROFILE_ID, null, null, "app.jfr", RecordingEventSource.JDK, Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(60), Instant.EPOCH, true, false, RECORDING_ID));
            when(recordings.profile(PROFILE_ID)).thenReturn(Optional.of(profile));
            // The standard 45 s budget, and the task handed back after a tenth of a second.
            RecordingsMcpTools tools = RecordingsMcpToolsFixture.of(recordings, new PipelineRunRegistry<>(
                    ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), FIXED_CLOCK), registry, FIXED_CLOCK)
                    .withAnswers(new OperationAnswers(SHORT_TASK_WAIT))
                    .build();
            when(assembler.toolset())
                    .thenReturn(new CompositeToolset(List.of(new ReflectiveToolset(tools, "recordings"))));
            MockMvcTester mvc = following();
            ObjectNode arguments = Json.createObject().put("path", file.toString());

            JsonNode created;
            try {
                created = result(mvc, toolCall("recordings_analyzeFile", arguments, McpTestRequests.tasksClient()));
            } catch (AssertionError e) {
                release.countDown();
                throw e;
            }

            assertThat(created.path("resultType").asString()).isEqualTo("task");
            assertThat(created.path("status").asString()).isEqualTo("working");
            String taskId = created.path("taskId").asString();
            assertThat(result(mvc, tasks("tasks/get", taskId)).path("status").asString()).isEqualTo("working");

            release.countDown();

            await().atMost(5, SECONDS).untilAsserted(() -> assertThat(
                    result(mvc, tasks("tasks/get", taskId)).path("status").asString()).isEqualTo("completed"));
            JsonNode finished = result(mvc, tasks("tasks/get", taskId)).path("result");
            assertThat(finished.path("isError").asBoolean()).isFalse();
            String taskAnswer = finished.path("content").get(0).path("text").asString();
            assertThat(Json.readTree(taskAnswer).path("profileId").asString()).isEqualTo(PROFILE_ID);
            String waited = result(mvc, toolCall("recordings_analyzeFile", arguments))
                    .path("content").get(0).path("text").asString();
            assertThat(taskAnswer).isEqualTo(waited);
        }

        @Test
        void cancelsATask() {
            servingTheTaskTools();
            MockMvcTester mvc = following();
            String taskId = result(mvc, toolCall("task_start", Json.createObject(), McpTestRequests.tasksClient()))
                    .path("taskId").asString();

            JsonNode acknowledged = result(mvc, tasks("tasks/cancel", taskId));

            assertThat(acknowledged.path("resultType").asString()).isEqualTo("complete");
            assertThat(acknowledged.has("status")).isFalse();
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertThat(result(mvc, tasks("tasks/get", taskId)).path("status").asString()).isEqualTo("cancelled"));
        }

        @Test
        void refusesAnUnknownTaskAsInvalidParams() {
            assertThat(post(following(), tasks("tasks/get", "no-such-task")))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }

        @Test
        void refusesAClientThatDidNotDeclareTheExtension() {
            assertThat(post(following(), request("tasks/get", Json.createObject().put("taskId", "t-1"))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32021);
        }

        /** A task of a family this installation withholds is as unknown as one that never existed. */
        @Test
        void refusesATaskOfAWithheldFamilyAsInvalidParams() {
            BoundedJobs<String, String> jobs = ToolFixtures.jobs(
                    BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, FIXED_CLOCK);
            String taskId = registry.register(OperationKind.HUB_DOWNLOAD, jobs.rememberCompleted("s", "done"),
                    OperationResults.Value::new);
            MockMvcTester withoutHubs = following(kind -> !kind.reachesHub());

            assertThat(post(withoutHubs, tasks("tasks/get", taskId)))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
            assertThat(post(following(), tasks("tasks/get", taskId)))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.status").asString().isEqualTo("completed");
        }
    }

    /**
     * A real tool asking through the envelope: {@code recordings_delete} for a client that renders
     * forms answers {@code input_required}, and the retry's {@code inputResponses} reach the tool.
     */
    @Nested
    class InputRequests {

        private static final String CONFIRM_DELETION = "confirmDeletion";

        private final RecordingsManager recordings = mock(RecordingsManager.class);

        private MockMvcTester servingDelete() {
            when(recordings.findRecording(RECORDING_ID)).thenReturn(Optional.of(new Recording(
                    RECORDING_ID, "app.jfr", null, RecordingEventSource.JDK, Instant.EPOCH, Instant.EPOCH,
                    Instant.EPOCH.plusSeconds(60), true, PROFILE_ID, "app.jfr", List.of())));
            RecordingsMcpTools tools = RecordingsMcpToolsFixture.of(recordings, new PipelineRunRegistry<>(
                    ProfileInitStages.DEFINITION, PipelineRunOptions.unbounded(), FIXED_CLOCK),
                    new McpOperationRegistry(FIXED_CLOCK), FIXED_CLOCK).build();
            when(assembler.toolset())
                    .thenReturn(new CompositeToolset(List.of(new ReflectiveToolset(tools, "recordings"))));
            return mvcWith(true);
        }

        private JsonNode result(MockMvcTester mvc, McpTestRequests.Request request) {
            MvcTestResult result = post(mvc, request).exchange();
            assertThat(result).hasStatusOk();
            return Json.readTree(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
                    .path("result");
        }

        private McpTestRequests.Request delete() {
            return toolCall("recordings_delete", Json.createObject().put("recordingId", RECORDING_ID),
                    McpTestRequests.elicitingClient());
        }

        private McpTestRequests.Request retried(String responseJson) {
            return delete().withId(Json.readTree("2")).editBody(body -> ((ObjectNode) body.get("params"))
                    .set("inputResponses", Json.readTree("{\"" + CONFIRM_DELETION + "\":" + responseJson + "}")));
        }

        @Test
        void asksWithServerInfoAndNoCacheHint() {
            MockMvcTester mvc = servingDelete();

            JsonNode asked = result(mvc, delete());

            assertThat(asked.path("resultType").asString()).isEqualTo("input_required");
            assertThat(asked.path("_meta").path("io.modelcontextprotocol/serverInfo").path("name").asString())
                    .isEqualTo("jeffrey");
            assertThat(asked.has("ttlMs")).isFalse();
            assertThat(asked.has("cacheScope")).isFalse();
            assertThat(asked.has("isError")).isFalse();
            JsonNode question = asked.path("inputRequests").path(CONFIRM_DELETION);
            assertThat(question.path("method").asString()).isEqualTo("elicitation/create");
            assertThat(question.path("params").path("requestedSchema").path("properties").has("confirm")).isTrue();
            verify(recordings, never()).deleteRecording(RECORDING_ID);
        }

        @Test
        void theRetrysAnswerReachesTheTool() {
            MockMvcTester mvc = servingDelete();

            JsonNode done = result(mvc, retried("{\"action\":\"accept\",\"content\":{\"confirm\":true}}"));

            assertThat(done.path("resultType").asString()).isEqualTo("complete");
            assertThat(done.path("isError").asBoolean()).isFalse();
            assertThat(Json.readTree(done.path("content").get(0).path("text").asString())
                    .path("recordingId").asString()).isEqualTo(RECORDING_ID);
            verify(recordings).deleteRecording(RECORDING_ID);
        }

        @Test
        void aDeclinedRetryCompletesWithoutDeleting() {
            MockMvcTester mvc = servingDelete();

            JsonNode done = result(mvc, retried("{\"action\":\"decline\"}"));

            assertThat(done.path("resultType").asString()).isEqualTo("complete");
            assertThat(done.path("isError").asBoolean()).isFalse();
            assertThat(Json.readTree(done.path("content").get(0).path("text").asString())
                    .path("status").asString()).isEqualTo("NOT_CONFIRMED");
            verify(recordings, never()).deleteRecording(RECORDING_ID);
        }

        @Test
        void aClientThatDeclaredNoElicitationIsNeverAsked() {
            MockMvcTester mvc = servingDelete();

            JsonNode done = result(mvc, toolCall("recordings_delete",
                    Json.createObject().put("recordingId", RECORDING_ID)));

            assertThat(done.path("resultType").asString()).isEqualTo("complete");
            assertThat(done.has("inputRequests")).isFalse();
            verify(recordings).deleteRecording(RECORDING_ID);
        }
    }

    /**
     * A tool that starts slow work in the operation registry and defers to it, and its synchronous
     * twin answering what the slow work answers once it finishes.
     */
    public static class TaskTools {

        private static final String VALUE = "rows";

        private final McpOperationRegistry registry;
        private final BoundedJobs<String, String> jobs = ToolFixtures.jobs(
                BoundedJobs.WAIT_BUDGET, BoundedJobs.COMPLETED_RETENTION, FIXED_CLOCK);
        private final CountDownLatch released = new CountDownLatch(1);

        TaskTools(McpOperationRegistry registry) {
            this.registry = registry;
        }

        void release() {
            released.countDown();
        }

        @Tool(description = "Starts slow work and defers to it")
        public McpToolOutcome start() {
            OperationHandle<String> handle = jobs.startOrJoin("work", false, value -> true, control -> {
                try {
                    released.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    control.checkCancellation();
                    throw new IllegalStateException(e);
                }
                return VALUE;
            });
            String operationId = registry.register(OperationKind.HEAP_OQL, handle, OperationResults.Value::new,
                    value -> McpToolResult.text(answer(value)));
            return new McpToolOutcome.Deferred(operationId);
        }

        @Tool(description = "Answers at once")
        public String now() {
            return answer(VALUE);
        }

        private static String answer(String value) {
            return "answer:" + value;
        }
    }

    /**
     * Stands in for the profiles family, so {@code jeffrey://profiles} and the summary template have
     * the tools behind them that the real assembler would provide.
     */
    public static class ProfilesTools {

        private static final String KNOWN_PROFILE = "p-1";

        @Tool(description = "Every analysed profile")
        public String list(
                @ToolParam(required = false, description = "continuation") String cursor) {
            if (cursor != null && !cursor.isEmpty()) {
                throw new IllegalArgumentException("Invalid cursor: " + cursor);
            }
            return "| id |\n| -- |\n| p-1 |";
        }

        @Tool(description = "What one profile holds")
        public String summary(
                @ToolParam(required = false, description = "profile") String profileId) {
            if (!KNOWN_PROFILE.equals(profileId)) {
                throw Exceptions.profileNotFound(profileId);
            }
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

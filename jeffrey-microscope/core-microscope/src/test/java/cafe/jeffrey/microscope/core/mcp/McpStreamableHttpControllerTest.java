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

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.provider.profile.api.DatabaseManagerResolver;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static cafe.jeffrey.microscope.core.web.MockMvcSupport.mockMvcTesterFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The per-profile endpoint the headless CLI backend connects to.
 * <p>
 * It shares its envelope with the installation-wide endpoint and differs in the two things this
 * covers: it is scoped by query parameter rather than by tool argument, and its gate is a live
 * setting read per request rather than a property fixed at startup. Neither had a test, so nothing
 * said that switching the provider away closes it, or that an unknown toolset is refused rather than
 * served empty.
 */
@ExtendWith(MockitoExtension.class)
class McpStreamableHttpControllerTest {

    private static final String URI = "/api/internal/mcp/claude-code";
    private static final String PROFILE_ID = "profile-1";
    private static final String CLAUDE_CODE = "claude-code";

    private static final String INITIALIZE = """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-06-18","capabilities":{}}}""";
    private static final String TOOLS_LIST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";

    @Mock
    ProfileManagerResolver profileManagerResolver;

    @Mock
    DatabaseManagerResolver databaseManagerResolver;

    @Mock
    SettingsStore settingsStore;

    private MockMvcTester mvc() {
        return mockMvcTesterFor(new McpStreamableHttpController(
                profileManagerResolver, databaseManagerResolver, settingsStore));
    }

    private void providerIs(String provider) {
        when(settingsStore.getString(eq(MicroscopeSettingKeys.AI_PROVIDER), anyString()))
                .thenReturn(provider);
    }

    @Nested
    class ProviderGate {

        /**
         * The gate is read per request rather than at wiring time, so that switching the provider in
         * the UI closes the endpoint without a restart. A closed one answers 404: a server that is
         * not serving should look like no server, not like one refusing to talk.
         */
        @Test
        void answers404WhileAnotherProviderIsSelected() {
            providerIs(MicroscopeSettingKeys.PROVIDER_NONE);

            assertThat(mvc().post().uri(URI)
                    .param("profileId", PROFILE_ID)
                    .param("toolset", "jfr")
                    .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
                    .hasStatus(404);
        }

        /**
         * Nothing about the profile is touched while the gate is shut, so a closed endpoint cannot be
         * used to find out which profiles exist or to open one.
         */
        @Test
        void resolvesNoProfileWhileTheGateIsShut() {
            providerIs(MicroscopeSettingKeys.PROVIDER_NONE);

            mvc().post().uri(URI)
                    .param("profileId", PROFILE_ID)
                    .param("toolset", "jfr")
                    .contentType(MediaType.APPLICATION_JSON).content(TOOLS_LIST)
                    .exchange();

            verifyNoInteractions(profileManagerResolver, databaseManagerResolver);
        }

        @Test
        void answersTheEnvelopeWhenClaudeCodeIsSelected() {
            providerIs(CLAUDE_CODE);

            assertThat(mvc().post().uri(URI)
                    .param("profileId", PROFILE_ID)
                    .param("toolset", "jfr")
                    .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.result.serverInfo.name").asString().isEqualTo("jeffrey");
        }

        /**
         * initialize is answered before any toolset is built, so a profile that cannot be opened does
         * not stop the client from connecting and reading the error from its first real call.
         */
        @Test
        void answersInitializeWithoutBuildingTheToolset() {
            providerIs(CLAUDE_CODE);

            mvc().post().uri(URI)
                    .param("profileId", PROFILE_ID)
                    .param("toolset", "jfr")
                    .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE)
                    .exchange();

            verifyNoInteractions(profileManagerResolver, databaseManagerResolver);
        }
    }

    @Nested
    class ToolsetScope {

        /**
         * The two names it serves are jfr and heap. Anything else is the caller naming a family that
         * does not exist, which is a bad argument rather than an empty result.
         */
        @Test
        void refusesAToolsetItDoesNotServe() {
            providerIs(CLAUDE_CODE);

            assertThat(mvc().post().uri(URI)
                    .param("profileId", PROFILE_ID)
                    .param("toolset", "nonsense")
                    .contentType(MediaType.APPLICATION_JSON).content(TOOLS_LIST))
                    .hasStatusOk()
                    .bodyJson()
                    .extractingPath("$.error.code").isEqualTo(-32602);
        }
    }
}

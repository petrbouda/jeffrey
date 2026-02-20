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

package pbouda.jeffrey.platform.manager.workspace.remote;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.TestContext;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyException;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings;
import pbouda.jeffrey.shared.common.model.EffectiveProfilerSettings.SettingsLevel;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteProfilerClientImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SETTINGS_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/profiler/settings";

    private RemoteProfilerClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteProfilerClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class FetchProfilerSettings {

        @Test
        void returnsEffectiveSettings_onSuccess() throws JsonProcessingException {
            server.expect(requestTo(SETTINGS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(sampleProfilerSettingsResponse()),
                            MediaType.APPLICATION_JSON));

            EffectiveProfilerSettings result = client.fetchProfilerSettings(WORKSPACE_ID, PROJECT_ID);

            assertEquals("cpu=true,alloc=true", result.agentSettings());
            assertEquals(SettingsLevel.PROJECT, result.level());
            server.verify();
        }

        @Test
        void returnsNoneSettings_whenBodyIsNull() {
            server.expect(requestTo(SETTINGS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            EffectiveProfilerSettings result = client.fetchProfilerSettings(WORKSPACE_ID, PROJECT_ID);

            assertEquals(SettingsLevel.NONE, result.level());
            assertNull(result.agentSettings());
            server.verify();
        }
    }

    @Nested
    class UpsertProfilerSettings {

        @Test
        void completesSuccessfully_onSuccess() {
            server.expect(requestTo(SETTINGS_URL))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withNoContent());

            assertDoesNotThrow(() ->
                    client.upsertProfilerSettings(WORKSPACE_ID, PROJECT_ID, "cpu=true"));
            server.verify();
        }
    }

    @Nested
    class DeleteProfilerSettings {

        @Test
        void completesSuccessfully_onSuccess() {
            server.expect(requestTo(SETTINGS_URL))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withNoContent());

            assertDoesNotThrow(() -> client.deleteProfilerSettings(WORKSPACE_ID, PROJECT_ID));
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onServerError() {
            server.expect(requestTo(SETTINGS_URL))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Server error")));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class,
                    () -> client.deleteProfilerSettings(WORKSPACE_ID, PROJECT_ID));

            assertEquals(ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getCode());
            server.verify();
        }
    }
}

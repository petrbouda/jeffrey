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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.TestContext;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteDiscoveryClient.WorkspaceResult;
import pbouda.jeffrey.platform.resources.response.ProjectResponse;
import pbouda.jeffrey.platform.resources.response.PublicApiInfoResponse;
import pbouda.jeffrey.platform.resources.response.WorkspaceResponse;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyException;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteDiscoveryClientImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RemoteDiscoveryClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteDiscoveryClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class Info {

        @Test
        void returnsPublicApiInfo_onSuccess() {
            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            "{\"version\":\"1.2.3\",\"apiVersion\":1}",
                            MediaType.APPLICATION_JSON));

            PublicApiInfoResponse result = client.info();

            assertEquals("1.2.3", result.version());
            assertEquals(1, result.apiVersion());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onServerError() {
            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Server error")));

            assertThrows(RemoteJeffreyException.class, () -> client.info());
            server.verify();
        }
    }

    @Nested
    class AllWorkspaces {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<WorkspaceResponse> workspaces = List.of(sampleWorkspaceResponse());

            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(workspaces),
                            MediaType.APPLICATION_JSON));

            List<WorkspaceResponse> result = client.allWorkspaces();

            assertEquals(1, result.size());
            assertEquals(WORKSPACE_ID, result.getFirst().id());
            assertEquals("Test Workspace", result.getFirst().name());
            server.verify();
        }

        @Test
        void returnsEmptyList_whenServerReturnsEmptyArray() {
            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

            List<WorkspaceResponse> result = client.allWorkspaces();

            assertTrue(result.isEmpty());
            server.verify();
        }
    }

    @Nested
    class Workspace {

        @Test
        void returnsAvailable_withWorkspaceInfo_onSuccess() throws JsonProcessingException {
            WorkspaceResponse wsResponse = sampleWorkspaceResponse();

            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(wsResponse),
                            MediaType.APPLICATION_JSON));

            WorkspaceResult result = client.workspace(WORKSPACE_ID);

            assertEquals(WorkspaceStatus.AVAILABLE, result.status());
            assertNotNull(result.info());
            assertEquals(WorkspaceType.REMOTE, result.info().type());
            assertEquals(WORKSPACE_ID, result.info().originId());
            assertEquals("Test Workspace", result.info().name());
            server.verify();
        }

        @Test
        void returnsOffline_whenRemoteJeffreyUnavailable() {
            // Simulate connection failure by making the invocation throw ResourceAccessException
            // The MockRestServiceServer approach: we create the client with an invoker that will
            // throw ResourceAccessException, which is wrapped into RemoteJeffreyUnavailableException
            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(request -> {
                        throw new java.io.IOException("Connection refused");
                    });

            WorkspaceResult result = client.workspace(WORKSPACE_ID);

            assertEquals(WorkspaceStatus.OFFLINE, result.status());
            assertNull(result.info());
        }

        @Test
        void returnsUnavailable_onAnyOtherException() {
            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Server error")));

            WorkspaceResult result = client.workspace(WORKSPACE_ID);

            assertEquals(WorkspaceStatus.UNAVAILABLE, result.status());
            assertNull(result.info());
            server.verify();
        }
    }

    @Nested
    class AllProjects {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<ProjectResponse> projects = List.of(sampleProjectResponse());

            server.expect(requestTo(REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID + "/projects"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(projects),
                            MediaType.APPLICATION_JSON));

            List<ProjectResponse> result = client.allProjects(WORKSPACE_ID);

            assertEquals(1, result.size());
            assertEquals(PROJECT_ID, result.getFirst().id());
            assertEquals("Test Project", result.getFirst().name());
            server.verify();
        }
    }
}

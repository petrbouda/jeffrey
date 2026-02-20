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
import pbouda.jeffrey.platform.resources.response.InstanceResponse;
import pbouda.jeffrey.platform.resources.response.InstanceSessionResponse;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteInstancesClientImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INSTANCES_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/instances";
    private static final String INSTANCE_URL = INSTANCES_URL + "/" + INSTANCE_ID;
    private static final String INSTANCE_SESSIONS_URL = INSTANCE_URL + "/sessions";

    private RemoteInstancesClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteInstancesClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class ProjectInstances {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<InstanceResponse> instances = List.of(sampleInstanceResponse());

            server.expect(requestTo(INSTANCES_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(instances), MediaType.APPLICATION_JSON));

            List<InstanceResponse> result = client.projectInstances(WORKSPACE_ID, PROJECT_ID);

            assertEquals(1, result.size());
            assertEquals(INSTANCE_ID, result.getFirst().id());
            assertEquals("host-1", result.getFirst().hostname());
            server.verify();
        }

        @Test
        void returnsEmptyList_whenBodyIsNull() {
            server.expect(requestTo(INSTANCES_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            List<InstanceResponse> result = client.projectInstances(WORKSPACE_ID, PROJECT_ID);

            assertTrue(result.isEmpty());
            server.verify();
        }
    }

    @Nested
    class ProjectInstance {

        @Test
        void returnsInstance_onSuccess() throws JsonProcessingException {
            server.expect(requestTo(INSTANCE_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(sampleInstanceResponse()),
                            MediaType.APPLICATION_JSON));

            InstanceResponse result = client.projectInstance(WORKSPACE_ID, PROJECT_ID, INSTANCE_ID);

            assertEquals(INSTANCE_ID, result.id());
            assertEquals("ACTIVE", result.status());
            assertEquals(2, result.sessionCount());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_whenNotFound() {
            server.expect(requestTo(INSTANCE_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.CLIENT, ErrorCode.RESOURCE_NOT_FOUND, "Instance not found")));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class,
                    () -> client.projectInstance(WORKSPACE_ID, PROJECT_ID, INSTANCE_ID));

            assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());
            server.verify();
        }
    }

    @Nested
    class ProjectInstanceSessions {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<InstanceSessionResponse> sessions = List.of(sampleInstanceSessionResponse());

            server.expect(requestTo(INSTANCE_SESSIONS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(sessions), MediaType.APPLICATION_JSON));

            List<InstanceSessionResponse> result =
                    client.projectInstanceSessions(WORKSPACE_ID, PROJECT_ID, INSTANCE_ID);

            assertEquals(1, result.size());
            assertEquals(SESSION_ID, result.getFirst().id());
            assertTrue(result.getFirst().isActive());
            server.verify();
        }

        @Test
        void returnsEmptyList_whenBodyIsNull() {
            server.expect(requestTo(INSTANCE_SESSIONS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            List<InstanceSessionResponse> result =
                    client.projectInstanceSessions(WORKSPACE_ID, PROJECT_ID, INSTANCE_ID);

            assertTrue(result.isEmpty());
            server.verify();
        }
    }
}

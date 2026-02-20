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
import pbouda.jeffrey.platform.resources.response.ImportantMessageResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteMessagesClientImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MESSAGES_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/messages";
    private static final String ALERTS_URL = MESSAGES_URL + "/alerts";

    private RemoteMessagesClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteMessagesClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class GetMessages {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<ImportantMessageResponse> messages = List.of(sampleMessageResponse());

            server.expect(requestTo(MESSAGES_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(messages), MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getMessages(WORKSPACE_ID, PROJECT_ID, null, null);

            assertEquals(1, result.size());
            assertEquals("Test Title", result.getFirst().title());
            assertEquals("MESSAGE", result.getFirst().type());
            server.verify();
        }

        @Test
        void returnsEmptyList_whenBodyIsNull() {
            server.expect(requestTo(MESSAGES_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getMessages(WORKSPACE_ID, PROJECT_ID, null, null);

            assertTrue(result.isEmpty());
            server.verify();
        }

        @Test
        void passesQueryParams_whenStartAndEndProvided() throws JsonProcessingException {
            List<ImportantMessageResponse> messages = List.of(sampleMessageResponse());

            server.expect(requestTo(MESSAGES_URL + "?start=100&end=200"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(messages), MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getMessages(WORKSPACE_ID, PROJECT_ID, 100L, 200L);

            assertEquals(1, result.size());
            server.verify();
        }

        @Test
        void omitsQueryParams_whenStartAndEndAreNull() throws JsonProcessingException {
            List<ImportantMessageResponse> messages = List.of(sampleMessageResponse());

            server.expect(requestTo(MESSAGES_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(messages), MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getMessages(WORKSPACE_ID, PROJECT_ID, null, null);

            assertEquals(1, result.size());
            server.verify();
        }
    }

    @Nested
    class GetAlerts {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<ImportantMessageResponse> alerts = List.of(sampleAlertResponse());

            server.expect(requestTo(ALERTS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(alerts), MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getAlerts(WORKSPACE_ID, PROJECT_ID, null, null);

            assertEquals(1, result.size());
            assertEquals("Alert Title", result.getFirst().title());
            assertTrue(result.getFirst().isAlert());
            server.verify();
        }

        @Test
        void returnsEmptyList_whenBodyIsNull() {
            server.expect(requestTo(ALERTS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getAlerts(WORKSPACE_ID, PROJECT_ID, null, null);

            assertTrue(result.isEmpty());
            server.verify();
        }

        @Test
        void passesQueryParams_whenProvided() throws JsonProcessingException {
            List<ImportantMessageResponse> alerts = List.of(sampleAlertResponse());

            server.expect(requestTo(ALERTS_URL + "?start=500&end=1000"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(alerts), MediaType.APPLICATION_JSON));

            List<ImportantMessageResponse> result = client.getAlerts(WORKSPACE_ID, PROJECT_ID, 500L, 1000L);

            assertEquals(1, result.size());
            server.verify();
        }
    }
}

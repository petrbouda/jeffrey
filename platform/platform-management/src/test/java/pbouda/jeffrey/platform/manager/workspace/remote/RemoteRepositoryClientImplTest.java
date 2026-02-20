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
import pbouda.jeffrey.platform.resources.response.RecordingSessionResponse;
import pbouda.jeffrey.platform.resources.response.RepositoryStatisticsResponse;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.ErrorType;
import pbouda.jeffrey.shared.common.exception.RemoteJeffreyException;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteRepositoryClientImplTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SESSIONS_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/repository/sessions";
    private static final String SESSION_URL = SESSIONS_URL + "/" + SESSION_ID;
    private static final String STATISTICS_URL = REMOTE_URI + "/api/public/workspaces/" + WORKSPACE_ID
            + "/projects/" + PROJECT_ID + "/repository/statistics";

    private RemoteRepositoryClientImpl client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        client = new RemoteRepositoryClientImpl(ctx.invoker());
        server = ctx.server();
    }

    @Nested
    class RecordingSessions {

        @Test
        void returnsList_onSuccess() throws JsonProcessingException {
            List<RecordingSessionResponse> sessions = List.of(sampleSessionResponse());

            server.expect(requestTo(SESSIONS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(MAPPER.writeValueAsString(sessions), MediaType.APPLICATION_JSON));

            List<RecordingSessionResponse> result = client.recordingSessions(WORKSPACE_ID, PROJECT_ID);

            assertEquals(1, result.size());
            assertEquals(SESSION_ID, result.getFirst().id());
            assertEquals(RecordingStatus.FINISHED, result.getFirst().status());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_whenProjectNotFound() {
            server.expect(requestTo(SESSIONS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.CLIENT, ErrorCode.PROJECT_NOT_FOUND, "Project not found")));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class,
                    () -> client.recordingSessions(WORKSPACE_ID, PROJECT_ID));

            assertEquals(ErrorCode.PROJECT_NOT_FOUND, ex.getCode());
            server.verify();
        }
    }

    @Nested
    class RecordingSession {

        @Test
        void returnsSession_onSuccess() throws JsonProcessingException {
            server.expect(requestTo(SESSION_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(sampleSessionResponse()),
                            MediaType.APPLICATION_JSON));

            RecordingSessionResponse result = client.recordingSession(WORKSPACE_ID, PROJECT_ID, SESSION_ID);

            assertEquals(SESSION_ID, result.id());
            assertEquals("session-one", result.name());
            assertEquals(1, result.files().size());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_whenSessionNotFound() {
            server.expect(requestTo(SESSION_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(ErrorType.CLIENT, ErrorCode.RECORDING_SESSION_NOT_FOUND, "Session not found")));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class,
                    () -> client.recordingSession(WORKSPACE_ID, PROJECT_ID, SESSION_ID));

            assertEquals(ErrorCode.RECORDING_SESSION_NOT_FOUND, ex.getCode());
            server.verify();
        }
    }

    @Nested
    class RepositoryStatistics {

        @Test
        void returnsStatistics_onSuccess() throws JsonProcessingException {
            server.expect(requestTo(STATISTICS_URL))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            MAPPER.writeValueAsString(sampleStatisticsResponse()),
                            MediaType.APPLICATION_JSON));

            RepositoryStatisticsResponse result = client.repositoryStatistics(WORKSPACE_ID, PROJECT_ID);

            assertEquals(2, result.totalSessions());
            assertEquals(RecordingStatus.FINISHED, result.sessionStatus());
            assertEquals(2048L, result.totalSize());
            assertEquals(2, result.jfrFiles());
            server.verify();
        }
    }

    @Nested
    class DeleteSession {

        @Test
        void completesSuccessfully_onSuccess() {
            server.expect(requestTo(SESSION_URL))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withNoContent());

            assertDoesNotThrow(() -> client.deleteSession(WORKSPACE_ID, PROJECT_ID, SESSION_ID));
            server.verify();
        }
    }
}

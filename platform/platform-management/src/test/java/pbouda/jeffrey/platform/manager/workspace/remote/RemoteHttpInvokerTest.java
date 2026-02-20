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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import pbouda.jeffrey.shared.common.exception.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pbouda.jeffrey.platform.manager.workspace.remote.RemoteClientTestSupport.*;

class RemoteHttpInvokerTest {

    private RemoteHttpInvoker invoker;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        TestContext ctx = createInvokerAndServer();
        invoker = ctx.invoker();
        server = ctx.server();
    }

    @Nested
    class GetInvocation {

        @Test
        void returnsResponseBody_onSuccess() {
            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"version\":\"1.0\",\"apiVersion\":1}", MediaType.APPLICATION_JSON));

            ResponseEntity<String> result = invoker.get(() ->
                    invoker.restClient().get()
                            .uri("/api/public/info")
                            .retrieve()
                            .toEntity(String.class));

            assertNotNull(result.getBody());
            assertTrue(result.getBody().contains("1.0"));
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onClientError() {
            String errorBody = errorJson(ErrorType.CLIENT, ErrorCode.PROJECT_NOT_FOUND, "Project not found");

            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorBody));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class, () ->
                    invoker.get(() -> invoker.restClient().get()
                            .uri("/api/public/info")
                            .retrieve()
                            .toEntity(String.class)));

            assertEquals(ErrorCode.PROJECT_NOT_FOUND, ex.getCode());
            assertEquals(ErrorType.CLIENT, ex.getType());
            assertEquals(REMOTE_URI, ex.getRemoteUri());
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onServerError() {
            String errorBody = errorJson(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Internal error");

            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorBody));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class, () ->
                    invoker.get(() -> invoker.restClient().get()
                            .uri("/api/public/info")
                            .retrieve()
                            .toEntity(String.class)));

            assertEquals(ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getCode());
            assertEquals(ErrorType.INTERNAL, ex.getType());
            server.verify();
        }

        @Test
        void throwsJeffreyInternalException_onNonJsonError() {
            server.expect(requestTo(REMOTE_URI + "/api/public/info"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                            .contentType(MediaType.TEXT_HTML)
                            .body("<html>Bad Gateway</html>"));

            JeffreyInternalException ex = assertThrows(JeffreyInternalException.class, () ->
                    invoker.get(() -> invoker.restClient().get()
                            .uri("/api/public/info")
                            .retrieve()
                            .toEntity(String.class)));

            assertTrue(ex.getMessage().contains("502"));
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onResourceAccessException() {
            RemoteJeffreyUnavailableException ex = assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    invoker.get(() -> {
                        throw new ResourceAccessException("Connection refused");
                    }));

            assertTrue(ex.getMessage().contains(REMOTE_URI.toString()));
        }
    }

    @Nested
    class PostInvocation {

        @Test
        void returnsResponseBody_onSuccess() {
            server.expect(requestTo(REMOTE_URI + "/api/public/test"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));

            ResponseEntity<String> result = invoker.post(() ->
                    invoker.restClient().post()
                            .uri("/api/public/test")
                            .retrieve()
                            .toEntity(String.class));

            assertNotNull(result.getBody());
            assertTrue(result.getBody().contains("ok"));
            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onClientError() {
            String errorBody = errorJson(ErrorType.CLIENT, ErrorCode.RECORDING_SESSION_NOT_FOUND, "Session not found");

            server.expect(requestTo(REMOTE_URI + "/api/public/test"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorBody));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class, () ->
                    invoker.post(() -> invoker.restClient().post()
                            .uri("/api/public/test")
                            .retrieve()
                            .toEntity(String.class)));

            assertEquals(ErrorCode.RECORDING_SESSION_NOT_FOUND, ex.getCode());
            server.verify();
        }
    }

    @Nested
    class DeleteInvocation {

        @Test
        void completesNormally_onSuccess() {
            server.expect(requestTo(REMOTE_URI + "/api/public/test"))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withNoContent());

            assertDoesNotThrow(() ->
                    invoker.delete(() -> invoker.restClient().delete()
                            .uri("/api/public/test")
                            .retrieve()
                            .toBodilessEntity()));

            server.verify();
        }

        @Test
        void throwsRemoteJeffreyException_onError() {
            String errorBody = errorJson(ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Server error");

            server.expect(requestTo(REMOTE_URI + "/api/public/test"))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withServerError()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(errorBody));

            RemoteJeffreyException ex = assertThrows(RemoteJeffreyException.class, () ->
                    invoker.delete(() -> invoker.restClient().delete()
                            .uri("/api/public/test")
                            .retrieve()
                            .toBodilessEntity()));

            assertEquals(ErrorCode.UNKNOWN_ERROR_RESPONSE, ex.getCode());
            server.verify();
        }
    }

    @Nested
    class StreamingInvocation {

        @Test
        void completesNormally_whenInvocationSucceeds() {
            assertDoesNotThrow(() ->
                    invoker.streaming(() -> 200));
        }

        @Test
        void throwsRemoteJeffreyUnavailable_onResourceAccessException() {
            RemoteJeffreyUnavailableException ex = assertThrows(RemoteJeffreyUnavailableException.class, () ->
                    invoker.streaming(() -> {
                        throw new ResourceAccessException("Connection refused");
                    }));

            assertTrue(ex.getMessage().contains(REMOTE_URI.toString()));
        }
    }

    @Nested
    class ToRemoteError {

        @Test
        void returnsRemoteJeffreyException_fromValidErrorResponse() {
            ErrorResponse errorResponse = new ErrorResponse(
                    ErrorType.CLIENT, ErrorCode.PROJECT_NOT_FOUND, "Project not found");

            JeffreyException ex = invoker.toRemoteError(404, () -> errorResponse);

            assertInstanceOf(RemoteJeffreyException.class, ex);
            RemoteJeffreyException remote = (RemoteJeffreyException) ex;
            assertEquals(ErrorCode.PROJECT_NOT_FOUND, remote.getCode());
            assertEquals(ErrorType.CLIENT, remote.getType());
            assertEquals(REMOTE_URI, remote.getRemoteUri());
        }

        @Test
        void returnsRemoteJeffreyException_withInternalError() {
            ErrorResponse errorResponse = new ErrorResponse(
                    ErrorType.INTERNAL, ErrorCode.UNKNOWN_ERROR_RESPONSE, "Internal error");

            JeffreyException ex = invoker.toRemoteError(500, () -> errorResponse);

            assertInstanceOf(RemoteJeffreyException.class, ex);
            assertEquals(ErrorType.INTERNAL, ex.getType());
        }

        @Test
        void returnsJeffreyInternalException_whenParsingFails() {
            JeffreyException ex = invoker.toRemoteError(502, () -> {
                throw new RuntimeException("Cannot parse error response");
            });

            assertInstanceOf(JeffreyInternalException.class, ex);
            assertTrue(ex.getMessage().contains("502"));
        }
    }
}
